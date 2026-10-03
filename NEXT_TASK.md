# NEXT_TASK.md — 下一阶段任务清单

> **v2.3.2 已发布 + WidgetCompat 模块 v1.0**。仓库: github.com/jinyuzerothree-collab/Hyper-Ice-Cream（已改名）。
> 设备: 无线调试（端口变化找用户要）；Root = KernelSU su。构建 build_tt.py / build_compat.py；网络代理 7897。
> ⚠️ 用户使用平板时禁止远程 tap；Java 字段初始化器禁止引用 this/Context；DSH android.jar 缺类（ActivityInfo 等）→ 模块代码对框架类型全反射。

## 1. 【P0 待用户验收】
- [ ] 沉浸式状态栏（白色刘海块消失）
- [ ] 关于页：细字标（sans-serif-light）+ 整页渐变融合 + 语言切换（简/繁/EN）
- [ ] Hyper 时钟小组件：工具页「添加 Hyper 时钟小组件到桌面」一键钉选 → 拉到 2×4 看比例复现渲染
- [ ] **WidgetCompat 模块**（已装 APK，包名 com.hypericecream.widgetcompat）：LSPosed 启用 → 作用域勾 com.miui.home + android → 重启 → 旧版小组件应重新出现在桌面选择器并可堆叠
- [ ] 白名单文件 /data/system/hypericecream_widget_whitelist.txt（enabled=1 + com.zcode.themetool 已预置；添加其他包名即恢复其组件）

## 2. 【P0 下轮交付】DockTweaks 模块（Dock 三常驻图标）
- 〔取证〕Dock 为 Flutter 渲染：图标资源 = com.miui.home APK 内 flutter_assets（ic_dock_xiaoai_*.svg / icon_super_xiaoai_*.webp / search_* / resident）
- 方案: hook AssetManager.open(String)，匹配 dock 图标资源名 → 返回透明图（隐藏）或用户自定义图（换圆角观感）
- 参考 build_compat.py 全反射管线；UI: 三开关 + 自定义图路径

## 3. 【P1】
- [ ] 白名单管理 UI（列出有 widget 接收器的三方包 → 勾选 → su 写白名单文件）
- [ ] HyperWidget 天气接入（content://weather）+ 分钟刷新 + 多字体换挡
- [ ] 索引真实条目回填（Neo/超级液态米果 sha256 + download_url）
- [ ] Compose/MIUIX 迁移评估（真 blur 前提）

## 禁止事项
- 不做: 长按菜单移植、KSU 挂载、自建服务器、P2P、硬编码尺寸
- 用户使用设备时禁止远程点击；部署前自动备份
