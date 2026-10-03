package com.zcode.themetool;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
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

/** 关于页 v2：HyperCeiler 层级 + 整页渐变融合（无分界线）+ 全粗体 + 无图标 + 语言切换。 */
public class AboutPage {

    public static View build(Activity act) {
        L10n.init(act);
        boolean night = isNight(act);

        ScrollView scroll = new ScrollView(act);
        scroll.setFillViewport(true);
        // 整页渐变背景：Hero 与正文自然糅合，无分界线
        GradientDrawable pageBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                night
                        ? new int[]{0xFF1B2438, 0xFF241B33, 0xFF0F141F}
                        : new int[]{0xFFFFD3E2, 0xFFE3D4FF, 0xFFFFEDF0});
        scroll.setBackground(pageBg);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(act, 20), dp(act, 64), dp(act, 20), dp(act, 40));
        scroll.addView(root);

        // ===== Hero：自绘字标（Canvas 粗体，不依赖系统 TextView 字重） =====
        ImageView wordmark = new ImageView(act);
        wordmark.setImageBitmap(wordmarkBitmap(act, night));
        wordmark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(act, 78));
        wordmark.setLayoutParams(wlp);
        root.addView(wordmark);

        TextView ver = new TextView(act);
        ver.setText(AboutBuild.VERSION_NAME + " | " + L10n.t(act, "release"));
        ver.setTextSize(15);
        ver.setTypeface(Typeface.DEFAULT_BOLD);
        ver.setTextColor(night ? 0xAAFFFFFF : 0xAA3A1A28);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(act, 8), 0, dp(act, 26));
        root.addView(ver);

        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body);

        // ===== 设备卡片 =====
        LinearLayout device = whiteCard(act, night);
        device.addView(head(act, night, userName(act) + "的" + marketName(act)));
        device.addView(bigRow(act, night, marketName(act), L10n.t(act, "dev_model")));
        device.addView(bigRow(act, night, "Android " + Build.VERSION.RELEASE, L10n.t(act, "android_ver")));
        device.addView(bigRow(act, night, osVersion(), L10n.t(act, "os_ver")));
        body.addView(cardWrap(act, device));

        // ===== 开发者卡片 =====
        LinearLayout dev = whiteCard(act, night);
        LinearLayout drow = new LinearLayout(act);
        drow.setOrientation(LinearLayout.HORIZONTAL);
        drow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView av = new ImageView(act);
        try {
            av.setImageBitmap(BitmapFactory.decodeResource(act.getResources(), R.drawable.dev_avatar));
        } catch (Throwable ignored) {
        }
        GradientDrawable avClip = new GradientDrawable();
        avClip.setCornerRadius(dp(act, 30));
        av.setBackground(avClip);
        av.setClipToOutline(true);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(act, 60), dp(act, 60));
        alp.rightMargin = dp(act, 16);
        av.setLayoutParams(alp);
        drow.addView(av);
        LinearLayout dcol = new LinearLayout(act);
        dcol.setOrientation(LinearLayout.VERTICAL);
        dcol.addView(txt(act, "Ache | Hyper Ice Cream", 18, true, night ? 0xFFF0F0F0 : 0xFF111111, 2));
        dcol.addView(txt(act, "@" + gitUser(), 13, true, night ? 0xAAFFFFFF : 0xAA333333, 0));
        drow.addView(dcol);
        dev.addView(drow);
        dev.addView(clickRow(act, night, L10n.t(act, "dev") + "  ›", "https://github.com/jinyuzerothree-collab"));
        dev.addView(clickRow(act, night, L10n.t(act, "repo") + "  ›", repoUrl()));
        body.addView(cardWrap(act, dev));

        // ===== 菜单 =====
        LinearLayout menu = whiteCard(act, night);
        menu.addView(menuRow(act, night, L10n.t(act, "contributors"), null, repoUrl() + "/graphs/contributors"));
        menu.addView(menuRow(act, night, L10n.t(act, "website"), null, repoUrl()));
        menu.addView(menuRow(act, night, L10n.t(act, "translate"), L10n.t(act, "translate_sub"), repoUrl() + "/issues"));
        menu.addView(menuRow(act, night, L10n.t(act, "support"), L10n.t(act, "support_sub"), repoUrl() + "/issues"));
        body.addView(cardWrap(act, menu));

        // ===== 协议 =====
        LinearLayout legal = whiteCard(act, night);
        legal.addView(menuRow(act, night, L10n.t(act, "terms"), null, repoUrl() + "/blob/main/LICENSE"));
        legal.addView(menuRow(act, night, L10n.t(act, "privacy"), null, repoUrl()));
        body.addView(cardWrap(act, legal));

        // ===== 语言 =====
        body.addView(sectionLabel(act, night, L10n.t(act, "language")));
        LinearLayout lang = whiteCard(act, night);
        lang.addView(langRow(act, night));
        body.addView(cardWrap(act, lang));

        // ===== 译者 =====
        body.addView(sectionLabel(act, night, L10n.t(act, "translator")));
        LinearLayout tr = whiteCard(act, night);
        tr.addView(valueRow(act, night, "Hyper Ice Cream " + L10n.t(act, "official"), L10n.langName(L10n.get())));
        body.addView(cardWrap(act, tr));

        // ===== 杂项 =====
        LinearLayout misc = whiteCard(act, night);
        misc.addView(menuRow(act, night, L10n.t(act, "license") + " (MIT)", null, repoUrl() + "/blob/main/LICENSE"));
        misc.addView(menuRow(act, night, L10n.t(act, "changelog"), null, repoUrl() + "/blob/main/CHANGELOG.md"));
        body.addView(cardWrap(act, misc));

        TextView foot = txt(act, "Made with Hyper Ice Cream", 12, true,
                night ? 0x77FFFFFF : 0x77000000, 0);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        flp.topMargin = dp(act, 22);
        foot.setLayoutParams(flp);
        body.addView(foot);
        return scroll;
    }

    /** 自绘字标：Canvas 加粗描边渲染，厚度不受系统字体影响 */
    private static android.graphics.Bitmap wordmarkBitmap(Activity act, boolean night) {
        float density = act.getResources().getDisplayMetrics().density;
        String text = "Hyper Ice Cream";
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(40 * density);
        p.setTypeface(Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        p.setFakeBoldText(true);
        p.setLetterSpacing(-0.01f);
        p.setColor(night ? 0xFFF0E2EC : 0xFF7A2F4E);
        p.setStyle(android.graphics.Paint.Style.FILL_AND_STROKE);
        p.setStrokeWidth(1.1f * density); // 描边减细：粗而不肿
        float tw = p.measureText(text);
        android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
        int w = (int) (tw + 8 * density);
        int h = (int) (fm.descent - fm.ascent + 8 * density);
        android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bm);
        cv.drawText(text, 4 * density, -fm.ascent + 4 * density, p);
        return bm;
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

    private static String osVersion() {
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
        bg.setColor(night ? 0xF2202028 : 0xFAFFFFFF);
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
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT_BOLD); // 全粗体
        t.setTextColor(color);
        t.setPadding(0, 0, 0, dp(act, padBottomDp));
        return t;
    }

    private static TextView head(Activity act, boolean night, String s) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(night ? 0xFFF0F0F0 : 0xFF111111);
        t.setPadding(0, 0, 0, dp(act, 12));
        return t;
    }

    private static TextView sectionLabel(Activity act, boolean night, String s) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(night ? 0xCCFFFFFF : 0xCC222222);
        t.setPadding(dp(act, 8), dp(act, 16), 0, dp(act, 6));
        return t;
    }

    private static View bigRow(Activity act, boolean night, String value, String label) {
        LinearLayout v = new LinearLayout(act);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(0, dp(act, 10), 0, dp(act, 10));
        TextView tv = new TextView(act);
        tv.setText(value);
        tv.setTextSize(20);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(night ? 0xFFF0F0F0 : 0xFF111111);
        v.addView(tv);
        TextView tl = new TextView(act);
        tl.setText(label);
        tl.setTextSize(13);
        tl.setTypeface(Typeface.DEFAULT_BOLD);
        tl.setTextColor(night ? 0xAAFFFFFF : 0xAA333333);
        v.addView(tl);
        return v;
    }

    private static View menuRow(final Activity act, boolean night, String label, String sub, final String url) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(act, 13), 0, dp(act, 13));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(clp);
        col.addView(txt(act, label, 16, true, night ? 0xFFF0F0F0 : 0xFF111111, sub == null ? 0 : 2));
        if (sub != null) col.addView(txt(act, sub, 12, true, night ? 0xAAFFFFFF : 0xAA333333, 0));
        r.addView(col);
        TextView chev = new TextView(act);
        chev.setText("›");
        chev.setTextSize(20);
        chev.setTypeface(Typeface.DEFAULT_BOLD);
        chev.setTextColor(night ? 0x88FFFFFF : 0x88000000);
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
        t.setTypeface(Typeface.DEFAULT_BOLD);
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

    private static View langRow(final Activity act, boolean night) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(act, 10), 0, dp(act, 10));
        TextView l = new TextView(act);
        l.setText(L10n.t(act, "language"));
        l.setTextSize(16);
        l.setTypeface(Typeface.DEFAULT_BOLD);
        l.setTextColor(night ? 0xFFF0F0F0 : 0xFF111111);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        l.setLayoutParams(llp);
        r.addView(l);
        TextView cur = new TextView(act);
        cur.setText(L10n.langName(L10n.get()));
        cur.setTextSize(13);
        cur.setTypeface(Typeface.DEFAULT_BOLD);
        cur.setTextColor(night ? 0xAAFFFFFF : 0xAA333333);
        cur.setPadding(0, 0, dp(act, 8), 0);
        r.addView(cur);
        TextView chev = new TextView(act);
        chev.setText("›");
        chev.setTextSize(20);
        chev.setTypeface(Typeface.DEFAULT_BOLD);
        chev.setTextColor(night ? 0x88FFFFFF : 0x88000000);
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

    private static View valueRow(Activity act, boolean night, String label, String value) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(act, 10), 0, dp(act, 10));
        TextView l = new TextView(act);
        l.setText(label);
        l.setTextSize(16);
        l.setTypeface(Typeface.DEFAULT_BOLD);
        l.setTextColor(night ? 0xFFF0F0F0 : 0xFF111111);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        l.setLayoutParams(llp);
        r.addView(l);
        TextView c = new TextView(act);
        c.setText(value + "  ›");
        c.setTextSize(13);
        c.setTypeface(Typeface.DEFAULT_BOLD);
        c.setTextColor(night ? 0xAAFFFFFF : 0xAA333333);
        r.addView(c);
        return r;
    }

    private static int dp(Activity act, int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density);
    }

    public static class AboutBuild {
        public static final String VERSION_NAME = "2.2";
    }
}
