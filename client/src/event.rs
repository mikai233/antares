use crate::schedule::Schedule;
use mlua::prelude::LuaUserData;
use mlua::{MetaMethod, UserDataMethods};

#[derive(Debug, Clone, strum::Display, mlua::FromLua)]
pub enum Lua2RustEvent {
    SetSharedKey(Vec<u8>),
    AddSchedule(Schedule),
    CancelSchedule(String),
    Shutdown,
}

impl LuaUserData for Lua2RustEvent {
    fn add_methods<M: UserDataMethods<Self>>(methods: &mut M) {
        methods.add_function("SetSharedKey", |_, remote_public_key: Vec<u8>| {
            Ok(Lua2RustEvent::SetSharedKey(remote_public_key))
        });
        methods.add_function("Shutdown", |_, ()| Ok(Lua2RustEvent::Shutdown));
    }
}

#[derive(Debug, Clone, Eq, PartialEq, strum::Display, mlua::FromLua)]
pub enum Rust2LuaEvent {
    SharedKeyHaveSet,
    Connected,
    ConsoleCommand,
}

impl LuaUserData for Rust2LuaEvent {
    fn add_methods<M: UserDataMethods<Self>>(methods: &mut M) {
        methods.add_method("ToString", |_, this, ()| Ok(this.to_string()));
        methods.add_meta_method(MetaMethod::Eq, |_, this, other: Rust2LuaEvent| {
            Ok(*this == other)
        });
        methods.add_function("SharedKeyHaveSet", |_, ()| {
            Ok(Rust2LuaEvent::SharedKeyHaveSet.to_string())
        });
        methods.add_function(
            "Connected",
            |_, ()| Ok(Rust2LuaEvent::Connected.to_string()),
        );
        methods.add_function("ConsoleCommand", |_, ()| {
            Ok(Rust2LuaEvent::ConsoleCommand.to_string())
        });
    }
}
