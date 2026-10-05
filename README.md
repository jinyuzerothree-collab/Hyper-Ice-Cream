# HyperOS-Theme-Installer

在部分 HyperOS 平板上，系统主题商店缺失或功能受限，第三方 MTZ 主题无法正常导入和应用。本工具通过 Root 权限将主题组件直接部署到系统主题运行时目录（`/data/system/theme/`），绕过主题商店完成第三方主题的应用、备份和还原。

适用于 Xiaomi Pad 8 Pro（HyperOS 4.0.0.31 实测），其他 HyperOS 平板理论可用，未逐一测试。

## 功能列表

- 识别 MTZ 主题包 / zip 包 / 已解包目录 / 单组件文件
- 组件勾选式部署（只应用选中的部分）
- 部署前自动备份当前主题到 `/sdcard/ThemeToolBackup/`
- 一键还原最近一次备份
- 一键重启 SystemUI
- 内置文件浏览器（过滤 mtz/zip/ttf，可切换显示全部文件）

## 支持内容

| 组件 | 覆盖内容 | 部署位置 | 状态 |
|---|---|---|---|
| 状态栏 / 电池图标 | 状态栏资源、电池图标（含暗色模式） | `/data/system/theme/com.android.systemui` | 已验证 |
| 系统图标 / 桌面图标 | 桌面图标包、系统图标替换 | `/data/system/theme/icons` | 已验证 |
| 壁纸 | 静态壁纸 | `/data/system/theme/wallpaper` | 已验证 |
| 锁屏壁纸 | 锁屏壁纸图 | `/data/system/theme/lock_wallpaper` | 已验证 |
| 字体 | 主题包 `fonts/` 内的 ttf/otf | `/data/system/theme/fonts/` | 实验性 |

说明：桌面图标包含在「系统图标」组件中，没有独立的桌面组件。框架级深度主题（framework-res 注入）不在本工具范围内；maml 动画、动态壁纸、音效组件会被自动跳过。

## 下载

- GitHub Releases: https://github.com/jinyuzerothree-collab/HyperOS-Theme-Installer/releases
- 123云盘镜像（国内直连，永久有效）: https://1833975553.share.123pan.cn/123pan/ick9Td-NxW73

## 使用要求

- 已 Root 的 HyperOS 平板（KernelSU / Magisk 均可，需要授权 su）
- Android 10 及以上（实测 Android 17 / HyperOS 4）
- 允许本工具「所有文件访问」权限（应用内引导授权）
- 主题包来源不限：MTZ、zip、已解包目录均可

## 使用方法

1. 安装 APK，打开应用，允许 Root 授权和「所有文件访问」权限
2. 点「选择主题包 / 字体文件」，在内置浏览器中选择主题文件
3. 工具自动解包并列出可部署组件，勾选要应用的部分
4. 点「部署到系统主题」，工具自动备份当前主题后写入并重启 SystemUI
5. 壁纸如未立即变化，重启设备一次
6. 如需恢复，点「还原最近一次备份」；备份同时保存在 `/sdcard/ThemeToolBackup/`

电脑端另提供命令行版本 `tools/theme_tool.py`（Python 3 + adb），用法见文件内注释。

## 已知问题

- 字体组件为实验性：部分主题的字体部署后需要重启设备，个别主题字体不生效（引擎限制，无副作用）
- 框架级深度主题（framework-res 注入、状态栏布局重构）不支持
- maml 动画、动态壁纸、音效组件会被自动跳过
- 部分主题包结构非标准时，组件识别可能不全（可用 inspect 模式查看）
- 系统大版本更新后无需重新部署（`/data/system/theme` 在 data 分区）

## 方案致谢与实现说明

本项目部分功能的实现思路来自社区开发者的开源/共享成果，在此郑重致谢（排名不分先后）：

| 方案 / 软件 | 作者 | 本项目中的用途 |
|---|---|---|
| **HomeTweaks**（HyperOS 桌面增强） | **CypressFjord**（[酷安主页](https://www.coolapk.com/u/22382603)） | Dock 常驻图标隐藏：本项目对其「通过注入桌面进程、对 Flutter 编译产物 libapp.so 打原生补丁」的方案进行分析后，以独立实现的方式完成了底栏超级小爱 / 搜索 / 互联设备图标的隐藏 |
| **MiPAD小部件修复**（nisekana.mipadwidgetsizer） | nisekana | 第三方小部件在小米平板桌面上的尺寸适配思路参考（span 计算调整） |
| **HyperStack**（com.HyperStack.byst） | 见软件内声明 | 第三方小部件与小米官方组件堆叠（miuiWidget 机制）的功能参考，本项目在逆向其机制后以自有代码实现内置 |

如果上述署名有遗漏或错误，请通过 Issue 联系我们，我们会第一时间补正。

### AI Agent 使用指南

本项目提供面向 AI Agent 的调试辅助功能（开机自动跳过锁屏、开机上报、Dock 图标隐藏）。
使用前请阅读 [docs/AGENT_GUIDE.md](docs/AGENT_GUIDE.md) 并把文档发给你的 AI Agent——
**这些功能有安全代价（降低锁屏安全性、开放网络调试通道），请务必知悉风险后再启用，项目不对后果负责。**

### 时钟的实现路径

本项目提供两条时钟实现路径：

1. **原生主题时钟（推荐，稳定）**：通过 Root 权限将主题包内的时钟组件**注入系统主题运行时目录**（`/data/system/theme/`），由 MIUI 主题引擎按原生方式渲染——效果等同官方主题商店的时钟，**布局为固定的 2×1**。
2. **Hyper 自由时钟小组件（实验性）**：本项目自研的标准 AppWidget——读取组件内的素材并**按比例计算**布局，支持任意尺寸拉伸，星期 / 时段 / 时间 / 日期 / 农历 / 天气（含天气图标）六个元素均可在工具页的布局编辑器中用数字精确调整位置、大小与字重。该功能为实验性，布局可能随桌面版本变化。

## 免责声明

- 本工具通过 Root 权限直接修改系统主题目录，操作不当可能导致界面显示异常
- 使用前请确认已理解备份与还原流程；部署前工具会自动备份
- 因使用本工具造成的任何问题由使用者自行承担
- 请勿将本工具用于盗版主题的分发，主题版权归各自作者所有

## 反馈方式

- 提交 Issue：请使用 Bug report 模板，**必须包含设备型号、系统版本、工具版本、复现步骤和完整日志**
- 没有日志的问题原则上无法排查
- 功能讨论可前往 Discussions

## 更新日志

见 [CHANGELOG.md](CHANGELOG.md)。
