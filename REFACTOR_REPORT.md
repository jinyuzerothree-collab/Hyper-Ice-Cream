# REFACTOR_REPORT.md — 修复 + 重构 + 体验优化报告

**版本**: v1.5 (versionCode 6)　**日期**: 2026-10-03
**性质**: BUG 修复 + UI 重构 + 用户体验优化（按用户指令暂停新功能开发）

---

## 一、已修复问题

### 1. Gadgets 缩放失效（根因修复，待用户最终确认手柄出现）

- **现象**: 平板桌面添加主题时钟后为 2×1 固定尺寸，无缩放手柄；手机端同包可缩放。
- **根因（实锤）**:
  - 出厂 gadget 对照（〔取证〕clock_classical.mtz / weather_4x1.mtz / notes.mtz）: 每个包内 `description.xml` 携带 **`<MIUI-Theme category="clock" size="4:2">`** 尺寸声明（语义 size=宽:高，category=组件类型，三样本交叉验证）。
  - 用户样本 Neo.mtz 的 `clock_2x4`〔实测〕: **缺失 description.xml**，manifest 裸在根目录（`<Clock type="awesome">` 旧格式）→ Launcher（GadgetMtzParser）解析不到 size → **降级为固定尺寸 Gadget**。手机 Launcher 对无声明组件宽容（默认可缩放），平板严格——此为两端差异的解释〔推断，基于行为差异〕。
- **修复方式（无硬编码尺寸）**: 部署时检测小组件容器，**缺失 description.xml 则自动注入** `<MIUI-Theme category="clock" size="W:H">`——尺寸从组件名后缀解析（clock_2x4 → 2:4），category 从前缀映射（clock/weather/notes/calculator）。完全以组件自身声明为准，缺失才补声明。
- **设备端验证**: 重打包 clock_2x4（含 size="2:4"）已部署到 `/data/system/theme/clock_2x4`（属主/权限正确），Launcher 已重启。**待用户确认**: 小组件选择器中尺寸应显示 2×4，添加后出现缩放手柄。

### 2. 主界面内容超屏（上一版 v1.4 期间已修，本次固化）
- 整页 ScrollView + 日志区固定高度内滚。部署按钮可达。

### 3. 社区下载无反馈（用户报告的三大问题全部修复）

| 用户报告的问题 | 修复 |
|---|---|
| 点击下载无进度显示 | 新增**下载任务管理器**: 确定进度条（按 Content-Length）+ 百分比 + 实时速度（MB/s）+ 直连/镜像标识 |
| 无法判断卡在哪个阶段 | **三阶段提示**: 【1/3 下载中 x% 速度】【2/3 SHA256 校验中…】【3/3 ✅ 完成】；失败显示具体错误（HTTP 码/sha 不匹配/超时） |
| GitHub 网络波动误判卡死 | 直连失败**自动切 gh-proxy 镜像**重试；读超时 30s 明确报错；下载互斥锁（进行中重复点击给 Toast） |
| 下载后 sha256 校验 | 索引含 sha256 时自动校验，不匹配删除文件并报错 |

### 4. 社区主题重复贡献逻辑（用户指出的设计错误）

- 修正为: **仅本地导入的主题**部署成功后弹"分享给社区？"；**社区下载的主题**部署后不再询问（路径登记到 SharedPreferences），日志提示"该主题来自社区下载，不再重复贡献"。

### 5. UI 全面重构（Liquid Glass + Material You）

- **三页面架构**: 部署 / 社区 / 工具（备份、还原、重启 SystemUI、重启 Launcher 移入工具页）
- **Liquid Glass 悬浮底栏**: 悬浮于内容之上（距底 22dp 留白）、30dp 大圆角、半透明玻璃底色（日间浅灰/夜间深黑，0xB3 透明度）、1px 高光描边、16dp elevation 柔和阴影、点击缩放动画（0.96 回弹）、切换时 Dock 弹性反馈、页面淡入转场
- **Material You 动态取色**: `WallpaperManager.getWallpaperColors()` 取壁纸主色 → 按日夜模式提亮/压暗为 accent → Dock 激活态药丸底色
- **视觉层级**: 每页大标题 + 副标题；社区卡片圆角化（18dp）+ 间距系统；激活 tab 加粗白字
- 明确弃用: 传统 BottomNavigation / 贴边导航 / 矩形底栏

## 二、未修复问题

1. **Gadgets 缩放手柄的最终确认**: 根因修复已部署设备，但桌面选择器行为需要用户解锁平板后目视确认（设备锁屏密码阻塞了远程自测——应用进程存活、零崩溃已验证）。
2. **Lockscreen/wallpaper 之外的组件缩放**: 同一修复逻辑自动适用（notes/weather/calculator 都走注入路径），但样本只有 clock。
3. **themes/index.json 中 Neo 条目**: sha256/preview_url/download_url 还是占位符——需要用户上传 Neo.mtz 到 Release 或网盘后回填（或授权我从本地文件计算 sha256 并挂 Release 资产）。

## 三、修改/新增文件

| 类型 | 文件 | 内容 |
|---|---|---|
| 重构 | `app/src/main/java/com/zcode/themetool/MainActivity.java` | 三页面架构、Dock、动态取色、widget 容器自动修复（prepareWidgetContainer）、社区去重贡献 |
| 重构 | `app/src/main/java/com/zcode/themetool/CommunityFragment.java` | 下载任务管理器（进度/速度/阶段/sha256/互斥/镜像回退）、卡片圆角化 |
| 重构 | `app/src/main/res/layout/activity_main.xml` | FrameLayout 三页 + 悬浮 Dock |
| 新增 | `CONTRIBUTING.md`（上一轮）| 收录规范 |
| 新增 | `themes/index.json`（上一轮）| 索引样板 |
| 修改 | `app/src/main/AndroidManifest.xml` | INTERNET 权限；vc6/vn1.5 |
| 修改 | `CHANGELOG.md` | 1.3/1.4/1.5 条目 |

## 四、测试结果

- 〔实测〕构建: v1.5 (74KB) 编译/对齐/签名通过
- 〔实测〕安装: 覆盖安装成功，versionName=1.4→1.5 进程存活
- 〔实测〕启动无崩溃: am start 后进程在跑（pid 23294），logcat 无 FATAL/Exception（仅无害 baseline.prof 提示）
- 〔实测〕gadgets 修复部署: repack 后 clock_2x4（含 size 声明）落位 /data/system/theme/，chown/chmod 正确，Launcher 已重启
- 〔阻塞〕UI 视觉自测: 设备锁屏（混合密码），无法远程解锁 → 等用户解锁后按下方"待验证内容"清单目视验收
- 〔阻塞〕社区页在线拉取实测: 同上（需解锁操作）

## 五、待验证内容（用户回来后 3 分钟）

1. 打开 App → 底部应见**悬浮玻璃 Dock**（部署/社区/工具三 tab），点击有缩放动画与页面切换
2. 「部署」页选 Neo.mtz → 勾选时钟组件 → 部署（日志应出现"已为 clock_2x4 注入尺寸声明 2x4"）→ 工具页重启桌面 → 桌面长按 → 添加小组件 → 时钟应显示 **2×4**，添加后**长按出现缩放手柄**
3. 「社区」页 → 刷新列表 → 应显示索引加载状态（当前只有 Neo 样例占位条目）
4. 动态取色: Dock 激活药丸颜色应接近壁纸主色

## 六、已知风险

- 动态取色在部分壁纸纯色/深色场景下对比度可能不理想（accent 简单提亮/压暗，非完整 Monet 色板生成）
- Liquid Glass 的"毛玻璃模糊"为模拟（半透明+高光描边+阴影）；真 behind-blur 需 API31 RenderEffect 方案，视用户反馈决定是否升级
- description.xml 注入采用出厂最小字段集（category/size/title/version）；若个别第三方组件需要 title locale 变体，仍可正常显示（选择器显示组件文件名）
- 社区下载与部署间的中断恢复: 下载文件在 cache，进程被杀需重新下载（可接受）

## 七、下一步计划

1. 用户验证缩放手柄 → 通过则 tag v1.5 发 Release
2. 回填 Neo 索引真实下载/预览链接（挂 Release 资产 community-v1）
3. index.json Actions 自动化（阶段三）
4. 视反馈决定 RenderEffect 真 blur

---
*报告完。所有"已修复"均指代码实现+可编译+装机，目视验收项已在"待验证内容"列明。*
