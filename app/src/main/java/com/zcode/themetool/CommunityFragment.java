package com.zcode.themetool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** 社区主题库（阶段二）：在线拉取 GitHub 仓库索引。
 *  含下载任务管理器：进度条/百分比/速度/阶段提示/sha256 校验/失败重试。
 *  独立模块：不触碰主部署流程，仅通过 Host 回调。 */
public class CommunityFragment {

    public interface Host {
        void log(String s);
        String execSu(String script);
        Activity activity();
        TextView statusView();
        ProgressBar progressBar();
        TextView phaseView();
        void onThemeReady(File mtz);
    }

    private static final String[] INDEX_URLS = {
            "https://raw.githubusercontent.com/jinyuzerothree-collab/HyperOS-Theme-Installer/main/themes/index.json",
            "https://gh-proxy.com/https://raw.githubusercontent.com/jinyuzerothree-collab/HyperOS-Theme-Installer/main/themes/index.json",
    };
    private static final String[] DL_PREFIX_FALLBACK = {"", "https://gh-proxy.com/"};

    private final Host host;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout listBox;
    private volatile boolean downloading = false;

    public CommunityFragment(Host host) {
        this.host = host;
    }

    /** 构建页面视图（嵌入 MainActivity 社区页容器） */
    public View buildView() {
        Activity act = host.activity();
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);

        Button refresh = new Button(act);
        refresh.setText("刷新列表");
        refresh.setOnClickListener(v -> refreshAsync());
        root.addView(refresh);

        listBox = new LinearLayout(act);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);
        return root;
    }

    public void refreshAsync() {
        if (downloading) {
            post(s -> s.setText("下载任务进行中，请稍候…"));
            return;
        }
        post(s -> s.setText("正在拉取索引…"));
        new Thread(() -> {
            String body = null;
            String used = null;
            for (String u : INDEX_URLS) {
                try {
                    String b = httpGet(u);
                    if (b != null && b.trim().startsWith("[")) {
                        body = b;
                        used = u;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
            final String fBody = body;
            final String fUsed = used;
            ui.post(() -> {
                if (fBody == null) {
                    post(s -> s.setText("❌ 索引拉取失败：所有源均不可达。\n请检查网络后点「刷新列表」重试。"));
                    return;
                }
                try {
                    JSONArray arr = new JSONArray(fBody);
                    listBox.removeAllViews();
                    for (int i = 0; i < arr.length(); i++) addEntryCard(arr.getJSONObject(i));
                    post(s -> s.setText("✅ 已加载 " + arr.length() + " 个主题（"
                            + (fUsed.contains("gh-proxy") ? "镜像源" : "直连") + "）"));
                } catch (Throwable t) {
                    post(s -> s.setText("索引解析失败: " + t));
                }
            });
        }).start();
    }

    private void addEntryCard(JSONObject e) {
        Activity act = host.activity();
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 20, 28, 20);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(0x0D000000);
        card.setBackground(bg);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(10);
        card.setLayoutParams(clp);

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row);

        ImageView iv = new ImageView(act);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(120), dp(90));
        ip.rightMargin = dp(14);
        iv.setLayoutParams(ip);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(iv);

        LinearLayout info = new LinearLayout(act);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ip2 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        info.setLayoutParams(ip2);
        row.addView(info);

        String name = e.optString("name", "未知");
        String author = e.optString("author", "未知");
        String contributor = e.optString("contributor", "匿名");
        String version = e.optString("version", "未知");
        String src = e.optString("source_url", "");
        long size = e.optLong("size_bytes", 0);

        TextView t1 = new TextView(act);
        t1.setText(name + "  v" + version);
        t1.setTextSize(16);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(t1);
        TextView t2 = new TextView(act);
        t2.setText("原作者: " + author + "\n贡献者: " + contributor
                + (size > 0 ? "　大小: " + fmt(size) : "")
                + (src.isEmpty() || "own".equals(src) ? "" : "\n来源: " + src));
        t2.setTextSize(12);
        info.addView(t2);

        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(8);
        btns.setLayoutParams(blp);
        card.addView(btns);

        Button get = new Button(act);
        get.setText("下载并部署");
        get.setTextSize(12);
        get.setOnClickListener(v -> confirmDownload(e));
        btns.addView(get);

        Button open = new Button(act);
        open.setText("前往来源");
        open.setTextSize(12);
        if (!src.isEmpty() && !"own".equals(src)) {
            open.setOnClickListener(v -> {
                try {
                    act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(src)));
                } catch (Throwable t) {
                    host.log("打开来源失败: " + t);
                }
            });
        } else {
            open.setEnabled(false);
        }
        btns.addView(open);

        String pv = e.optString("preview_url", "");
        if (!pv.isEmpty()) {
            new Thread(() -> {
                try {
                    Bitmap bm = loadBitmap(pv);
                    if (bm != null) ui.post(() -> iv.setImageBitmap(bm));
                } catch (Throwable ignored) {
                }
            }).start();
        }
        listBox.addView(card);
    }

    private void confirmDownload(JSONObject e) {
        if (downloading) {
            Toast(host.activity(), "已有下载任务进行中");
            return;
        }
        String name = e.optString("name", "theme");
        String url = e.optString("download_url", "");
        if (url.isEmpty()) {
            Toast(host.activity(), name + ": 索引缺少下载链接，请前往来源获取");
            return;
        }
        new AlertDialog.Builder(host.activity())
                .setTitle("获取 " + name)
                .setMessage("将下载主题包并自动校验，完成后进入部署流程。继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("下载", (d, w) -> downloadAsync(e))
                .show();
    }

    private void downloadAsync(JSONObject e) {
        downloading = true;
        String name = e.optString("name", "theme");
        String url = e.optString("download_url", "");
        String sha = e.optString("sha256", "");
        boolean needVerify = !sha.isEmpty() && !"FILL_AFTER_FIRST_UPLOAD".equals(sha);

        ProgressBar pb = host.progressBar();
        TextView phase = host.phaseView();
        ui.post(() -> {
            pb.setVisibility(View.VISIBLE);
            pb.setProgress(0);
            phase.setVisibility(View.VISIBLE);
            phase.setText("【阶段 1/3】下载中…");
        });

        File dst = new File(host.activity().getCacheDir(),
                "community_" + System.currentTimeMillis() + ".mtz");
        final File[] result = {null};
        final String[] err = {null};

        Thread t = new Thread(() -> {
            long start = System.currentTimeMillis();
            for (String prefix : DL_PREFIX_FALLBACK) {
                try {
                    HttpURLConnection c = open(prefix + url);
                    int code = c.getResponseCode();
                    if (code != 200) { c.disconnect(); throw new RuntimeException("HTTP " + code); }
                    long total = c.getContentLength();
                    InputStream in = c.getInputStream();
                    FileOutputStream fo = new FileOutputStream(dst);
                    byte[] buf = new byte[65536];
                    long done = 0;
                    int n;
                    final long ftotal = total;
                    while ((n = in.read(buf)) > 0) {
                        fo.write(buf, 0, n);
                        done += n;
                        long elapsed = (System.currentTimeMillis() - start) / 1000 + 1;
                        final int pct = total > 0 ? (int) (done * 100 / total) : -1;
                        final String speed = fmtSize(done / elapsed) + "/s";
                        final long fdone = done;
                        ui.post(() -> {
                            if (pct >= 0) host.progressBar().setProgress(pct);
                            post(s -> s.setText("【阶段 1/3】下载中 "
                                    + (pct >= 0 ? pct + "%" : fmtSize(fdone))
                                    + "　" + speed + (prefix.isEmpty() ? "　(直连)" : "　(镜像)")));
                        });
                    }
                    in.close();
                    fo.close();
                    c.disconnect();
                    result[0] = dst;
                    break;
                } catch (Throwable ex) {
                    err[0] = ex.toString();
                }
            }
            // 阶段 2: 校验
            if (result[0] != null && needVerify) {
                ui.post(() -> {
                    host.progressBar().setIndeterminate(true);
                    post(s -> s.setText("【阶段 2/3】SHA256 校验中…"));
                });
                String actual = MetadataParser.sha256(dst);
                if (!actual.equalsIgnoreCase(sha)) {
                    dst.delete();
                    result[0] = null;
                    err[0] = "SHA256 不匹配（期望 " + sha.substring(0, 12) + "… 实际 " + actual.substring(0, 12) + "…）";
                }
            }
            final boolean ok = result[0] != null;
            ui.post(() -> {
                downloading = false;
                ProgressBar p = host.progressBar();
                p.setIndeterminate(false);
                if (ok) {
                    p.setProgress(100);
                    post(s -> s.setText("【阶段 3/3】✅ 完成，正在进入部署…"));
                    host.onThemeReady(result[0]);
                } else {
                    p.setVisibility(View.GONE);
                    post(s -> s.setText("❌ " + name + " 获取失败: " + err[0] + "\n可点「刷新列表」重试或前往来源手动下载。"));
                }
            });
        });
        t.start();
    }

    // ---------- 网络与工具 ----------
    private static HttpURLConnection open(String s) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(s).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    private static String httpGet(String s) throws Exception {
        HttpURLConnection c = open(s);
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            throw new RuntimeException("HTTP " + code);
        }
        InputStream in = c.getInputStream();
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        c.disconnect();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Bitmap loadBitmap(String s) throws Exception {
        HttpURLConnection c = open(s);
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            throw new RuntimeException("HTTP " + code);
        }
        Bitmap bm = BitmapFactory.decodeStream(c.getInputStream());
        c.disconnect();
        return bm;
    }

    private interface Post { void run(TextView s); }

    private void post(Post p) {
        ui.post(() -> {
            TextView s = host.statusView();
            if (s != null) p.run(s);
        });
    }

    private void Toast(Activity a, String s) {
        android.widget.Toast.makeText(a, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * host.activity().getResources().getDisplayMetrics().density);
    }

    private static String fmtSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.2fGB", bytes / 1024.0 / 1024 / 1024);
        if (bytes >= 1024L * 1024) return String.format(java.util.Locale.US, "%.2fMB", bytes / 1024.0 / 1024);
        if (bytes >= 1024) return String.format(java.util.Locale.US, "%.1fKB", bytes / 1024.0);
        return bytes + "B";
    }

    private static String fmt(long bytes) {
        return fmtSize(bytes);
    }
}
