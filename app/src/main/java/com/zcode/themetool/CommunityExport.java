package com.zcode.themetool;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 社区主题贡献包导出（阶段一：仅本地导出，无网络上传）。
 *  导出结构: /sdcard/ThemeToolCommunity/<name>_<ts>/
 *    ├── theme.mtz   原始主题包
 *    ├── meta.json   标准化元数据
 *    ├── preview/    预览图
 *    └── <name>_community.zip 上述内容打包（用于发送给维护者） */
public class CommunityExport {

    public static final String BASE_DIR = "/sdcard/ThemeToolCommunity";
    public static final String CONTRIBUTION_URL =
            "https://github.com/jinyuzerothree-collab/HyperOS-Theme-Installer/issues/new?template=bug_report.yml";

    public static class Result {
        public File dir;      // 导出目录
        public File zip;      // 打包 zip（发送用）
        public File metaJson; // meta.json
    }

    public static String anonId(Context ctx) {
        android.content.SharedPreferences sp =
                ctx.getSharedPreferences("community_pref", Context.MODE_PRIVATE);
        String id = sp.getString("anon_id", null);
        if (id == null) {
            id = "anon-" + UUID.randomUUID().toString().substring(0, 8);
            sp.edit().putString("anon_id", id).apply();
        }
        return id;
    }

    /** 组装导出目录并打包。pickedSource 可为文件或目录（目录时自动打成 theme.mtz）。 */
    public static Result build(Context ctx, File pickedSource, File unpackedBase,
                               MetadataParser.Meta meta) throws Exception {
        String safeName = (meta.name == null ? "theme" : meta.name)
                .replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "_");
        if (safeName.isEmpty() || safeName.equals("_")) safeName = "theme";
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = new File(Environment_getExternal(), BASE_DIR.substring(1) + "/" + safeName + "_" + ts);
        // Environment.getExternalStorageDirectory() = /sdcard；BASE_DIR 去掉开头斜杠
        dir = new File("/sdcard/ThemeToolCommunity/" + safeName + "_" + ts);
        dir.mkdirs();
        File previewDir = new File(dir, "preview");
        previewDir.mkdirs();

        // 1. theme.mtz
        File themeMtz = new File(dir, "theme.mtz");
        if (pickedSource.isFile()) {
            copy(pickedSource, themeMtz);
        } else {
            zipDir(pickedSource, themeMtz);
        }

        // 2. preview/
        if (unpackedBase != null) {
            File pv = new File(unpackedBase, "preview");
            File[] imgs = pv.listFiles();
            if (imgs != null) for (File img : imgs) copy(img, new File(previewDir, img.getName()));
        }

        // 3. meta.json
        JSONObject json = new JSONObject();
        json.put("schema", 1);
        json.put("theme_name", "未知".equals(meta.name) ? safeName : meta.name);
        json.put("author", meta.author);
        json.put("version", meta.version);
        json.put("ui_version", meta.uiVersion);
        JSONArray comps = new JSONArray();
        for (String c : meta.components) comps.put(c);
        json.put("components", comps);
        json.put("sha256", meta.sha256);
        json.put("size_bytes", meta.sizeBytes);
        json.put("exported_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
        json.put("tool_version", "1.3");
        json.put("anonymous_id", anonId(ctx));
        json.put("device_model", android.os.Build.MODEL); // 仅型号，便于适配排查；如介意可手动删除
        File metaJson = new File(dir, "meta.json");
        FileOutputStream fo = new FileOutputStream(metaJson);
        fo.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
        fo.close();

        // 4. 打包 zip
        File zip = new File(dir.getParentFile(), dir.getName() + "_community.zip");
        ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(zip));
        zipRec(dir, dir, zo);
        zo.close();

        Result r = new Result();
        r.dir = dir;
        r.zip = zip;
        r.metaJson = metaJson;
        return r;
    }

    private static File Environment_getExternal() { return new File("/sdcard"); }

    public static void copy(File src, File dst) throws Exception {
        InputStream in = new FileInputStream(src);
        FileOutputStream fo = new FileOutputStream(dst);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
        in.close();
        fo.close();
    }

    public static void zipDir(File dir, File outZip) throws Exception {
        ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(outZip));
        zipRec(dir, dir, zo);
        zo.close();
    }

    public static void zipRec(File base, File cur, ZipOutputStream zo) throws Exception {
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
}
