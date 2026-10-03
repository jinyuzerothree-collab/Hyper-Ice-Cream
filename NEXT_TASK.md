# NEXT_TASK.md — 下一阶段任务清单

> **v2.5 全合一已发布并装机**：com.zcode.themetool = 主题工具 + LSPosed 模块（hook/HyperHook.java）。taplusfix 独立模块已卸载。
> 构建 build_tt.py（写 assets/xposed_init=HyperHook；javac classpath 含 xposed-stub）；模块配置文件 /data/system/hypericecream_*.txt|conf。
> 网络: 代理 7897；gh 在 C:\abuild\gh\bin\gh.exe；仓库 Hyper-Ice-Cream。

## 1. 【P0 重启后验收】
- [ ] LSPosed → Hyper Ice Cream（模块区）→ 启用 → 作用域: com.miui.home + android + com.miui.contentcatcher（重启后检查是否保持）
- [ ] 旧组件恢复：桌面长按 → 小部件 → 旧系统声明组件出现 → 添加 → 尺寸表已预置 com.zcode.themetool 2 4
- [ ] Hyper Widget 2×4 比例复现渲染
- [ ] 全局渐变背景 + 滑动过渡 + 无黑条
- [ ] 传送门/剪贴板（原 TaplusFix 功能）不回退
- [ ] logcat 抓 HyperIceCream 标签确认 hooks 在三进程装载

## 2. 【P1】DockTweaks 收尾（hook 已内置，默认关）
- 配置 /data/system/hypericecream_dock.conf: hide_phone=1 / hide_xiaoai=1 / hide_search=1 即隐藏对应图标（资源流返回空）
- 待做: App 内 UI 开关；若返回空流导致 Flutter 异常，改为返回全透明同名图片
## 3. 【P1】假信号收尾（hook 已内置，默认关 fake=0）
- 已实现: system_server + systemui 的 SubscriptionManager 列表注入假订阅（SubscriptionInfo 最大参数构造反射，slot/名称/运营商可配置）
- 已知缺口: 只注入订阅不注入 ServiceState/SignalStrength → 图标可能显示无服务/叉号；下轮 hook ServiceState/SignalStrength.getLevel per-slot
- 启用: /data/system/hypericecream_signal.conf 写 fake=1、slot=1、name=中国移动
## 4. 【P2】白名单/尺寸管理 UI；假信号槽位 UI；开场动画（用户暂缓）

## 禁止事项
- 不做: 长按菜单移植、KSU 挂载、自建服务器、P2P、硬编码尺寸
- 用户使用设备时禁止远程点击；部署前自动备份
