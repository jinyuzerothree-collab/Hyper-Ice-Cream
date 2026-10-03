# PROJECT_HANDOVER.md — HyperOS-Theme-Installer 项目交接报告

> 生成时间: 2026-10-03
> 交接目的: 新会话/新开发者零背景接手开发
> 阅读本文档后无需回溯任何历史对话。所有事实均来自实机测试与二进制取证，标注〔实测〕〔取证〕〔推断〕〔未确认〕。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 1. 项目概述

- **项目名称**: HyperOS-Theme-Installer（应用显示名「主题直装」）
- **项目目标**: 在 Root 权限下，将第三方 MTZ 主题包的组件（状态栏/电池图标、图标、壁纸、锁屏壁纸、字体、桌面小组件）直接部署到 HyperOS 系统主题运行时目录 `/data/system/theme/`，绕过缺失/受限的主题商店完成第三方主题应用。
- **诞生原因**: 用户的 Xiaomi Pad 8 Pro 升级 HyperOS 4（Android 17）后平板固件没有完整主题商店；用户侧载手机版主题商店导致系统级崩溃循环（缺 `miuix.mipalette.MiPalette` 类的 SystemUI 插件被侧载 → SystemUI 崩溃循环 → 每天多次"闪屏重启"）。本项目是排障过程的产物：先修复崩溃，再找到"直接部署组件到 `/data/system/theme/`"这条绕过商店的可行路线（2026-10-02 实测电池图标/状态栏主题部署成功）。
- **解决的问题**: HyperOS 平板无法导入应用第三方 MTZ 主题；主题商店缺失导致小部件/字体等组件无获取渠道。
- **适用设备**: Xiaomi Pad 8 Pro（piano / 25091RP04C）〔实测〕；其他 HyperOS 平板理论可用〔未确认〕。
- **适用系统**: Android 10+（minSdk 26），实测 Android 17 / HyperOS 4.0.0.31.XPYCNXM。
- **项目定位**: 单机 Root 工具（无网络权限、无账号体系）。配套两个附属物：PC 命令行版 `tools/theme_tool.py`、社区主题库（GitHub 即后端，阶段一）。
- **当前开发状态**: v1.3（versionCode 4）已构建、已安装到用户设备（versionName=1.3 验证）、三个功能提交已推送 GitHub。**v1.3 的小组件（gadgets）部署在真机上的端到端验证尚未完成**（用户装上后还没回报小组件是否出现在桌面选择器）——这是当前最高优先级待办。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 2. 当前功能

### 2.1 已完成功能（全部〔实测〕）

| 功能 | 实现方式 / 原理 | 代码位置 |
|---|---|---|
| 主题包识别与解包 | mtz/zip = 标准 zip；`java.util.zip.ZipInputStream` 解包到 cache；单文件（无扩展名 zip 容器）直接当组件 | `MainActivity.detectComponents(File picked)` |
| 组件勾选部署（核心） | 主题引擎在运行时从 `/data/system/theme/<组件ID>` 读取组件；**每个组件 = 一个 zip 容器文件，文件名 = 组件 ID**（实测 `com.android.systemui` 3.3MB zip 部署后电池图标/状态栏主题立即生效）。部署 = `su` 执行 `cp` + `chown system_theme:system_theme` + `chmod 755`（壁纸 600） | `MainActivity.doDeploy()` |
| 已验证可部署组件 | `com.android.systemui`（状态栏/电池图标，**用户已确认生效**）、`icons`（系统/桌面图标，引擎原生支持）、`wallpaper`、`lock_wallpaper` | 同上 |
| 字体部署（实验性） | 主题包 `fonts/` 目录打 zip → `/data/system/theme/fonts/`（引擎是否读取未验证） | `doDeploy()` needFontsZip 分支 |
| 自动备份 | 部署前 `cp -a /data/system/theme/. /sdcard/ThemeToolBackup/backup_<ts>/` | `doBackup()` |
| 一键还原 | 从最近备份目录逐文件复制回 + 重启 SystemUI | `doRestore()` |
| 重启 SystemUI | `su -c 'am crash com.android.systemui'` | `btn_restart` |
| 重启桌面（v1.3 新增） | `su -c 'am force-stop com.miui.home'`（Launcher 自动重启） | `doRestartLauncher()` |
| 内置文件浏览器 | 替代 HyperOS 捆绑 SAF 选择器（其只显示极少文件）；过滤 mtz/zip/ttf，可切全部；需 `MANAGE_EXTERNAL_STORAGE` | `showBrowser()` / `showDirDialog()` |
| 主题小组件部署（v1.3 新增） | 识别顶层 widget 命名组件（`clock_2x4` 等，正则 `^(clock|weather|notes|calculator|gadget)[_-]`）+ 兼容 `gadgets/` 目录内 `*.mtz/*.mrc`；部署到 `/data/system/theme/<组件ID>` 与 `/data/system/theme/gadgets/<文件>`；逐组件独立勾选 | `WIDGET_PATTERN` + `detectComponents()` + `doDeploy()` |
| 元数据预览卡（v1.3 新增） | 解包后解析 `description.xml`（XmlPullParser，tag 容错：title/author/designer/version/description/uiVersion，首见优先）→ 显示预览图（preview/ 首图降采样）+ 名称/作者/版本/UI 版本/大小/SHA256(截断)/组件清单；缺失显示"未知" | `MetadataParser.java` + `renderMeta()` |
| SHA256 计算 | `MessageDigest` 流式 | `MetadataParser.sha256(File)` |
| 社区贡献包导出（v1.3 新增） | 部署成功后弹窗"分享主题给社区？" → 生成 `/sdcard/ThemeToolCommunity/<名称>_<ts>/`（theme.mtz + meta.json + preview/ + 打包 zip）；meta.json 含 schema/theme_name/author/version/ui_version/components/sha256/size_bytes/exported_at/tool_version/anonymous_id/device_model | `CommunityExport.java` + `doCommunityExport()` |
| 导出三入口 | 分享到 GitHub（打开仓库 Issue 页 `CONTRIBUTION_URL`）/ 保存到本地（已导出，显示路径）/ 发送给维护者（ACTION_SEND meta 文本） | `doCommunityExport()` |
| PC 命令行版 | `tools/theme_tool.py`（inspect/backup/deploy/restore/find-mtz），Python3+adb | 独立脚本 |

### 2.2 关键依赖关系

- APK 本体：**零第三方依赖**（纯 android.jar framework API + org.json 内置 + XmlPullParser 内置），无 androidx。
- Root：KernelSU（用户设备）提供 `su`；`Runtime.exec("su")` 写脚本到 stdin。
- 构建：JDK21 + aapt2 + r8(D8) + uber-apk-signer + android.jar（无 gradle）。
- PC 工具：Python 3 + adb 在 PATH。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 3. 当前项目结构

仓库根目录: `C:\Users\雪糕\.zcode\workspace\default\HyperOS-Theme-Installer\`（GitHub 仓库的本地克隆，main 分支）

```
HyperOS-Theme-Installer/
├── PROJECT_HANDOVER.md              ← 本文件
├── NEXT_TASK.md                     ← 下一阶段任务清单
├── README.md                        # 项目说明（含 123云盘镜像链接）
├── LICENSE                          # MIT
├── CHANGELOG.md                     # 1.0 / 1.1 / 1.2（v1.3 条目【待补】）
├── .gitignore                       # *.apk 但豁免 release_publish/*.apk
├── app/src/main/
│   ├── AndroidManifest.xml          # vc4/vn1.3; minSdk26 targetSdk34; MANAGE_EXTERNAL_STORAGE
│   ├── java/com/zcode/themetool/
│   │   ├── MainActivity.java        # 全部 UI/部署/浏览器/元数据渲染逻辑（单 Activity）
│   │   ├── MetadataParser.java      # description.xml 解析 + SHA256 + 组件/预览清单
│   │   └── CommunityExport.java     # 社区贡献包导出 + 匿名ID + 打包
│   └── res/
│       ├── layout/activity_main.xml # 主界面（按钮/组件勾选容器/预览图/元信息卡/日志区）
│       └── values/strings.xml
├── scripts/build_apk.py             # 公开版构建脚本（工具链路径参数化，占位符 path\to\...）
├── tools/theme_tool.py              # PC 命令行版
├── docs/功能报告.md
├── release_publish/
│   ├── HyperOS-Theme-Installer-v1.2.apk   # v1.2 发布资产（v1.3 发布时替换）
│   └── release-notes-v1.2.md
└── .github/ISSUE_TEMPLATE/
    ├── bug_report.yml               # 强制: 设备型号/系统版本/工具版本/复现步骤/完整日志
    ├── feature_request.yml
    └── config.yml                   # blank_issues_enabled: false; discussions 链接
```

**逻辑位置速查**：
- 入口/UI: `MainActivity.onCreate()`
- 主题解析: `detectComponents()` → 解包 + 组件分类（已知组件/字体/壁纸/锁屏/widget 模式/gadgets 目录）
- 元数据解析: `MetadataParser.parse()`（commit 3 的 `renderMeta()` 负责显示）
- Root 部署逻辑: `doDeploy()` → `execSu(script)`（`Runtime.exec("su")` + stdin 写脚本）
- 社区导出: `CommunityExport.build()` + `doCommunityExport()`（三入口对话框）

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 4. GitHub 信息

- **仓库**: https://github.com/jinyuzerothree-collab/HyperOS-Theme-Installer （Public）
- **默认分支**: main
- **账号**: jinyuzerothree-collab（gh 设备流授权，token 存于本机 gh keyring；`C:\abuild\gh\bin\gh.exe` 便携版）
- **Release**: v1.2 已发布（含 APK 资产）https://github.com/jinyuzerothree-collab/HyperOS-Theme-Installer/releases/tag/v1.2 ；**v1.3 尚未创建 Release**〔待办〕
- **Issues**: 已开启，模板 bug_report.yml 强制字段：设备型号/系统版本/工具版本/复现步骤/完整日志（"没有日志的问题原则上无法排查"）
- **Discussions**: 已开启
- **当前版本**: v1.3 (versionCode 4)
- **提交历史**（main）:
  - `495ce13` Initial Release v1.2
  - `25daad7` docs: add 123pan mirror download link
  - `3f118ce` feat: add gadgets deployment support
  - `7bfc43e` feat: add community theme export
  - `be0999e` feat: add theme metadata preview ← 当前 HEAD
- **Tag**: v1.2（v1.3 tag 待打）

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 5. 已完成研究成果

标注规则：〔实测〕=设备上跑过命令验证；〔取证〕=APK/固件二进制解析证据；〔推断〕=由证据推理，未直接验证。

### 5.1 Root / 权限分析（需求一）

- 〔实测〕`/data/system/theme/` 权限: `drwxrwxrwx system_theme:system_theme`（DAC 0777），SELinux 类型 `u:object_r:theme_data_file:s0`，部分文件带 MCS 类别 `c512,c768`；父目录 `/data/system` = `drwxrwxr-x system:system`（可遍历）。
- 〔实测〕**shell 身份（uid 2000 = ADB = Shizuku 默认）写 `/data/system/theme` = DENIED**——DAC 0777 放行但 SELinux `theme_data_file` 对 shell 域无 write allow。chown/chmod/删除级联不可用。
- 〔实测〕shell **可读**主题目录（READ-OK）；**可执行 `am crash com.android.systemui`**（无 root 重启 SystemUI）。
- 〔实测〕Root(su) 下写/删/chown/chmod 全部可用（整个 v1.2 部署流程即证明）。
- 〔推断〕**结论**：部署核心必须 Root；Shizuku/ADB 只能做"读"与"重启 SystemUI"两个辅助动作；Sui = root 提供器，等价 Root 不算摆脱。SELinux 策略不改（改策略本身需要 Root 且破坏安全模型，不推荐）。

### 5.2 主题运行时目录分析

- 〔实测〕引擎运行时目录 = `/data/system/theme/`，属主 `system_theme:system_theme`（一个专用 uid，主题引擎进程 com.android.thememanager 以该 uid 运行）。
- 〔实测〕组件部署模型：**组件 = 单个 zip 容器文件，文件名 = 组件 ID，平铺在 `/data/system/theme/` 下**。`com.android.systemui`（zip 内 res/ + nightmode/res/ + theme_values.xml）部署后状态栏/电池图标立即生效（am crash 重启 SystemUI 后）。
- 〔实测〕已部署并生效：com.android.systemui（电池图标主题，用户确认）、icons（遇见主题 11,746,223 字节，与主题包 icons 完全一致）、lock_wallpaper、wallpaper（600 权限）。
- 〔取证〕目录内还有引擎自有内容：`.runtime/`、`rights/`（主题版权）、`compatibility-v12/`；无 currentconfig 文件。
- 〔实测〕出厂默认主题基座 = `/product/media/theme/default/`（含 components + gadgets/ + default.mtz 存根 22 字节）。`/system/media` 是指向 `/product/media` 的**符号链接**。
- 〔推断〕Launcher 按活动主题基座解析：用户主题 `/data/system/theme/` 优先，回退出厂基座（见 5.4）。

### 5.3 Launcher / Gadgets 分析（需求二）

- 〔取证〕出厂 `gadgets/` 内是小组件包：`calculator.mtz`、`clock_classical.mtz`+`.mrc`、`notes.mtz`、`weather_4x1.mtz`、`weather_4x4.mtz`——每个小组件一个独立 mtz。
- 〔实测样本〕第三方主题 Neo.mtz（71.5MB，作者 JellyBeans）中小组件以**顶层 zip 容器文件**形态存在：`clock_2x4`（357KB，内含 manifest.xml + src/num/white/type_*/num_*.png），与 `com.android.systemui` 同级同格式。**没有 gadgets/ 目录**。
- 〔取证〕com.miui.home（75MB）内置完整小组件加载链：`GadgetMtzParser`、`GadgetFactory`、`GadgetController`、`GadgetFromOverlay`、`GadgetList`、`GadgetClock`、`GadgetSearch`、`Upgrader`；二进制含 12 处 `/data/system/theme` 引用（含 `/data/system/theme/ai_icon/`、`/data/system/theme/market/icon.config`）与 `gadgets/clock.mtz`、`gadgets/weather_clock.mtz` 相对路径。
- 〔推断〕加载路径 = `<活动主题基座>/gadgets/<名称>.mtz`（出厂风格）或 `/data/system/theme/<组件ID>`（用户主题组件，与 systemui 同构）。
- 〔实测〕平板 Launcher 的加载代码**完整存在，机制没有缺失**——缺失的是分发渠道（主题商店）。
- 〔未确认〕把第三方主题的 clock_2x4 等部署到 `/data/system/theme/` 后，Launcher 的组件选择器是否出现主题小组件（v1.3 已部署代码，等待用户在设备上验证——NEXT_TASK 第一项）。
- 〔取证〕Settings.apk 内 TaplusController 类存在但未接线（`Could not find Context-only controller for pref: com.android.settings.special.TaplusController`）。

### 5.4 传送门（contentextension）分析（独立子系统，与主题工具并行）

- 〔实测〕传送门 App = com.miui.contentextension（手机版侧载为普通应用）；配置注册被 `x.a.b()`（com.miui.contentcatcher 进程，/product/app/ContentCatcherOS4）的 SET_ACTIVITY_WATCHER 校验拦截。
- 〔实测〕后台读剪贴板被 AOSP 限制：`ClipboardService: Denying clipboard access... not in focus`。
- 〔实测〕自建 Xposed 模块 TaplusFix（com.zcode.taplusfix）v7 修复了两个门：①hook x.a.b 放行 updateClientConfig；②hook system_server 的 ClipboardService.clipboardAccessAllowed（ret Z，dex proto 解析确认）对 contentextension 强制 true。**剪贴板触发链路全通**（用户实测：闲鱼链接→小米商城卡片→跳转）。
- 〔取证〕长按菜单管线在 OS4 平板固件中**被物理删除**（全部 boot*.vdex 扫描 0 条 taplus/contentextension 字符串；同设备 OS3 时代可用 = 回退；小米折叠屏构建包含它）。恢复 = 从折叠屏 ROM 移植框架管线，超出模块/Hook 范畴，已建议停止。
- 〔取证〕小米私有门 `ClipboardServiceStub.clipboardAccessResult`（ret I）语义未知——TaplusFix v7 以 OBSERVE 模式挂载（仅记录），若剪贴板路径失效可在 v8 强制。

### 5.5 社区主题库分析（需求三）

- 设计定稿：GitHub 仓库即后端（零服务器成本）。阶段一 = 应用内导出标准化贡献包（已实现）+ GitHub 手动收录；阶段二 = index.json + 客户端社区页；阶段三 = GitHub App 自动校验 CI。不推荐自建服务器/P2P/网盘 API。

### 5.6 元数据提取分析（需求四）

- 〔实测样本 Neo.mtz〕description.xml 结构：`<theme><version>29.260904</version><uiVersion>17</uiVersion><author>JellyBeans</author><designer>...<title>Neo</title><description>长文</description><authors>/<designers>/<titles>/<descriptions> locale 变体...`
- 〔实测〕组件 = 全部顶层 zip 容器文件；预览图 = `preview/` 目录 jpg。
- 已实现解析（MetadataParser.java），字段缺失回退"未知"。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 6. 当前已知事实（快速索引）

**路径（设备）**
- 主题运行时: `/data/system/theme/`（SELinux `theme_data_file`）
- 出厂默认主题: `/product/media/theme/default/`（gadgets/ 在此）
- `/system/media` → `/product/media` 符号链接
- SystemUI 插件出厂位: `/product/app/MIUISystemUIPlugin`（v18.2.2.2.0）
- Launcher: com.miui.home（/data/app, 75MB）; 主题引擎: com.android.thememanager（uid system_theme）; ContentCatcher: com.miui.contentcatcher（/product/app/ContentCatcherOS4）
- LSPosed 配置: /data/adb/lspd/config/modules_config.db（SQLite，26 模块/137 scope）

**类名（已确认存在）**
- Launcher: GadgetMtzParser, GadgetFactory, GadgetController, GadgetFromOverlay, GadgetList, GadgetClock
- ContentCatcher: `x.a.b(String)`（权限门）, ContentCatcherService, ClipboardServiceStub.clipboardAccessResult(int)
- AOSP: ClipboardService.clipboardAccessAllowed(boolean)

**已确认可行**
- 组件 zip 容器直部署到 /data/system/theme（com.android.systemui 电池图标主题生效）
- wallpaper/lock_wallpaper/icons 部署
- shell 身份读主题目录 + am crash
- LSPosed hook system_server（clipboard）与 com.miui.contentcatcher（x.a.b）稳定运行
- gh-proxy.com 镜像让 123 云盘离线下载 GitHub 资产

**已确认不可行**
- 无 Root 写 /data/system/theme（SELinux theme_data_file 拦 shell 与普通应用域）
- KSU 挂载（本设备）: ksud 报 mount=true 但 product//system_ext 路径不生效；ksud 安装器丢弃 product/ 路径文件；ksud 拒绝纯数字模块 ID
- 18.3 线 SystemUI 插件修改版在本机 SystemUI 17.03 上运行（缺 MiPalette + 接口不匹配，原作者确认不适配）
- 123 云盘直接离线下载 GitHub 资产（服务器解析失败；需 gh-proxy 镜像中转）
- 123 云盘分享 APK（需实名认证后才能分享——用户已完成实名，分享链接已生成）

**环境事实**
- aapt2 / `java -jar` 在含中文的路径下失败 → 构建暂存到 C:\abuild
- Windows Python 文本模式写脚本产生 CRLF → Android mksh 把 `\r` 并入参数（曾导致 /data/local/tmp 目录"消失"）→ 写脚本必须 `newline='\n'`
- javac 必须编译全部 .class（含内部类 MainHook$1）
- 平板无线调试端口每次重启都变；adb 链路慢（0.3-8MB/s 波动）且常掉线；旧端口 10061 拒绝
- 用户 git 全局配置 `url.https://gh-proxy.com/https://github.com/.insteadof` 会劫持 push（已在本仓库用最长匹配自身重写抵消：`url."https://github.com/jinyuzerothree-collab/HyperOS-Theme-Installer".insteadOf` 同值）

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 7. 当前待开发功能

| 优先级 | 功能 | 难度 | 依赖 |
|---|---|---|---|
| P0 | **gadgets 真机端到端验证**（用户在桌面小组件选择器确认） | 低 | 无——代码已装 |
| P0 | v1.3 GitHub Release（tag v1.3 + APK + notes）+ CHANGELOG/README 补 1.3 条目 | 低 | 验证通过 |
| P1 | gadgets 兼容性微调（若选择器不出现：按 Launcher 日志调整部署形态，可能需 .mrc 同放/目录结构变化） | 中 | P0 失败时 |
| P1 | 社区库阶段二：index.json 自动生成（GitHub Actions）+ 应用内"社区"只读浏览页 | 中 | 无 |
| P2 | 天气组件数据源确认（com.miui.weather2 在平板的可用性） | 低 | — |
| P2 | Shizuku 只读模式（inspect 场景） | 低 | 用户装 Shizuku |
| P3 | 小组件自动上桌面（Launcher 内部 API，高风险） | 高 | P0 闭环 |
| 搁置 | 传送门长按菜单移植（OS4 平板固件缺框架代码，需从折叠屏 ROM 移植，OTA 会破坏） | 极高 | 用户明确要求才做 |

平板侧遗留（与本项目无关但用户知晓）：Scene 升级（scene-daemon 在 A17 SIGSEGV）；HyperGlow 通知使用权+自启动设置。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 8. Gadgets 开发状态

- **为什么做**: 手机主题自带桌面小组件（时钟/天气/便签/计算器），平板因无主题商店无法获取；取证证明 Launcher 加载代码完整，仅缺分发与部署。
- **研究结论**: 组件 = mtz 内顶层 zip 容器（如 clock_2x4）或 gadgets/ 目录内 .mtz/.mrc；加载路径 = 活动主题基座下 `gadgets/<名称>.mtz`（出厂风格）或平铺组件 ID（用户主题风格，与 com.android.systemui 同构——已部署验证过同构性）。
- **当前实现状态**: v1.3 已实现识别（两种形态）+ 逐组件勾选 + 部署（/data/system/theme/<组件ID> 与 /data/system/theme/gadgets/）+ 重启桌面按钮 + 操作提示。APK 已装到用户设备。
- **缺少什么**: 真机端到端确认（用户在"添加小组件"选择器里看到主题组件）。
- **下一步**: 见 NEXT_TASK.md 第 1-3 条。
- **测试样本**: Neo.mtz（71.5MB，C:\Users\雪糕\Downloads\Neo.mtz，含 clock_2x4 时钟组件）已用于解析并驱动实现。用户计划上传更多含小组件的主题包用于兼容性验证。
- **验证流程**: 选 mtz → 看组件清单出现时钟组件 → 部署（自动备份）→ 重启桌面 → 桌面长按 → 添加小组件 → 找主题组件 → 添加渲染。失败则 `adb logcat | grep -aiE "Gadget|Mtz"` 定位（GadgetMtzParser/GadgetFactory 日志），按需调整部署形态。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 9. 社区主题库开发状态

- **方案**: GitHub 仓库即后端（零服务器成本）。PR/Issue = 审核队列；raw/Release = 分发；Actions = CI 与索引生成。
- **为什么选 GitHub**: 项目已托管在 GitHub；零成本；PR 天然审核流；版本化与防篡改天然具备；国内访问走镜像可缓解。
- **数据格式**: 导出目录 `<name>_<ts>/{theme.mtz, meta.json, preview/}` + `<name>_<ts>_community.zip`；meta.json 字段 schema=1, theme_name, author, version, ui_version, components[], sha256, size_bytes, exported_at, tool_version, anonymous_id, device_model。
- **阶段一（已实现导出侧）**: 应用内"分享主题给社区？"→ 三入口（GitHub Issue 页/本地路径/系统分享）。收录 = 维护者手动把贡献包放进仓库 `themes/` 目录。
- **阶段二（待做）**: 仓库根 `index.json`（GitHub Actions 从 themes/ 自动生成）+ 应用"社区"标签页只读浏览 + 下载（raw 直链 + gh-proxy 镜像回退）。
- **阶段三（待做）**: 贡献自动化（GitHub App/Issue Form 附件自动入库）+ 哈希/格式 CI 校验。
- **隐私/防恶意**: 上传仅含主题包+用户显式署名（可匿名 UUID）；无设备指纹字段；格式白名单校验；sha256 指纹；人工审核首包；举报下架；mtz 为纯资源无执行面。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 10. 元数据解析开发状态

- **已支持**（MetadataParser.java）: description.xml 的 title/author/designer/version/description/uiVersion（首见优先，tag 小写匹配，TEXT 事件取值）；顶层组件清单（排除 preview/ 与 description.xml）；preview/ 图片清单；pickedSource 的 SHA256 与大小。全部字段缺失回退"未知"。
- **计划支持**: locale 变体优先级（zh_CN 优先于默认）；`theme_values.xml` 组件参数；预览图分类（launcher/lockscreen/aod/icons 按文件名前缀）；适配度提示（资源密度目录 vs 当前设备）；把 meta 接入社区导出的字段完整性校验。
- **需要解析的文件**: description.xml（已做）、theme_values.xml（部分包有）、clock_2x4 等组件容器的 manifest.xml（组件级元信息，尚未解析）。
- **需要提取的字段**: 现有六个 + 组件级名称/尺寸（从组件 ID 或 manifest）。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 11. 已知问题

1. **gadgets 端到端未验证**〔未确认〕：v1.3 部署逻辑按二进制证据实现，但"桌面小组件选择器出现主题组件"尚未被用户确认。
2. **字体部署实验性**〔未确认〕：/data/system/theme/fonts 是否被 OS4 引擎读取未验证。
3. **剪贴板 OBSERVE 门未闭环**：TaplusFix v7 对 ClipboardServiceStub.clipboardAccessResult 只观察不强制（int 语义未知）；当前剪贴板路径可用，暂不需要 v8。
4. **设备连的不稳定**：adb 掉线频繁、端口随重启变化、传输速度 0.3-8MB/s 波动。
5. **构建环境坑**：aapt2/java -jar 中文路径失败；CRLF；javac 必须含内部类与全部新 .java；github.com 直连超时必须走代理 127.0.0.1:7897；用户全局 git gh-proxy insteadOf 劫持 push（本仓库已用自身重写抵消）。
6. **mount 坏死**：KSU 挂载在本机对 product//system_ext 无效（ksud 假 mount=true）——任何依赖 KSU 挂载的方案都不可用。
7. **用户设备 LSPosed 22 模块全启用状态**（用户自行开启，含 hyperbetter/hyperisland 等）——系统稳定性由用户配置决定，与本工具无关但排障时需注意变量。
8. **兼容性风险**：mtz 主题作者适配质量浮动；非标准包（缺 description.xml）降级为组件清单展示；天气组件依赖天气数据源。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 12. 下一个开发者行动指南（按顺序执行）

**第一步：gadgets 端到端验证（P0）**
1. 与用户确认 v1.3 已装（`adb shell dumpsys package com.zcode.themetool | grep versionName` → 1.3）
2. 让用户选 Neo.mtz → 确认元数据卡与时钟组件出现在勾选列表
3. 勾选时钟组件部署 → 点「重启桌面」→ 桌面长按 → 添加小组件 → 查找主题组件
4. 成功 → 进第二步；失败 → `adb logcat | grep -aiE "Gadget|Mtz"` 抓 GadgetMtzParser 日志，按报告 5.3 的两种形态（gadgets/ 子目录 vs 平铺组件 ID）调整部署路径，重测

**第二步：发布 v1.3（P0）**
1. CHANGELOG.md 补 [1.3] 条目；README 功能列表加小组件
2. 复制新 APK 到 release_publish/（命名 HyperOS-Theme-Installer-v1.3.apk）
3. `gh release create v1.3 --repo jinyuzerothree-collab/HyperOS-Theme-Installer --title "v1.3" --notes-file ... release_publish/...apk`（记得 HTTPS_PROXY=http://127.0.0.1:7897，gh 在 C:\abuild\gh\bin\gh.exe）
4. git tag v1.3 并推送

**第三步：社区库阶段二（P1）**
1. 仓库加 `.github/workflows/index.yml`：扫描 themes/ 生成 index.json
2. 应用加"社区"页：拉 index.json（raw + gh-proxy 回退）→ 列表 → 下载部署（复用 doDeploy）
3. 收录规范写进 CONTRIBUTING.md

**第四步（可选）**：Shizuku 只读模式；天气数据源确认；TaplusFix v8（OBSERVE 数据到位后强制 stub 门）。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 13. 当前工作目录

| 用途 | 路径 |
|---|---|
| **项目仓库根** | `C:\Users\雪糕\.zcode\workspace\default\HyperOS-Theme-Installer` |
| 应用源码 | `<仓库根>\app\src\main\`（java/res/AndroidManifest.xml） |
| 构建脚本（开发用） | `C:\Users\雪糕\.zcode\workspace\default\build_tt.py` |
| 构建输出 | `C:\abuild\ttap_build\signed\themetool_unsigned-aligned-debugSigned.apk`（当前=v1.3） |
| 构建暂存（ASCII 路径，aapt2 要求） | `C:\abuild\ttap_src\`、`C:\abuild\ttap_build\` |
| 工具链 | JDK21: `G:\大肥鱼\tools\jdk\jdk-21.0.2\bin\`；aapt2/r8: `G:\大肥鱼\Xiaomi15_Camera_Port_Project\Source\camera_lsposed\build\`；android.jar: `C:\abuild\android.jar`；签名器: `C:\abuild\uber-apk-signer.jar` |
| gh CLI（便携） | `C:\abuild\gh\bin\gh.exe`（授权账号 jinyuzerothree-collab） |
| Release 目录 | `<仓库根>\release_publish\` |
| 工作区杂项（调试脚本/取证文件，不属于仓库） | `C:\Users\雪糕\.zcode\workspace\default\`（analyze/patch/inspect_*.py、*_test.sh、ce.txt、services.jar、stock 插件 apk、theme_backup_* 等） |
| 主题样本 | `C:\Users\雪糕\Downloads\Neo.mtz`（71.5MB，clock_2x4 时钟组件样本） |
| 设备临时区 | 设备 `/data/local/tmp/`（各类 test/backup 脚本） |

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 14. 当前配置

- **SDK**: compile android.jar API 37（C:\abuild\android.jar，137MB，取自 G:\大肥鱼\tools）；minSdk 26；targetSdk 34
- **构建**: 无 Gradle。流水线 = aapt2 compile res → aapt2 link(--java 生成 R.java) → javac --release 8（编译 java/ 全部 .java + R.java）→ r8.jar 的 D8 --min-api 26 → python zipfile 注入 classes.dex 到 aapt2 base.apk → uber-apk-signer（zipalign+debug 签名 v2/v3）
- **语言**: Java 8 源级（无 Kotlin）
- **依赖库**: 无第三方（纯 framework + 内置 org.json/XmlPullParser）
- **签名**: uber-apk-signer 内嵌 debug keystore（注意：用户侧重签流程曾被用于绕过 CorePatch 场景，现直装即可）
- **Root 方案**: 用户设备 KernelSU v3.2.5（shell 已授权 su）；Zygisk Next 1.5.0；LSPosed 2.2.0（用户已自行启用全部 22 模块，含 CorePatch）
- **构建机**: Windows 10 (10.0.19045)，Python 3.12，git 2.56，adb 33.0.0（C:\Windows\System32\adb.exe）
- **网络**: github.com 必须经代理 `http://127.0.0.1:7897`（HTTPS_PROXY/HTTP_PROXY）；aapt2 与 java -jar 不接受中文路径

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 15. 开发日志（时间线）

- **2026-10-02 上午**: 用户报四故障（SCENE 无 root / HyperGlow 无权限 / 互传闪屏重启 / 音乐异常）。无线调试配对接入（设备 25091RP04C / HyperOS 4.0.0.31 / Android 17 / KSU 3.2.5 / LSPosed 2.2.0）。
- **2026-10-02 中午**: 定位崩溃循环根因 = 侧载 miui.systemui.plugin v18.3.2.22.0 缺 MiPalette 类 → SystemUI 崩溃循环（7 轮 SYSTEM_RESTART）。SCENE = scene-daemon 在 A17 dlsym 失败 SIGSEGV（KSU 授权本身正常）。HyperGlow = 通知使用权被拒 + 后台剪贴板限制。
- **2026-10-02 下午**: 两次尝试回退插件（pm uninstall --user 0 的隐藏陷阱 → install-existing 往返 → 最终 root `pm uninstall` 正确回退出厂插件）。用户自行装修改版循环复现 → 18.3 线判死（作者确认不适配）。
- **2026-10-02 傍晚**: 转向主题直装方案。发现 /data/system/theme 运行时目录可写路径。部署遇见主题 com.android.systemui → **电池图标/状态栏主题生效（用户确认）**。锁屏/壁纸同步部署（已备份）。确诊三个"灵异现象"：ksud 拒绝纯数字模块 ID、/system/media 符号链接致挂载失效、Windows CRLF 破坏设备脚本。
- **2026-10-02 晚**: 传送门攻坚：TaplusFix Xposed 模块用 DSH 工具链从零构建（aapt2+javac+D8+签名，三版本迭代：入口类名修正/内部类遗漏/反射适配私有 stub）。v7 双 Hook 生效：剪贴板触发全通（用户实测闲鱼卡片跳转）。长按菜单管线经 vdex 全扫确认被 OS4 平板固件删除 → 建议停止。
- **2026-10-03 凌晨**: 主题直装 APK（com.zcode.themetool）v1.0-1.2 用同一工具链构建并装机，用户实测好评。123 云盘镜像：登录、离线下载经 gh-proxy 拉取 APK 入库、实名认证后分享链接生成。
- **2026-10-03**: GitHub 开源发布（仓库/Release v1.2/Issue 模板/README/CHANGELOG/MIT）。
- **2026-10-03 下午**: 可行性研究（需求一 Root 依赖实测：SELinux theme_data_file 拦 shell 写；需求二 gadgets 取证：出厂 gadgets/ + Launcher GadgetMtz 管线 + Neo.mtz 顶层组件形态）。输出《下一阶段功能可行性分析报告》。
- **2026-10-03 晚（当前）**: v1.3 实现（gadgets 部署 / 社区导出 / 元数据预览三提交，已推送）。APK 已装用户设备。**待办：用户实测小组件 → 发布 v1.3 → 社区库阶段二。**

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
