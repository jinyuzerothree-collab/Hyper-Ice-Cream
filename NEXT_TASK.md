# NEXT_TASK.md — 下一阶段任务清单

> **v2.7 已发布**（vc17/2.7，287KB）。仓库: Hyper-Ice-Cream。构建 build_tt.py；代理 7897；gh 在 C:\abuild\gh\bin\gh.exe。
> ⚠️ 用户使用设备时禁止远程 tap；字段初始化器禁止引用 this/Context；DSH android.jar 是残缺 stub → 模块代码对框架类型全反射；javac classpath = xposed-stub.jar + android.jar。
> 设备 172.19.0.1:40321 无法连接（非无线调试地址）——等用户给真实端口。

## 1. 【P0 待装机+验收】v2.7
- [ ] 安装 Hyper-Ice-Cream-v2.7.apk（release_publish 或 Release 页）
- [ ] LSPosed → Hyper Ice Cream → 启用 → 作用域: com.miui.home + android + com.miui.contentcatcher → 重启
- [ ] 工具页「选择要恢复的组件应用」→ 勾选 → 保存白名单 → 重启 → 旧组件出现在小部件列表
- [ ] Claude 问答等已恢复组件可缩放（用户实测部分已可——证明 miuiWidget 注入路径有效）
- [ ] 关于页字重（已全 medium/bold）确认
- [ ] Hyper Widget 2×4（hyper_widget_info 已改 targetCellHeight=4）

## 2. 【P0 用户核心诉求——下轮主攻】时钟 2×4 复现渲染
- 用户反馈 Hyper Widget 与原版对比缺: 农历月日、天气（应在下部）、位置偏差
- 待做: HyperWidget v3——android.icu.util.ChineseCalendar 补农历；manifest frac 解析加固；天气行预留（数据源 content://weather 需系统查询，或从主题 weather 贴图取图标 + 系统天气 App 数据）
- 尺寸表已写: /data/system/hypericecream_widget_sizes.txt "com.zcode.themetool 2 4"——尺寸重写 hook 已内置，验证 HyperWidget 添加时是否直接 2×4

## 3. 【P1】
- [ ] DockTweaks 空流异常回退（改全透明图）；用户验收隐藏效果
- [ ] 假信号已按用户要求移除（v2.7）
- [ ] Claude 问答组件 4×2 异常排查（疑似其自身声明问题，非本模块）

## 禁止事项
- 不做: 长按菜单移植、KSU 挂载、自建服务器、P2P、硬编码尺寸
- 用户使用设备时禁止远程点击；部署前自动备份
