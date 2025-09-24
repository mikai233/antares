use std::collections::HashMap;
use std::ops::Not;

use anyhow::{anyhow, Context};
use futures::{SinkExt, StreamExt};
use mlua::prelude::LuaFunction;
use mlua::Lua;
use rustyline::hint::HistoryHinter;
use tokio::net::TcpStream;
use tokio::select;
use tokio::sync::mpsc::{unbounded_channel, UnboundedReceiver, UnboundedSender};
use tokio::task::JoinHandle;
use tokio_util::codec::{Framed, FramedRead, LinesCodec};
use tracing::{error, info, warn};

use crate::codec::{ProtoCodec, ProtoCodecError, ProtobufPacket};
use crate::config::Config;
use crate::ecdh::Ecdh;
use crate::event::{Lua2RustEvent, Rust2LuaEvent};
use crate::helper::{init_global_helper, unix_timestamp, ProtobufHelper};
use crate::key_pair::KeyPair;
use crate::logger::init_lua_global_logger;
use crate::schedule::{OnceInfo, RateInfo, Schedule};

pub type ProtoSender = UnboundedSender<ProtobufPacket>;
pub type ProtoReceiver = UnboundedReceiver<ProtobufPacket>;

#[derive(Debug)]
pub struct ProtoChannel {
    pub sender: ProtoSender,
    pub receiver: ProtoReceiver,
}

impl ProtoChannel {
    pub fn new() -> Self {
        let (sender, receiver) = unbounded_channel();
        Self { sender, receiver }
    }
}

pub type EventSender = UnboundedSender<Lua2RustEvent>;
pub type EventReceiver = UnboundedReceiver<Lua2RustEvent>;

#[derive(Debug)]
pub struct EventChannel {
    pub sender: EventSender,
    pub receiver: EventReceiver,
}

impl EventChannel {
    pub fn new() -> Self {
        let (sender, receiver) = unbounded_channel();
        Self { sender, receiver }
    }
}

pub type ScheduleSender = UnboundedSender<String>;
pub type ScheduleReceiver = UnboundedReceiver<String>;

#[derive(Debug)]
pub struct ScheduleChannel {
    pub sender: ScheduleSender,
    pub receiver: ScheduleReceiver,
}

impl ScheduleChannel {
    pub fn new() -> Self {
        let (sender, receiver) = unbounded_channel();
        Self { sender, receiver }
    }
}

#[derive(Debug, Clone, Eq, PartialEq)]
pub enum ClientStatus {
    Init,
    Connected,
    Authenticating,
    Authorised,
}

pub struct Client {
    pub lua: Lua,
    pub key_pair: KeyPair,
    pub config: Config,
    pub status: ClientStatus,
    pub proto_channel: ProtoChannel,
    pub event_channel: EventChannel,
    pub schedule_channel: ScheduleChannel,
    pub schedules: HashMap<String, JoinHandle<()>>,
}

impl Client {
    pub fn new(config: Config) -> anyhow::Result<Self> {
        let lua = unsafe { Lua::unsafe_new() };
        let key_pair =
            Ecdh::generate_key_pair().map_err(|_| anyhow!("generate key pair failed"))?;
        let proto_channel = ProtoChannel::new();
        let event_channel = EventChannel::new();
        let schedule_channel = ScheduleChannel::new();
        let client = Self {
            lua,
            key_pair,
            config,
            status: ClientStatus::Init,
            proto_channel,
            event_channel,
            schedule_channel,
            schedules: HashMap::new(),
        };
        Ok(client)
    }
    fn before_init(&mut self) -> anyhow::Result<()> {
        self.lua.globals().set("Config", self.config.clone())?;
        Ok(())
    }

    fn after_init(&mut self) -> anyhow::Result<()> {
        Ok(())
    }

    pub fn init(&mut self) -> anyhow::Result<()> {
        info!("start init lua env");
        self.before_init()?;
        let lua_init = &self.config.lua_init;
        info!("LUA_INIT:{}", lua_init);
        let main_code = std::fs::read_to_string(&self.config.lua_init)
            .context(format!("failed to load {}", lua_init))?;
        init_lua_global_logger(&self.lua)?;
        init_global_helper(self)?;
        self.lua.load(&*main_code).exec()?;
        self.after_init()?;
        info!("lua env init done");
        Ok(())
    }

    pub async fn start(&mut self) -> anyhow::Result<()> {
        let stream = TcpStream::connect(self.config.socket_addr.unwrap()).await?;
        self.status = ClientStatus::Connected;
        self.publish_lua_event(Rust2LuaEvent::Connected, None);
        let mut framed = Framed::new(stream, ProtoCodec::new());
        let config = rustyline::Config::builder().build();
        let helper = ProtobufHelper {
            hinter: HistoryHinter::default(),
        };
        let mut rl = rustyline::Editor::with_config(config)?;
        rl.set_helper(Some(helper));
        let mut stdin = FramedRead::new(tokio::io::stdin(), LinesCodec::new());

        loop {
            select! {
                biased;
                packet = framed.next(), if self.status == ClientStatus::Connected || self.status == ClientStatus::Authorised => {
                    match packet {
                        None => {
                            info!("connection closed");
                            break;
                        }
                        Some(packet) => {
                            // 登录回包之前都不要接收新的包，因为要等回包之后的AES密钥设置成功
                            if matches!(self.status, ClientStatus::Connected) {
                                self.status = ClientStatus::Authenticating;
                            }
                            self.handle_proto_message(packet);
                        }
                    }
                }
                Some(event) = self.event_channel.receiver.recv() => {
                    if !self.handle_event(&mut framed, event).await {
                        break;
                    }
                }
                Some(packet) = self.proto_channel.receiver.recv() => {
                    self.send_proto_message(&mut framed, packet).await;
                }
                Some(key) = self.schedule_channel.receiver.recv() => {
                    self.handle_schedule(key);
                }
                Some(Ok(console_input)) = stdin.next() => {
                    self.publish_lua_event(Rust2LuaEvent::ConsoleCommand, Some(vec![console_input]));
                }
                _ = tokio::signal::ctrl_c() => {
                    break;
                }
            }
        }
        Ok(())
    }

    fn handle_proto_message(&self, msg: Result<ProtobufPacket, ProtoCodecError>) {
        match self.exec_proto(msg) {
            Ok(_) => {}
            Err(err) => {
                error!("exec proto error:{:?}", err);
            }
        }
    }

    fn exec_proto(&self, msg: Result<ProtobufPacket, ProtoCodecError>) -> anyhow::Result<()> {
        let msg = msg?;
        let func: LuaFunction = self.lua.globals().get("handle_proto_message")?;
        func.call::<()>((msg.id, msg.body))?;
        Ok(())
    }

    async fn send_proto_message(
        &self,
        framed: &mut Framed<TcpStream, ProtoCodec>,
        wrap: ProtobufPacket,
    ) {
        match framed.send(wrap).await {
            Ok(_) => {}
            Err(err) => {
                error!("send proto message error:{:?}", err);
            }
        };
    }

    async fn handle_event(
        &mut self,
        framed: &mut Framed<TcpStream, ProtoCodec>,
        event: Lua2RustEvent,
    ) -> bool {
        match event {
            Lua2RustEvent::SetSharedKey(remote_public_key) => {
                if let Some(self_private_key) = self.key_pair.private_key.take() {
                    let shared_key =
                        Ecdh::calculate_shared_key(self_private_key, remote_public_key.as_slice());
                    match shared_key {
                        Ok(shared_key) => {
                            self.status = ClientStatus::Authorised;
                            let mut shared_key_array = [0u8; 32];
                            shared_key_array.copy_from_slice(shared_key.as_slice());
                            framed.codec_mut().set_share_key(shared_key_array);
                            self.publish_lua_event(Rust2LuaEvent::SharedKeyHaveSet, None);
                        }
                        Err(error) => {
                            error!("set shared key failed:{:?}", error);
                            return false;
                        }
                    }
                } else {
                    error!("set shared key failed, self private key not exists, make sure each key pair only used once");
                    return false;
                }
            }
            Lua2RustEvent::AddSchedule(schedule) => match schedule {
                Schedule::ScheduleAtFixedRate(rate) => {
                    self.new_fixed_rate_schedule(rate);
                }
                Schedule::ScheduleOnce(once) => {
                    self.new_once_schedule(once);
                }
            },
            Lua2RustEvent::CancelSchedule(key) => {
                if self.cancel_schedule(key.clone()).not() {
                    warn!("cancel schedule key:{} not exists", key);
                }
            }
            Lua2RustEvent::Shutdown => {
                info!("shutdown event received");
                let _ = framed.close().await;
                self.event_channel.receiver.close();
                self.proto_channel.receiver.close();
                self.schedule_channel.receiver.close();
                return false;
            }
        }
        true
    }

    fn handle_schedule(&mut self, key: String) {
        if let Some(err) = self.exec_schedule(key.clone()).err() {
            error!("exec schedule key:{} with error:{:?}", key, err);
        }
    }

    fn exec_schedule(&mut self, key: String) -> anyhow::Result<()> {
        let func: LuaFunction = self.lua.globals().get("handle_schedule")?;
        let now = unix_timestamp().as_millis();
        func.call::<()>((key, now))?;
        Ok(())
    }

    fn publish_lua_event(&self, event: Rust2LuaEvent, params: Option<Vec<String>>) {
        if let Some(err) = self.exec_lua_event(event, params).err() {
            error!("publish lua event error:{:?}", err);
        }
    }

    fn exec_lua_event(
        &self,
        event: Rust2LuaEvent,
        parameters: Option<Vec<String>>,
    ) -> anyhow::Result<()> {
        let func: LuaFunction = self.lua.globals().get("handle_event")?;
        func.call::<()>((event.to_string(), parameters))?;
        Ok(())
    }

    fn new_fixed_rate_schedule(&mut self, info: RateInfo) {
        self.remove_completed_schedules();
        let mut interval = tokio::time::interval(info.interval);
        let key = info.key.clone();
        let sender = self.schedule_channel.sender.clone();
        let handle = tokio::spawn(async move {
            let key = key.clone();
            let mut initial_delay = info.initial_delay.is_zero().not();
            loop {
                if initial_delay {
                    initial_delay = false;
                    tokio::time::sleep(info.initial_delay).await;
                }
                interval.tick().await;
                if let Some(err) = sender.send(key.clone()).err() {
                    error!("send fixed rate schedule error:{:?}", err);
                }
            }
        });

        info!("add new fixed rate schedule:{:?}", info);
        if let Some(previous) = self.schedules.insert(info.key.clone(), handle) {
            warn!(
                "duplicate schedule key:{}, previous rate schedule will abort",
                info.key
            );
            previous.abort();
        }
    }

    fn new_once_schedule(&mut self, info: OnceInfo) {
        self.remove_completed_schedules();
        let sender = self.schedule_channel.sender.clone();
        let key = info.key.clone();
        let handle = tokio::spawn(async move {
            tokio::time::sleep(info.delay).await;
            if let Some(err) = sender.send(key).err() {
                error!("send once schedule error:{:?}", err);
            }
        });
        info!("add new once schedule:{:?}", info);
        if let Some(previous) = self.schedules.insert(info.key.clone(), handle) {
            warn!(
                "duplicate schedule key:{}, previous once schedule will abort",
                info.key
            );
            previous.abort();
        }
    }

    fn cancel_schedule(&mut self, key: String) -> bool {
        self.schedules.remove(&key).is_some_and(|handle| {
            handle.abort();
            true
        })
    }

    fn remove_completed_schedules(&mut self) {
        self.schedules.retain(|_, handle| !handle.is_finished());
    }
}
