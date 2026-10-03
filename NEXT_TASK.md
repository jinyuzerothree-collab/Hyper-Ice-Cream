# NEXT_TASK.md — 下一阶段任务清单

> 新会话直接从第 1 条开始。背景细节见同目录 PROJECT_HANDOVER.md。
> 设备连接: 无线调试（端口每次重启变化，让用户提供）；Root = KernelSU su（shell 已授权）。
> 网络: github.com 必须走代理 `set HTTPS_PROXY=http://127.0.0.1:7897`；gh 在 `C:\abuild\gh\bin\gh.exe`；构建 `python C:\Users\雪糕\.zcode\workspace\default\build_tt.py`。

## 0. 【已完成 2026-10-03】里程碑记录
- ✅ gadgets 部署端到端验证通过（用户实测：Neo.mtz clock_2x4 时钟组件成功上桌面）
- ✅ 主界面滚动修复（f51c134，内容超屏导致部署按钮点不到）
- ✅ 时钟组件"太小"定性：mrc 组件布局用 #vw/#vh 自适应，尺寸由桌面格子决定——用户长按组件拖拽手柄即可放大到 2×4/4×4，无需改包
- ✅ 天气数据源确认：clock_2x4 manifest 内置 WeatherProvider binder（content://weather），无需额外数据源 App

## 1. 【P0 当前进行中】社区页阶段二（用户已确认需求）
- [ ] 应用内新增"社区"页面：在线拉取 GitHub 仓库主题索引
  - 索引源: themes/index.json（仓库内，先手工维护几条；后续 Actions 自动生成）
  - 网络: java.net.HttpURLConnection，GitHub raw 直连 + gh-proxy.com 镜像自动回退（国内无代理场景）
- [ ] 列表项展示: 预览图（index 内 URL 懒加载）+ 主题名 + 作者 + 版本 + 来源声明（"来源: <贡献者自述>"字段必填）+ 大小
- [ ] 详情/操作: 「下载并部署」（下载 mtz 到 cache → 复用 doDeploy 流程）+ 「前往链接」（浏览器打开原始发布页）
- [ ] 索引 schema（index.json）:
  ```json
  [{"name":"Neo","author":"JellyBeans","contributor":"<上传者署名/匿名>","version":"29.260904",
    "ui_version":"17","components":["clock_2x4","icons",...],"sha256":"...","size_bytes":71508200,
    "preview_url":"https://.../preview_0.jpg","download_url":"https://.../Neo.mtz",
    "source_url":"https://...（用户获取主题的原始发布页，必填）","notes":"..."}]
  ```
- [ ] 服务端样板: 仓库新建 themes/ 目录，先收录 Neo（用户授权后），index.json 手写一条
- [ ] CONTRIBUTING.md: 收录规范（必须声明原作者、来源链接；侵权 Issue 下架）

## 2. 【P0】发布 v1.4
- [ ] CHANGELOG.md 补 1.3/1.4 条目；README 加小组件已验证 + 社区页说明
- [ ] APK → release_publish/HyperOS-Theme-Installer-v1.4.apk
- [ ] git tag v1.4 + gh release create v1.4（走代理 7897）

## 3. 【P1】导出流程增强（用户要求的声明字段）
- [ ] meta.json 增加 contributor（上传者署名，默认匿名 ID 可改）与 source_url（获取来源，用户填写）
- [ ] 导出对话框加两个输入框（署名 + 来源链接），source_url 空时提示"社区收录需声明来源"
- [ ] 社区导出包含 preview/ 全部图（已实现）✓

## 4. 【P2 可选】
- [ ] index.json 的 Actions 自动生成（扫描 themes/*/meta.json）
- [ ] Shizuku 只读模式
- [ ] TaplusFix v8（OBSERVE 数据到位后强制 stub 门）

## 禁止事项（用户约定）
- 不做: 长按菜单移植（终判）、KSU 挂载方案、自建服务器、P2P
- 状态变更先说明；不删用户文件；部署前必须自动备份
