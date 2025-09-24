use std::cmp::Ordering;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use base64::Engine;
use lua_protobuf_rs::protoc::LuaProtoc;
use mlua::prelude::LuaUserData;
use mlua::{ExternalError, UserDataMethods};
use rustyline::completion::Completer;
use rustyline::highlight::Highlighter;
use rustyline::hint::{Hinter, HistoryHinter};
use rustyline::validate::Validator;
use rustyline::{Context, Helper};
use sha2::Digest;
use sha2::Sha256;
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

impl LuaUserData for CryptoHelper {
    fn add_methods<M: UserDataMethods<Self>>(methods: &mut M) {
        methods.add_function("base64_encode", |_, bytes: Vec<u8>| {
            Ok(base64::engine::general_purpose::STANDARD.encode(bytes))
        });
        methods.add_function("base64_encode_str", |_, str: String| {
            Ok(base64::engine::general_purpose::STANDARD.encode(str))
        });
        methods.add_function("base64_decode", |_, str: String| {
            let bytes = base64::engine::general_purpose::STANDARD
                .decode(str)
                .map_err(|e| e.into_lua_err())?;
            Ok(bytes)
        });
        methods.add_function("sha256", |_, str: String| {
            let mut hasher = Sha256::new();
            hasher.update(str.as_bytes());
            let hash = hasher.finalize();
            Ok(format!("{:x}", hash))
        });
        methods.add_function("md5", |_, str: String| {
            let digest = md5::compute(str);
            Ok(format!("{:x}", digest))
        });
    }
}

pub fn unix_timestamp() -> Duration {
    let now = SystemTime::now();

    now.duration_since(UNIX_EPOCH).expect("Time went backwards")
}

#[derive(Debug, Clone)]
struct MessageHelper {
    pub proto_sender: ProtoSender,
    pub event_sender: EventSender,
}

impl LuaUserData for MessageHelper {
    fn add_methods<M: UserDataMethods<Self>>(methods: &mut M) {
        methods.add_method("send_message", |_, this, (id, bytes): (i32, Vec<u8>)| {
            let packet = ProtobufPacket::new(id, bytes);
            this.proto_sender
                .send(packet)
                .map_err(|e| e.into_lua_err())?;
            Ok(())
        });
        methods.add_method("publish_event", |_, this, event: Lua2RustEvent| {
            this.event_sender
                .send(event)
                .map_err(|e| e.into_lua_err())?;
            Ok(())
        });
    }
}

impl MessageHelper {
    pub fn new(proto_sender: ProtoSender, event_sender: EventSender) -> Self {
        Self {
            proto_sender,
            event_sender,
        }
    }
}

#[derive(Debug, Clone)]
struct IOHelper;

impl LuaUserData for IOHelper {
    fn add_methods<M: UserDataMethods<Self>>(methods: &mut M) {
        methods.add_function("list_files", |_, path: String| {
            let mut files = vec![];
            for file in WalkDir::new(path).into_iter().filter_map(|file| file.ok()) {
                if file.metadata().map_err(|e| e.into_lua_err())?.is_file() {
                    files.push(format!("{}", file.path().display()));
                }
            }
            Ok(files)
        });
        methods.add_function("read_to_string", |_, path: String| {
            std::fs::read_to_string(path).map_err(|e| e.into_lua_err())
        });
    }
}

#[derive(Debug, Clone)]
struct Utils;

impl LuaUserData for Utils {
    fn add_methods<M: UserDataMethods<Self>>(methods: &mut M) {
        methods.add_function("unix_timestamp", |_, ()| Ok(unix_timestamp().as_millis()));
        methods.add_function("lexical_sort", |_, string_vec: Vec<String>| {
            let mut string_vec = string_vec;
            string_vec.sort_by(|a, b| lexical_sort::lexical_cmp(&a.to_string(), &b.to_string()));
            Ok(string_vec)
        });
        methods.add_function("lexical_cmp", |_, (a, b): (String, String)| {
            let result = match lexical_sort::lexical_cmp(&a, &b) {
                Ordering::Less => -1,
                Ordering::Equal => 0,
                Ordering::Greater => 1,
            };
            Ok(result)
        });
        methods.add_function("split", |_, (str, pat): (String, String)| {
            let res: Vec<String> = str.split(&pat).map(|s| s.to_string()).collect();
            Ok(res)
        });
        methods.add_function("splitn", |_, (str, n, pat): (String, usize, String)| {
            let res: Vec<String> = str.splitn(n, &pat).map(|s| s.to_string()).collect();
            Ok(res)
        });
        methods.add_function("uuid", |_, ()| {
            let uid = uuid::Uuid::new_v4().to_string();
            Ok(uid)
        });
    }
}

pub struct ProtobufHelper {
    pub hinter: HistoryHinter,
}

impl Completer for ProtobufHelper {
    type Candidate = String;

    fn complete(
        &self,
        _line: &str,
        _pos: usize,
        _ctx: &Context<'_>,
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
