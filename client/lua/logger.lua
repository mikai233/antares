---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by mikai.
--- DateTime: 2022/12/20 23:22
---

Logger = Logger

function trace(maker, ...)
    Logger.trace(maker, { ... })
end

function debug(maker, ...)
    Logger.debug(maker, { ... })
end

function info(maker, ...)
    Logger.info(maker, { ... })
end

function warn(maker, ...)
    Logger.warn(maker, { ... })
end

function error(maker, ...)
    Logger.error(maker, { ... })
end