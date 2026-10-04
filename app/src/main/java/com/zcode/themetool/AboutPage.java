package com.zcode.themetool;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 关于页 v3：
 *  - 动态炫光背景（三色光斑缓动，替代静态渐变的生硬感）
 *  - Hero 恢复应用图标（黑白处理）+ 加粗字标 + 足够留白
 *  - 下滑时顶部淡入「关于」横幅（不大，刚好）
 *  - OS 版本显示完整长串（去掉 OS 前缀），字体统一 medium 粗一点
 *  - 移除「支持」入口（需要收款码时再加回） */
public class AboutPage {

    public static View build(Activity act) {
        L10n.init(act);
        final boolean night = isNight(act);

        // ===== 根容器：炫光层 + 滚动内容 + 顶部横幅 =====
        FrameLayout frame = new FrameLayout(act);

        final GlowView glow = new GlowView(act, night);
        frame.addView(glow, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ObservableScrollView scroll = new ObservableScrollView(act);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        frame.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(act, 20), dp(act, 72), dp(act, 20), dp(act, 40));
        scroll.addView(root);

        // ===== Hero：黑白应用图标（足够留白） =====
        ImageView icon = new ImageView(act);
        try {
            Bitmap src = BitmapFactory.decodeResource(act.getResources(), R.mipmap.ic_launcher);
            if (src != null) {
                Bitmap gray = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(gray);
                Paint pm = new Paint(Paint.ANTI_ALIAS_FLAG);
                ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(0f); // 黑白处理
                pm.setColorFilter(new ColorMatrixColorFilter(cm));
                cv.drawBitmap(src, 0, 0, pm);
                icon.setImageBitmap(gray);
            }
        } catch (Throwable ignored) {
        }
        GradientDrawable iconClip = new GradientDrawable();
        iconClip.setCornerRadius(dp(act, 22));
        icon.setBackground(iconClip);
        icon.setClipToOutline(true);
        icon.setCropToPadding(true);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(act, 84), dp(act, 84));
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        ilp.topMargin = dp(act, 26);
        icon.setLayoutParams(ilp);
        root.addView(icon);

        // ===== Hero：字标（加粗，不过粗）+ 版本 =====
        TextView name = new TextView(act);
        name.setText("Hyper Ice Cream");
        name.setTextSize(30);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        name.setTextColor(night ? 0xFFF0F0F0 : 0xFF000000);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.gravity = Gravity.CENTER_HORIZONTAL;
        nlp.topMargin = dp(act, 18);
        name.setLayoutParams(nlp);
        root.addView(name);

        TextView ver = new TextView(act);
        ver.setText(versionName(act) + " | " + L10n.t(act, "release"));
        ver.setTextSize(15);
        ver.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        ver.setTextColor(night ? 0xAAFFFFFF : 0xAA000000);
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

        // ===== 菜单（「支持」入口暂撤，收款码就绪后加回） =====
        LinearLayout menu = whiteCard(act, night);
        menu.addView(menuRow(act, night, L10n.t(act, "contributors"), null, repoUrl() + "/graphs/contributors"));
        menu.addView(menuRow(act, night, L10n.t(act, "website"), null, repoUrl()));
        menu.addView(menuRow(act, night, L10n.t(act, "translate"), L10n.t(act, "translate_sub"), repoUrl() + "/issues"));
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

        // ===== 顶部横幅：下滑后淡入「关于」（不大，刚好） =====
        final TextView bar = new TextView(act);
        bar.setText(L10n.t(act, "about"));
        bar.setTextSize(16);
        bar.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        bar.setTextColor(night ? 0xFFF0F0F0 : 0xFF111111);
        bar.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bar.setPadding(0, 0, 0, dp(act, 10));
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(night ? 0xE6141418 : 0xF2FFFFFF);
        bar.setBackground(barBg);
        int sb = act.getResources().getIdentifier("status_bar_height", "dimen", "android");
        int top = sb > 0 ? act.getResources().getDimensionPixelSize(sb) : dp(act, 24);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, top + dp(act, 44));
        bar.setAlpha(0f);
        frame.addView(bar, blp);

        // 下滑 > 120dp 开始淡入，70dp 内完成
        scroll.setScrollCb(new ObservableScrollView.ScrollCb() {
            @Override
            public void onScrolled(int y) {
                float a = (y - dp(act, 120)) / (float) dp(act, 70);
                bar.setAlpha(Math.max(0f, Math.min(1f, a)));
                glow.setDim(Math.max(0f, Math.min(1f, (y - dp(act, 260)) / (float) dp(act, 200))));
            }
        });
        return frame;
    }

    /** 动态炫光：三色光斑绕行缓动（替代静态渐变），离开屏幕自动停帧省电 */
    static class GlowView extends View {
        private final int[][] PALETTE = {
                {0x88FFD3E2, 0x88D4C2FF, 0x88B8D9FF},   // 日间：粉 / 薰衣草 / 天蓝
                {0x513D5AFE, 0x55834CFF, 0x4400BFA5},   // 夜间：蓝 / 紫 / 青
        };
        private final boolean night;
        private ValueAnimator anim;
        private float phase;
        private float dim = 0f; // 滚出视口时压暗

        GlowView(Activity act, boolean night) {
            super(act);
            this.night = night;
        }

        void setDim(float d) { dim = d; invalidate(); }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(9000);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setRepeatMode(ValueAnimator.RESTART);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (anim != null) anim.cancel();
            anim = null;
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (dim >= 1f) return;
            int[] cols = PALETTE[night ? 1 : 0];
            float w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            float r = Math.max(w, h) * 0.62f;
            // 三个光斑绕行：相位错开 120°，轨道半径不同 → 永不重合的炫光
            float[][] orbit = {{0.32f, 0.36f, 0.16f}, {0.62f, 0.68f, 0.22f}, {0.44f, 0.82f, 0.18f}};
            float speed = (float) (Math.PI * 2);
            for (int i = 0; i < 3; i++) {
                float t = phase * speed + i * 2.0944f;
                float cx = (float) (w * orbit[i][0] + Math.cos(t) * w * orbit[i][2]);
                float cy = (float) (h * orbit[i][1] + Math.sin(t) * h * orbit[i][2] * 0.7f);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setShader(new RadialGradient(cx, cy, r,
                        new int[]{cols[i], 0x00000000}, null, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, r, p);
            }
            if (dim > 0f) {
                Paint d = new Paint();
                d.setColor(Color.argb((int) (200 * dim), 0, 0, 0));
                c.drawRect(0, 0, w, h, d);
            }
        }
    }

    /** 可观察滚动：供顶部横幅淡入 */
    static class ObservableScrollView extends ScrollView {
        interface ScrollCb { void onScrolled(int y); }
        private ScrollCb cb;
        ObservableScrollView(Activity a) { super(a); }
        void setScrollCb(ScrollCb c) { cb = c; }
        @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
            super.onScrollChanged(l, t, ol, ot);
            if (cb != null) cb.onScrolled(t);
        }
    }

    /** 版本号：从 PackageManager 读取（单一事实源），失败回退常量 */
    private static String versionName(Activity act) {
        try {
            PackageInfo pi = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
            if (pi.versionName != null && !pi.versionName.isEmpty()) return pi.versionName;
        } catch (Throwable ignored) {
        }
        return AboutBuild.VERSION_NAME;
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

    /** OS 版本：完整长串（如 4.0.0.31.XPYCNXM），去掉 OS 前缀 */
    private static String osVersion() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", "ro.build.version.incremental"});
            byte[] b = new byte[128];
            int n = p.getInputStream().read(b);
            p.waitFor();
            String s = n > 0 ? new String(b, 0, n).trim() : "";
            if (s.length() > 2 && (s.startsWith("OS") || s.startsWith("Os"))) s = s.substring(2);
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
        // 统一 medium：粗一点点，不细不肿
        t.setTypeface(Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        t.setTextColor(color);
        t.setPadding(0, 0, 0, dp(act, padBottomDp));
        return t;
    }

    private static TextView head(Activity act, boolean night, String s) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(24);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setTextColor(night ? 0xFFF0F0F0 : 0xFF111111);
        t.setPadding(0, 0, 0, dp(act, 12));
        return t;
    }

    private static TextView sectionLabel(Activity act, boolean night, String s) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(14);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
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
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setTextColor(night ? 0xFFF0F0F0 : 0xFF111111);
        v.addView(tv);
        TextView tl = new TextView(act);
        tl.setText(label);
        tl.setTextSize(13);
        tl.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
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
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
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
        l.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        l.setTextColor(night ? 0xFFF0F0F0 : 0xFF111111);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        l.setLayoutParams(llp);
        r.addView(l);
        TextView cur = new TextView(act);
        cur.setText(L10n.langName(L10n.get()));
        cur.setTextSize(13);
        cur.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
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
        l.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        l.setTextColor(night ? 0xFFF0F0F0 : 0xFF111111);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        l.setLayoutParams(llp);
        r.addView(l);
        TextView c = new TextView(act);
        c.setText(value + "  ›");
        c.setTextSize(13);
        c.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        c.setTextColor(night ? 0xAAFFFFFF : 0xAA333333);
        r.addView(c);
        return r;
    }

    private static int dp(Activity act, int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density);
    }

    public static class AboutBuild {
        public static final String VERSION_NAME = "2.9";
    }
}
