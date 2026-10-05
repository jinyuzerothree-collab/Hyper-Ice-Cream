package com.hyper.icecream;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * 时钟小组件布局模型 v2 —— 原有布局基线 + 每元素数字微调。
 * 六元素（星期/时段/时间/日期/农历/天气），每元素：
 *   on  显隐；dx/dy 左右/上下偏移（dp，相对原有布局基线，可负）；
 *   s   大小倍率（1.0 = 原始字号）；w 字体粗细（0细 1常规 2粗 3特粗）；c 颜色。
 * 渲染坐标 = 基线(baseX/baseY 占画布比例) + 偏移，字号 = 基线(baseS 占高) × 倍率。
 * 持久化到应用私有目录 widget_layout.json（渲染在本应用进程，无需 root）。
 */
public class WidgetLayout {

    /** 单元素布局参数（数字输入，全部相对原有布局） */
    public static class El {
        public boolean on = true;
        public int dx = 0, dy = 0;      // 左右/上下偏移 dp
        public float s = 1.0f;          // 大小倍率，100% = 1.0
        public int w = 2;               // 0细 1常规 2粗 3特粗
        public int c = 0xFFFFFFFF;      // 白

        public JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("on", on); o.put("dx", dx); o.put("dy", dy);
            o.put("s", s); o.put("w", w); o.put("c", c);
            return o;
        }

        public static El from(JSONObject o) {
            El e = new El();
            try {
                e.on = o.optBoolean("on", true);
                e.dx = clampInt(o.optInt("dx", 0), -999, 999);
                e.dy = clampInt(o.optInt("dy", 0), -999, 999);
                e.s = clampF((float) o.optDouble("s", 1.0), 0.2f, 4.0f);
                e.w = clampInt(o.optInt("w", 2), 0, 3);
                e.c = o.optInt("c", 0xFFFFFFFF);
            } catch (Throwable ignored) {
            }
            return e;
        }
    }

    public static final String[] KEYS = {
            "dow", "period", "time", "date", "lunar", "weather", "wxicon"};

    // ---------- 原有布局基线（占画布宽/高比例；y 为文字基线位置） ----------
    public static float baseX(String k) {
        if ("period".equals(k) || "lunar".equals(k)) return 0.42f;
        if ("wxicon".equals(k)) return 0.05f;
        return 0.05f;
    }

    public static float baseY(String k) {
        if ("dow".equals(k) || "period".equals(k)) return 0.10f;
        if ("time".equals(k)) return 0.30f;
        if ("date".equals(k) || "lunar".equals(k)) return 0.62f;
        if ("wxicon".equals(k)) return 0.80f;
        return 0.82f; // weather
    }

    public static float baseS(String k) {
        if ("time".equals(k)) return 0.32f;
        if ("date".equals(k) || "lunar".equals(k)) return 0.09f;
        if ("wxicon".equals(k)) return 0.22f;
        if ("weather".equals(k)) return 0.085f;
        return 0.075f; // dow / period
    }

    public final java.util.HashMap<String, El> els = new java.util.HashMap<>();

    public WidgetLayout() {
        resetDefault();
    }

    public El el(String k) {
        El e = els.get(k);
        if (e == null) {
            e = new El();
            els.put(k, e);
        }
        return e;
    }

    /** 原有布局：全部零偏移、原始字号、粗体（2粗）；天气图标默认隐藏（原布局无图标） */
    public final void resetDefault() {
        els.clear();
        for (String k : KEYS) {
            El e = new El();
            if ("wxicon".equals(k)) e.on = false;
            els.put(k, e);
        }
    }

    /** 主题复刻预设：时间放大上提，信息行整体下沉拉开层次 */
    public void applyThemePreset() {
        resetDefault();
        el("time").s = 1.3f;
        el("time").dy = -6;
        el("dow").dy = -10;
        el("period").dy = -10;
        el("date").dy = 8;
        el("lunar").dy = 8;
        el("weather").dy = 18;
    }

    public String serialize() {
        try {
            JSONObject root = new JSONObject();
            root.put("v", 2);
            JSONObject e = new JSONObject();
            for (String k : KEYS) e.put(k, el(k).toJson());
            root.put("els", e);
            return root.toString();
        } catch (Throwable t) {
            return "{\"v\":2}";
        }
    }

    public void deserialize(String s) {
        resetDefault();
        if (s == null || s.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(s);
            if (root.optInt("v", 0) != 2) return; // 旧格式直接回默认
            JSONObject e = root.optJSONObject("els");
            if (e == null) return;
            for (String k : KEYS) {
                JSONObject o = e.optJSONObject(k);
                if (o != null) els.put(k, El.from(o));
            }
        } catch (Throwable ignored) {
        }
    }

    private static int clampInt(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float clampF(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ---------- 持久化 ----------

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "widget_layout.json");
    }

    public static WidgetLayout load(Context ctx) {
        WidgetLayout l = new WidgetLayout();
        try {
            File f = file(ctx);
            if (f.exists()) {
                FileInputStream in = new FileInputStream(f);
                byte[] buf = new byte[(int) f.length()];
                int n = in.read(buf);
                in.close();
                if (n > 0) l.deserialize(new String(buf, "UTF-8"));
            }
        } catch (Throwable ignored) {
        }
        return l;
    }

    public void save(Context ctx) {
        try {
            FileOutputStream out = new FileOutputStream(file(ctx));
            out.write(serialize().getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {
        }
    }
}
