package com.hyper.icecream;

import android.content.Context;
import java.util.HashMap;

/** 三语支持（简体中文 / 繁體中文 / English），存偏好，运行时取词。 */
public class L10n {
    public static final String ZH_HANS = "zh-Hans";
    public static final String ZH_HANT = "zh-Hant";
    public static final String EN = "en";

    private static String lang = ZH_HANS;

    public static void init(Context ctx) {
        lang = ctx.getSharedPreferences("community_pref", Context.MODE_PRIVATE)
                .getString("app_lang", ZH_HANS);
    }

    public static String get() { return lang; }

    public static void set(Context ctx, String l) {
        lang = l;
        ctx.getSharedPreferences("community_pref", Context.MODE_PRIVATE)
                .edit().putString("app_lang", l).apply();
    }

    public static String langName(String l) {
        if (ZH_HANT.equals(l)) return "繁體中文";
        if (EN.equals(l)) return "English";
        return "简体中文";
    }

    private static final HashMap<String, String[]> M = new HashMap<>();
    // keys: [zh-Hans, zh-Hant, en]
    private static void put(String k, String a, String b, String c) { M.put(k, new String[]{a, b, c}); }
    static {
        put("sub", "HyperOS 平板主题增强工具", "HyperOS 平板主題增強工具", "Theme enhancer for HyperOS pads");
        put("pick", "选择主题包 / 字体文件", "選擇主題包 / 字體檔案", "Pick theme package / font");
        put("deploy", "部署到系统主题（自动备份）", "部署到系統主題（自動備份）", "Deploy to system theme (auto backup)");
        put("backup", "仅备份当前主题", "僅備份當前主題", "Backup current theme only");
        put("restore", "还原最近一次备份", "還原最近一次備份", "Restore latest backup");
        put("restart_sysui", "重启系统界面 (SystemUI)", "重啟系統介面 (SystemUI)", "Restart SystemUI");
        put("restart_home", "重启桌面（部署小组件后使用）", "重啟桌面（部署小組件後使用）", "Restart launcher (after widget deploy)");
        put("community", "社区主题库（在线浏览 / 获取）", "社區主題庫（線上瀏覽 / 獲取）", "Community themes (browse / get)");
        put("perm", "授权所有文件访问", "授權所有檔案存取", "Grant all-files access");
        put("tab_home", "部署", "部署", "Home");
        put("tab_community", "社区", "社區", "Community");
        put("tab_tools", "工具", "工具", "Tools");
        put("tab_about", "关于", "關於", "About");
        put("about", "关于", "關於", "About");
        put("dev_model", "设备型号", "設備型號", "Model");
        put("android_ver", "Android 版本", "Android 版本", "Android version");
        put("os_ver", "OS 版本", "OS 版本", "OS version");
        put("contributors", "贡献者", "貢獻者", "Contributors");
        put("website", "官方网站", "官方網站", "Website");
        put("translate", "翻译", "翻譯", "Translate");
        put("translate_sub", "帮助我们将应用翻译为您的语言", "幫助我們將應用翻譯為您的語言", "Help us translate the app");
        put("support", "支持", "支持", "Donate");
        put("support_sub", "您可以在此处捐赠以支持我们", "您可以在此處捐贈以支持我們", "Support the development");
        put("terms", "用户协议", "用戶協議", "Terms of use");
        put("privacy", "隐私政策", "隱私政策", "Privacy policy");
        put("translator", "译者", "譯者", "Translator");
        put("language", "语言", "語言", "Language");
        put("license", "开源许可", "開源許可", "License");
        put("repo", "GitHub 仓库", "GitHub 倉庫", "GitHub repository");
        put("changelog", "更新日志", "更新日誌", "Changelog");
        put("official", "官方", "官方", "Official");
        put("release", "release", "release", "release");
        put("dev", "开发者", "開發者", "Developer");
        // v2.9 工具页权限卡 / Dock 样式
        put("perm_section", "权限状态", "權限狀態", "Permissions");
        put("perm_check", "检查权限授予", "檢查權限授予", "Check permissions");
        put("root_state", "Root 权限", "Root 權限", "Root access");
        put("lsp_state", "LSP 权限", "LSP 權限", "LSPosed access");
        put("root_ok", "Root 权限已授予", "Root 權限已授予", "Root granted");
        put("root_no", "Root 未授予", "Root 未授予", "Root not granted");
        put("lsp_ok", "LSP 权限已授予", "LSP 權限已授予", "LSPosed granted");
        put("lsp_no", "LSP 未授予", "LSP 未授予", "LSPosed not granted");
        put("dock_section", "Dock 常驻图标样式（实验性）", "Dock 常駐圖標樣式（實驗性）", "Dock resident icons (experimental)");
        put("dock_none", "不改动", "不改動", "No change");
        put("dock_hidden", "隐藏常驻图标", "隱藏常駐圖標", "Hide resident icons");
        put("dock_round", "圆角背景风格", "圓角背景風格", "Rounded background style");
        put("dock_note", "部署主题时也会询问；桌面为 Flutter 渲染，两种样式的实际生效均待验证。", "部署主題時也會詢問；桌面為 Flutter 渲染，兩種樣式的實際生效均待驗證。", "Also asked during deploy; launcher is Flutter-rendered, both styles pending verification.");
        put("dock_ask_title", "Dock 常驻图标样式", "Dock 常駐圖標樣式", "Dock resident icons");
        put("dock_ask_msg", "本次部署主题时，是否同时调整 Dock 常驻图标？（实验性）", "本次部署主題時，是否同時調整 Dock 常駐圖標？（實驗性）", "Adjust dock resident icons with this deploy? (experimental)");
        // v3.1 时钟布局编辑器
        put("wl_title", "时钟小组件布局编辑", "時鐘小組件佈局編輯", "Clock widget layout editor");
        put("wl_sub", "每个元素可直接输入数字微调：左右/上下偏移、大小、粗细，预览即时生效。", "每個元素可直接輸入數字微調：左右/上下偏移、大小、粗細，預覽即時生效。", "Type numbers to tweak each element's offsets, size and weight; live preview.");
        put("wl_el_dow", "星期", "星期", "Weekday");
        put("wl_el_period", "时段", "時段", "Day period");
        put("wl_el_time", "时间", "時間", "Time");
        put("wl_el_date", "日期", "日期", "Date");
        put("wl_el_lunar", "农历", "農曆", "Lunar date");
        put("wl_el_weather", "天气", "天氣", "Weather");
        put("wl_show", "显示", "顯示", "Show");
        put("wl_pos_x", "左右偏移", "左右偏移", "X offset");
        put("wl_pos_y", "上下偏移", "上下偏移", "Y offset");
        put("wl_size", "大小", "大小", "Size");
        put("wl_unit_dp", "dp", "dp", "dp");
        put("wl_unit_pct", "%", "%", "%");
        put("wl_weight", "字体粗细", "字體粗細", "Font weight");
        put("wl_thin", "细", "細", "Thin");
        put("wl_regular", "常规", "常規", "Regular");
        put("wl_wbold", "粗", "粗", "Bold");
        put("wl_wblack", "特粗", "特粗", "Extra bold");
        put("wl_color", "颜色", "顏色", "Color");
        put("wl_reset", "恢复默认", "恢復默認", "Reset defaults");
        put("wl_preset_theme", "主题复刻", "主題復刻", "Theme replica");
        put("wl_apply", "保存并应用到桌面", "保存並應用到桌面", "Save & apply to home screen");
        put("wl_applied", "已应用！桌面未刷新请等待下一分钟或重新添加小组件。", "已應用！桌面未刷新請等待下一分鐘或重新添加小組件。", "Applied! If not refreshed, wait a minute or re-add the widget.");
        put("wl_note", "时间每分钟自动刷新；若被省电冻结，请把本应用加入省电白名单。", "時間每分鐘自動刷新；若被省電凍結，請把本應用加入省電白名單。", "Time refreshes every minute; whitelist this app if battery saver freezes it.");
        // v3.1 图标包导入
        put("ik_section", "图标包导入（实验性）", "圖標包導入（實驗性）", "Icon pack import (experimental)");
        put("ik_scan", "检测已装图标包", "檢測已裝圖標包", "Detect installed icon packs");
        put("ik_none", "未检测到图标包：需要安装含 appfilter 映射的第三方图标包（Nova/ADW/Lawnchair 格式）。", "未檢測到圖標包：需要安裝含 appfilter 映射的第三方圖標包（Nova/ADW/Lawnchair 格式）。", "No icon packs found — install a pack with appfilter.xml (Nova/ADW/Lawnchair style).");
        put("ik_pick_title", "选择图标包", "選擇圖標包", "Pick an icon pack");
        put("ik_building", "正在提取图标…", "正在提取圖標…", "Extracting icons…");
        put("ik_confirm_title", "应用图标包？", "應用圖標包？", "Apply this icon pack?");
        put("ik_confirm_msg", "已提取 %1 个图标。\n将写入系统主题 icons 组件（会先自动备份当前主题），重启桌面生效。", "已提取 %1 個圖標。\n將寫入系統主題 icons 組件（會先自動備份當前主題），重啟桌面生效。", "Extracted %1 icons.\nWrites the icons component into the system theme (auto-backup first). Restart launcher to apply.");
        put("ik_applied", "✅ 图标包已应用。部分图标未变化属正常（未映射应用回退原图）；请点「重启桌面」生效。", "✅ 圖標包已應用。部分圖標未變化屬正常（未映射應用回退原圖）；請點「重啟桌面」生效。", "✅ Icon pack applied. Unmapped apps keep original icons; tap 'Restart launcher' to apply.");
        put("ik_deploy_fail", "部署未确认，检查上方日志（多半是 su 未授权）。", "部署未確認，檢查上方日誌（多半是 su 未授權）。", "Deploy not confirmed — check the log (su denied?).");
        put("ik_note", "支持标准 appfilter 图标包；未映射的应用自动回退原图标。", "支持標準 appfilter 圖標包；未映射的應用自動回退原圖標。", "Standard appfilter packs supported; unmapped apps fall back to original icons.");
    }

    public static String t(Context c, String key) {
        String[] v = M.get(key);
        if (v == null) return key;
        if (ZH_HANT.equals(lang)) return v[1];
        if (EN.equals(lang)) return v[2];
        return v[0];
    }
}
