# NEXT_TASK.md — 下一阶段任务清单

> **v3.1 (vc24) 已构建推送**（数字版布局编辑器 + 图标包导入，未发布 Release）。仓库: Hyper-Ice-Cream。
> 设备: 192.168.3.87:{端口重启会变}；Root = KernelSU su。构建 build_tt.py（workspace 根）；gh 在 C:\abuild\gh\bin\gh.exe。
> ⚠️ 字段初始化器禁止引用 this/Context；DSH android.jar 是残缺 stub → 模块代码对框架类型全反射。
> ⚠️ 本机 shell 是 cmd：`;` 不是分隔符（会被当成程序参数），用 `&&` 或单命令。多行 python -c 会静默失败，写脚本文件或单行。

## 1. 【P0 回家后设备验证（按序）】
- [ ] **icons 格式实证**（vc24 图标包导入的依据，已本地对 Neo.mtz 核实：zip 内 `res/drawable-xxhdpi/<包名>.png` 240×240 + 可选 fancy_icons/theme_fallback/transform_config）：
  `adb shell su -c 'unzip -l /data/system/theme/icons | head -30'`（设备上现存的生效样本，应吻合）；顺带 `stat -c '%U %G %a' /data/system/theme/icons` 记录原始属主/权限
- [ ] 装机 vc24 → 验收布局编辑器：数字输入（左右/上下偏移 dp 可负、大小 %、粗细四档）→ 保存并应用 → 桌面按新布局渲染；时间每分钟自动走（省电冻结则加白名单）
- [ ] 天气条：无数据时网络兜底（IP 定位 + Open-Meteo，30min 缓存）后自动出现
- [ ] 图标包导入：检测（需装 Nova/ADW 格式包）→ 提取进度 → 确认 → 自动备份 → cp+chown 部署 → 重启桌面 → 图标生效；未映射应用回退原图标
- [ ] v3.0 遗留：组件选择器「实验性」标注显示；社区页 Neo 下载回归

## 2. 【P1 备选】
- [ ] 图标包增强：activity 级映射（包名.类.png）、fancy_icons 动态图标（日历/天气）、遮罩形状预览
- [ ] 布局配置导入/导出（社区分享布局 JSON）
- [ ] 白名单管理器 UI 美化；索引真实条目回填

## 3. 【P1】DockTweaks 备选方案
- Flutter 资源走 NDK AAssetManager（原生层），Java hook 拦截不到（v2.8 实测）
- 方案1: LSPlant/Dobby 原生 hook AAssetManager_open（重）
- 方案2: Launcher APK repack 替换 webp/svg（fs-verity 风险）
- 方案3: 找 Launcher Flutter 偏好开关（最轻，搜 shared_prefs + flutter SharedPreferences）
- 方案4: 询问用户是否接受"仅隐藏"做不到、改为"透明占位"

## 4. 【P2】Compose 迁移评估；开场动画（用户暂缓）

## 禁止事项
- 不做: 长按菜单移植、KSU 挂载、自建服务器、P2P、硬编码尺寸
- 用户使用设备时禁止远程点击；部署前自动备份


