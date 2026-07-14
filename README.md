# HyperRing

HyperRing 是一个面向 HyperOS 双卡场景的现代 Android 铃声工具。

第一版可构建 MVP 优先打通最关键、风险最高的链路：选择音频、导入系统铃声库，并尝试应用到 SIM 1、SIM 2、双卡或系统默认电话铃声。

音频剪辑和波形编辑会在后续版本补上。当前版本先把 HyperOS 双卡铃声设置链路跑通，方便尽早上真机验证。

## 当前能力

- 使用 Android 文件选择器选择音频。
- 将音频复制到 `MediaStore.Audio`，并标记为系统铃声。
- 将导入后的铃声应用到：
  - SIM 1
  - SIM 2
  - SIM 1 + SIM 2
  - 系统默认电话铃声
  - 仅导入系统铃声库
- 通过系统页面请求 `WRITE_SETTINGS` 权限。
- 在系统支持时，优先尝试 Android 官方的按通话账户设置铃声接口。
- 支持填写已校准的 HyperOS 私有铃声 key，但不会默认写入未经验证的 key。
- 提供诊断信息：设备信息、可通话账户、铃声相关系统设置项。

## 构建

```powershell
.\gradlew.bat :app:assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 开源协议

Apache License 2.0。
