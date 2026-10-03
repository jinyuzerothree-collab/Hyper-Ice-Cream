package com.zcode.themetool;

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
    }

    public static String t(Context c, String key) {
        String[] v = M.get(key);
        if (v == null) return key;
        if (ZH_HANT.equals(lang)) return v[1];
        if (EN.equals(lang)) return v[2];
        return v[0];
    }
}
