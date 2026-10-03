# NEXT_TASK.md — 下一阶段任务清单

> **v2.8 已装机**（vc18/2.8，白名单 getInstalledProviders 修复版）。仓库: Hyper-Ice-Cream。Release v2.6 已发布（v2.7 tag 的 APK 也是好版，v2.8=白名单修复版）。
> 设备: 192.168.3.87:37033（端口重启会变）；Root = KernelSU su。构建 build_tt.py；代理 7897。
> ⚠️ 字段初始化器禁止引用 this/Context；DSH android.jar 是残缺 stub → 模块代码对框架类型全反射。

## 1. 【P0 待用户验收】
- [ ] LSPosed → Hyper Ice Cream 模块已启用、三作用域已勾（数据库自动配置过，重启后检查是否保持）
- [ ] 工具页「选择要恢复的组件应用」→ 应列出**所有**带组件的应用（修复后用 getInstalledProviders）→ 勾选 → 保存 → 重启 → 旧组件出现在小部件列表
- [ ] Hyper Widget 默认 2×4（hyper_widget_info targetCellHeight=4 已改）→ 工具页一键钉选 → 验证渲染（数字时钟+日期）
- [ ] 关于页字重（medium/bold）确认
- [ ] 沉浸式状态栏（白色刘海消失）
- [ ] Dock 图标隐藏开关（工具页三复选框，写 dock.conf，重启桌面生效——注意 Flutter 原生加载可能拦截不到，如实反馈）

## 2. 【P1 下轮主攻】HyperWidget v3（用户核心不满：缺农历/天气/位置）
- [ ] 农历: android.icu.util.ChineseCalendar 绘制农历月日（第二行）
- [ ] 天气: content://weather 查询或系统天气数据 + 主题 weather 贴图
- [ ] 位置: manifest frac 解析加固（当前默认 0.12/0.42/0.88 只对 Neo 准确，其他主题需实测）
- [ ] Claude 问答 4×2 异常: 疑似其自身声明（确认非本模块问题）

## 3. 【P1】DockTweaks 备选方案
- Flutter 资源走 NDK AAssetManager（原生层），Java hook 拦截不到（v2.8 实测）
- 方案1: LSPlant/Dobby 原生 hook AAssetManager_open（重）
- 方案2: Launcher APK repack 替换 webp/svg（fs-verity 风险）
- 方案3: 找 Launcher Flutter 偏好开关（最轻，搜 shared_prefs + flutter SharedPreferences）
- 方案4: 询问用户是否接受"仅隐藏"做不到、改为"透明占位"

## 4. 【P2】索引真实条目回填；Compose 迁移评估；开场动画（用户暂缓）

## 禁止事项
- 不做: 长按菜单移植、KSU 挂载、自建服务器、P2P、硬编码尺寸
- 用户使用设备时禁止远程点击；部署前自动备份
