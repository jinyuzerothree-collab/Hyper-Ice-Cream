# NEXT_TASK.md — 下一阶段任务清单

> **v2.0 已发布（品牌: Hyper Ice Cream）**。设备: 无线调试（端口变化找用户要）；Root = KernelSU su。
> 网络: github.com 走代理 `set HTTPS_PROXY=http://127.0.0.1:7897`；gh 在 `C:\abuild\gh\bin\gh.exe`；构建 `python C:\Users\雪糕\.zcode\workspace\default\build_tt.py`。
> 注意: 全局 git insteadOf 把 github.com 重写为 gh-proxy 前缀（已用仓库级同名重写抵消 push 影响）。

## 1. 【P0 待用户验收】
- [ ] 悬浮 Glass Dock 四页视觉（部署/社区/工具/关于）+ 壁纸取色药丸
- [ ] gadgets 缩放手柄（description.xml 注入修复已部署设备；手动 repack 版 clock_2x4(size=2:4) 也在 /data/system/theme/clock_2x4）
- [ ] 「Hyper Ice Cream」系统小组件：桌面长按 → 小部件 → Hyper Ice Cream → 添加后自由缩放；应显示主题数字时钟（素材来自 clock_2x4）
- [ ] 关于页：设备卡片/开发者/链接
- [ ] 社区页刷新（样例条目）

## 2. 【P1】HyperWidget 增强
- [ ] 分钟级刷新（AlarmManager；当前 30min 周期）
- [ ] 天气接入（主题 weather 贴图已可提取；数据源 content://weather 需自实现查询）
- [ ] 多字体支持（type_0~type_11 换挡，对应主题 BroadcastBinder fontID 机制）
- [ ] 用户上传新组件样本（weather/notes/calculator）时扩展素材提取（WIDGET_PATTERN 已兼容命名）

## 3. 【P1】社区回填与回归
- [ ] Neo 真实 download_url/sha256/preview_url 回填 index.json（sha256 可本地计算）
- [ ] 真实条目下载全流程回归（进度条/校验/部署）
- [ ] GitHub Actions 自动生成 index.json（阶段三）

## 4. 【P2】真 blur 迁移评估
- 实测 HyperOS4 运行时无 blurBehindRadius 字段（NoSuchFieldException）→ 需 Compose/MIUIX 工具链迁移（Gradle+Kotlin 环境，大工程，单列）
- 备选: RenderEffect 快照方案（对静态内容可行）

## 禁止事项（用户约定）
- 不做: 长按菜单移植、KSU 挂载方案、自建服务器、P2P、硬编码组件尺寸
- 状态变更先说明；不删用户文件；部署前必须自动备份
