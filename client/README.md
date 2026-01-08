# Debug Client

# Usage

1. In [proto.lua](lua/proto.lua), set `proto_path` to your proto file directory. By default, a `proto` folder will be created in the current directory containing Lua hint files for the protocols; this can be disabled if not needed.
2. Configure your server and account information in [config.yaml](config.yaml).
3. Start the client. You can specify the configuration file path with the `-c` parameter (defaults to `config.yaml`).

## Send GM Commands

Type commands directly into the console.

Example: `gmPlayerLevel 30`

## Send Protocols

Protocols must start with a `$`, also sent via the console.

Example: `$ReceiveRewardReq { id = 1}`

There must be a space between the protocol name and the data.

## Lua Scripts

You can use Lua scripts to batch send protocols and perform operations based on the results. Refer to existing scripts for examples.
