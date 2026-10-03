# NEXT_TASK.md — 下一阶段任务清单

> **v2.2 已发布**。设备: 无线调试（端口变化找用户要）；Root = KernelSU su。构建 `python C:\Users\雪糕\.zcode\workspace\default\build_tt.py`；网络走代理 7897。
> ⚠️ 用户正在使用平板时禁止远程 input tap 干扰。Java 字段初始化器禁止引用 this/Context（v2.1 闪退教训）。

## 1. 【P0 新功能——已调研，下轮实现】DockTweaks 模块（Dock 三常驻图标）
- 〔取证〕hyper_launcher_app Dock 是 **Flutter 渲染**：三常驻图标 = com.miui.home APK 内 flutter_assets 资源（ic_dock_xiaoai_*.svg / icon_super_xiaoai_*.webp / search_* / resident 标记）
- 方案: LSPosed 模块 hook `AssetManager.open(String)`（com.miui.home 进程），对匹配 flutter_assets 的 dock 图标资源流重定向:
  - 隐藏模式 → 返回全透明 webp/svg 同名资源
  - 自定义模式 → 返回用户选择的图片（可带圆角透明边 → 实现"圆角改变"观感）
- 模块名: com.hypericecream.docktweaks；UI 三开关 + 自定义图选择；用既有 hooksrc 工具链（javac/r8/aapt2/签名，注意 stub 只反射调用）
- 回归风险: launcher 重启后生效；资源名列表按设备 dump 确认

## 2. 【P0 待用户验收 v2.2】
- [ ] 关于页 v2：无图标、纯粗体文字、整页渐变无分界线
- [ ] HyperWidget v2：部署时钟后桌面添加「Hyper Ice Cream」小部件，拉到 2×4——应按时钟原始比例复现（日期/时间位置同主题）
- [ ] 部署时钟时宽×高选择器弹窗
- [ ] 闪烁/闪退确认消失

## 3. 【P1】
- [ ] HyperWidget 天气接入（主题 weather 贴图 + content://weather 查询）
- [ ] 多字体换挡（type_0~11）
- [ ] 索引真实条目回填 + 下载回归
- [ ] Compose/MIUIX 迁移评估（真 blur）

## 禁止事项
- 不做: 长按菜单移植、KSU 挂载、自建服务器、P2P、硬编码尺寸
- 用户使用设备时禁止远程点击；部署前必须自动备份
