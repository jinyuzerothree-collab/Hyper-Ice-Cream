# HyperOS-Theme-Installer v1.2

## 更新内容

### 新增
- 内置文件浏览器：只过滤显示 mtz/zip/ttf 文件，可切换显示全部文件，支持目录导航
- 需要授权一次「所有文件访问」权限（替代 HyperOS 捆绑的文件选择器）
- 组件标签明确「桌面图标」归属（包含在系统图标组件中）
- 支持直接选择 ttf 字体文件部署到 /data/system/theme/fonts/（实验性）

### 修复
- 组件勾选后按勾选状态部署，未勾选组件不再被覆盖

## 已知问题

- 字体组件为实验性：OS4 引擎对 /data/system/theme/fonts 的读取未获官方确认，部分主题字体部署后不生效（无副作用，可还原）
- 框架级深度主题（framework-res 注入、状态栏布局重构）不支持
- maml 动画、动态壁纸、音效组件会自动跳过
- 每月 OTA 更新后不需要重新部署

## 注意事项

- 需要 Root（KernelSU / Magisk，授权 su）和「所有文件访问」权限
- 每次部署前自动备份当前主题到 /sdcard/ThemeToolBackup/，异常时用「还原最近一次备份」恢复
- 请勿用于盗版主题分发，主题版权归各自作者所有

## 下载

- APK: HyperOS-Theme-Installer-v1.2.apk（见本页 Assets）
- 源码: 本页 Source code (zip / tar.gz)
