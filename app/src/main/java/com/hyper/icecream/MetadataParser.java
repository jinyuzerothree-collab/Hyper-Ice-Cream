package com.hyper.icecream;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;

/** MTZ 主题元数据解析：description.xml 字段、组件清单、预览图、SHA256。字段缺失返回"未知"，不抛异常。 */
public class MetadataParser {

    public static class Meta {
        public String name = "未知";
        public String author = "未知";
        public String version = "未知";
        public String uiVersion = "未知";
        public String description = "";
        public final List<String> components = new ArrayList<>();   // 全部顶层组件 ID
        public final List<File> previews = new ArrayList<>();       // preview/ 下图片
        public String sha256 = "未知";
        public long sizeBytes = 0;
    }

    /** base = 解包后的主题根目录；pickedSource = 用户选择的原始文件（算哈希用），可为 null */
    public static Meta parse(File base, File pickedSource) {
        Meta m = new Meta();
        if (pickedSource != null && pickedSource.isFile()) {
            m.sizeBytes = pickedSource.length();
            m.sha256 = sha256(pickedSource);
        }
        if (base == null || !base.isDirectory()) return m;

        parseDescription(new File(base, "description.xml"), m);

        // 组件清单：全部顶层条目（排除 preview 与 description.xml）
        File[] tops = base.listFiles();
        if (tops != null) {
            Arrays.sort(tops, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File t : tops) {
                String n = t.getName();
                if (n.equals("preview") || n.equals("description.xml")) continue;
                m.components.add(n + (t.isDirectory() ? "/" : ""));
            }
        }
        // 预览图
        File previewDir = new File(base, "preview");
        File[] pv = previewDir.listFiles();
        if (pv != null) {
            List<File> imgs = new ArrayList<>();
            for (File f : pv) {
                String low = f.getName().toLowerCase();
                if (low.endsWith(".jpg") || low.endsWith(".png")) imgs.add(f);
            }
            Collections.sort(imgs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            m.previews.addAll(imgs);
        }
        return m;
    }

    private static void parseDescription(File xml, Meta m) {
        if (!xml.isFile()) return;
        try {
            InputStream in = new FileInputStream(xml);
            XmlPullParser p = Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setInput(in, null);
            int event = p.getEventType();
            String current = null;
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = p.getName().toLowerCase();
                    if (tag.equals("title") || tag.equals("author") || tag.equals("designer")
                            || tag.equals("version") || tag.equals("description") || tag.equals("uiversion")) {
                        current = tag;
                    }
                } else if (event == XmlPullParser.TEXT && current != null) {
                    String text = p.getText().trim();
                    if (!text.isEmpty()) {
                        switch (current) {
                            case "title":
                                if (m.name.equals("未知")) m.name = text;
                                break;
                            case "author":
                            case "designer":
                                if (m.author.equals("未知")) m.author = text;
                                break;
                            case "version":
                                if (m.version.equals("未知")) m.version = text;
                                break;
                            case "uiversion":
                                if (m.uiVersion.equals("未知")) m.uiVersion = text;
                                break;
                            case "description":
                                if (m.description.isEmpty()) m.description = text;
                                break;
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    current = null;
                }
                event = p.next();
            }
            in.close();
        } catch (Throwable ignored) {
            // 元数据解析失败不影响主流程，字段保持"未知"
        }
    }

    public static String sha256(File f) {
        try {
            MessageDigest dig = MessageDigest.getInstance("SHA-256");
            InputStream in = new FileInputStream(f);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) dig.update(buf, 0, n);
            in.close();
            StringBuilder sb = new StringBuilder();
            for (byte b : dig.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            return "未知";
        }
    }
}
