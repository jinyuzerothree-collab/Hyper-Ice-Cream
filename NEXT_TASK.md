# NEXT_TASK.md — 下一阶段任务清单

> **v2.1 已发布**。设备: 无线调试（端口变化找用户要）；Root = KernelSU su。构建 `python C:\Users\雪糕\.zcode\workspace\default\build_tt.py`；网络走代理 7897；gh 在 C:\abuild\gh\bin\gh.exe。
> ⚠️ 用户正在使用平板时禁止远程 input tap 干扰（v2.1 自测教训）。

## 1. 【P0 待用户验收 v2.1】
- [ ] 顶部黑条已移除（NoActionBar）
- [ ] 关于页 HyperCeiler 式（渐变 Hero + Ache的设备卡 + 开发者头像卡 + 菜单列表 + 译者）
- [ ] 语言切换（关于页 → 语言 → 简/繁/EN 即时生效）
- [ ] Dock 左右滑动切页 + 激活态玻璃覆盖层（非紫色实色）
- [ ] 巧克力雪糕科技风图标
- [ ] 小组件部署时宽×高选择器（写入 description.xml size）
- [ ] gadgets 缩放手柄（注入 size 后是否出现——Plan A 终验）

## 2. 【P1】用户上传的头像 = Downloads/mmexport1791026326501.jpg 已内置；若用户换头像 → 替换 res/drawable-nodpi/dev_avatar.jpg 重构建

## 3. 【P1】样本库扩充
- [ ] 超级液态米果.mtz：同为顶层 clock_2x4 容器（71KB，含 preview_clock_2x4_0.png）——部署/素材提取流程通用，可作为 Widget 引擎第二素材源
- [ ] 用户 Downloads 里出现系统界面组件 18.2.1.88.0 V2.1/V3.0/V3.1_fix（新插件线！用户在自测新修改版）——如装了 V3.x 且出问题，按 v1.3 同法排查（缺类/接口不匹配）

## 4. 【P2】
- [ ] HyperWidget 分钟刷新 + 天气接入 + 多字体换挡
- [ ] 关于页用户名可编辑入口
- [ ] Compose/MIUIX 迁移评估（真 blur 前提）
- [ ] 开场引导动画（用户暂缓，素材齐后做）

## 禁止事项（用户约定）
- 不做: 长按菜单移植、KSU 挂载方案、自建服务器、P2P、硬编码组件尺寸
- 用户正在使用设备时禁止远程点击/截屏干扰
- 状态变更先说明；不删用户文件；部署前必须自动备份
