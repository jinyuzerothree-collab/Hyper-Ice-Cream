# NEXT_TASK.md — 下一阶段任务清单

> 新会话直接从第 1 条开始。背景细节见同目录 PROJECT_HANDOVER.md。
> 设备连接: 无线调试（端口每次重启变化，让用户提供）；Root = KernelSU su（shell 已授权）。
> 网络: github.com 必须走代理 `set HTTPS_PROXY=http://127.0.0.1:7897`；gh 在 `C:\abuild\gh\bin\gh.exe`；构建 `python C:\Users\雪糕\.zcode\workspace\default\build_tt.py`。

## 1. 【P0】gadgets 端到端验证（用户设备上 v1.3 已装，versionName=1.3）
- [ ] 用户选 Neo.mtz（C:\Users\雪糕\Downloads\Neo.mtz）→ 确认元数据卡显示 Neo/JellyBeans/29.260904 + 预览图
- [ ] 确认组件列表出现「时钟组件 (clock_2x4)」
- [ ] 勾选部署 → 点「重启桌面 (Launcher)」→ 桌面长按 → 添加小组件 → 查找主题时钟组件
- [ ] 失败: `adb logcat | grep -aiE "Gadget|Mtz"` 定位；调整部署形态（gadgets/ 子目录 vs 平铺组件 ID；.mrc 同放）
- [ ] 成功: 在 README 功能表把小组件状态改为已验证

## 2. 【P0】发布 v1.3
- [ ] CHANGELOG.md 补 [1.3] 条目（gadgets 部署 / 社区导出 / 元数据预览卡）
- [ ] README 功能列表加"主题小组件部署"
- [ ] `cp C:\abuild\ttap_build\signed\themetool_unsigned-aligned-debugSigned.apk release_publish\HyperOS-Theme-Installer-v1.3.apk`
- [ ] commit（feat 三个已在历史: 3f118ce/7bfc43e/be0999e）+ git tag v1.3 + push
- [ ] `gh release create v1.3 --repo jinyuzerothree-collab/HyperOS-Theme-Installer --title "v1.3" --notes-file <notes> <apk>`（走代理 7897）

## 3. 【P1】gadgets 兼容性收集（验证失败时）
- [ ] 收集失败日志 + 用户的其它小组件主题包
- [ ] 对比 /product/media/theme/default/gadgets/clock_classical.mtz 与 Neo clock_2x4 的内部结构差异
- [ ] 按差异调整（可能: 组件需要 .mrc 伴随文件 / manifest 字段 / 部署目录改为 gadgets/）

## 4. 【P1】社区库阶段二
- [ ] 仓库加 themes/ 目录 + .github/workflows/index.yml（生成 index.json: name/author/version/sha256/preview 链接）
- [ ] 应用加"社区"页: 拉 index.json（raw.githubusercontent + gh-proxy 回退）→ 列表 → 下载到 cache → 复用 doDeploy
- [ ] CONTRIBUTING.md（收录规范）

## 5. 【P2 可选】
- [ ] Shizuku 只读模式（inspect/备份展示）
- [ ] com.miui.weather2 平板可用性确认（天气组件数据源）
- [ ] TaplusFix v8: 读 OBSERVE 日志（ClipboardServiceStub.clipboardAccessResult 返回值）决定是否强制
- [ ] 平板侧遗留: 用户升级 Scene；HyperGlow 开通知使用权

## 禁止事项（用户约定）
- 不做: 长按菜单移植（OS4 固件缺代码，已终判）、KSU 挂载方案（本机 mount 失效）、自建服务器、P2P
- 状态变更先说明原因并等确认；不删用户文件；部署前必须自动备份
