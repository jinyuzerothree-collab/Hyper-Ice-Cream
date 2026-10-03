package com.zcode.themetool;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 关于页：1:1 对标 HyperCeiler 关于页层级。
 *  渐变 Hero（图标+名称+版本）→ 设备卡 → 开发者卡 → 菜单卡 → 协议卡 → 译者/语言 → 许可/仓库。 */
public class AboutPage {

    public static View build(Activity act) {
        L10n.init(act);
        boolean night = isNight(act);

        ScrollView scroll = new ScrollView(act);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);

        // ===== Hero：渐变背景 + 图标 + 名称 + 版本 =====
        LinearLayout hero = new LinearLayout(act);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(act, 24), dp(act, 72), dp(act, 24), dp(act, 60));
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                night
                        ? new int[]{0xFF1B2438, 0xFF241B33, 0xFF101622}
                        : new int[]{0xFFFFC9DE, 0xFFD9C8FF, 0xFFFFE9EE});
        hero.setBackground(heroBg);
        root.addView(hero);

        ImageView icon = new ImageView(act);
        icon.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(act, 96), dp(act, 96));
        icon.setLayoutParams(ilp);
        hero.addView(icon);

        TextView name = new TextView(act);
        name.setText("Hyper Ice Cream");
        name.setTextSize(30);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(night ? 0xFFE8D5E0 : 0xFF8C3A5C);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(act, 18), 0, dp(act, 6));
        hero.addView(name);

        TextView ver = new TextView(act);
        ver.setText(AboutBuild.VERSION_NAME + " | " + L10n.t(act, "release"));
        ver.setTextSize(14);
        ver.setTextColor(night ? 0x99FFFFFF : 0x99000000);
        ver.setGravity(Gravity.CENTER);
        hero.addView(ver);

        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 40));
        root.addView(body);

        // ===== 设备卡片（白底大字，HyperCeiler 式） =====
        LinearLayout device = whiteCard(act, night);
        TextView dt = new TextView(act);
        dt.setText(userName(act) + "的" + marketName(act));
        dt.setTextSize(22);
        dt.setTypeface(Typeface.DEFAULT_BOLD);
        dt.setTextColor(night ? 0xFFEEEEEE : 0xFF111111);
        dt.setPadding(0, 0, 0, dp(act, 10));
        device.addView(dt);
        device.addView(bigRow(act, night, marketName(act), L10n.t(act, "dev_model")));
        device.addView(bigRow(act, night, "Android " + Build.VERSION.RELEASE, L10n.t(act, "android_ver")));
        device.addView(bigRow(act, night, osVersion(act), L10n.t(act, "os_ver")));
        body.addView(cardWrap(act, device));

        // ===== 开发者卡片（头像 + 名称） =====
        LinearLayout dev = whiteCard(act, night);
        LinearLayout drow = new LinearLayout(act);
        drow.setOrientation(LinearLayout.HORIZONTAL);
        drow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView av = new ImageView(act);
        av.setImageBitmap(BitmapFactory.decodeResource(act.getResources(), R.drawable.dev_avatar));
        GradientDrawable avClip = new GradientDrawable();
        avClip.setCornerRadius(dp(act, 28));
        av.setBackground(avClip);
        av.setClipToOutline(true);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(act, 56), dp(act, 56));
        alp.rightMargin = dp(act, 16);
        av.setLayoutParams(alp);
        drow.addView(av);
        LinearLayout dcol = new LinearLayout(act);
        dcol.setOrientation(LinearLayout.VERTICAL);
        dcol.addView(txt(act, "Ache | Hyper Ice Cream", 17, true, night ? 0xFFEAEAEA : 0xFF111111, 2));
        dcol.addView(txt(act, "@" + gitUser(), 13, false, night ? 0x99FFFFFF : 0x99000000, 0));
        drow.addView(dcol);
        dev.addView(drow);
        dev.addView(clickRow(act, night, L10n.t(act, "dev") + "  ›", "https://github.com/jinyuzerothree-collab"));
        dev.addView(clickRow(act, night, L10n.t(act, "repo") + "  ›", repoUrl()));
        body.addView(cardWrap(act, dev));

        // ===== 菜单卡（贡献者/官网/翻译/支持） =====
        LinearLayout menu = whiteCard(act, night);
        menu.addView(menuRow(act, night, L10n.t(act, "contributors"), null, "https://github.com/jinyuzerothree-collab/Hyper-Ice-Cream/graphs/contributors"));
        menu.addView(menuRow(act, night, L10n.t(act, "website"), null, repoUrl()));
        menu.addView(menuRow(act, night, L10n.t(act, "translate"), L10n.t(act, "translate_sub"), "https://github.com/jinyuzerothree-collab/Hyper-Ice-Cream/issues"));
        menu.addView(menuRow(act, night, L10n.t(act, "support"), L10n.t(act, "support_sub"), "https://github.com/jinyuzerothree-collab/Hyper-Ice-Cream/issues"));
        body.addView(cardWrap(act, menu));

        // ===== 协议卡 =====
        LinearLayout legal = whiteCard(act, night);
        legal.addView(menuRow(act, night, L10n.t(act, "terms"), null, "https://github.com/jinyuzerothree-collab/Hyper-Ice-Cream/blob/main/LICENSE"));
        legal.addView(menuRow(act, night, L10n.t(act, "privacy"), null, "https://github.com/jinyuzerothree-collab/Hyper-Ice-Cream"));
        body.addView(cardWrap(act, legal));

        // ===== 语言切换 =====
        TextView llabel = txt(act, L10n.t(act, "language"), 13, false,
                night ? 0x99FFFFFF : 0x99000000, 0);
        llabel.setPadding(dp(act, 8), dp(act, 14), 0, dp(act, 6));
        body.addView(llabel);
        LinearLayout lang = whiteCard(act, night);
        lang.addView(langRow(act, night));
        body.addView(cardWrap(act, lang));

        // ===== 译者 =====
        TextView trlabel = txt(act, L10n.t(act, "translator"), 13, false,
                night ? 0x99FFFFFF : 0x99000000, 0);
        trlabel.setPadding(dp(act, 8), dp(act, 14), 0, dp(act, 6));
        body.addView(trlabel);
        LinearLayout tr = whiteCard(act, night);
        tr.addView(valueRow(act, night, "Hyper Ice Cream " + L10n.t(act, "official"), L10n.langName(L10n.get()), null));
        body.addView(cardWrap(act, tr));

        // ===== 许可 / 仓库 / 更新日志 =====
        LinearLayout misc = whiteCard(act, night);
        misc.addView(clickRow(act, night, L10n.t(act, "license") + " (MIT)  ›", "https://github.com/jinyuzerothree-collab/Hyper-Ice-Cream/blob/main/LICENSE"));
        misc.addView(clickRow(act, night, L10n.t(act, "changelog") + "  ›", "https://github.com/jinyuzerothree-collab/Hyper-Ice-Cream/blob/main/CHANGELOG.md"));
        body.addView(cardWrap(act, misc));

        body.addView(txt(act, "Made with Hyper Ice Cream", 11, false,
                night ? 0x55FFFFFF : 0x55000000, 0));
        return scroll;
    }

    private static String userName(Activity act) {
        return act.getSharedPreferences("community_pref", Activity.MODE_PRIVATE)
                .getString("display_name", "Ache");
    }

    private static String marketName(Activity act) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", "ro.product.marketname"});
            byte[] b = new byte[128];
            int n = p.getInputStream().read(b);
            p.waitFor();
            String s = n > 0 ? new String(b, 0, n).trim() : "";
            return s.isEmpty() ? Build.MODEL : s;
        } catch (Throwable t) {
            return Build.MODEL;
        }
    }

    private static String osVersion(Activity act) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", "ro.mi.os.version.name"});
            byte[] b = new byte[128];
            int n = p.getInputStream().read(b);
            p.waitFor();
            String s = n > 0 ? new String(b, 0, n).trim() : "";
            return s.isEmpty() ? Build.DISPLAY : s;
        } catch (Throwable t) {
            return Build.DISPLAY;
        }
    }

    private static String gitUser() { return "jinyuzerothree-collab"; }
    private static String repoUrl() { return "https://github.com/jinyuzerothree-collab/Hyper-Ice-Cream"; }

    private static boolean isNight(Activity act) {
        int m = act.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return m == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static LinearLayout whiteCard(Activity act, boolean night) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(act, 20), dp(act, 18), dp(act, 20), dp(act, 18));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(act, 24));
        bg.setColor(night ? 0xF2151515 : 0xFAFFFFFF);
        c.setBackground(bg);
        return c;
    }

    private static LinearLayout cardWrap(Activity act, LinearLayout c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(act, 14);
        c.setLayoutParams(lp);
        return c;
    }

    private static TextView txt(Activity act, String s, float sp, boolean bold, int color, int padBottomDp) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setTextColor(color);
        t.setPadding(0, 0, 0, dp(act, padBottomDp));
        return t;
    }

    /** HyperCeiler 式大字行：主值大字加粗，标签小字浅色 */
    private static View bigRow(Activity act, boolean night, String value, String label) {
        LinearLayout v = new LinearLayout(act);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(0, dp(act, 10), 0, dp(act, 10));
        TextView tv = new TextView(act);
        tv.setText(value);
        tv.setTextSize(19);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(night ? 0xFFEAEAEA : 0xFF111111);
        v.addView(tv);
        TextView tl = new TextView(act);
        tl.setText(label);
        tl.setTextSize(12);
        tl.setTextColor(night ? 0x99FFFFFF : 0x99000000);
        v.addView(tl);
        return v;
    }

    private static View menuRow(final Activity act, boolean night, String label, String sub, final String url) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(act, 12), 0, dp(act, 12));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(clp);
        col.addView(txt(act, label, 16, true, night ? 0xFFEAEAEA : 0xFF111111, sub == null ? 0 : 2));
        if (sub != null) col.addView(txt(act, sub, 12, false, night ? 0x99FFFFFF : 0x99000000, 0));
        r.addView(col);
        TextView chev = new TextView(act);
        chev.setText("›");
        chev.setTextSize(18);
        chev.setTextColor(night ? 0x66FFFFFF : 0x66000000);
        r.addView(chev);
        if (url != null) {
            r.setOnClickListener(v -> {
                try {
                    act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Throwable ignored) {
                }
            });
        }
        return r;
    }

    private static View clickRow(final Activity act, boolean night, String label, final String url) {
        TextView t = new TextView(act);
        t.setText(label);
        t.setTextSize(14);
        t.setPadding(0, dp(act, 12), 0, dp(act, 12));
        t.setTextColor(night ? 0xFFB8C4FF : 0xFF3D5AFE);
        t.setOnClickListener(v -> {
            try {
                act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Throwable ignored) {
            }
        });
        return t;
    }

    /** 语言切换行：点击弹三选 */
    private static View langRow(final Activity act, boolean night) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(act, 10), 0, dp(act, 10));
        TextView l = new TextView(act);
        l.setText(L10n.t(act, "language"));
        l.setTextSize(16);
        l.setTypeface(Typeface.DEFAULT_BOLD);
        l.setTextColor(night ? 0xFFEAEAEA : 0xFF111111);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        l.setLayoutParams(llp);
        r.addView(l);
        TextView cur = new TextView(act);
        cur.setText(L10n.langName(L10n.get()));
        cur.setTextSize(13);
        cur.setTextColor(night ? 0x99FFFFFF : 0x99000000);
        cur.setPadding(0, 0, dp(act, 8), 0);
        r.addView(cur);
        TextView chev = new TextView(act);
        chev.setText("›");
        chev.setTextSize(18);
        chev.setTextColor(night ? 0x66FFFFFF : 0x66000000);
        r.addView(chev);
        r.setOnClickListener(v -> {
            final String[] opts = {L10n.ZH_HANS, L10n.ZH_HANT, L10n.EN};
            String[] names = {"简体中文", "繁體中文", "English"};
            new android.app.AlertDialog.Builder(act)
                    .setTitle(L10n.t(act, "language"))
                    .setItems(names, (d, w) -> {
                        L10n.set(act, opts[w]);
                        act.recreate();
                    })
                    .show();
        });
        return r;
    }

    private static View valueRow(Activity act, boolean night, String label, String value, String url) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(act, 10), 0, dp(act, 10));
        TextView l = new TextView(act);
        l.setText(label);
        l.setTextSize(16);
        l.setTypeface(Typeface.DEFAULT_BOLD);
        l.setTextColor(night ? 0xFFEAEAEA : 0xFF111111);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        l.setLayoutParams(llp);
        r.addView(l);
        TextView c = new TextView(act);
        c.setText(value + "  ›");
        c.setTextSize(13);
        c.setTextColor(night ? 0x99FFFFFF : 0x99000000);
        r.addView(c);
        return r;
    }

    private static int dp(Activity act, int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density);
    }

    public static class AboutBuild {
        public static final String VERSION_NAME = "2.1";
    }
}
