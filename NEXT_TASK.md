# NEXT_TASK.md — 下一阶段任务清单

> **v3.0 已发布**（vc21，Release v3.0 资产 HyperIceCream-v3.0.apk，2026-10-05 更新）。仓库: Hyper-Ice-Cream。
> 设备: 192.168.3.87:{端口重启会变}；Root = KernelSU su。构建 build_tt.py（workspace 根）；gh 在 C:\abuild\gh\bin\gh.exe。
> ⚠️ 字段初始化器禁止引用 this/Context；DSH android.jar 是残缺 stub → 模块代码对框架类型全反射。
> ⚠️ 本机 shell 是 cmd：`;` 不是分隔符（会被当成程序参数），用 `&&` 或单命令。

## 1. 【P0 待用户验收】
- [ ] 时钟小组件选择器/钉选提示均带「实验性」标注（三语）→ 用户确认显示
- [ ] 部署 Neo → 钉选 Widget → 拉 2×4 → TextClock 版验证（时间实时 + 农历 + 天气 + 时段）
- [ ] 社区页 Neo 条目下载 + 进度显示回归

## 2. 【P1 备选】
- [ ] 天气源: content://weather 查不到时降级 Open-Meteo API（需定位权限或 IP 定位）
- [ ] 白名单管理器 UI 美化；索引真实条目回填

## 3. 【P1】DockTweaks 备选方案
- Flutter 资源走 NDK AAssetManager（原生层），Java hook 拦截不到（v2.8 实测）
- 方案1: LSPlant/Dobby 原生 hook AAssetManager_open（重）
- 方案2: Launcher APK repack 替换 webp/svg（fs-verity 风险）
- 方案3: 找 Launcher Flutter 偏好开关（最轻，搜 shared_prefs + flutter SharedPreferences）
- 方案4: 询问用户是否接受"仅隐藏"做不到、改为"透明占位"

## 4. 【P2】Compose 迁移评估；开场动画（用户暂缓）

## 禁止事项
- 不做: 长按菜单移植、KSU 挂载、自建服务器、P2P、硬编码尺寸
- 用户使用设备时禁止远程点击；部署前自动备份
