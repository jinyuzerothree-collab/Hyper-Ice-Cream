package com.hyper.icecream;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Xml;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.xmlpull.v1.XmlPullParser;

/**
 * 图标包导入（实验性）——检测已装图标包（Nova/ADW/Lawnchair 等标准 appfilter 格式），
 * 把包内 drawable 渲染成 MIUI 主题 icons 组件格式（res/drawable-xxhdpi/<包名>.png，240×240，
 * 内容占 65% 留遮罩边距，格式对 Neo.mtz 实测样本），交给现有部署管线写入
 * /data/system/theme/icons。未映射的应用由主题引擎自动回退原图标。
 */
public class IconPacks {

    public static class Pack {
        public final String pkg;
        public final String label;
        public Pack(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    public interface Progress {
        void on(int done, int total);
    }

    /** 常见图标包 intent（ADW/Nova/Lawnchair 系事实标准） */
    private static final String[] PROBE_ACTIONS = {
            "org.adw.launcher.THEMES", "org.adw.launcher.theme.DRAWER"};

    /** 检测已装图标包：intent 快路径 + 全量扫描 appfilter 兜底（Arcticons 等不声明 THEMES intent） */
    public static List<Pack> detect(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        LinkedHashMap<String, Pack> out = new LinkedHashMap<>();
        for (String act : PROBE_ACTIONS) {
            try {
                List<ResolveInfo> ri = pm.queryIntentActivities(new Intent(act), 0);
                for (ResolveInfo r : ri) {
                    if (r == null || r.activityInfo == null) continue;
                    String pkg = r.activityInfo.packageName;
                    if (out.containsKey(pkg)) continue;
                    if (!hasAppfilter(ctx, pkg)) continue;
                    addPack(pm, out, pkg);
                }
            } catch (Throwable ignored) {
            }
        }
        if (out.isEmpty()) {
            // 兜底：遍历全部已装应用查 assets/appfilter.xml
            try {
                for (android.content.pm.PackageInfo pi : pm.getInstalledPackages(0)) {
                    if (pi == null || pi.applicationInfo == null) continue;
                    android.content.pm.ApplicationInfo ai = pi.applicationInfo;
                    String pkg = ai.packageName;
                    if (out.containsKey(pkg)) continue;
                    if ((ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                            && (ai.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                        continue; // 跳过纯系统包（图标包都是三方或更新过的）
                    }
                    if (!hasAppfilter(ctx, pkg)) continue;
                    addPack(pm, out, pkg);
                }
            } catch (Throwable ignored) {
            }
        }
        return new ArrayList<>(out.values());
    }

    private static void addPack(PackageManager pm, LinkedHashMap<String, Pack> out, String pkg) {
        String label;
        try {
            label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Throwable t) {
            label = pkg;
        }
        out.put(pkg, new Pack(pkg, label));
    }

    private static boolean hasAppfilter(Context ctx, String pkg) {
        try {
            Context pc = ctx.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);
            try {
                pc.getAssets().open("appfilter.xml").close();
                return true;
            } catch (Throwable notAsset) {
                Resources res = pc.getResources();
                return res.getIdentifier("appfilter", "xml", pkg) != 0
                        || res.getIdentifier("appfilter", "raw", pkg) != 0;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /** 解析 appfilter.xml → 包名 → drawable 名（组件级映射收敛为包级，先到先得） */
    public static LinkedHashMap<String, String> parseAppfilter(Context ctx, String packPkg) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        InputStream is = null;
        XmlPullParser xp = null;
        try {
            Context pc = ctx.createPackageContext(packPkg, Context.CONTEXT_IGNORE_SECURITY);
            try {
                is = pc.getAssets().open("appfilter.xml");
            } catch (Throwable notAsset) {
            }
            if (is != null) {
                xp = Xml.newPullParser();
                xp.setInput(is, null);
            } else {
                Resources res = pc.getResources();
                int id = res.getIdentifier("appfilter", "xml", packPkg);
                if (id != 0) {
                    xp = res.getXml(id); // res/xml：编译型 XML，直接给 XmlResourceParser
                } else {
                    id = res.getIdentifier("appfilter", "raw", packPkg);
                    if (id != 0) {
                        is = res.openRawResource(id);
                        xp = Xml.newPullParser();
                        xp.setInput(is, null);
                    }
                }
            }
            if (xp == null) return map;
            int t = xp.getEventType();
            while (t != XmlPullParser.END_DOCUMENT) {
                if (t == XmlPullParser.START_TAG && "item".equals(xp.getName())) {
                    String comp = xp.getAttributeValue(null, "component");
                    String drw = xp.getAttributeValue(null, "drawable");
                    if (comp != null && drw != null) {
                        String p = parsePkg(comp);
                        if (p != null && !map.containsKey(p)) map.put(p, drw);
                    }
                }
                t = xp.next();
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                if (is != null) is.close();
            } catch (Throwable ignored) {
            }
        }
        return map;
    }

    /** "ComponentInfo{com.pkg/com.pkg.Cls}" / "com.pkg/cls" / "com.pkg" → "com.pkg" */
    private static String parsePkg(String comp) {
        int i = comp.indexOf('{');
        int j = comp.indexOf('}');
        if (i >= 0 && j > i) comp = comp.substring(i + 1, j);
        int s = comp.indexOf('/');
        if (s > 0) comp = comp.substring(0, s);
        comp = comp.trim();
        if (comp.isEmpty() || !comp.contains(".")) return null;
        return comp;
    }

    /**
     * 渲染并打包 icons 组件：res/drawable-xxhdpi/<包名>.png，240×240。
     * 有内在尺寸的图标缩放到画布 65% 居中（与主题图标留边一致），自适应图标全幅绘制交给系统遮罩。
     * 返回 zip 文件（部署时 cp 到 /data/system/theme/icons）。
     */
    public static File build(Context ctx, String packPkg, LinkedHashMap<String, String> map,
                             Progress cb) throws Exception {
        Context pc = ctx.createPackageContext(packPkg, Context.CONTEXT_IGNORE_SECURITY);
        Resources res = pc.getResources();
        File out = new File(ctx.getCacheDir(), "icons_pack_" + System.currentTimeMillis());
        ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(out));
        int total = map.size();
        int done = 0;
        try {
            for (Map.Entry<String, String> e : map.entrySet()) {
                String pkg = e.getKey();
                String name = e.getValue();
                if (!pkg.equals(packPkg)) {
                    int id = res.getIdentifier(name, "drawable", packPkg);
                    if (id == 0) id = res.getIdentifier(name, "mipmap", packPkg);
                    if (id != 0) {
                        try {
                            Bitmap bmp = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
                            Canvas cv = new Canvas(bmp);
                            Drawable d = res.getDrawable(id);
                            int w = d.getIntrinsicWidth();
                            int h = d.getIntrinsicHeight();
                            if (w > 0 && h > 0) {
                                float sc = (240f * 0.65f) / Math.max(w, h);
                                int dw = Math.max(1, Math.round(w * sc));
                                int dh = Math.max(1, Math.round(h * sc));
                                d.setBounds((240 - dw) / 2, (240 - dh) / 2,
                                        (240 + dw) / 2, (240 + dh) / 2);
                            } else {
                                d.setBounds(0, 0, 240, 240);
                            }
                            d.draw(cv);
                            zo.putNextEntry(new ZipEntry("res/drawable-xxhdpi/" + pkg + ".png"));
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, zo);
                            zo.closeEntry();
                            bmp.recycle();
                        } catch (Throwable skipOne) {
                        }
                    }
                }
                done++;
                if (cb != null && (done % 20 == 0 || done == total)) cb.on(done, total);
            }
        } finally {
            try {
                zo.close();
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    /** 快速统计 icons zip 里的图标数（验证部署文件时用） */
    public static int countIcons(File f) {
        try {
            ZipInputStream zi = new ZipInputStream(new FileInputStream(f));
            int n = 0;
            ZipEntry e;
            while ((e = zi.getNextEntry()) != null) {
                if (e.getName().startsWith("res/") && e.getName().endsWith(".png")) n++;
            }
            zi.close();
            return n;
        } catch (Throwable t) {
            return -1;
        }
    }
}
