# NEXT_TASK.md — 下一阶段任务清单

> v1.5 已发布（重构+修复）。设备连接: 无线调试（端口变化找用户要）；Root = KernelSU su。
> 网络: github.com 走代理 `set HTTPS_PROXY=http://127.0.0.1:7897`；gh 在 `C:\abuild\gh\bin\gh.exe`；构建 `python C:\Users\雪糕\.zcode\workspace\default\build_tt.py`。

## 1. 【P0 待用户验证】Gadgets 缩放手柄
- 根因已实锤+修复已部署: 出厂组件带 description.xml `<MIUI-Theme category size>`（size=宽:高），第三方简化格式（如 Neo clock_2x4）缺失 → Launcher 降级固定尺寸
- 工具已实现自动注入（prepareWidgetContainer），设备上手动 repack 的 clock_2x4(size=2:4) 也已部署
- 用户解锁平板 → 桌面长按 → 添加小组件 → 时钟应显示 2×4 → 添加后长按出现缩放手柄
- 若仍无手柄: 抓 `adb logcat | grep -aiE gadget` 看 GadgetMtzParser 尺寸解析；候选补法: content/manifest.xml 包装（出厂格式是 content/ 子目录+<Gadget> 根元素，Neo 是根级 manifest+<Clock>）

## 2. 【P0 待用户验证】v1.5 UI/UX 验收
- 悬浮 Glass Dock 三页切换 + 动态取色（壁纸主色药丸）
- 社区页刷新列表（索引当前 1 条样例）
- 下载进度管理器（需 index 里有真实 download_url 的条目才能完整验证）

## 3. 【P1】回填 Neo 索引真实数据
- [ ] 向用户要 Neo.mtz 的发布页链接（source_url/download_url）
- [ ] 或: 计算本地 Neo.mtz sha256 + 挂到 GitHub Release（community-v1）作为 download_url + preview 图上传 themes/Neo/
- [ ] 更新 themes/index.json 推送

## 4. 【P1】贡献信息在社区导出 UI 中落位核查（v1.4 加的 askContributionInfo 在 v1.5 重构后仍在主流程，回归测试一遍）

## 5. 【P2】
- [ ] RenderEffect 真 behind-blur（API31+）视用户反馈
- [ ] index.json Actions 自动生成
- [ ] Shizuku 只读模式

## 禁止事项（用户约定）
- 不做: 长按菜单移植、KSU 挂载方案、自建服务器、P2P、硬编码组件尺寸
- 状态变更先说明；不删用户文件；部署前必须自动备份
