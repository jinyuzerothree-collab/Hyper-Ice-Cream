package com.zcode.themetool;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 41;
    // 主题小组件命名模式（clock_2x4 / weather_4x1 / notes_xxx / calculator_xxx / gadget*）
    private static final Pattern WIDGET_PATTERN =
            Pattern.compile("^(clock|weather|notes|calculator|gadget)([_\\-](\\d+)x(\\d+))?$",
                    Pattern.CASE_INSENSITIVE);
    private static final String COMMUNITY_PREF = "community_pref";
    private static final String KEY_COMMUNITY_FILES = "community_files";

    private TextView log;
    private LinearLayout compsBox;
    private File unpackedDir;
    private final List<Object[]> comps = new ArrayList<>(); // {name, label, path}
    private boolean hasGadgets = false;
    private File lastPicked;
    private File lastBase;
    private MetadataParser.Meta lastMeta;

    private File browserDir = new File("/sdcard/Download");
    private boolean showAll = false;

    // 页面与底栏
    private ScrollView pageHome, pageCommunity, pageTools;
    private View dock;
    private TextView tabHome, tabCommunity, tabTools, tabAbout;
    private int accentColor = 0xFF3D7EFF;
    private CommunityFragment community;
    private String pendingCommunityFile; // 社区下载主题的路径 → 禁止再次贡献

    private void log(String s) {
        runOnUiThread(() -> logView.setText(logView.getText() + s + "\n"));
    }

    private TextView logView;

    private String execSu(String script) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            OutputStream os = p.getOutputStream();
            os.write((script + "\nexit\n").getBytes("UTF-8"));
            os.flush();
            os.close();
            byte[] out = readAll(p.getInputStream());
            byte[] err = readAll(p.getErrorStream());
            p.waitFor();
            return new String(out, "UTF-8") + new String(err, "UTF-8");
        } catch (Throwable t) {
            return "SU-ERROR: " + t;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    private boolean storageOk() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        logView = findViewById(R.id.log);
        compsBox = findViewById(R.id.comps);
        unpackedDir = new File(getCacheDir(), "unpack");

        applyDynamicColor();

        String id = execSu("id");
        log("root检查: " + (id.contains("uid=0") ? "OK (已授权)" : "未授权，部署时请允许 su 请求"));
        if (!storageOk()) {
            findViewById(R.id.btn_perm).setVisibility(View.VISIBLE);
            log("提示：请在「部署」页点「授权所有文件访问」。");
        }
        findViewById(R.id.btn_perm).setOnClickListener(v -> {
            try {
                startActivity(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                        Uri.parse("package:com.zcode.themetool")));
            } catch (Throwable t) {
                startActivity(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"));
            }
        });

        findViewById(R.id.btn_pick).setOnClickListener(v -> showBrowser());
        findViewById(R.id.btn_deploy).setOnClickListener(v -> doDeploy());
        findViewById(R.id.btn_backup).setOnClickListener(v -> doBackup());
        findViewById(R.id.btn_restore).setOnClickListener(v -> doRestore());
        findViewById(R.id.btn_restart).setOnClickListener(v -> {
            execSu("am crash com.android.systemui");
            log("SystemUI 重启指令已发送。");
        });
        findViewById(R.id.btn_launcher).setOnClickListener(v -> doRestartLauncher());

        pageHome = findViewById(R.id.page_home);
        pageCommunity = findViewById(R.id.page_community);
        pageTools = findViewById(R.id.page_tools);
        dock = findViewById(R.id.dock);
        tabHome = findViewById(R.id.tab_home);
        tabCommunity = findViewById(R.id.tab_community);
        tabTools = findViewById(R.id.tab_tools);
        tabAbout = findViewById(R.id.tab_about);
        tabHome.setOnClickListener(v -> switchPage(0));
        tabCommunity.setOnClickListener(v -> switchPage(1));
        tabTools.setOnClickListener(v -> switchPage(2));
        tabAbout.setOnClickListener(v -> switchPage(3));
        applyDockStyle();
        switchPage(0);
        attachGlassDock(); // 真·液态玻璃：独立窗口 + blurBehind（API31+），失败则用内嵌 Dock
    }

    @Override
    protected void onDestroy() {
        try {
            if (glassDockView != null && glassDockParent != null) {
                glassDockParent.removeView(glassDockView);
                glassDockView = null;
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    // ---------- 真·液态玻璃 Dock（独立窗口 + FLAG_BLUR_BEHIND 实时背景模糊） ----------
    private LinearLayout glassDockView;
    private android.view.WindowManager glassDockParent;

    private void attachGlassDock() {
        try {
            if (Build.VERSION.SDK_INT < 31) return; // blurBehind 需要 S+
            LinearLayout dockView = new LinearLayout(this);
            dockView.setOrientation(LinearLayout.HORIZONTAL);
            dockView.setPadding(dp(8), 0, dp(8), 0);
            GradientDrawable glass = new GradientDrawable();
            glass.setCornerRadius(dp(30));
            glass.setColor(isNight() ? 0x66141414 : 0x66F5F7FA); // 更通透：模糊由系统提供
            glass.setStroke((int) dp(1), isNight() ? 0x30FFFFFF : 0x50FFFFFF);
            dockView.setBackground(glass);
            dockView.setElevation(dp(18));
            dockView.setClickable(true);

            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams(
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                    (int) dp(56),
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                    android.graphics.PixelFormat.TRANSLUCENT);
            lp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
            lp.y = (int) dp(22);
            try {
                // blurBehindRadius 在部分 android.jar 缺失，反射设置（API31+）
                java.lang.reflect.Field f = lp.getClass().getField("blurBehindRadius");
                f.set(lp, (int) dp(28));
                lp.flags |= android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            } catch (Throwable t) {
                log("系统不支持窗口背景模糊，Dock 将以通透玻璃显示: " + t.getClass().getSimpleName());
            }
            lp.windowAnimations = android.R.style.Animation_Dialog;

            String[] labels = {"部署", "社区", "工具", "关于"};
            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                TextView t = new TextView(this);
                t.setText(labels[i]);
                t.setTextSize(15);
                t.setGravity(android.view.Gravity.CENTER);
                t.setPadding(dp(18), 0, dp(18), 0);
                t.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
                t.setOnClickListener(v -> {
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(60)
                            .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start();
                    runOnUiThread(() -> switchPage(idx));
                });
                dockView.addView(t);
                glassTabs.add(t);
            }
            glassDockParent = getWindowManager();
            getWindow().getDecorView().post(() -> {
                try {
                    glassDockParent.addView(dockView, lp);
                    glassDockView = dockView;
                    findViewById(R.id.dock).setVisibility(View.GONE);
                    styleGlassTabs(0);
                } catch (Throwable t) {
                    log("液态玻璃 Dock 初始化回退: " + t);
                }
            });
        } catch (Throwable t) {
            log("液态玻璃 Dock 不可用，使用内嵌 Dock: " + t);
        }
    }

    private final List<TextView> glassTabs = new ArrayList<>();

    private void styleGlassTabs(int active) {
        if (glassDockView == null) return;
        for (int i = 0; i < glassTabs.size(); i++) {
            TextView t = glassTabs.get(i);
            if (i == active) {
                GradientDrawable pill = new GradientDrawable();
                pill.setCornerRadius(dp(24));
                pill.setColor(accentColor);
                t.setBackground(pill);
                t.setTextColor(Color.WHITE);
                t.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                t.setBackground(null);
                t.setTextColor(isNight() ? 0xE6FFFFFF : 0xE6111111);
                t.setTypeface(Typeface.DEFAULT);
            }
        }
    }

    // ---------- 动态取色（Material You 近似） ----------
    private boolean isNight() {
        int m = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return m == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyDynamicColor() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                WallpaperManager wm = WallpaperManager.getInstance(this);
                android.app.WallpaperColors wc = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                if (wc != null) {
                    int primary = wc.getPrimaryColor().toArgb();
                    // 提亮/压暗作为 accent，保证对比度
                    accentColor = isNight() ? lighten(primary, 0.35f) : darken(primary, 0.15f);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static int lighten(int c, float f) {
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        r += (255 - r) * f; g += (255 - g) * f; b += (255 - b) * f;
        return Color.rgb(r, g, b);
    }

    private static int darken(int c, float f) {
        return Color.rgb((int) (Color.red(c) * (1 - f)),
                (int) (Color.green(c) * (1 - f)), (int) (Color.blue(c) * (1 - f)));
    }

    // ---------- Liquid Glass 悬浮底栏 ----------
    private void applyDockStyle() {
        boolean night = isNight();
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(dp(30));
        glass.setColor(night ? 0xB3141414 : 0xB3F5F7FA);
        glass.setStroke((int) dp(1), night ? 0x26FFFFFF : 0x40FFFFFF);
        dock.setBackground(glass);
        dock.setElevation(dp(16));
        // 高光：顶部内描边模拟液态玻璃反射
        GradientDrawable highlight = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33FFFFFF, 0x00FFFFFF});
        highlight.setCornerRadius(dp(30));
        // tab 样式
        styleTab(tabHome, true);
        styleTab(tabCommunity, false);
        styleTab(tabTools, false);
    }

    private void styleTab(TextView t, boolean active) {
        if (active) {
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(dp(24));
            pill.setColor(accentColor);
            t.setBackground(pill);
            t.setTextColor(Color.WHITE);
            t.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            t.setBackground(null);
            t.setTextColor(isNight() ? 0xCCFFFFFF : 0xCC111111);
            t.setTypeface(Typeface.DEFAULT);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void switchPage(int idx) {
        if (idx == 1) openCommunity(); // 懒构建 + 每次进入刷新
        if (idx == 3 && aboutBuilt == false) {
            ((LinearLayout) findViewById(R.id.about_container)).addView(AboutPage.build(this));
            aboutBuilt = true;
        }
        pageHome.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        pageCommunity.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        pageTools.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        findViewById(R.id.page_about).setVisibility(idx == 3 ? View.VISIBLE : View.GONE);
        styleTab(tabHome, idx == 0);
        styleTab(tabCommunity, idx == 1);
        styleTab(tabTools, idx == 2);
        styleTab(tabAbout, idx == 3);
        styleGlassTabs(idx);
        View target = idx == 0 ? pageHome : (idx == 1 ? pageCommunity
                : (idx == 2 ? pageTools : findViewById(R.id.page_about)));
        target.setAlpha(0f);
        target.animate().alpha(1f).setDuration(180).start();
        // 轻微弹性反馈
        View bar = glassDockView != null ? glassDockView : dock;
        bar.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70)
                .withEndAction(() -> bar.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start();
    }

    private boolean aboutBuilt = false;

    // ---------- 内置文件浏览器 ----------
    private void showBrowser() {
        if (!storageOk()) {
            Toast.makeText(this, "请先授权所有文件访问", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!browserDir.exists()) browserDir = new File("/sdcard");
        showDirDialog();
    }

    private void showDirDialog() {
        File[] fs = browserDir.listFiles();
        List<String> items = new ArrayList<>();
        List<File> files = new ArrayList<>();
        File parent = browserDir.getParentFile();
        if (parent != null && parent.canRead()) {
            items.add("↩ 上级目录");
            files.add(parent);
        }
        List<File> dirs = new ArrayList<>();
        List<File> matches = new ArrayList<>();
        List<File> others = new ArrayList<>();
        if (fs != null) {
            Arrays.sort(fs, (a, b2) -> a.getName().compareToIgnoreCase(b2.getName()));
            for (File f : fs) {
                if (f.isDirectory()) dirs.add(f);
                else {
                    String n = f.getName().toLowerCase();
                    if (n.endsWith(".mtz") || n.endsWith(".zip") || n.endsWith(".ttf")) matches.add(f);
                    else others.add(f);
                }
            }
        }
        for (File d : dirs) { items.add("📁 " + d.getName()); files.add(d); }
        for (File f : matches) { items.add("📄 " + f.getName() + " (" + f.length() / 1024 + "KB)"); files.add(f); }
        if (showAll) for (File f : others) { items.add("· " + f.getName()); files.add(f); }
        if (dirs.isEmpty() && matches.isEmpty() && others.isEmpty()) items.add("(空目录)");

        new AlertDialog.Builder(this)
                .setTitle(browserDir.getAbsolutePath() + (showAll ? "  [全部]" : "  [mtz/zip/ttf]"))
                .setItems(items.toArray(new String[0]), (dlg, which) -> {
                    File f = files.get(which);
                    if (f.isDirectory()) {
                        browserDir = f;
                        showDirDialog();
                    } else if (f.getName().toLowerCase().endsWith(".ttf")) {
                        dlg.dismiss();
                        comps.clear();
                        lastMeta = null;
                        lastPicked = f;
                        addComp("fonts", f.getAbsolutePath());
                        renderComps();
                        renderMeta();
                        log("已选择字体: " + f.getName() + "\n字体为实验性：部署后建议重启设备。");
                    } else {
                        dlg.dismiss();
                        detectComponents(f);
                    }
                })
                .setNeutralButton(showAll ? "只看 mtz/zip/ttf" : "显示全部文件", (dlg, w) -> {
                    showAll = !showAll;
                    showDirDialog();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    // ---------- 组件识别 ----------
    private void detectComponents(File picked) {
        comps.clear();
        unpackedDir.delete();
        unpackedDir.mkdirs();
        File base = picked;
        try {
            if (zipfileHasEntries(picked)) {
                File ex = new File(unpackedDir, "x");
                ZipInputStream zi = new ZipInputStream(new FileInputStream(picked));
                ZipEntry e;
                byte[] buf = new byte[65536];
                int n;
                while ((e = zi.getNextEntry()) != null) {
                    File out = new File(ex, e.getName());
                    if (e.isDirectory()) { out.mkdirs(); continue; }
                    out.getParentFile().mkdirs();
                    BufferedOutputStream bo = new BufferedOutputStream(new FileOutputStream(out));
                    while ((n = zi.read(buf)) > 0) bo.write(buf, 0, n);
                    bo.close();
                }
                zi.close();
                base = ex;
                log("已解包主题包");
            }
        } catch (Throwable t) {
            log("非 zip 或解包失败，按单文件处理: " + t);
        }

        lastPicked = picked;
        lastBase = base;
        lastMeta = MetadataParser.parse(base, picked);

        for (String comp : new String[]{"com.android.systemui", "icons"}) {
            File f = new File(base, comp);
            if (f.isFile()) addComp(comp, f.getAbsolutePath());
        }
        for (String comp : new String[]{"com.android.systemui", "icons"}) {
            File d = new File(base, comp);
            if (d.isDirectory() && !hasComp(comp)) {
                File zip = new File(unpackedDir, comp + ".zip");
                if (zipDir(d, zip)) addComp(comp, zip.getAbsolutePath());
            }
        }
        File fontsDir = new File(base, "fonts");
        if (fontsDir.isDirectory()) {
            File zip = new File(unpackedDir, "fonts.zip");
            if (zipDir(fontsDir, zip)) addComp("fonts", zip.getAbsolutePath());
        } else if (new File(base, "fonts").isFile()) {
            addComp("fonts", new File(base, "fonts").getAbsolutePath());
        }
        File wall = findImage(base, false);
        File lock = findImage(base, true);
        if (wall != null) addComp("wallpaper", wall.getAbsolutePath());
        if (lock != null) addComp("lock_wallpaper", lock.getAbsolutePath());

        // 主题小组件：顶层 widget 命名组件 + gadgets/ 目录
        hasGadgets = false;
        File[] tops = base.listFiles();
        if (tops != null) {
            for (File t : tops) {
                String n = t.getName();
                if (hasComp(n) || !WIDGET_PATTERN.matcher(n).matches()) continue;
                if (t.isFile()) {
                    addComp(n, t.getAbsolutePath());
                    hasGadgets = true;
                } else if (t.isDirectory()) {
                    File zip = new File(unpackedDir, n + ".zip");
                    if (zipDir(t, zip)) {
                        addComp(n, zip.getAbsolutePath());
                        hasGadgets = true;
                    }
                }
            }
        }
        File gadgetsDir = new File(base, "gadgets");
        if (gadgetsDir.isDirectory()) {
            File[] gs = gadgetsDir.listFiles();
            if (gs != null) {
                Arrays.sort(gs, (a, b2) -> a.getName().compareToIgnoreCase(b2.getName()));
                for (File g : gs) {
                    String low = g.getName().toLowerCase();
                    if (!low.endsWith(".mtz") && !low.endsWith(".mrc")) continue;
                    hasGadgets = true;
                    addComp("gadgets/" + g.getName(), g.getAbsolutePath());
                }
            }
        }

        renderComps();
        renderMeta();
        log("提示：桌面图标的替换包含在「系统图标」组件中（桌面没有独立组件）。");
    }

    private void renderComps() {
        compsBox.removeAllViews();
        for (Object[] c : comps) {
            CheckBox cb = new CheckBox(this);
            cb.setText((String) c[1]);
            cb.setChecked(true);
            cb.setTag(c);
            compsBox.addView(cb);
        }
        if (comps.isEmpty()) {
            log("未发现可部署组件");
            findViewById(R.id.btn_deploy).setEnabled(false);
        } else {
            findViewById(R.id.btn_deploy).setEnabled(true);
            log("勾选要应用的组件后点「部署」。");
        }
    }

    private void renderMeta() {
        ImageView pv = findViewById(R.id.meta_preview);
        TextView mt = findViewById(R.id.meta_text);
        if (lastMeta == null) {
            pv.setVisibility(View.GONE);
            mt.setVisibility(View.GONE);
            return;
        }
        if (!lastMeta.previews.isEmpty()) {
            Bitmap bm = decodeSampled(lastMeta.previews.get(0), 720);
            if (bm != null) {
                pv.setImageBitmap(bm);
                pv.setVisibility(View.VISIBLE);
            } else {
                pv.setVisibility(View.GONE);
            }
        } else {
            pv.setVisibility(View.GONE);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("主题名称: ").append(lastMeta.name).append('\n');
        sb.append("作者: ").append(lastMeta.author).append('\n');
        sb.append("版本: ").append(lastMeta.version).append('\n');
        sb.append("适配 UI 版本: ").append(lastMeta.uiVersion).append('\n');
        sb.append("主题大小: ").append(fmtSize(lastMeta.sizeBytes)).append('\n');
        String sha = lastMeta.sha256;
        if (sha.length() > 20) sha = sha.substring(0, 20) + "…";
        sb.append("SHA256: ").append(sha).append('\n');
        sb.append("组件清单: ");
        if (lastMeta.components.isEmpty()) sb.append("未知");
        else {
            java.util.Iterator<String> it = lastMeta.components.iterator();
            while (it.hasNext()) sb.append(it.next()).append(it.hasNext() ? "、 " : "");
        }
        mt.setText(sb.toString());
        mt.setVisibility(View.VISIBLE);
    }

    private String fmtSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.2fGB", bytes / 1024.0 / 1024 / 1024);
        if (bytes >= 1024L * 1024) return String.format(java.util.Locale.US, "%.2fMB", bytes / 1024.0 / 1024);
        if (bytes >= 1024) return String.format(java.util.Locale.US, "%.1fKB", bytes / 1024.0);
        return bytes + "B";
    }

    private Bitmap decodeSampled(File f, int target) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth / sample > target) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        } catch (Throwable t) {
            return null;
        }
    }

    private void addComp(String name, String path) {
        String label;
        if (name.equals("com.android.systemui")) label = "状态栏 / 电池图标 (SystemUI)";
        else if (name.equals("icons")) label = "系统图标 / 桌面图标 (icons)";
        else if (name.equals("wallpaper")) label = "壁纸";
        else if (name.equals("lock_wallpaper")) label = "锁屏壁纸";
        else if (name.equals("fonts")) label = "字体（实验性，部署后建议重启设备）";
        else if (name.startsWith("gadgets/")) label = "主题小组件 (gadgets/" + name.substring(8) + ")";
        else if (name.startsWith("clock")) label = "时钟组件 (" + name + ")";
        else if (name.startsWith("weather")) label = "天气组件 (" + name + ")";
        else if (name.startsWith("notes")) label = "便签组件 (" + name + ")";
        else if (name.startsWith("calculator")) label = "计算器组件 (" + name + ")";
        else if (name.startsWith("gadget")) label = "主题小组件 (" + name + ")";
        else label = name;
        comps.add(new Object[]{name, label, path});
    }

    private boolean hasComp(String n) {
        for (Object[] c : comps) if (c[0].equals(n)) return true;
        return false;
    }

    private boolean zipfileHasEntries(File f) {
        try { ZipFile zf = new ZipFile(f); zf.close(); return true; }
        catch (Throwable t) { return false; }
    }

    private boolean zipDir(File dir, File outZip) {
        try {
            ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(outZip));
            zipRec(dir, dir, zo);
            zo.close();
            return true;
        } catch (Throwable t) { log("打包 " + dir.getName() + " 失败: " + t); return false; }
    }

    private void zipRec(File base, File cur, ZipOutputStream zo) throws Exception {
        File[] fs = cur.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) zipRec(base, f, zo);
            else {
                String arc = base.toPath().relativize(f.toPath()).toString().replace('\\', '/');
                zo.putNextEntry(new ZipEntry(arc));
                FileInputStream fi = new FileInputStream(f);
                byte[] buf = new byte[65536];
                int n;
                while ((n = fi.read(buf)) > 0) zo.write(buf, 0, n);
                fi.close();
            }
        }
    }

    private File findImage(File base, boolean lock) {
        List<File> all = new ArrayList<>();
        walk(base, all);
        java.util.Collections.sort(all, (a, b) -> Long.compare(b.length(), a.length()));
        for (File f : all) {
            String n = f.getName().toLowerCase();
            if (!(n.endsWith(".jpg") || n.endsWith(".png"))) continue;
            if (lock && n.contains("lock") && n.contains("wall")) return f;
            if (!lock && !n.contains("lock") && n.startsWith("wallpaper")) return f;
        }
        return null;
    }

    private void walk(File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) walk(f, out);
            else out.add(f);
        }
    }

    // ---------- 部署 ----------
    private void doDeploy() {
        List<Object[]> selected = new ArrayList<>();
        for (int i = 0; i < compsBox.getChildCount(); i++) {
            Object child = compsBox.getChildAt(i);
            if (child instanceof CheckBox) {
                CheckBox cb = (CheckBox) child;
                if (cb.isChecked()) selected.add((Object[]) cb.getTag());
            }
        }
        if (selected.isEmpty()) { log("未勾选任何组件"); return; }

        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        StringBuilder sb = new StringBuilder();
        sb.append("mkdir -p /sdcard/ThemeToolBackup/backup_").append(ts).append('\n');
        sb.append("cp -a /data/system/theme/. /sdcard/ThemeToolBackup/backup_").append(ts).append("/\n");
        sb.append("chmod -R 777 /sdcard/ThemeToolBackup/backup_").append(ts).append('\n');

        boolean needFontsZip = false;
        String fontsPath = null;
        boolean deployedGadgets = false;
        for (Object[] c : selected) {
            String name = (String) c[0];
            String path = (String) c[2];
            if (name.equals("fonts")) {
                needFontsZip = path.endsWith(".zip");
                fontsPath = path;
                continue;
            }
            // 小组件：注入标准 description.xml（size 声明），否则 Launcher 识别为固定尺寸
            if (WIDGET_PATTERN.matcher(name).matches() && !"gadgets".equals(name)) {
                File fixed = prepareWidgetContainer(name, path);
                if (fixed != null) path = fixed.getAbsolutePath();
                deployedGadgets = true;
            }
            if (name.contains("/")) {
                String parent = name.substring(0, name.lastIndexOf('/'));
                sb.append("mkdir -p /data/system/theme/").append(parent).append('\n');
                deployedGadgets = true;
            }
            sb.append("cp '").append(path).append("' /data/system/theme/").append(name).append('\n');
            sb.append("chown system_theme:system_theme /data/system/theme/").append(name).append('\n');
            sb.append("chmod ").append(name.equals("wallpaper") ? "600" : "755")
              .append(" /data/system/theme/").append(name).append('\n');
        }
        sb.append("echo DEPLOY-DONE\n");
        String r = execSu(sb.toString());
        if (needFontsZip && fontsPath != null) {
            StringBuilder fs2 = new StringBuilder();
            fs2.append("mkdir -p /data/system/theme/fonts\n");
            if (needFontsZip) {
                fs2.append("cd /data/system/theme/fonts\n");
                fs2.append("unzip -o '").append(fontsPath).append("'\n");
            } else {
                File ttf = new File(fontsPath);
                fs2.append("cp '").append(fontsPath).append("' /data/system/theme/fonts/")
                   .append(ttf.getName()).append('\n');
            }
            fs2.append("chown -R system_theme:system_theme /data/system/theme/fonts\n");
            fs2.append("chmod -R 755 /data/system/theme/fonts\n");
            fs2.append("echo FONTS-DONE\n");
            r += execSu(fs2.toString());
        }
        log(r);
        if (r.contains("DEPLOY-DONE")) {
            log("✅ 部署完成，已自动备份到 /sdcard/ThemeToolBackup/backup_" + ts);
            if (deployedGadgets) {
                exportWidgetAssets();
                log("主题小组件已部署（含标准尺寸声明）。桌面长按 → 添加小组件；\n另可用「Hyper Ice Cream」系统小组件（自由缩放多尺寸）。");
            }
            log("壁纸如未变化请重启设备；工具页可一键重启 SystemUI/Launcher。");
            // 贡献去重：社区下载路径 OR sha256 与社区索引一致 → 不询问
            boolean isCommunity = pendingCommunityFile != null
                    && pendingCommunityFile.equals(lastPicked.getAbsolutePath());
            if (!isCommunity && lastMeta != null && communitySha256Exists(lastMeta.sha256)) {
                isCommunity = true;
                log("（与社区已有主题 sha256 一致，不再贡献）");
            }
            if (!isCommunity && lastMeta != null) {
                new AlertDialog.Builder(this)
                        .setTitle("分享主题给社区？")
                        .setMessage("当前主题已成功部署。\n是否将该主题贡献到社区主题库？\n帮助更多平板用户发现优质主题。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("分享", (d, w) -> askContributionInfo())
                        .show();
            } else if (isCommunity && pendingCommunityFile != null) {
                log("（该主题来自社区下载，不再重复贡献）");
                pendingCommunityFile = null;
            }
        } else {
            log("部署未确认，检查上面输出（多半是 su 未授权）。");
        }
    }

    /** sha256 去重：与社区索引缓存比对（精确哈希匹配 = 同一包，不询问贡献） */
    private boolean communitySha256Exists(String sha) {
        if (sha == null || sha.equals("未知") || sha.length() < 20) return false;
        try {
            File cache = new File(getFilesDir(), "index_cache.json");
            if (!cache.isFile()) return false;
            JSONArray arr = new JSONArray(new String(readAll(new FileInputStream(cache)), "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.getJSONObject(i).optString("sha256", "");
                if (!s.isEmpty() && s.equalsIgnoreCase(sha)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 部署时钟组件后：把数字贴图导出到应用私有目录供 Hyper Ice Cream Widget 使用 */
    private void exportWidgetAssets() {
        try {
            String uid = getPackageManager().getPackageInfo(getPackageName(), 0).applicationInfo.uid + "";
            String script = "mkdir -p /data/data/com.zcode.themetool/files/widget_assets/clock_2x4\n"
                    + "cd /data/data/com.zcode.themetool/files/widget_assets/clock_2x4\n"
                    + "unzip -o /data/system/theme/clock_2x4 'src/num/*' >/dev/null 2>&1\n"
                    + "chown -R " + uid + ":" + uid + " /data/data/com.zcode.themetool/files/widget_assets\n"
                    + "chmod -R 755 /data/data/com.zcode.themetool/files/widget_assets\n"
                    + "echo WIDGET-ASSETS-OK\n";
            String r = execSu(script);
            if (r.contains("WIDGET-ASSETS-OK")) {
                HyperWidgetProvider.renderAll(this);
                log("Hyper Ice Cream Widget 素材已就绪。");
            }
        } catch (Throwable t) {
            log("Widget 素材导出失败（Widget 将用字体降级显示）: " + t);
        }
    }

    /** 小组件容器修复：缺失 description.xml 时注入标准 <MIUI-Theme category size> 声明。
     *  出厂参照: clock_classical.mtz size="4:2" / weather_4x1 size="4:1" / notes size="2:2"。
     *  根因: Launcher 依赖 description.xml 的 size 属性判断可缩放尺寸，缺失则降级为固定尺寸。 */
    private File prepareWidgetContainer(String name, String path) {
        try {
            if (!zipfileHasEntries(new File(path))) return null;
            ZipFile zf = new ZipFile(new File(path));
            boolean hasDesc = zf.getEntry("description.xml") != null;
            zf.close();
            if (hasDesc) return null;

            String w = "2", h = "2";
            Matcher m = Pattern.compile("(\\d+)x(\\d+)$").matcher(name);
            if (m.find()) { w = m.group(1); h = m.group(2); }
            String category = "clock";
            String low = name.toLowerCase();
            if (low.startsWith("weather")) category = "weather";
            else if (low.startsWith("notes")) category = "notes";
            else if (low.startsWith("calculator")) category = "calculator";

            String desc = "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>\n"
                    + "<MIUI-Theme category=\"" + category + "\" size=\"" + w + ":" + h + "\">\n"
                    + "\t<version>1</version>\n\t<uiVersion>1</uiVersion>\n"
                    + "\t<title>" + name + "</title>\n</MIUI-Theme>\n";

            File out = new File(getCacheDir(), "widget_" + name + "_" + System.currentTimeMillis());
            ZipInputStream zi = new ZipInputStream(new FileInputStream(path));
            ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(out));
            ZipEntry e;
            byte[] buf = new byte[65536];
            int n;
            while ((e = zi.getNextEntry()) != null) {
                zo.putNextEntry(new ZipEntry(e.getName()));
                while ((n = zi.read(buf)) > 0) zo.write(buf, 0, n);
                zo.closeEntry();
            }
            zi.close();
            zo.putNextEntry(new ZipEntry("description.xml"));
            zo.write(desc.getBytes("UTF-8"));
            zo.closeEntry();
            zo.close();
            log("已为 " + name + " 注入尺寸声明 " + w + "x" + h + "（修复缩放识别）");
            return out;
        } catch (Throwable t) {
            log("小组件容器修复失败（按原样部署）: " + t);
            return null;
        }
    }

    private void doRestartLauncher() {
        String r = execSu("am force-stop com.miui.home");
        log(r.contains("SU-ERROR") ? "重启桌面失败: " + r : "✅ 桌面（Launcher）已重启，重新进入桌面即可。");
    }

    private void doBackup() {
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        String r = execSu("mkdir -p /sdcard/ThemeToolBackup/backup_" + ts +
                "\ncp -a /data/system/theme/. /sdcard/ThemeToolBackup/backup_" + ts + "/" +
                "\nchmod -R 777 /sdcard/ThemeToolBackup/backup_" + ts +
                "\necho BACKUP-DONE");
        log(r);
        log(r.contains("BACKUP-DONE") ? "✅ 已备份到 /sdcard/ThemeToolBackup/backup_" + ts : "备份失败");
    }

    private void doRestore() {
        String r0 = execSu("ls /sdcard/ThemeToolBackup/");
        String latest = null;
        for (String line : r0.split("\n")) {
            String t = line.trim();
            if (t.startsWith("backup_")) {
                if (latest == null || t.compareTo(latest) > 0) latest = t;
            }
        }
        if (latest == null) { log("没有找到任何备份"); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("for f in /sdcard/ThemeToolBackup/").append(latest).append("/*; do\n");
        sb.append("  cp \"$f\" /data/system/theme/$(basename \"$f\")\n");
        sb.append("done\n");
        sb.append("for f in /data/system/theme/*; do chown system_theme:system_theme \"$f\"; done\n");
        sb.append("chmod 600 /data/system/theme/wallpaper 2>/dev/null\n");
        sb.append("echo RESTORE-DONE\n");
        String r = execSu(sb.toString());
        log(r);
        log(r.contains("RESTORE-DONE") ? "✅ 已从 " + latest + " 还原" : "还原未确认");
    }

    // ---------- 社区贡献（仅本地导入主题） ----------
    private void askContributionInfo() {
        android.widget.EditText nameIn = new android.widget.EditText(this);
        nameIn.setHint("署名（留空 = " + CommunityExport.anonId(this) + "）");
        nameIn.setSingleLine(true);
        android.widget.EditText srcIn = new android.widget.EditText(this);
        srcIn.setHint("来源链接（你在哪里获取的主题，可空）");
        srcIn.setSingleLine(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 16, 48, 0);
        box.addView(nameIn);
        box.addView(srcIn);
        new AlertDialog.Builder(this)
                .setTitle("贡献信息")
                .setMessage("收录规范：原作者署名自动取自主题包；请尽量填写主题来源。")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("生成贡献包", (d, w) -> doCommunityExport(
                        nameIn.getText().toString().trim(),
                        srcIn.getText().toString().trim()))
                .show();
    }

    private void doCommunityExport(String contributor, String sourceUrl) {
        try {
            CommunityExport.Result r = CommunityExport.build(this, lastPicked, lastBase, lastMeta,
                    contributor, sourceUrl);
            log("✅ 社区贡献包已导出: " + r.dir);
            new AlertDialog.Builder(this)
                    .setTitle("贡献包已生成")
                    .setMessage("导出目录:\n" + r.dir +
                            "\n\n包含 theme.mtz / meta.json / preview/")
                    .setPositiveButton("分享到GitHub", (d, w) -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(CommunityExport.CONTRIBUTION_URL)));
                        } catch (Throwable t) {
                            log("打开浏览器失败: " + t);
                        }
                    })
                    .setNeutralButton("发送给维护者", (d, w) -> {
                        String metaText = readText(r.metaJson);
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("text/plain");
                        i.putExtra(Intent.EXTRA_SUBJECT, "主题社区贡献: " + lastMeta.name);
                        i.putExtra(Intent.EXTRA_TEXT, metaText +
                                "\n\n请将 " + r.dir.getName() + " 目录内的 theme.mtz 一并提交给维护者。");
                        startActivity(Intent.createChooser(i, "发送贡献信息"));
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (Throwable t) {
            log("社区导出失败: " + t);
        }
    }

    private String readText(File f) {
        try {
            byte[] all = readAll(new FileInputStream(f));
            return new String(all, "UTF-8");
        } catch (Throwable t) {
            return "(读取失败)";
        }
    }

    // ---------- 社区页托管（CommunityFragment 回调） ----------
    private void openCommunity() {
        if (community == null) {
            community = new CommunityFragment(new CommunityFragment.Host() {
                @Override public void log(String s) { MainActivity.this.log(s); }
                @Override public String execSu(String script) { return MainActivity.this.execSu(script); }
                @Override public Activity activity() { return MainActivity.this; }
                @Override public TextView statusView() { return findViewById(R.id.community_status); }
                @Override public android.widget.ProgressBar progressBar() { return findViewById(R.id.community_progress); }
                @Override public TextView phaseView() { return findViewById(R.id.community_phase); }
                @Override public void onThemeReady(File mtz) {
                    runOnUiThread(() -> {
                        // 社区下载主题：登记路径，部署成功后不再询问贡献
                        SharedPreferences sp = getSharedPreferences(COMMUNITY_PREF, MODE_PRIVATE);
                        Set<String> set = new HashSet<>(sp.getStringSet(KEY_COMMUNITY_FILES, new HashSet<>()));
                        set.add(mtz.getAbsolutePath());
                        sp.edit().putStringSet(KEY_COMMUNITY_FILES, set).apply();
                        pendingCommunityFile = mtz.getAbsolutePath();
                        comps.clear();
                        detectComponents(mtz);
                        Toast.makeText(MainActivity.this,
                                "下载完成，已识别组件。请勾选后点「部署到系统主题」", Toast.LENGTH_LONG).show();
                        switchPage(0);
                        pageHome.post(() -> pageHome.smoothScrollTo(0, 0));
                    });
                }
            });
        }
        LinearLayout container = findViewById(R.id.community_container);
        container.removeAllViews();
        container.addView(community.buildView());
        community.refreshAsync();
    }
}
