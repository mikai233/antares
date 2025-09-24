use std::fs::read;
use std::net::SocketAddr;
use std::process::exit;

use anyhow::{Context, anyhow};
use clap::Parser;
use tracing::info;
use trust_dns_resolver::AsyncResolver;
use trust_dns_resolver::config::{ResolverConfig, ResolverOpts};
use trust_dns_resolver::name_server::{GenericConnector, TokioRuntimeProvider};

use crate::client::Client;
use crate::config::Config;
use crate::logger::init_logger;

mod client;
mod codec;
mod config;
mod crypto;
mod ecdh;
mod event;
mod helper;
mod key_pair;
mod logger;
mod schedule;

#[derive(Debug, Parser)]
struct Cli {
    #[clap(short, long, default_value = "config.yaml")]
    config: String,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();
    let resolver = dns_resolver();
    // console_subscriber::init();
    let config = read(&cli.config).context(format!("failed to load {}", cli.config))?;
    let mut config: Config = serde_yaml::from_slice(&config)?;
    let host_port = config.remote_addr.split(":").collect::<Vec<&str>>();
    let host = host_port[0];
    let port: u16 = host_port[1].parse().context("parse port error")?;
    let response = resolver.lookup_ip(host).await?;
    let ip_addr = response
        .iter()
        .next()
        .ok_or(anyhow!(format!("unable to resolve ip from host {}", host)))?;
    config.socket_addr = Some(SocketAddr::new(ip_addr, port));
    init_logger(config.log_level.parse()?)?;
    let mut client = Client::new(config)?;
    client.init()?;
    client.start().await?;
    info!("client shutdown");
    exit(0);
}

fn dns_resolver() -> AsyncResolver<GenericConnector<TokioRuntimeProvider>> {
    AsyncResolver::new(
        ResolverConfig::default(),
        ResolverOpts::default(),
        GenericConnector::new(TokioRuntimeProvider::new()),
    )
}
