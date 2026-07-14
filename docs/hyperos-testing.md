# HyperOS 双卡铃声测试

请在小米、Redmi 或 POCO 的 HyperOS 双卡真机上执行以下测试。

## 基础冒烟测试

1. 安装 Debug APK。
2. 启动 App，按提示授予音频和电话相关权限。
3. 点击 `授权修改系统设置`，在系统页面允许 HyperRing 修改系统设置。
4. 选择一个较短的音频文件。
5. 选择 `SIM 1`，点击 `导入并应用`。
6. 用另一部手机拨打 SIM 1，确认铃声是否生效。
7. 回到 App，选择 `SIM 2`，再次点击 `导入并应用`。
8. 拨打 SIM 2，确认铃声是否生效。
9. 再测试 `SIM 1 + SIM 2`，确认双卡是否都被设置。

## 当 SIM 单独设置失败时

如果 App 提示当前系统没有可用的官方按 SIM 设置接口，或者 SIM1/SIM2 设置后没有实际生效，请使用 `诊断` 区域辅助校准。

已在 Xiaomi `25098PN5AC` / Android SDK 36 诊断中观察到：

```text
SIM 1: ringtone_sound_slot_1
SIM 2: ringtone_sound_slot_2
双卡分离开关: ringtone_sound_use_uniform = 0
SIM 1 设置页显示路径: more_ringtone_value_call1 / more_ringtone_value_call64
SIM 2 设置页显示路径: more_ringtone_value_call128
```

当前实测结果：

```text
Xiaomi 25098PN5AC / Android SDK 36: SIM 2 铃声导入并应用成功
```

HyperRing 会在小米、Redmi、POCO 设备上默认尝试这组 key。下面的手动校准流程用于处理不同 HyperOS 版本或不同机型不兼容的情况。

1. 点击 `刷新诊断`，保存当前输出。
2. 打开 HyperOS 系统设置，手动修改 SIM 1 铃声。
3. 回到 HyperRing，再次点击 `刷新诊断`，保存第二份输出。
4. 打开 HyperOS 系统设置，手动修改 SIM 2 铃声。
5. 回到 HyperRing，再次点击 `刷新诊断`，保存第三份输出。
6. 对比三份诊断输出中包含 `ringtone` 的系统设置项。
7. 找出 SIM 1 和 SIM 2 分别变化的 key。
8. 将发现的 key 填入 `HyperOS 校准` 区域并保存。
9. 再次测试 `SIM 1`、`SIM 2` 和 `SIM 1 + SIM 2`。

HyperRing 默认不会写入猜测出来的 HyperOS 私有 key。只有在真机校准后手动填写 key，App 才会尝试写入。这样可以避免对未知机型或未知系统版本做不可控修改。

## 记录建议

每次测试请记录：

- 手机型号。
- HyperOS 版本。
- Android 版本。
- 是否双卡都已插入并启用。
- `SIM 1` 设置结果。
- `SIM 2` 设置结果。
- `SIM 1 + SIM 2` 设置结果。
- 失败时的 App 状态文字和诊断输出。
