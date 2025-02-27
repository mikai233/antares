---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by mikai233.
--- DateTime: 2022/12/20 19:04
---

local json = require("lua/ext/json")
local message_log_ignore = { PingResp = true }

function handle_proto_message(id, bytes)
    local name, resp = decode_resp(id, bytes)
    debug_resp(name, resp)
    if name ~= nil and resp ~= nil then
        MessageWaitQueue:on_resp(id, resp)
        MessageDispatcher:on_resp(name, resp)
    end
end

---@param name string
---@param resp table
function debug_resp(name, resp)
    local should_log = not message_log_ignore[name]
    if should_log or should_log == nil then
        info("{} {}", name, json.encode(resp))
    end
end