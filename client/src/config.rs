use std::net::SocketAddr;

use mlua::prelude::LuaUserData;
use mlua::UserDataFields;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    pub lua_init: String,
    pub log_level: String,
    pub remote_addr: String,
    pub socket_addr: Option<SocketAddr>,
    pub client_ip: String,
    pub world_id: i64,
    pub account: String,
    pub sex: i32,
}

impl LuaUserData for Config {
    fn add_fields<'lua, F: UserDataFields<'lua, Self>>(fields: &mut F) {
        fields.add_field_method_get("lua_init", |_, config| Ok(config.lua_init.clone()));
        fields.add_field_method_get("remote_addr", |_, config| {
            Ok(config.remote_addr.to_string())
        });
        fields.add_field_method_get("socket_addr", |_, config| {
            Ok(config.socket_addr.unwrap().to_string())
        });
        fields.add_field_method_get("client_ip", |_, config| Ok(config.client_ip.clone()));
        fields.add_field_method_get("world_id", |_, config| Ok(config.world_id.clone()));
        fields.add_field_method_get("account", |_, config| Ok(config.account.clone()));
        fields.add_field_method_get("sex", |_, config| Ok(config.sex.clone()));
    }
}
