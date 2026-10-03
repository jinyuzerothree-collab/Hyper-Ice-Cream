# NEXT_TASK.md — 下一阶段任务清单

> **v2.6 已构建待装机**（281KB，vc16/2.6；设备掉线，等端口）。仓库: Hyper-Ice-Cream。
> v2.5 全合一已发布（app=模块合一，taplusfix 已卸载）。构建 build_tt.py / build_compat.py；代理 7897。

## 1. 【P0 待装机验收】v2.6 新增
- [ ] 工具页: Dock 三常驻图标隐藏开关（写 /data/system/hypericecream_dock.conf，重启桌面生效）
- [ ] 工具页: 假信号「启动显示」浮层（SYSTEM_ALERT_WINDOW 授权后，状态栏右上角四格+4G 图标，纯显示）
- [ ] 关于页字重加粗（全部 medium/bold）
- [ ] 全局渐变背景铺满四页 + 滑动过渡动画
- [ ] HyperWidget 默认尺寸改为 2×4（targetCellHeight=4）

## 2. 【P0 用户操作】合一模块激活
- [ ] LSPosed → Hyper Ice Cream → 启用 → 作用域: com.miui.home + android + com.miui.contentcatcher → 重启
- [ ] 旧组件恢复/Hyper Widget/传送门/剪贴板逐项验收
- [ ] logcat 抓 HyperIceCream 标签验证三进程 hook 装载

## 3. 【P1】
- [ ] 假信号增强: 真正让系统显示有信号的格（需 hook ServiceState/SignalStrength per-slot，当前浮层方案已保证可见性）
- [ ] DockTweaks: 返回空流若导致 Flutter 异常 → 改全透明同名图
- [ ] 白名单/尺寸管理 UI
- [ ] 索引真实条目回填（Neo/超级液态米果）

## 禁止事项
- 不做: 长按菜单移植、KSU 挂载、自建服务器、P2P、硬编码尺寸
- 用户使用设备时禁止远程点击；部署前自动备份
