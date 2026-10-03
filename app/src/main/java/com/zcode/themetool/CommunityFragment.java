package com.zcode.themetool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

/** 社区主题库页面（阶段二）：在线拉取 GitHub 仓库索引，浏览/下载部署/前往来源。
 *  独立模块：不触碰 MainActivity 的部署主流程，仅复用其 root 部署脚本模式。 */
public class CommunityFragment {

    public interface Host {
        void log(String s);
        String execSu(String script);
        Activity activity();
        void onDeployCommunityTheme(File mtz);
    }

    // 索引候选源：raw 直连 + gh-proxy 镜像回退（国内无代理场景）
    private static final String[] INDEX_URLS = {
            "https://raw.githubusercontent.com/jinyuzerothree-collab/HyperOS-Theme-Installer/main/themes/index.json",
            "https://gh-proxy.com/https://raw.githubusercontent.com/jinyuzerothree-collab/HyperOS-Theme-Installer/main/themes/index.json",
    };
    private static final String[] DL_PREFIX_FALLBACK = {
            "",
            "https://gh-proxy.com/",
    };

    private final Host host;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout listBox;
    private TextView status;
    private List<JSONObject> entries = new ArrayList<>();

    public CommunityFragment(Host host) {
        this.host = host;
    }

    /** 构建页面视图（由 MainActivity 塞进对话框/容器） */
    public View buildView() {
        Activity act = host.activity();
        ScrollView scroll = new ScrollView(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        scroll.addView(root);

        TextView title = new TextView(act);
        title.setText("社区主题库（GitHub 在线索引）");
        title.setTextSize(16);
        root.addView(title);

        status = new TextView(act);
        status.setText("下拉刷新：点下方「刷新列表」。\n声明：所有主题版权归原作者所有；\n「来源」为贡献者自述的获取渠道。");
        status.setTextSize(12);
        root.addView(status);

        Button refresh = new Button(act);
        refresh.setText("刷新列表");
        refresh.setOnClickListener(v -> refreshAsync());
        root.addView(refresh);

        listBox = new LinearLayout(act);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);
        return scroll;
    }

    public void refreshAsync() {
        status.setText("正在拉取索引…");
        new Thread(() -> {
            String body = null;
            String used = null;
            for (String u : INDEX_URLS) {
                try {
                    body = httpGet(u);
                    if (body != null && body.trim().startsWith("[")) {
                        used = u;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
            final String err = body == null ? "所有索引源均不可达（检查网络或稍后再试）" : null;
            final String src = used;
            final String data = body;
            ui.post(() -> {
                if (err != null) {
                    status.setText(err);
                    return;
                }
                try {
                    JSONArray arr = new JSONArray(data);
                    entries.clear();
                    listBox.removeAllViews();
                    for (int i = 0; i < arr.length(); i++) entries.add(arr.getJSONObject(i));
                    status.setText("已加载 " + entries.size() + " 个主题（索引源: "
                            + (src.contains("gh-proxy") ? "镜像" : "直连") + "）");
                    for (int i = 0; i < entries.size(); i++) addEntryCard(entries.get(i));
                } catch (Throwable t) {
                    status.setText("索引解析失败: " + t);
                }
            });
        }).start();
    }

    private void addEntryCard(JSONObject e) {
        Activity act = host.activity();
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(12, 12, 12, 12);
        card.setBackgroundColor(0x11000000);

        ImageView iv = new ImageView(act);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(200, 150);
        ip.rightMargin = 16;
        iv.setLayoutParams(ip);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(iv);

        LinearLayout info = new LinearLayout(act);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ip2 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        info.setLayoutParams(ip2);
        card.addView(info);

        String name = e.optString("name", "未知");
        String author = e.optString("author", "未知");
        String contributor = e.optString("contributor", "匿名");
        String version = e.optString("version", "未知");
        String src = e.optString("source_url", "");
        long size = e.optLong("size_bytes", 0);

        TextView t1 = new TextView(act);
        t1.setText(name + "  v" + version);
        t1.setTextSize(15);
        info.addView(t1);
        TextView t2 = new TextView(act);
        t2.setText("原作者: " + author + "\n贡献者: " + contributor
                + (size > 0 ? "\n大小: " + fmt(size) : "")
                + (src.isEmpty() ? "" : "\n来源: " + src));
        t2.setTextSize(12);
        info.addView(t2);

        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        info.addView(btns);

        Button get = new Button(act);
        get.setText("下载并部署");
        get.setTextSize(11);
        get.setOnClickListener(v -> confirmDownload(e));
        btns.addView(get);

        Button open = new Button(act);
        open.setText("前往来源");
        open.setTextSize(11);
        if (!src.isEmpty()) {
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

        // 预览图懒加载
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
        String name = e.optString("name", "theme");
        String url = e.optString("download_url", "");
        if (url.isEmpty()) {
            host.log(name + ": 索引缺少 download_url，请前往来源页面获取");
            return;
        }
        new AlertDialog.Builder(host.activity())
                .setTitle("获取 " + name)
                .setMessage("将从社区索引下载主题包并进入部署流程（部署前照常自动备份）。继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("下载", (d, w) -> downloadAsync(e))
                .show();
    }

    private void downloadAsync(JSONObject e) {
        String name = e.optString("name", "theme");
        String url = e.optString("download_url", "");
        status.setText("正在下载 " + name + " …");
        new Thread(() -> {
            File dst = new File(host.activity().getCacheDir(), "community_" + System.currentTimeMillis() + ".mtz");
            boolean ok = false;
            String lastErr = null;
            for (String prefix : DL_PREFIX_FALLBACK) {
                try {
                    httpDownload(prefix + url, dst);
                    ok = true;
                    break;
                } catch (Throwable t) {
                    lastErr = t.toString();
                }
            }
            final boolean fok = ok;
            final String ferr = lastErr;
            final File fdst = dst;
            ui.post(() -> {
                if (fok) {
                    status.setText(name + " 下载完成，进入部署…");
                    host.onDeployCommunityTheme(fdst);
                } else {
                    status.setText(name + " 下载失败: " + ferr);
                }
            });
        }).start();
    }

    // ---------- 网络工具 ----------
    private static HttpURLConnection open(String s) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(s).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
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

    private static void httpDownload(String s, File dst) throws Exception {
        HttpURLConnection c = open(s);
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            throw new RuntimeException("HTTP " + code);
        }
        InputStream in = c.getInputStream();
        FileOutputStream fo = new FileOutputStream(dst);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
        in.close();
        fo.close();
        c.disconnect();
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

    private static String fmt(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.2fGB", bytes / 1024.0 / 1024 / 1024);
        if (bytes >= 1024L * 1024) return String.format(java.util.Locale.US, "%.2fMB", bytes / 1024.0 / 1024);
        return String.format(java.util.Locale.US, "%.1fKB", bytes / 1024.0);
    }
}
