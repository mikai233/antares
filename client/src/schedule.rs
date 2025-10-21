use std::time::Duration;

use mlua::prelude::LuaUserData;
use mlua::{ExternalError, UserDataMethods};

use crate::client::EventSender;
use crate::event::Lua2RustEvent;

#[derive(Debug, Clone)]
pub enum Schedule {
    ScheduleAtFixedRate(RateInfo),
    ScheduleOnce(OnceInfo),
}

#[derive(Debug, Clone)]
pub struct RateInfo {
    pub key: String,
    pub initial_delay: Duration,
    pub interval: Duration,
}

impl RateInfo {
    pub fn new(key: String, initial_delay: Duration, interval: Duration) -> Self {
        Self {
            key,
            initial_delay,
            interval,
        }
    }
}

#[derive(Debug, Clone)]
pub struct OnceInfo {
    pub key: String,
    pub delay: Duration,
}

impl OnceInfo {
    pub fn new(key: String, delay: Duration) -> Self {
        Self { key, delay }
    }
}

pub struct Scheduler {
    pub sender: EventSender,
}

impl Scheduler {
    pub fn new(sender: EventSender) -> Self {
        Self { sender }
    }
}

impl LuaUserData for Scheduler {
    fn add_methods<M: UserDataMethods<Self>>(methods: &mut M) {
        methods.add_method(
            "schedule_at_fixed_rate",
            |_, this, (key, initial_delay, interval): (String, u64, u64)| {
                let initial_delay = Duration::from_millis(initial_delay);
                let interval = Duration::from_millis(interval);
                let rate_info = RateInfo::new(key, initial_delay, interval);
                this.sender
                    .send(Lua2RustEvent::AddSchedule(Schedule::ScheduleAtFixedRate(
                        rate_info,
                    )))
                    .map_err(|e| e.into_lua_err())?;
                Ok(())
            },
        );
        methods.add_method("schedule_once", |_, this, (key, delay): (String, u64)| {
            let delay = Duration::from_millis(delay);
            let once_info = OnceInfo::new(key, delay);
            this.sender
                .send(Lua2RustEvent::AddSchedule(Schedule::ScheduleOnce(
                    once_info,
                )))
                .map_err(|e| e.into_lua_err())?;
            Ok(())
        });
        methods.add_method("cancel_schedule", |_, this, key: String| {
            this.sender
                .send(Lua2RustEvent::CancelSchedule(key))
                .map_err(|e| e.into_lua_err())?;
            Ok(())
        });
    }
}
