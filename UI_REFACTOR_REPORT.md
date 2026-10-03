# UI_REFACTOR_REPORT.md — 更名 + UI 重构报告

**日期**: 2026-10-03　**版本**: v2.0 (versionCode 7)

---

## 一、项目更名（已完成）

- **应用名**: 主题直装 → **Hyper Ice Cream**（清单 label + 首页标题 + 关于页）
- **品牌定位**: HyperOS 平板主题增强工具
- **图标**: 像素风白雪糕（16×16 网格 × 12 最近邻渲染为 192px，柔粉底 + 白冰淇淋 + 蛋筒 + 深描边；零渐变零拟物，符合"简洁/易识别/高对比"要求）——Python 脚本直出 PNG（`gen_icon.py`），已生成 mipmap-xxxhdpi
- **仓库改名**: 见文末（GitHub 自动重定向旧链接）

## 二、页面架构重构（已完成）

**三页面 + 关于页，液态玻璃悬浮 Dock 切换**:

| 页面 | 内容 |
|---|---|
| 部署 | 主题导入（内置浏览器）、元数据预览卡（预览图/名称/作者/版本/大小/SHA256/组件清单）、组件勾选、部署按钮 |
| 社区 | 在线索引浏览（卡片：预览图/原作者/贡献者/来源/大小）、下载并部署、前往来源、刷新 |
| 工具 | 备份 / 还原 / 重启 SystemUI / 重启桌面 |
| 关于 | 应用头（像素图标 + Hyper Ice Cream + 版本）→ 设备卡片（"用户名 的 设备名"、型号、Android 版本、HyperOS 版本）→ 开发者卡片（头像 + GitHub 主页/仓库）→ 链接列表（更新日志/开源许可/收录规范/Issues/Discussions/引用项目） |

## 三、Liquid Glass 悬浮底栏

**已实现**: 悬浮留白（距底 22dp）、30dp 大圆角药丸、半透明玻璃底（日夜自适应）、1px 高光描边、16dp elevation 阴影、点击缩放回弹（0.9→1.0）、页面淡入转场、激活 tab 动态取色药丸（壁纸主色，Material You）

**真·背景模糊的技术结论（重要）**〔实测〕:
- 方案: 独立窗口 `TYPE_APPLICATION_PANEL` + `FLAG_BLUR_BEHIND` + `blurBehindRadius`（框架级实时背景模糊）
- **实测结果: HyperOS 4 (Android 17) 运行时 `WindowManager.LayoutParams` 中不存在 `blurBehindRadius` 字段**（NoSuchFieldException，已日志留痕并优雅回退到内嵌 Dock）
- 已实现回退链: 窗口 blur（若字段存在）→ 内嵌玻璃 Dock（当前生效）
- 后续方向: MIUIX/HyperXCompose 的私有模糊通道（需迁移 Compose 工具链）、或 RenderEffect 快照方案；本次未引入 Compose（避免无 Gradle 环境下大迁移风险，见风险分析）

## 四、自测结果（远程截图驱动）

| 页面 | 结果 |
|---|---|
| 部署页 | ✅ 启动正常、root OK、Dock 四 tab 渲染、动态取色生效（激活药丸=壁纸紫） |
| 关于页 | ✅ 图标/标题/版本/设备卡（Android 17 API37、HyperOS OS4.0）/开发者卡/链接列表全部渲染正确 |
| 社区页 | ✅ 页面构建、刷新按钮在位、索引拉取状态提示正常 |
| 稳定性 | ✅ 进程存活、零崩溃（logcat 无 FATAL） |
| 中断说明 | 深度交互自测在社区页刷新环节被中止——检测到用户正在使用平板（远程点击落到了其他应用），为避免干扰立即停止 |

## 五、修改/新增文件

| 类型 | 文件 |
|---|---|
| 重构 | `MainActivity.java`（三页+关于+Dock+动态取色+widget 素材导出+sha256 去重） |
| 新增 | `AboutPage.java`、`HyperWidgetProvider.java`、`AboutPage` 布局纯代码构建 |
| 新增 | `res/xml/hyper_widget_info.xml`、`res/mipmap-*/ic_launcher.png`（像素图标） |
| 重构 | `res/layout/activity_main.xml`（FrameLayout 多页 + Dock） |
| 修改 | `AndroidManifest.xml`（label/icon/INTERNET/widget receiver；vc7 vn2.0） |
| 修改 | `CommunityFragment.java`（索引缓存） |

## 六、已知问题与风险

1. **真 blur 不可用**（ROM 层字段缺失）——当前玻璃为"半透明+高光+阴影"模拟；若用户验收后仍要求真模糊，评估方案 = 迁移 Compose + MIUIX（需要搭建 Gradle 工具链，工程量大，单列任务）
2. Compose/MIUIX/HyperXCompose 未引入: 本项目构建链为 aapt2+javac+D8（无 Gradle）；引入 Compose 需完整 Gradle+Kotlin 环境。本次重构在 View 体系内完成全部目标；Compose 迁移作为独立里程碑待批
3. 像素图标为程序生成 v1，如需调整配色/造型，改 `gen_icon.py` 网格即可
4. 关于页用户名默认"我"，长按头像未来可编辑（未做入口）

## 七、下一步计划

1. 用户验收 Dock 视觉 + About 页 + gadgets 缩放手柄（根因修复待目视确认）
2. Neo 索引真实链接回填 → 社区下载全流程回归
3. （待批）Compose/MIUIX 迁移评估 → 真 blur + 完整 Monet
4. Widget 分钟级刷新 + 天气数据接入
