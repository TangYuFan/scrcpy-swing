## Android Remote Control

Scrcpy-Swing 是一个 Android 手机远程控制应用，通过 USB 或者 WIFI 连接，参考 Scrcpy 移植为纯 JAVA-Swing 项目，可执行文件 exe 约 30MB。

### 游戏模式
<table border="1">
  <tr>
    <th><img src="./demo/v2.gif?raw=true" width="280" height="150"></th>
  </tr>
</table>

### 普通模式
<table border="1">
  <tr>
    <th><img src="./demo/v1.gif?raw=true" width="150" height="280"></th>
  </tr>
</table>

<hr style="height:1px; border:none; background-color:#ccc; margin:20px 0;" />


## Feature List

<table>
  <tbody>
    <tr>
      <td>实时投屏</td>
      <td>视频 H264 编码，桌面端使用 OPENGL 渲染。</td>
      <td>✅ 100%</td>
    </tr>
    <tr>
      <td>控制 / 映射</td>
      <td>支持点击、拖拽、滚轮、长按、自定义键位映射等，针对 fps 游戏使用 GLFW 捕获 delta 位移以及鼠标锁定优化。</td>
      <td>✅ 100%</td>
    </tr>
    <tr>
      <td>实体按键</td>
      <td>提供虚拟按键执行 Home、返回、任务切换、电源等操作。</td>
      <td>✅ 100%</td>
    </tr>
	<tr>
      <td>声音系统</td>
      <td>接管手机音频到 PC 端。</td>
      <td>📝 待开发</td>
    </tr>
    <tr>
      <td>设备管理</td>
      <td>支持多设备切换，可通过 WiFi 或 USB 连接不同手机，一键开启 Wifi 调试。</td>
      <td>✅ 100%</td>
    </tr>
    <tr>
      <td>日志系统</td>
      <td>提供 PC 和 Android 双端日志，便于调试。</td>
	  <td>✅ 100%</td>
    </tr>
	<tr>
      <td>AI-Agent</td>
      <td>接入多模态大模型，在 PC 上绕过手机内部权限通过 Agent 完全控制手机。</td>
	  <td>📝 待开发</td>
    </tr>
	<tr>
      <td>游戏辅助</td>
      <td>提供键位映射，AI自瞄辅助等，目前已适配《三角洲行动》。</td>
	  <td>⏳ 50%</td>
    </tr>
  </tbody>
</table>

