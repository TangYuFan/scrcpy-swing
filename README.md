


## 项目说明

此项目为 android 手机远程控制软件，类似于 Scrcpy 原理在手机内通过 adb app_process 启动后台应用实时屏幕录制以及编码。
宿主机通过 Socket 接收视频流渲染。

## 操作流程

（1）准备 `scrcpy-server-v3.3.4.jar`（已放在 PC 工程资源路径 `src/main/resources/scrcpy/`）
（2）运行 PC 应用，选择设备后会自动 push 并启动 scrcpy server（当前先实现视频）

