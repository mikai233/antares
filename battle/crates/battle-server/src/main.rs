use std::collections::HashMap;
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use clap::Parser;
use hmac::{Hmac, Mac};
use prost::Message;
use serde::Serialize;
use sha2::Sha256;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::Mutex;
use tracing::{error, info, warn};
use zookeeper::{Acl, CreateMode, WatchedEvent, Watcher, ZkError, ZooKeeper};

mod protocol {
    #![allow(dead_code)]

    include!(concat!(env!("OUT_DIR"), "/com.mikai233.protocol.rs"));
}

use protocol::{BattleFrameKind, BattleFrameNotify, BattleFrameReq};

const MAX_FRAME_BYTES: usize = 1024 * 1024;
type HmacSha256 = Hmac<Sha256>;

#[derive(Debug, Parser)]
struct Cli {
    #[clap(long, default_value = "127.0.0.1:7001")]
    bind: String,

    #[clap(long, default_value = "local-dev-battle-secret")]
    token_secret: String,

    #[clap(long)]
    zookeeper: Option<String>,

    #[clap(long, default_value = "/antares/battle/instances")]
    discovery_path: String,

    #[clap(long)]
    instance_id: Option<String>,

    #[clap(long)]
    public_host: Option<String>,

    #[clap(long)]
    public_port: Option<u16>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct BattleInstanceRegistration {
    instance_id: String,
    host: String,
    port: u16,
    transport: String,
    load: i32,
    state: String,
}

struct NoopWatcher;

impl Watcher for NoopWatcher {
    fn handle(&self, _event: WatchedEvent) {}
}

#[derive(Clone, Default)]
struct BattleRuntime {
    accepted_frames_by_battle_id: Arc<Mutex<HashMap<i64, i64>>>,
    token_secret: Arc<String>,
}

impl BattleRuntime {
    fn new(token_secret: String) -> Self {
        Self {
            accepted_frames_by_battle_id: Arc::default(),
            token_secret: Arc::new(token_secret),
        }
    }

    async fn handle(&self, request: BattleFrameReq) -> BattleFrameNotify {
        if let Err(error) = self.validate_token(&request) {
            warn!(
                battle_id = request.battle_id,
                player_id = request.player_id,
                sequence = request.sequence,
                %error,
                "battle frame rejected"
            );
            return BattleFrameNotify {
                battle_id: request.battle_id,
                player_id: request.player_id,
                sequence: request.sequence,
                kind: BattleFrameKind::BattleError as i32,
                payload: Vec::new(),
                error: error.to_string(),
            };
        }
        let accepted = {
            let mut counts = self.accepted_frames_by_battle_id.lock().await;
            let accepted = counts.entry(request.battle_id).or_insert(0);
            *accepted += 1;
            *accepted
        };
        info!(
            battle_id = request.battle_id,
            player_id = request.player_id,
            sequence = request.sequence,
            accepted,
            "battle frame accepted"
        );
        BattleFrameNotify {
            battle_id: request.battle_id,
            player_id: request.player_id,
            sequence: request.sequence,
            kind: BattleFrameKind::BattleAck as i32,
            payload: accepted.to_string().into_bytes(),
            error: String::new(),
        }
    }

    fn validate_token(&self, request: &BattleFrameReq) -> anyhow::Result<()> {
        let parts = request.token.split('.').collect::<Vec<_>>();
        anyhow::ensure!(parts.len() == 5, "invalid battle token format");
        anyhow::ensure!(parts[0] == "v1", "unsupported battle token version");
        let battle_id = parts[1].parse::<i64>()?;
        let player_id = parts[2].parse::<i64>()?;
        let expires_at = parts[3].parse::<i64>()?;
        anyhow::ensure!(battle_id == request.battle_id, "battle token battleId mismatch");
        anyhow::ensure!(player_id == request.player_id, "battle token playerId mismatch");
        anyhow::ensure!(expires_at > now_millis(), "battle token expired");

        let body = format!("{}.{}.{}.{}", parts[0], parts[1], parts[2], parts[3]);
        let signature = URL_SAFE_NO_PAD.decode(parts[4])?;
        let mut mac = HmacSha256::new_from_slice(self.token_secret.as_bytes())?;
        mac.update(body.as_bytes());
        mac.verify_slice(&signature)?;
        Ok(())
    }
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt::init();
    let cli = Cli::parse();
    let listener = TcpListener::bind(&cli.bind).await?;
    let _registration = register_instance(&cli)?;
    let runtime = BattleRuntime::new(cli.token_secret);
    info!("battle server listening on {}", cli.bind);

    loop {
        let (stream, peer) = listener.accept().await?;
        let runtime = runtime.clone();
        tokio::spawn(async move {
            if let Err(error) = handle_connection(runtime, stream).await {
                warn!(%peer, %error, "battle connection failed");
            }
        });
    }

}

async fn handle_connection(runtime: BattleRuntime, mut stream: TcpStream) -> anyhow::Result<()> {
    let frame_len = stream.read_u32().await? as usize;
    anyhow::ensure!(
        frame_len <= MAX_FRAME_BYTES,
        "request frame too large: {}",
        frame_len
    );

    let mut request_payload = vec![0_u8; frame_len];
    stream.read_exact(&mut request_payload).await?;
    let request = BattleFrameReq::decode(request_payload.as_slice())?;
    let response = runtime.handle(request).await;
    let response_payload = response.encode_to_vec();
    if response_payload.len() > MAX_FRAME_BYTES {
        error!(size = response_payload.len(), "battle response frame too large");
        return Ok(());
    }
    stream.write_u32(response_payload.len() as u32).await?;
    stream.write_all(&response_payload).await?;
    stream.flush().await?;
    Ok(())
}

fn now_millis() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("system clock is before UNIX_EPOCH")
        .as_millis() as i64
}

fn register_instance(cli: &Cli) -> anyhow::Result<Option<ZooKeeper>> {
    let Some(connect_string) = &cli.zookeeper else {
        return Ok(None);
    };
    let (bind_host, bind_port) = split_host_port(&cli.bind)?;
    let port = cli.public_port.unwrap_or(bind_port);
    let instance_id = cli
        .instance_id
        .clone()
        .unwrap_or_else(|| format!("battle-{port}"));
    let host = cli.public_host.clone().unwrap_or(bind_host);
    let registration = BattleInstanceRegistration {
        instance_id: instance_id.clone(),
        host,
        port,
        transport: "tcp-frame".to_string(),
        load: 0,
        state: "UP".to_string(),
    };
    let zk = ZooKeeper::connect(
        connect_string,
        std::time::Duration::from_secs(5),
        NoopWatcher,
    )?;
    ensure_path(&zk, &cli.discovery_path)?;
    let path = format!("{}/{}", cli.discovery_path.trim_end_matches('/'), instance_id);
    let payload = serde_json::to_vec(&registration)?;
    match zk.create(
        &path,
        payload,
        Acl::open_unsafe().clone(),
        CreateMode::Ephemeral,
    ) {
        Ok(_) => {
            info!(%path, "battle instance registered");
        }
        Err(ZkError::NodeExists) => {
            zk.delete(&path, None)?;
            zk.create(
                &path,
                serde_json::to_vec(&registration)?,
                Acl::open_unsafe().clone(),
                CreateMode::Ephemeral,
            )?;
            info!(%path, "battle instance registration replaced");
        }
        Err(error) => return Err(error.into()),
    }
    Ok(Some(zk))
}

fn ensure_path(zk: &ZooKeeper, path: &str) -> anyhow::Result<()> {
    let mut current = String::new();
    for segment in path.split('/').filter(|segment| !segment.is_empty()) {
        current.push('/');
        current.push_str(segment);
        if zk.exists(&current, false)?.is_none() {
            match zk.create(
                &current,
                Vec::new(),
                Acl::open_unsafe().clone(),
                CreateMode::Persistent,
            ) {
                Ok(_) | Err(ZkError::NodeExists) => {}
                Err(error) => return Err(error.into()),
            }
        }
    }
    Ok(())
}

fn split_host_port(bind: &str) -> anyhow::Result<(String, u16)> {
    let Some((host, port)) = bind.rsplit_once(':') else {
        anyhow::bail!("bind address must be host:port");
    };
    Ok((host.to_string(), port.parse()?))
}
