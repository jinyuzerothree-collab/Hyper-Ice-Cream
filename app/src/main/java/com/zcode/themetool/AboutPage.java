package com.zcode.themetool;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 关于页（HyperCeiler 层级参考）：应用头 → 设备卡片 → 开发者卡片 → 链接列表。纯代码构建。 */
public class AboutPage {

    public static View build(Activity act) {
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(act, 20), dp(act, 20), dp(act, 20), dp(act, 120));

        // ===== 应用头 =====
        ImageView icon = new ImageView(act);
        icon.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(act, 84), dp(act, 84));
        ilp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        ilp.topMargin = dp(act, 12);
        icon.setLayoutParams(ilp);
        root.addView(icon);

        root.addView(title(act, "Hyper Ice Cream", 24, true, 1f));
        root.addView(title(act, "v" + BuildVersion.VERSION_NAME + " · HyperOS 平板主题增强工具", 12, false, 0.65f));

        // ===== 设备卡片 =====
        String market = getProp("ro.product.marketname");
        LinearLayout device = card(act);
        device.addView(title(act, userName(act) + " 的 " + (market.isEmpty() ? Build.MODEL : market), 16, true, 0.95f));
        device.addView(row(act, "设备型号", Build.MODEL + (market.isEmpty() ? "" : " (" + market + ")")));
        device.addView(row(act, "Android 版本", "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"));
        String hyper = getProp("ro.mi.os.version.name");
        device.addView(row(act, "HyperOS 版本", hyper.isEmpty() ? Build.DISPLAY : hyper));
        root.addView(wrapCard(device));

        // ===== 开发者卡片 =====
        LinearLayout dev = card(act);
        LinearLayout drow = new LinearLayout(act);
        drow.setOrientation(LinearLayout.HORIZONTAL);
        drow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView av = new ImageView(act);
        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(0xFF5B8DEF);
        av.setBackground(avBg);
        av.setPadding(dp(act, 14), dp(act, 8), dp(act, 14), dp(act, 8));
        av.setScaleType(ImageView.ScaleType.CENTER);
        av.setImageResource(android.R.drawable.ic_menu_myplaces);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(act, 46), dp(act, 46));
        alp.rightMargin = dp(act, 14);
        av.setLayoutParams(alp);
        drow.addView(av);
        LinearLayout dcol = new LinearLayout(act);
        dcol.setOrientation(LinearLayout.VERTICAL);
        dcol.addView(title(act, "jinyuzerothree-collab", 15, true, 0.95f));
        dcol.addView(title(act, "开发者 · GitHub", 12, false, 0.6f));
        drow.addView(dcol);
        dev.addView(drow);
        dev.addView(linkRow(act, "GitHub 主页", "https://github.com/jinyuzerothree-collab"));
        dev.addView(linkRow(act, "项目仓库", "https://github.com/jinyuzerothree-collab/HyperOS-Theme-Installer"));
        root.addView(wrapCard(dev));

        // ===== 链接列表 =====
        LinearLayout links = card(act);
        links.addView(linkRow(act, "更新日志", "https://github.com/jinyuzerothree-collab/HyperOS-Theme-Installer/blob/main/CHANGELOG.md"));
        links.addView(linkRow(act, "开源许可 (MIT)", "https://github.com/jinyuzerothree-collab/HyperOS-Theme-Installer/blob/main/LICENSE"));
        links.addView(linkRow(act, "主题收录规范", "https://github.com/jinyuzerothree-collab/HyperOS-Theme-Installer/blob/main/CONTRIBUTING.md"));
        links.addView(linkRow(act, "问题反馈 (Issues)", "https://github.com/jinyuzerothree-collab/HyperOS-Theme-Installer/issues"));
        links.addView(linkRow(act, "社区讨论 (Discussions)", "https://github.com/jinyuzerothree-collab/HyperOS-Theme-Installer/discussions"));
        links.addView(linkRow(act, "引用项目", "https://github.com/jinyuzerothree-collab/HyperOS-Theme-Installer#引用项目"));
        root.addView(wrapCard(links));

        root.addView(title(act, "Made with Hyper Ice Cream", 11, false, 0.4f));
        return root;
    }

    private static String userName(Activity act) {
        return act.getSharedPreferences("community_pref", Activity.MODE_PRIVATE)
                .getString("display_name", "我");
    }

    private static String getProp(String name) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", name});
            byte[] b = new byte[256];
            int n = p.getInputStream().read(b);
            p.waitFor();
            return n > 0 ? new String(b, 0, n).trim() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static LinearLayout card(Activity act) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(act, 18), dp(act, 16), dp(act, 18), dp(act, 16));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(act, 20));
        bg.setColor(0x0D000000);
        c.setBackground(bg);
        return c;
    }

    private static LinearLayout wrapCard(LinearLayout c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (14 * c.getContext().getResources().getDisplayMetrics().density);
        c.setLayoutParams(lp);
        return c;
    }

    private static TextView title(Activity act, String s, float sp, boolean bold, float alpha) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setAlpha(alpha);
        if (bold) t.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        if (!bold) t.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (bold) lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(act, 6);
        t.setLayoutParams(lp);
        return t;
    }

    private static View row(Activity act, String k, String v) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(act, 7), 0, dp(act, 7));
        TextView tk = new TextView(act);
        tk.setText(k);
        tk.setTextSize(13);
        tk.setAlpha(0.65f);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tk.setLayoutParams(klp);
        r.addView(tk);
        TextView tv = new TextView(act);
        tv.setText(v);
        tv.setTextSize(13);
        r.addView(tv);
        return r;
    }

    private static View linkRow(final Activity act, String label, final String url) {
        TextView t = new TextView(act);
        t.setText(label + "  ›");
        t.setTextSize(14);
        t.setPadding(0, dp(act, 12), 0, dp(act, 12));
        t.setOnClickListener(v -> {
            try {
                act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Throwable ignored) {
            }
        });
        return t;
    }

    private static int dp(Activity act, int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density);
    }

    /** 版本信息集中（避免 javac 缺 BuildConfig） */
    public static class BuildVersion {
        public static final String VERSION_NAME = "2.0";
    }
}
