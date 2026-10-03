package com.zcode.themetool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
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
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 41;
    // 主题小组件命名模式（clock_2x4 / weather_4x1 / notes_xxx / calculator_xxx / gadget*，样本 Neo.mtz 证实）
    private static final java.util.regex.Pattern WIDGET_PATTERN =
            java.util.regex.Pattern.compile("^(clock|weather|notes|calculator|gadget)([_\\-].+)?$",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
    private TextView log;
    private LinearLayout compsBox;
    private File unpackedDir;
    private final List<Object[]> comps = new ArrayList<>(); // {name, label, path}
    private boolean hasGadgets = false;

    private File browserDir = new File("/sdcard/Download");
    private boolean showAll = false;

    private void log(String s) {
        runOnUiThread(() -> log.setText(log.getText() + s + "\n"));
    }

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
        return android.os.Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        log = findViewById(R.id.log);
        compsBox = findViewById(R.id.comps);
        unpackedDir = new File(getCacheDir(), "unpack");

        Button pick = findViewById(R.id.btn_pick);
        Button deploy = findViewById(R.id.btn_deploy);
        Button backup = findViewById(R.id.btn_backup);
        Button restore = findViewById(R.id.btn_restore);
        Button restart = findViewById(R.id.btn_restart);
        Button launcher = findViewById(R.id.btn_launcher);
        Button perm = findViewById(R.id.btn_perm);

        String id = execSu("id");
        log("root检查: " + (id.contains("uid=0") ? "OK (已授权)" : "未授权，部署时请允许 su 请求"));
        if (!storageOk()) {
            perm.setVisibility(Button.VISIBLE);
            log("提示：请先点「授权所有文件访问」，否则浏览器看不到文件。");
        }
        perm.setOnClickListener(v -> {
            try {
                startActivity(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                        Uri.parse("package:com.zcode.themetool")));
            } catch (Throwable t) {
                startActivity(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"));
            }
        });

        pick.setOnClickListener(v -> showBrowser());
        deploy.setOnClickListener(v -> doDeploy());
        backup.setOnClickListener(v -> doBackup());
        restore.setOnClickListener(v -> doRestore());
        restart.setOnClickListener(v -> {
            execSu("am crash com.android.systemui");
            log("SystemUI 重启指令已发送。");
        });
        launcher.setOnClickListener(v -> doRestartLauncher());
    }

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
                        addComp("fonts", f.getAbsolutePath());
                        renderComps();
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

        // 主题小组件：顶层 widget 命名组件（clock_2x4 等，样本 Neo.mtz 证实）+ gadgets/ 目录（出厂风格）
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
            // gadgets/ 子目录组件：先建父目录（/data/system/theme/gadgets/）
            if (name.contains("/")) {
                String parent = name.substring(0, name.lastIndexOf('/'));
                sb.append("mkdir -p /data/system/theme/").append(parent).append('\n');
                deployedGadgets = true;
            }
            if (WIDGET_PATTERN.matcher(name).matches()) deployedGadgets = true;
            sb.append("cp '").append(path).append("' /data/system/theme/").append(name).append('\n');
            sb.append("chown system_theme:system_theme /data/system/theme/").append(name).append('\n');
            sb.append("chmod ").append(name.equals("wallpaper") ? "600" : "755")
              .append(" /data/system/theme/").append(name).append('\n');
        }
        sb.append("echo DEPLOY-DONE\n");
        String r = execSu(sb.toString());
        if (fontsPath != null) {
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
            if (fontsPath != null) log("字体为实验性部署，建议重启设备后查看效果。");
            if (deployedGadgets) {
                log("主题小组件已部署。请前往：\n  桌面长按 → 添加小组件 → 主题组件\n查看已安装内容；也可点「重启桌面」后查看。");
            }
            log("壁纸如未变化请重启设备；也可点「重启系统界面」。");
        } else {
            log("部署未确认，检查上面输出（多半是 su 未授权）。");
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
        String dir = "/sdcard/ThemeToolBackup/" + latest;
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
}
