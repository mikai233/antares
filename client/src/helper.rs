use std::cmp::Ordering;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use base64::Engine;
use lua_protobuf_rs::protoc::LuaProtoc;
use mlua::ExternalError;
use rustyline::completion::Completer;
use rustyline::highlight::Highlighter;
use rustyline::hint::{Hinter, HistoryHinter};
use rustyline::validate::Validator;
use rustyline::{Context, Helper};
use sha2::Digest;
use sha2::Sha256;
use silu_derive::{lua_function, lua_helper, lua_method};
use walkdir::WalkDir;

use crate::client::{Client, EventSender, ProtoSender};
use crate::codec::ProtobufPacket;
use crate::event::{Lua2RustEvent, Rust2LuaEvent};
use crate::schedule::Scheduler;

pub fn init_global_helper(client: &mut Client) -> anyhow::Result<()> {
    let lua = &client.lua;
    let globals = lua.globals();
    globals.set("LuaProtoc", lua.create_proxy::<LuaProtoc>()?)?;
    globals.set("CryptoHelper", lua.create_proxy::<CryptoHelper>()?)?;
    let message_helper = MessageHelper::new(
        client.proto_channel.sender.clone(),
        client.event_channel.sender.clone(),
    );
    globals.set("MessageHelper", message_helper)?;
    globals.set("IOHelper", lua.create_proxy::<IOHelper>()?)?;
    globals.set("ClientPublicKey", client.key_pair.public_key.as_ref())?;
    let lua_rust_event_user_data = lua.create_proxy::<Lua2RustEvent>()?;
    globals.set("Lua2RustEvent", lua_rust_event_user_data)?;
    let scheduler = Scheduler::new(client.event_channel.sender.clone());
    globals.set("Scheduler", scheduler)?;
    let utils_user_data = lua.create_proxy::<Utils>()?;
    globals.set("Utils", utils_user_data)?;
    let rust_lua_event_user_data = lua.create_proxy::<Rust2LuaEvent>()?;
    globals.set("Rust2LuaEvent", rust_lua_event_user_data)?;
    Ok(())
}

#[derive(Debug, Clone)]
struct CryptoHelper;

#[lua_helper]
impl CryptoHelper {
    #[lua_function]
    fn base64_encode(bytes: Vec<u8>) -> mlua::Result<String> {
        Ok(base64::engine::general_purpose::STANDARD.encode(bytes))
    }

    #[lua_function]
    fn base64_encode_str(str: String) -> mlua::Result<String> {
        Ok(base64::engine::general_purpose::STANDARD.encode(str))
    }

    #[lua_function]
    fn base64_decode(str: String) -> mlua::Result<Vec<u8>> {
        let bytes = base64::engine::general_purpose::STANDARD
            .decode(str)
            .map_err(|e| e.into_lua_err())?;
        Ok(bytes)
    }

    #[lua_function]
    fn sha256(str: String) -> mlua::Result<String> {
        let mut hasher = Sha256::new();
        hasher.update(str.as_bytes());
        let hash = hasher.finalize();
        Ok(format!("{:x}", hash))
    }

    #[lua_function]
    fn md5(str: String) -> mlua::Result<String> {
        let digest = md5::compute(str);
        Ok(format!("{:x}", digest))
    }
}

pub fn unix_timestamp() -> Duration {
    let now = SystemTime::now();
    let since_the_epoch = now.duration_since(UNIX_EPOCH).expect("Time went backwards");
    since_the_epoch
}

#[derive(Debug, Clone)]
struct MessageHelper {
    pub proto_sender: ProtoSender,
    pub event_sender: EventSender,
}

#[lua_helper]
impl MessageHelper {
    pub fn new(proto_sender: ProtoSender, event_sender: EventSender) -> Self {
        Self {
            proto_sender,
            event_sender,
        }
    }

    #[lua_method]
    fn send_message(&self, id: i32, bytes: Vec<u8>) -> mlua::Result<()> {
        let wrap = ProtobufPacket::new(id, bytes);
        self.proto_sender.send(wrap).map_err(|e| e.into_lua_err())?;
        Ok(())
    }

    #[lua_method]
    fn publish_event(&self, event: Lua2RustEvent) -> mlua::Result<()> {
        self.event_sender
            .send(event)
            .map_err(|e| e.into_lua_err())?;
        Ok(())
    }
}

#[derive(Debug, Clone)]
struct IOHelper;

#[lua_helper]
impl IOHelper {
    #[lua_function]
    fn list_files(path: String) -> mlua::Result<Vec<String>> {
        let mut files = vec![];
        for file in WalkDir::new(path).into_iter().filter_map(|file| file.ok()) {
            if file.metadata().map_err(|e| e.into_lua_err())?.is_file() {
                files.push(format!("{}", file.path().display()));
            }
        }
        Ok(files)
    }

    #[lua_function]
    fn read_to_string(path: String) -> mlua::Result<String> {
        Ok(std::fs::read_to_string(path)?)
    }
}

#[derive(Debug, Clone)]
struct Utils;

#[lua_helper]
impl Utils {
    #[lua_function]
    fn unix_timestamp() -> mlua::Result<u128> {
        Ok(unix_timestamp().as_millis())
    }

    #[lua_function]
    fn lexical_sort(string_vec: Vec<String>) -> mlua::Result<Vec<String>> {
        let mut string_vec = string_vec;
        string_vec.sort_by(|a, b| lexical_sort::lexical_cmp(&*a.to_string(), &*b.to_string()));
        Ok(string_vec)
    }

    #[lua_function]
    fn lexical_cmp(a: String, b: String) -> mlua::Result<i32> {
        let result = match lexical_sort::lexical_cmp(&*a, &*b) {
            Ordering::Less => -1,
            Ordering::Equal => 0,
            Ordering::Greater => 1,
        };
        Ok(result)
    }

    #[lua_function]
    fn split(str: String, pat: String) -> mlua::Result<Vec<String>> {
        let res: Vec<String> = str.split(&pat).map(|s| s.to_string()).collect();
        Ok(res)
    }

    #[lua_function]
    fn splitn(str: String, n: usize, pat: String) -> mlua::Result<Vec<String>> {
        let res: Vec<String> = str.splitn(n, &pat).map(|s| s.to_string()).collect();
        Ok(res)
    }

    #[lua_function]
    fn uuid() -> mlua::Result<String> {
        let uid = uuid::Uuid::new_v4().to_string();
        Ok(uid)
    }
}

pub struct ProtobufHelper {
    pub hinter: HistoryHinter,
}

impl Completer for ProtobufHelper {
    type Candidate = String;

    fn complete(
        &self,
        line: &str,
        pos: usize,
        ctx: &Context<'_>,
    ) -> rustyline::Result<(usize, Vec<Self::Candidate>)> {
        todo!()
    }
}

impl Highlighter for ProtobufHelper {}

impl Helper for ProtobufHelper {}

impl Hinter for ProtobufHelper {
    type Hint = String;

    fn hint(&self, line: &str, pos: usize, ctx: &Context<'_>) -> Option<Self::Hint> {
        self.hinter.hint(line, pos, ctx)
    }
}

impl Validator for ProtobufHelper {}
