package com.hyper.icecream;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * 时钟小组件布局模型 v1 —— 六元素（星期/时段/时间/日期/农历/天气），
 * 每个元素：显隐 on、位置 x/y（占宽高的 0~1）、字号 s（占高 0~1）、
 * 对齐 a（0左 1中 2右）、颜色 c（ARGB int）、粗体 b。
 * 持久化到应用私有目录 widget_layout.json（渲染在本应用进程，无需 root）。
 */
public class WidgetLayout {

    /** 单元素布局参数 */
    public static class El {
        public boolean on = true;
        public float x = 0.05f, y = 0.10f, s = 0.08f;
        public int a = 0;           // 0左 1中 2右
        public int c = 0xFFFFFFFF;  // 白
        public boolean b = true;    // 粗体

        public JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("on", on); o.put("x", x); o.put("y", y); o.put("s", s);
            o.put("a", a); o.put("c", c); o.put("b", b);
            return o;
        }

        public static El from(JSONObject o) {
            El e = new El();
            try {
                e.on = o.optBoolean("on", true);
                e.x = clamp01((float) o.optDouble("x", e.x));
                e.y = clamp01((float) o.optDouble("y", e.y));
                e.s = clamp01((float) o.optDouble("s", e.s));
                e.a = o.optInt("a", 0);
                e.c = o.optInt("c", 0xFFFFFFFF);
                e.b = o.optBoolean("b", true);
            } catch (Throwable ignored) {
            }
            return e;
        }
    }

    /** 六元素键名 */
    public static final String[] KEYS = {"dow", "period", "time", "date", "lunar", "weather"};

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

    /** 默认布局：近似 v5 垂直排版（星期 时段 / 大时间 / 日期 农历 / 天气） */
    public final void resetDefault() {
        els.clear();
        El dow = new El();     dow.x = 0.05f; dow.y = 0.10f; dow.s = 0.075f; els.put("dow", dow);
        El period = new El();  period.x = 0.42f; period.y = 0.10f; period.s = 0.075f; els.put("period", period);
        El time = new El();    time.x = 0.05f; time.y = 0.30f; time.s = 0.32f; time.b = true; els.put("time", time);
        El date = new El();    date.x = 0.05f; date.y = 0.62f; date.s = 0.09f; els.put("date", date);
        El lunar = new El();   lunar.x = 0.42f; lunar.y = 0.62f; lunar.s = 0.09f; els.put("lunar", lunar);
        El weather = new El(); weather.x = 0.05f; weather.y = 0.82f; weather.s = 0.085f; els.put("weather", weather);
    }

    /** 主题复刻预设：Neo 时钟的纵向比例（时段随星期，农历贴日期右侧） */
    public void applyThemePreset() {
        resetDefault();
        el("time").y = 0.24f;
        el("time").s = 0.36f;
        el("date").y = 0.66f;
        el("lunar").y = 0.66f;
        el("weather").y = 0.86f;
    }

    public String serialize() {
        try {
            JSONObject root = new JSONObject();
            root.put("v", 1);
            JSONObject e = new JSONObject();
            for (String k : KEYS) e.put(k, el(k).toJson());
            root.put("els", e);
            return root.toString();
        } catch (Throwable t) {
            return "{\"v\":1}";
        }
    }

    public void deserialize(String s) {
        resetDefault();
        if (s == null || s.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(s);
            JSONObject e = root.optJSONObject("els");
            if (e == null) return;
            for (String k : KEYS) {
                JSONObject o = e.optJSONObject(k);
                if (o != null) els.put(k, El.from(o));
            }
        } catch (Throwable ignored) {
        }
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
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
