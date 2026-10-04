package com.hyper.icecream;

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
import android.view.GestureDetector;
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
                        Uri.parse("package:com.hyper.icecream")));
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
        findViewById(R.id.btn_pin_widget).setOnClickListener(v -> pinClockWidget());
        buildToolExtras();

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
        initSwipeDetector();
        applyL10n();
        applyImmersive();
        applyGlobalBackground();
        switchPage(0);
        attachGlassDock(); // 真·液态玻璃：独立窗口 + blurBehind（API31+），失败则用内嵌 Dock
        autoExportWidgetAssets(); // 修复"部署在先、导出逻辑在后"导致素材缺失：打开应用即补导出
    }

    /** 全局渐变背景：关于页同款渐变铺满全部页面 */
    private void applyGlobalBackground() {
        View root = findViewById(R.id.root_container);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                isNight()
                        ? new int[]{0xFF1B2438, 0xFF241B33, 0xFF0F141F}
                        : new int[]{0xFFFFD3E2, 0xFFE3D4FF, 0xFFFFEDF0});
        root.setBackground(bg);
    }

    /** 旧组件白名单管理：列出全部小组件提供者（AppWidgetManager 通道，与 HyperStack 同源） */
    private void showWhitelistDialog() {
        java.util.LinkedHashMap<String, String> pkgs = new java.util.LinkedHashMap<>();
        android.appwidget.AppWidgetManager awm = android.appwidget.AppWidgetManager.getInstance(this);
        for (android.appwidget.AppWidgetProviderInfo info : awm.getInstalledProviders()) {
            if (info.provider == null) continue;
            String p = info.provider.getPackageName();
            if (p.equals(getPackageName())) continue; // 自身已预置
            CharSequence label;
            try {
                label = getPackageManager().getApplicationLabel(
                        getPackageManager().getApplicationInfo(p, 0));
            } catch (Throwable ignored) {
                label = p;
            }
            pkgs.put(p, label + " (" + p + ")");
        }
        // root dumpsys 补全（AppWidgetManager 可能过滤部分提供者）
        String dump = execSu("dumpsys appwidget 2>/dev/null");
        if (dump != null && !dump.isEmpty()) {
            java.util.regex.Matcher dm = java.util.regex.Pattern.compile(
                    "provider=ComponentInfo\\{([^ }]+) ").matcher(dump);
            while (dm.find()) {
                String dp2 = dm.group(1);
                if (!dp2.equals(getPackageName()) && !pkgs.containsKey(dp2)) {
                    CharSequence label;
                    try {
                        label = getPackageManager().getApplicationLabel(
                                getPackageManager().getApplicationInfo(dp2, 0));
                    } catch (Throwable ignored) { label = dp2; }
                    pkgs.put(dp2, label + " (" + dp2 + ")");
                }
            }
        }
        // 当前白名单
        java.util.Set<String> current = new java.util.HashSet<>();
        String cur = execSu("cat /data/system/hypericecream_widget_whitelist.txt 2>/dev/null");
        if (cur != null) {
            for (String line : cur.split("\n")) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("enabled=")
                        && !line.startsWith("#") && line.contains(".")) current.add(line);
            }
        }
        String[] names = pkgs.values().toArray(new String[0]);
        final String[] keys = pkgs.keySet().toArray(new String[0]);
        boolean[] checks = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) checks[i] = current.contains(keys[i]);

        new AlertDialog.Builder(this)
                .setTitle("选择要恢复旧组件的应用")
                .setMultiChoiceItems(names, checks, (d, w, c) -> checks[w] = c)
                .setPositiveButton("保存白名单", (d, w) -> {
                    StringBuilder sb = new StringBuilder("enabled=1\n");
                    int n = 0;
                    for (int i = 0; i < keys.length; i++) {
                        if (checks[i]) {
                            sb.append(keys[i]).append('\n');
                            n++;
                        }
                    }
                    sb.append(getPackageName()).append('\n');
                    String body = sb.toString().replace("\n", "\\n").replace("'", "'\\''");
                    String r = execSu("echo -e '" + body + "' > "
                            + "/data/system/hypericecream_widget_whitelist.txt && "
                            + "chmod 644 /data/system/hypericecream_widget_whitelist.txt && echo WL-SAVED");
                    log(r.contains("WL-SAVED")
                            ? "✅ 白名单已保存（" + n + " 个应用 + 自身）。重启平板后旧组件出现在小部件列表。"
                            : "白名单保存失败: " + r);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void buildToolExtras() {
        LinearLayout tools = (LinearLayout) ((ScrollView) findViewById(R.id.page_tools)).getChildAt(0);

        // ===== 权限状态卡（KSU 主页风格：两个大方块 + 检查行） =====
        TextView pt = new TextView(this);
        pt.setText(L10n.t(this, "perm_section"));
        pt.setTextSize(14);
        pt.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        pt.setPadding(0, dp(16), 0, dp(6));
        tools.addView(pt);

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams clp0 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp0.topMargin = dp(4);
        cards.setLayoutParams(clp0);

        rootCard = buildPermCard(cards, true);
        lspCard = buildPermCard(cards, false);
        tools.addView(cards, 1); // 紧跟「工具箱」标题

        Button checkPerm = new Button(this);
        checkPerm.setText(L10n.t(this, "perm_check"));
        checkPerm.setOnClickListener(v -> checkPerms(true));
        tools.addView(checkPerm, 2);
        permDetail = new TextView(this);
        permDetail.setTextSize(11);
        permDetail.setPadding(dp(4), 0, 0, 0);
        tools.addView(permDetail, 3);
        checkPerms(false); // 静默首查

        // ===== Dock 常驻图标样式（双方案：隐藏 / 圆角背景；部署主题时也会询问） =====
        TextView t = new TextView(this);
        t.setText(L10n.t(this, "dock_section"));
        t.setTextSize(14);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        t.setPadding(0, dp(16), 0, dp(6));
        tools.addView(t);
        final String[] opts = {L10n.t(this, "dock_none"), L10n.t(this, "dock_hidden"), L10n.t(this, "dock_round")};
        final android.widget.RadioGroup rg = new android.widget.RadioGroup(this);
        int cur = getSharedPreferences(COMMUNITY_PREF, MODE_PRIVATE).getInt("dock_style", 0);
        for (int i = 0; i < opts.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(opts[i]);
            rb.setId(100 + i);
            rb.setChecked(cur == i);
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((g, id) -> {
            int style = id - 100;
            getSharedPreferences(COMMUNITY_PREF, MODE_PRIVATE).edit().putInt("dock_style", style).apply();
            applyDockStyleToConf(style);
            log("Dock 样式 = " + opts[style] + "（重启桌面生效；实验性，桌面支持性待验证）");
        });
        tools.addView(rg);
        TextView dockNote = new TextView(this);
        dockNote.setText(L10n.t(this, "dock_note"));
        dockNote.setTextSize(11);
        tools.addView(dockNote);

        // ===== 旧组件白名单管理 =====
        TextView t2 = new TextView(this);
        t2.setText("旧版小组件白名单（恢复到新桌面）");
        t2.setTextSize(14);
        t2.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        t2.setPadding(0, dp(16), 0, dp(6));
        tools.addView(t2);
        Button wl = new Button(this);
        wl.setText("选择要恢复的组件应用");
        wl.setOnClickListener(v -> showWhitelistDialog());
        tools.addView(wl);
        TextView wlNote = new TextView(this);
        wlNote.setText("勾选后写入白名单并保存；模块作用域需含\ncom.miui.home + android(系统框架)，重启生效。");
        wlNote.setTextSize(11);
        tools.addView(wlNote);
    }

    private LinearLayout rootCard, lspCard;
    private TextView permDetail;

    /** LSPosed 风格状态卡：浅绿/灰底 + 大粗标题 + 大圆勾图标 */
    private LinearLayout buildPermCard(LinearLayout parent, boolean isRoot) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(24));
        bg.setColor(0xFFE8F5E9); // LSPosed 式浅绿
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        if (!isRoot) lp.leftMargin = dp(10);
        card.setLayoutParams(lp);

        // 右侧大圆勾（占卡片右半，与 LSPosed 一致）
        TextView checkIcon = new TextView(this);
        checkIcon.setTextSize(40);
        checkIcon.setTypeface(Typeface.DEFAULT_BOLD);
        checkIcon.setTextColor(0xFF4CAF50);
        checkIcon.setGravity(android.view.Gravity.CENTER);
        checkIcon.setText("✓");
        checkIcon.setTag("check_icon");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(52), dp(52));
        clp.gravity = android.view.Gravity.CENTER_VERTICAL
                | android.view.Gravity.END;
        clp.rightMargin = dp(4);
        checkIcon.setLayoutParams(clp);
        card.addView(checkIcon);

        TextView title = new TextView(this);
        title.setText(L10n.t(this, isRoot ? "root_state" : "lsp_state"));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF111111);
        title.setTag("title");
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = dp(6);
        title.setLayoutParams(tlp);
        card.addView(title, 0); // 标题在最前

        TextView detail = new TextView(this);
        detail.setTextSize(14);
        detail.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        detail.setTextColor(0xFF555555);
        detail.setTag("detail");
        card.addView(detail, 1); // 明细第二行

        parent.addView(card);
        return card;
    }

    private void setCardState(LinearLayout card, boolean ok, String detail) {
        if (card == null) return;
        // 背景色：绿=OK，灰=未授权
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(24));
        bg.setColor(ok ? 0xFFE8F5E9 : 0xFFF0F0F0);
        card.setBackground(bg);
        // 更新勾/叉图标
        for (int i = 0; i < card.getChildCount(); i++) {
            View ch = card.getChildAt(i);
            if (ch.getTag() != null && "check_icon".equals(ch.getTag())) {
                ((TextView) ch).setText(ok ? "✓" : "✕");
                ((TextView) ch).setTextColor(ok ? 0xFF4CAF50 : 0xFFBDBDBD);
            } else if (ch.getTag() != null && "detail".equals(ch.getTag())) {
                ((TextView) ch).setText(detail);
            } else if (ch.getTag() != null && "title".equals(ch.getTag())) {
                ((TextView) ch).setText(L10n.t(this, ok
                        ? (card == rootCard ? "root_ok" : "lsp_ok")
                        : (card == rootCard ? "root_no" : "lsp_no")));
            }
        }
    }

    /** 检查 Root 与 LSP 授权（su 可用性 + LSPosed 目录 + 本模块注册状态） */
    private void checkPerms(final boolean verbose) {
        new Thread(() -> {
            String id = execSu("id");
            final boolean rootOk = id.contains("uid=0");
            String lsp = rootOk ? execSu(
                    "test -d /data/adb/lspd && echo LSP-DIR; "
                    + "grep -c com.hyper.icecream /data/adb/lspd/config/modules_config.db 2>/dev/null") : "";
            final boolean lspInstalled = lsp.contains("LSP-DIR");
            int cnt = -1;
            try {
                for (String ln : lsp.split("\n")) {
                    ln = ln.trim();
                    if (ln.matches("\\d+")) { cnt = Integer.parseInt(ln); break; }
                }
            } catch (Throwable ignored) {
            }
            final boolean lspOk = lspInstalled && cnt > 0;
            final String rootDetail = rootOk ? "su -c id → uid=0" : "su 未授权";
            final String lspDetail = !lspInstalled ? "未检测到 /data/adb/lspd"
                    : (cnt > 0 ? "本模块已注册（" + cnt + " 条）" : "本模块未在 LSPosed 注册");
            runOnUiThread(() -> {
                setCardState(rootCard, rootOk, rootDetail);
                setCardState(lspCard, lspOk, lspDetail);
                if (permDetail != null) {
                    permDetail.setText("Root: " + rootDetail + "　|　LSPosed: " + lspDetail);
                }
                if (verbose) log("权限检查 → Root " + (rootOk ? "✓" : "✕")
                        + "　LSPosed " + (lspOk ? "✓" : "✕") + "（" + lspDetail + "）");
            });
        }).start();
    }

    /** Dock 样式落盘：0=不改动 1=隐藏三常驻图标 2=圆角背景（后者依赖桌面支持性，实验） */
    private void applyDockStyleToConf(int style) {
        String DOCK = "/data/system/hypericecream_dock.conf";
        String v = style == 1 ? "1" : "0";
        execSu("sed -i 's/hide_phone=[01]/hide_phone=" + v + "/;s/hide_xiaoai=[01]/hide_xiaoai=" + v
                + "/;s/hide_search=[01]/hide_search=" + v + "/' " + DOCK
                + " || printf 'hide_phone=%s\\nhide_xiaoai=%s\\nhide_search=%s\\n' " + v + " " + v + " " + v + " > " + DOCK);
        // 样式 2（圆角背景）需要桌面侧支持：记录选择，待 launcher prefs 调研落地后生效
        getSharedPreferences(COMMUNITY_PREF, MODE_PRIVATE).edit().putInt("dock_style", style).apply();
    }

    /** 部署主题时同时询问 Dock 样式（用户要求：加载主题的时候同时询问） */
    private void askDockStyleThen(final Runnable next) {
        final String[] opts = {L10n.t(this, "dock_none"), L10n.t(this, "dock_hidden"), L10n.t(this, "dock_round")};
        int cur = getSharedPreferences(COMMUNITY_PREF, MODE_PRIVATE).getInt("dock_style", 0);
        new AlertDialog.Builder(this)
                .setTitle(L10n.t(this, "dock_ask_title"))
                .setMessage(L10n.t(this, "dock_ask_msg"))
                .setSingleChoiceItems(opts, cur, null)
                .setPositiveButton("确定", (d, w) -> {
                    int sel = ((AlertDialog) d).getListView().getCheckedItemPosition();
                    if (sel < 0) sel = cur;
                    getSharedPreferences(COMMUNITY_PREF, MODE_PRIVATE).edit().putInt("dock_style", sel).apply();
                    applyDockStyleToConf(sel);
                    log("本次部署采用 Dock 样式: " + opts[sel]);
                    next.run();
                })
                .setNegativeButton("跳过", (d, w) -> next.run())
                .show();
    }


    private String dockConfGet(String key) {
        String r = execSu("grep -E '^" + key + "=' /data/system/hypericecream_dock.conf");
        return r.contains("=1") ? "1" : "0";
    }

    /** 沉浸式：渐变背景顶到屏幕最上沿（透明状态栏），消除"白色刘海块" */
    private void applyImmersive() {
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        int sb = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int top = sb > 0 ? getResources().getDimensionPixelSize(sb) : (int) dp(24);
        // 各页顶部补状态栏高度
        ((LinearLayout) pageHome.getChildAt(0)).setPadding(dp(20), top + dp(8), dp(20), dp(20));
        ((LinearLayout) pageCommunity.getChildAt(0)).setPadding(dp(20), top + dp(8), dp(20), dp(8));
        ((LinearLayout) pageTools.getChildAt(0)).setPadding(dp(20), top + dp(8), dp(20), dp(20));
        // 关于页自身 padding 在 AboutPage 内部（64dp 顶部已够）
    }

    /** 一键把 Hyper 时钟小组件钉到桌面（免去在系统选择器里翻找） */
    private void pinClockWidget() {
        try {
            HyperWidgetProvider.pinClockWidget(this);
            log("✅ 已弹出系统钉选确认框，确认后小组件直接上桌面。");
        } catch (Throwable t) {
            log("钉选失败: " + t + "，请到 桌面长按→小部件 手动添加。");
        }
    }

    /** 若系统主题里已有时钟组件而素材未导出，静默补导出 */
    private void autoExportWidgetAssets() {
        new Thread(() -> {
            String r = execSu("test -f /data/system/theme/clock_2x4 && echo HAVE-CLOCK");
            if (r.contains("HAVE-CLOCK")) {
                runOnUiThread(this::exportWidgetAssets);
            }
        }).start();
    }

    // ---------- 三语（简/繁/英） ----------
    private void applyL10n() {
        L10n.init(this);
        ((TextView) findViewById(R.id.home_sub)).setText(L10n.t(this, "sub"));
        ((Button) findViewById(R.id.btn_pick)).setText(L10n.t(this, "pick"));
        ((Button) findViewById(R.id.btn_deploy)).setText(L10n.t(this, "deploy"));
        ((Button) findViewById(R.id.btn_backup)).setText(L10n.t(this, "backup"));
        ((Button) findViewById(R.id.btn_restore)).setText(L10n.t(this, "restore"));
        ((Button) findViewById(R.id.btn_restart)).setText(L10n.t(this, "restart_sysui"));
        ((Button) findViewById(R.id.btn_launcher)).setText(L10n.t(this, "restart_home"));
        ((Button) findViewById(R.id.btn_perm)).setText(L10n.t(this, "perm"));
        tabHome.setText(L10n.t(this, "tab_home"));
        tabCommunity.setText(L10n.t(this, "tab_community"));
        tabTools.setText(L10n.t(this, "tab_tools"));
        tabAbout.setText(L10n.t(this, "tab_about"));
        for (TextView t : glassTabs) {
            int i = glassTabs.indexOf(t);
            t.setText(L10n.t(this, i == 0 ? "tab_home" : i == 1 ? "tab_community" : i == 2 ? "tab_tools" : "tab_about"));
        }
    }

    // ---------- 左右滑动切页 ----------
    private int currentPage = 0;
    private GestureDetector swipeDetector; // 必须在 onCreate 中初始化（构造期 Context 未挂载会 NPE）

    private void initSwipeDetector() {
        swipeDetector = new GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float vx, float vy) {
                        if (e1 == null || e2 == null) return false;
                        float dx = e2.getX() - e1.getX();
                        float dy = e2.getY() - e1.getY();
                        if (Math.abs(dx) > dp(80) && Math.abs(dx) > Math.abs(dy) * 2) {
                            int next = currentPage + (dx < 0 ? 1 : -1);
                            if (next >= 0 && next <= 3 && next != currentPage) {
                                switchPage(next);
                                return true;
                            }
                        }
                        return false;
                    }
                });
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (swipeDetector != null) swipeDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
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
                pill.setColor(isNight() ? 0x40FFFFFF : 0x4AFFFFFF);
                pill.setStroke((int) dp(1), isNight() ? 0x30FFFFFF : 0x50FFFFFF);
                t.setBackground(pill);
                t.setTextColor(isNight() ? 0xFFFFFFFF : 0xFF222222);
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
            // 液态玻璃同材质覆盖层：比 Dock 底更亮/更暗一层，非实色
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(dp(24));
            pill.setColor(isNight() ? 0x40FFFFFF : 0x4AFFFFFF);
            pill.setStroke((int) dp(1), isNight() ? 0x30FFFFFF : 0x50FFFFFF);
            t.setBackground(pill);
            t.setTextColor(isNight() ? 0xFFFFFFFF : 0xFF222222);
            t.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            t.setBackground(null);
            t.setTextColor(isNight() ? 0xB3FFFFFF : 0xB3222222);
            t.setTypeface(Typeface.DEFAULT);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void switchPage(int idx) {
        if (idx == currentPage && findViewById(idx == 0 ? R.id.page_home
                : idx == 1 ? R.id.page_community
                : idx == 2 ? R.id.page_tools : R.id.page_about).getVisibility() == View.VISIBLE) {
            return;
        }
        if (idx == 1) openCommunity(); // 懒构建 + 每次进入刷新
        if (idx == 3 && aboutBuilt == false) {
            ((LinearLayout) findViewById(R.id.about_container)).addView(AboutPage.build(this));
            aboutBuilt = true;
        }
        int dir = idx > currentPage ? 1 : -1;
        View oldPage = currentPage == 0 ? pageHome
                : currentPage == 1 ? pageCommunity
                : currentPage == 2 ? pageTools : findViewById(R.id.page_about);
        final View target = idx == 0 ? pageHome
                : idx == 1 ? pageCommunity
                : idx == 2 ? pageTools : findViewById(R.id.page_about);
        currentPage = idx;
        styleTab(tabHome, idx == 0);
        styleTab(tabCommunity, idx == 1);
        styleTab(tabTools, idx == 2);
        styleTab(tabAbout, idx == 3);
        styleGlassTabs(idx);
        // 丝滑滑动过渡：旧页滑出淡去，新页滑入
        float slide = 60 * dir * getResources().getDisplayMetrics().density;
        final View fOld = oldPage;
        fOld.animate().translationX(-slide).alpha(0f).setDuration(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    fOld.setVisibility(View.GONE);
                    fOld.setTranslationX(0);
                    fOld.setAlpha(1f);
                }).start();
        target.setVisibility(View.VISIBLE);
        target.setAlpha(0f);
        target.setTranslationX(slide);
        target.animate().translationX(0f).alpha(1f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        // Dock 弹性反馈
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
        doDeployInner();
    }

    private void doDeployInner() {
        List<Object[]> selected = new ArrayList<>();
        for (int i = 0; i < compsBox.getChildCount(); i++) {
            Object child = compsBox.getChildAt(i);
            if (child instanceof CheckBox) {
                CheckBox cb = (CheckBox) child;
                if (cb.isChecked()) selected.add((Object[]) cb.getTag());
            }
        }
        if (selected.isEmpty()) { log("未勾选任何组件"); return; }

        // 小组件：直接用组件名的内置比例注入 description.xml（不弹选择器——Launcher 忽略该声明但保留兼容性）
        final List<Object[]> sel = new ArrayList<>(selected);
        final java.util.HashMap<String, int[]> widgetSizes = new java.util.HashMap<>();
        final List<Object[]> widgets = new ArrayList<>();
        for (Object[] c : sel) {
            String n = (String) c[0];
            if (WIDGET_PATTERN.matcher(n).matches() || n.startsWith("gadgets/")) widgets.add(c);
        }
        if (widgets.isEmpty()) {
            runDeploy(sel, widgetSizes);
            return;
        }
        askWidgetSize(widgets, 0, widgetSizes, sel);
    }

    private void askWidgetSize(final List<Object[]> widgets, final int idx,
                               final java.util.HashMap<String, int[]> sizes, final List<Object[]> sel) {
        if (idx >= widgets.size()) {
            runDeploy(sel, sizes);
            return;
        }
        final Object[] c = widgets.get(idx);
        String name = (String) c[0];
        String shortName = name.startsWith("gadgets/") ? name.substring(8) : name;
        Matcher dm = Pattern.compile("(\\d+)x(\\d+)$").matcher(shortName);
        int defW = dm.find() ? Integer.parseInt(dm.group(1)) : 2;
        int defH2 = dm.matches() ? Integer.parseInt(dm.group(2)) : 2;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(android.view.Gravity.CENTER);
        android.widget.EditText wIn = new android.widget.EditText(this);
        wIn.setText(String.valueOf(defW));
        wIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        wIn.setGravity(android.view.Gravity.CENTER);
        android.widget.EditText hIn = new android.widget.EditText(this);
        hIn.setText(String.valueOf(defH2));
        hIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        hIn.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ep.setMargins(dp(12), 0, dp(12), 0);
        wIn.setLayoutParams(ep);
        hIn.setLayoutParams(ep);
        box.addView(wIn);
        box.addView(new TextView(this) {{ setText("×"); }});
        box.addView(hIn);

        new AlertDialog.Builder(this)
                .setTitle("小组件尺寸：" + shortName)
                .setMessage("选择宽 × 高（桌面格数，1–6）。\n将按所选尺寸注入组件声明。")
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("默认", (d, w) -> {
                    sizes.put(name, new int[]{defW, defH2});
                    askWidgetSize(widgets, idx + 1, sizes, sel);
                })
                .setPositiveButton("确定", (d, w) -> {
                    try {
                        int ww = Math.max(1, Math.min(6, Integer.parseInt(wIn.getText().toString())));
                        int hh = Math.max(1, Math.min(6, Integer.parseInt(hIn.getText().toString())));
                        sizes.put(name, new int[]{ww, hh});
                    } catch (Throwable t) {
                        sizes.put(name, new int[]{defW, defH2});
                    }
                    askWidgetSize(widgets, idx + 1, sizes, sel);
                })
                .show();
    }

    private void runDeploy(List<Object[]> selected, java.util.HashMap<String, int[]> widgetSizes) {

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
            // 小组件：注入标准 description.xml（size 用用户选择的宽×高）
            if (WIDGET_PATTERN.matcher(name).matches() && !"gadgets".equals(name)) {
                int[] wh = widgetSizes.get(name);
                File fixed = prepareWidgetContainer(name, path,
                        wh != null ? String.valueOf(wh[0]) : null,
                        wh != null ? String.valueOf(wh[1]) : null);
                if (fixed != null) path = fixed.getAbsolutePath();
                deployedGadgets = true;
            } else if (name.startsWith("gadgets/")) {
                int[] wh = widgetSizes.get(name);
                if (wh != null) {
                    File fixed = prepareWidgetContainer(name, path,
                            String.valueOf(wh[0]), String.valueOf(wh[1]));
                    if (fixed != null) path = fixed.getAbsolutePath();
                }
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

    /** 部署时钟组件后：把数字贴图与 manifest（含元素间距比例）导出到应用私有目录供 Hyper Widget 使用 */
    private void exportWidgetAssets() {
        try {
            String uid = getPackageManager().getPackageInfo(getPackageName(), 0).applicationInfo.uid + "";
            String script = "mkdir -p /data/data/com.hyper.icecream/files/widget_assets/clock_2x4\n"
                    + "cd /data/data/com.hyper.icecream/files/widget_assets/clock_2x4\n"
                    + "unzip -o /data/system/theme/clock_2x4 'src/num/*' 'manifest.xml' >/dev/null 2>&1\n"
                    + "chown -R " + uid + ":" + uid + " /data/data/com.hyper.icecream/files/widget_assets\n"
                    + "chmod -R 755 /data/data/com.hyper.icecream/files/widget_assets\n"
                    + "echo WIDGET-ASSETS-OK\n";
            String r = execSu(script);
            if (r.contains("WIDGET-ASSETS-OK")) {
                HyperWidgetProvider.renderAll(this);
                log("Hyper Ice Cream Widget 素材（贴图+布局比例）已就绪：桌面添加后可拉至任意尺寸，按时钟原始比例复现。");
            }
        } catch (Throwable t) {
            log("Widget 素材导出失败（Widget 将用字体降级显示）: " + t);
        }
    }

    /** 小组件容器修复：缺失 description.xml 时注入标准 <MIUI-Theme category size> 声明。
     *  出厂参照: clock_classical.mtz size="4:2" / weather_4x1 size="4:1" / notes size="2:2"。
     *  根因: Launcher 依赖 description.xml 的 size 属性判断组件尺寸；缺失则降级为固定 2×1。
     *  用户可在部署前自选宽×高（wIn/hIn），按组件名原始比例的倍数注入。 */
    private File prepareWidgetContainer(String name, String path, String wOverride, String hOverride) {
        try {
            if (!zipfileHasEntries(new File(path))) return null;
            ZipFile zf = new ZipFile(new File(path));
            boolean hasDesc = zf.getEntry("description.xml") != null;
            zf.close();
            if (hasDesc) return null;

            String w = wOverride != null && !wOverride.isEmpty() ? wOverride : "2";
            String h = hOverride != null && !hOverride.isEmpty() ? hOverride : "2";
            if (wOverride == null || hOverride == null ||
                    (wOverride.isEmpty() || hOverride.isEmpty())) {
                Matcher m = Pattern.compile("(\\d+)x(\\d+)$").matcher(name);
                if (m.find()) { w = m.group(1); h = m.group(2); }
            }
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
