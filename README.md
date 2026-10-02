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
