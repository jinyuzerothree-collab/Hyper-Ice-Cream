# NEXT_TASK.md — 下一阶段任务清单

> **仓库: Hyper-Ice-Cream（已改名）**。模块: Hyper Ice Cream Hook v2.4（合并版，包名 com.zcode.taplusfix，已装机 vc9/2.4）。构建 build_tt.py（应用）/ build_hook.py（模块）；网络代理 7897；gh 在 C:\abuild\gh\bin\gh.exe。
> ⚠️ 用户使用平板时禁止远程 tap；字段初始化器禁止引用 this/Context；DSH android.jar 缺 ActivityInfo 等类 → 模块对框架类型全反射；javac classpath = xposed-stub.jar + android.jar。

## 1. 【P0 待用户操作/验收】
- [ ] LSPosed → Hyper Ice Cream Hook（原 Taplus Fix）→ 作用域勾选 **com.miui.home + android + com.miui.contentcatcher** → 重启
- [ ] 旧版小组件恢复验证（白名单 /data/system/hypericecream_widget_whitelist.txt 已含自身包；添加其他包名即扩展）
- [ ] 应用 v2.3.1+：黑字标/沉浸状态栏/一键钉选/宽高选择器
- [ ] 独立 WidgetCompat 模块已卸载（功能并入 Hook v2.4）

## 2. 【P0 新功能调研完成——下轮实现】假信号工具（小白卡）
- 边界诚实说明: 只能做**显示层伪装**（状态栏/控制中心显示假信号格），射频层不可能凭空产生网络，打电话上网仍不可用
- 〔取证〕SystemUI 信号栈完整存在（C:\abuild\sysui.apk 已拉取）: StatusBarSignalPolicy / MobileSignalController / SignalCallback / ModernStatusBarMobileView
- 实现方案: LSPosed 模块 hook com.android.systemui → StatusBarSignalPolicy（或 MobileSignalController.getState）注入假 mobile state（SIM2 槽位显示 4/5 格 + 4G/5G 文本）；需要先 jadx 反编译 SystemUI 定位精确方法签名（下一轮第一步）
- UI: 开关 + 槽位选择 + 信号格数选择
- 风险: SystemUI hook 崩溃=状态栏循环；沿用「仅目标状态替换、异常吞掉」策略；先观察模式再强制

## 3. 【P1】DockTweaks 模块（Dock 三常驻图标隐藏/换图，Flutter 资源 Hook 方案已定稿）
## 4. 【P1】白名单管理 UI（列出三方 widget 接收器 → 勾选 → su 写白名单）
## 5. 【P2】HyperWidget 天气/分钟刷新/多字体；索引回填；Compose 迁移评估；开场动画（素材待定）

## 禁止事项
- 不做: 长按菜单移植、KSU 挂载、自建服务器、P2P、硬编码尺寸
- 用户使用设备时禁止远程点击；部署前自动备份
