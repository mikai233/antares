# 调试客户端

# 使用方式

1. 在[proto.lua](lua/proto.lua)中将`proto_path`设置为你的proto文件路径 默认会在当前目录下生成一个`proto`
   文件夹，里面会生成对应proto的lua提示文件，不需要可以关闭
2. 在[config.yaml](config.yaml)中配置你的服务端以及账号信息
3. 启动客户端 可指定`-c`参数指定配置文件路径 默认为`config.yaml`

## 发送GM指令

直接在控制台中输入指令即可

例如 `gmPlayerLevel 30`

## 发送协议

协议需要以$开头，也是在控制台中发送

例如 `$ReceiveRewardReq { id = 1}`

协议名和协议数据之间必须要有空格

## Lua脚本

可以用Lua脚本来批量发送协议以及根据返回结果做一些操作，具体可以参考现有的脚本