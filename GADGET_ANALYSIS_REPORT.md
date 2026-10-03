# GADGET_ANALYSIS_REPORT.md — 主题小组件架构分析报告

**日期**: 2026-10-03　**样本**: Neo.mtz clock_2x4（JellyBeans）+ 出厂 clock_classical/weather_4x1/notes 对照

---

## 一、根因（已实锤，非猜测）

平板把主题时钟识别为 **Fixed Gadget** 的直接原因：

| 证据 | 内容 |
|---|---|
| 〔取证〕出厂 clock_classical.mtz | 含 `description.xml`: `<MIUI-Theme category="clock" size="4:2">` |
| 〔取证〕出厂 weather_4x1.mtz | `category="weather" size="4:1"` |
| 〔取证〕出厂 notes.mtz | `category="notes" size="2:2"` |
| 〔实测〕Neo clock_2x4 | **无 description.xml**，manifest 裸在根目录（`<Clock type="awesome">`），资源在根级 `src/` |
| 〔实测〕部署无声明组件 | Launcher 识别成功并渲染，但固定 2×1、无缩放手柄 |

**结论**: Launcher 的 GadgetMtzParser 依赖 description.xml 的 `size="宽:高"` 属性建立组件的可缩放区间；缺失该声明 → 降级为固定尺寸。手机端 Launcher 对无声明组件默认放行缩放，平板端严格——造成两端差异。

## 二、Plan A 实施（已完成）

`MainActivity.prepareWidgetContainer()`：部署前检测 widget 容器（顶层组件名匹配 clock/weather/notes/calculator/gadget 前缀 + 尺寸后缀），缺失 description.xml 时自动注入最小标准声明：

```xml
<MIUI-Theme category="clock" size="2:4">
  <version>1</version><uiVersion>1</uiVersion><title>clock_2x4</title>
</MIUI-Theme>
```

- category 从组件名前缀映射；size 从名称 `WxH` 后缀解析；已带 description.xml 的组件原样部署
- 修复版 clock_2x4(size=2:4) 已部署到设备（属主 system_theme、755）——**待用户在桌面选择器确认手柄出现**

## 三、Plan B 实施（Widget 重构引擎 v1，已可用）

**Hyper Ice Cream Widget**（`HyperWidgetProvider.java`，标准 AppWidget，摆脱 MIUI Gadget 体系）：

- **尺寸自由**: `resizeMode="horizontal|vertical"`，2×1 起步，Launcher 内自由缩放到任意格数
- **按桶重排版（非拉伸）**: 三桶策略——small(≤2×2, 纯时间居中)、wide(宽≥250dp: 日期+时间)、large(≥250×180: 大号日期+时间)；字号按桶缩放
- **主题素材复用**: 部署时钟组件时自动把数字贴图（`src/num/white/type_*/num_*.png`）导出到应用私有目录（root unzip + chown 到应用 uid），Widget 用 `setImageViewBitmap` 渲染主题数字字体；无素材时降级系统字体时钟
- 部署时钟后自动 `renderAll()` 刷新已添加的 Widget

**mrc 文件结论**〔取证〕: 出厂 clock_classical.mrc (20KB) 与 .mtz 同名成对存在，为渲染端二进制伴随文件；Neo 无 .mrc 也能渲染，说明 mrc 非必需（缓存/加速用途）。

## 四、已知限制与下一步

1. Neo 的 clock_2x4 布局用 `#vw/#vh` 全格自适应——部署版（Gadget 通道）渲染尺寸跟随格子；但若 Launcher 仍不给手柄（待验证），Widget 通道是完整替代
2. Widget v1 未实现: 天气 ContentProviderBinder 解析（主题 weather 贴图已具备，接天气数据即可）、分钟自动刷新（已注册 updatePeriodMillis 30 分钟 + 桌面时间变化时系统会刷新 DateTime 类 widget——后续加 AlarmManager 每分钟刷新）
3. 完整 mrc 表达式引擎（VariableBinders/Trigger/动画）为长期方向，当前以"素材提取+重排版"路线满足需求
4. **待用户提供**: 更多带时钟/天气/便签组件的主题包，用于验证注入兼容性与 Widget 素材提取覆盖率
