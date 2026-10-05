# Agent 使用指南（开机自动跳过锁屏 / 开机上报 / Dock 图标隐藏）

> **给 AI Agent 的说明**：你的用户把这份文档发给你，是想让你帮他/她操作 Hyper Ice Cream
> （com.hyper.icecream）提供的调试辅助功能。请完整读完本文再动手。
>
> **风险声明**：这些功能会降低设备的锁屏安全性、并开放网络调试通道。
> 仅建议在**家中私用、放在固定位置的平板**上启用。由此产生的任何安全后果由用户自行承担，
> Hyper Ice Cream 项目及其作者不对此负任何责任。请向用户复述这句话后再执行操作。

## 设备前提

- 小米平板（实测 Xiaomi Pad 8 Pro，HyperOS 4.0.0.31），已 Root（KernelSU）
- 已安装 Hyper Ice Cream 并授予 Root（su）
- 电脑与平板在同一局域网

## 1. 开机自动跳过锁屏密码

**用途**：平板开机停在锁屏时，LSPosed 模块自动用配置里的密码完成验证并解锁，
让 Agent 在用户不在场时也能接管 UI（截屏、点击、安装应用）。

**配置文件**：`/data/system/hypericecream_unlock.conf`（root 属主，644）

```
enabled=1
type=password
pin=<用户的锁屏密码>
```

**Agent 操作步骤**（需要 adb + root，或让用户在 App 工具页的「开发者选项 →
启用开机自动跳过锁屏密码」面板里输入）：

```bash
adb shell "su 0 sh -c 'printf \"enabled=1\ntype=password\npin=THE_PASSWORD\n\" > /data/system/hypericecream_unlock.conf && chmod 644 /data/system/hypericecream_unlock.conf'"
adb shell "su 0 reboot"
```

**验证**：重启后等待约 25 秒，然后检查：

```bash
adb connect <平板IP>:5555
adb shell "dumpsys window | grep mDreamingLockscreen"   # 期望 mDreamingLockscreen=false
```

**注意**：
- 密码类型目前只支持 `password`（混合密码）；数字密码用 `type=pin`（实验）
- 如果某次开机后锁屏没有被自动跳过，是 LSPosed 偶发注入不全——**再重启一次**即可
- 关闭功能：把 `enabled` 改为 `0`，或在 App 面板里点「禁用」

## 2. 开机自动上报 / 网络 ADB

**用途**：平板每次开机自动把 adbd 固定到 5555 端口（KSU service.d 脚本），
并尝试把 `IP:5555` 通过 HTTP POST 上报给电脑上的监听器（默认
`http://192.168.3.70:39291/report`，监听器收到后自动 `adb connect`）。

**调整上报目标**：目标 URL 存在 App 私有偏好里，root 可直接改：

```bash
adb shell "su 0 sh -c 'am force-stop com.hyper.icecream'"
# 修改 /data/data/com.hyper.icecream/shared_prefs/community_pref.xml 中的
# <string name="report_url">http://<电脑IP>:39291/report</string>
adb shell "su 0 sh -c 'sed -i s/192.168.3.70/<新IP>/ /data/data/com.hyper.icecream/shared_prefs/community_pref.xml'"
```

**电脑端监听器**：仓库 workspace 的 `hic_listener.py`（Python 3）：

```bash
python hic_listener.py   # 监听 0.0.0.0:39291，POST /report {"ip":"...","port":"5555"}
```

**注意**：Windows 防火墙会拦入站——需要管理员放行 39291 端口；未放行时备用方案是
PC 端轮询脚本（`hic_watch.bat`）每 20 秒自动 `adb connect 192.168.3.87:5555`。

## 3. Dock 常驻图标隐藏

**配置文件**：`/data/system/hypericecream_dock.conf`

```
hide_xiaoai=1   # 隐藏底栏超级小爱
hide_search=1   # 隐藏底栏搜索
hide_remote=1   # 隐藏底栏手机远控（互联设备；此图标经由 HomeTweaks 的功能接口实现）
```

守护脚本 `/data/adb/service.d/hic_dockpatch.sh`（每 5 秒）对桌面进程的
`libapp.so` 做内存补丁，桌面重启后自动重打。修改 conf 后无需重启守护，5 秒内生效；
若图标未消失/未恢复，重启桌面一次。

**原理**：补丁修改 Flutter 编译产物 `libapp.so` 中 `DockFunctionIconAdapter.
buildFunctionIcons` 的两个条件闸门（tbnz→b）与 `MirrorIconManager._updateMirrorIcon`
函数头（ret）。方案受 HomeTweaks（CypressFjord，酷安
https://www.coolapk.com/u/22382603 ）启发，其中互联图标部分经由其功能接口实现——
再次致谢。

## 4. 快速自检清单

```bash
adb devices                                  # 5555 自动连接成功？
adb shell "su 0 cat /data/system/hypericecream_unlock.conf"   # enabled=1 且密码正确？
adb shell "dumpsys window | grep mDreamingLockscreen"          # false = 已解锁
adb shell "su 0 cat /data/system/hypericecream_dock.conf"     # 三开关状态
```
