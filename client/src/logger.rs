use dynfmt::Format;
use mlua::prelude::{LuaError, LuaUserData};
use mlua::{Lua, UserDataMethods};
use tracing::{debug, error, info, trace, warn};
use tracing_subscriber::fmt::time::ChronoLocal;

#[derive(Debug, Clone)]
pub struct LuaLogger;

impl LuaUserData for LuaLogger {
    fn add_methods<'lua, M: UserDataMethods<'lua, Self>>(methods: &mut M) {
        methods.add_function("trace", |_, (template, args): (String, Vec<String>)| {
            let format_str = dynfmt::SimpleCurlyFormat
                .format(&*template, args)
                .map_err(|e| LuaError::external(e.to_string()))?;
            let format_str = String::from(format_str);
            trace!("{}", format_str);
            Ok(())
        });

        methods.add_function("debug", |_, (template, args): (String, Vec<String>)| {
            let format_str = dynfmt::SimpleCurlyFormat
                .format(&*template, args)
                .map_err(|e| LuaError::external(e.to_string()))?;
            let format_str = String::from(format_str);
            debug!("{}", format_str);
            Ok(())
        });

        methods.add_function("info", |_, (template, args): (String, Vec<String>)| {
            let format_str = dynfmt::SimpleCurlyFormat
                .format(&*template, args)
                .map_err(|e| LuaError::external(e.to_string()))?;
            let format_str = String::from(format_str);
            info!("{}", format_str);
            Ok(())
        });

        methods.add_function("warn", |_, (template, args): (String, Vec<String>)| {
            let format_str = dynfmt::SimpleCurlyFormat
                .format(&*template, args)
                .map_err(|e| LuaError::external(e.to_string()))?;
            let format_str = String::from(format_str);
            warn!("{}", format_str);
            Ok(())
        });

        methods.add_function("error", |_, (template, args): (String, Vec<String>)| {
            let format_str = dynfmt::SimpleCurlyFormat
                .format(&*template, args)
                .map_err(|e| LuaError::external(e.to_string()))?;
            let format_str = String::from(format_str);
            error!("{}", format_str);
            Ok(())
        });
    }
}

pub fn init_logger(max_level: tracing::Level) -> anyhow::Result<()> {
    let format = tracing_subscriber::fmt::format()
        .pretty()
        .with_timer(ChronoLocal::default())
        .pretty()
        .with_file(false);
    tracing_subscriber::FmtSubscriber::builder()
        .event_format(format)
        .with_max_level(max_level)
        .init();
    Ok(())
}

pub fn init_lua_global_logger(lua: &Lua) -> anyhow::Result<()> {
    let globals = lua.globals();
    globals.set("Logger", LuaLogger)?;
    Ok(())
}
