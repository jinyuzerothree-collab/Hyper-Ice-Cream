package com.hyper.icecream;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.icu.util.ChineseCalendar;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Hyper 时钟小组件 v6 —— 画布自由布局版。
 * 六元素（星期/时段/时间/日期/农历/天气）在原有布局基线上做数字微调
 * （左右/上下偏移 dp、大小倍率、字体粗细、颜色），由 WidgetLayout 驱动；
 * 时间用每分钟 Alarm 精确刷新；天气 = 系统天气 provider 失败后 Open-Meteo 网络兜底。
 */
public class HyperWidgetProvider extends AppWidgetProvider {

    static final String ACTION_MINUTE_TICK = "com.hyper.icecream.WIDGET_MINUTE_TICK";
    private static final int TICK_RC = 2001;

    // 天气缓存：短缓存 60s 防编辑器拖动频繁查询；网络结果落盘缓存 30 分钟
    private static volatile String wxCache = null;
    private static volatile long wxAt = 0;
    private static volatile boolean wxFetching = false;
    // 天气类别（画图标用）：0晴 1多云 2阴 3雨 4雪 5雷 6雾
    private static volatile int wxCat = -1;
    private static volatile int wxCatNet = -1; // 网络结果独立缓存（30 分钟）

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) render(ctx, mgr, id);
        scheduleTick(ctx);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle opts) {
        render(ctx, mgr, id);
        scheduleTick(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String a = intent.getAction();
        if (ACTION_MINUTE_TICK.equals(a)
                || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a)
                || Intent.ACTION_DATE_CHANGED.equals(a)) {
            renderAll(ctx);
            scheduleTick(ctx);
        }
    }

    static void renderAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, HyperWidgetProvider.class));
        for (int id : ids) render(ctx, mgr, id);
    }

    static void pinClockWidget(android.app.Activity act) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(act);
            boolean ok = mgr.requestPinAppWidget(
                    new ComponentName(act, HyperWidgetProvider.class), null, null);
            if (!ok) throw new RuntimeException("桌面不支持钉选");
        } catch (Throwable t) {
            throw new RuntimeException("请到 桌面长按→小部件 手动添加");
        }
    }

    /** 每分钟对齐整分闹钟；无精确闹钟权限时降级 60s 窗口 */
    static void scheduleTick(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            long next = (System.currentTimeMillis() / 60000L + 1L) * 60000L;
            PendingIntent pi = PendingIntent.getBroadcast(ctx, TICK_RC,
                    new Intent(ctx, HyperWidgetProvider.class).setAction(ACTION_MINUTE_TICK),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pi);
            } catch (Throwable exactFail) {
                am.setWindow(AlarmManager.RTC, next, 60000, pi);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void render(Context ctx, AppWidgetManager mgr, int id) {
        Bundle o = mgr.getAppWidgetOptions(id);
        float density = ctx.getResources().getDisplayMetrics().density;
        int wDp = Math.max(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110),
                o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 110));
        int hDp = Math.max(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 220),
                o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220));
        int wPx = Math.max(64, Math.min(2048, (int) (wDp * density)));
        int hPx = Math.max(64, Math.min(2048, (int) (hDp * density)));

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_clock);
        Bitmap canvas = renderCanvas(ctx, wPx, hPx, Math.max(wDp, 1));
        if (canvas != null) {
            rv.setViewVisibility(R.id.widget_canvas, android.view.View.VISIBLE);
            rv.setViewVisibility(R.id.widget_fallback, android.view.View.GONE);
            rv.setImageViewBitmap(R.id.widget_canvas, canvas);
        } else {
            rv.setViewVisibility(R.id.widget_canvas, android.view.View.GONE);
            rv.setViewVisibility(R.id.widget_fallback, android.view.View.VISIBLE);
            rv.setTextViewText(R.id.widget_fallback,
                    new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
        }
        try {
            PendingIntent pi = PendingIntent.getActivity(ctx, 0,
                    ctx.getPackageManager().getLaunchIntentForPackage("com.android.deskclock"),
                    PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.widget_canvas, pi);
        } catch (Throwable ignored) {
        }
        mgr.updateAppWidget(id, rv);
    }

    /**
     * 画布渲染：原有布局基线 + 每元素偏移/缩放。
     * pxPerDp = 画布宽 / 小组件实际 dp 宽；编辑器预览传 wDp = 画布宽（1dp = 1px）。
     */
    public static Bitmap renderCanvas(Context ctx, int w, int h, int wDp) {
        WidgetLayout layout = WidgetLayout.load(ctx);
        float pxPerDp = w / (float) Math.max(wDp, 1);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShadowLayer(6, 0, 3, 0x99000000);

        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hr = cal.get(java.util.Calendar.HOUR_OF_DAY);
        String period;
        if (hr >= 0 && hr < 5) period = "凌晨";
        else if (hr < 8) period = "早上";
        else if (hr < 12) period = "上午";
        else if (hr < 14) period = "中午";
        else if (hr < 18) period = "下午";
        else if (hr < 22) period = "晚上";
        else period = "深夜";

        drawEl(cv, p, w, h, pxPerDp, layout, "dow",
                new SimpleDateFormat("EEEE", Locale.CHINA).format(new Date()));
        drawEl(cv, p, w, h, pxPerDp, layout, "period", period);
        drawEl(cv, p, w, h, pxPerDp, layout, "time",
                new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
        drawEl(cv, p, w, h, pxPerDp, layout, "date",
                new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date()));
        drawEl(cv, p, w, h, pxPerDp, layout, "lunar", getLunarString());
        drawEl(cv, p, w, h, pxPerDp, layout, "weather", getWeatherText(ctx));
        drawWxIconEl(cv, p, w, h, pxPerDp, layout);
        return out;
    }

    /** 天气图标元素：Canvas 手绘晴/多云/阴/雨/雪/雷/雾，底部对齐 y 基线 */
    private static void drawWxIconEl(Canvas cv, Paint p, int w, int h, float pxPerDp,
                                     WidgetLayout layout) {
        WidgetLayout.El e = layout.el("wxicon");
        if (!e.on) return;
        int cat = wxCat;
        if (cat < 0) return;
        float size = Math.max(16f, WidgetLayout.baseS("wxicon") * h * e.s);
        float left = WidgetLayout.baseX("wxicon") * w + e.dx * pxPerDp;
        float bottom = WidgetLayout.baseY("wxicon") * h + e.dy * pxPerDp;
        Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG);
        ip.setShadowLayer(6, 0, 3, 0x66000000);
        drawWxIcon(cv, ip, cat, left, bottom - size, size, e.c);
    }

    private static void drawWxIcon(Canvas cv, Paint p, int cat, float left, float top,
                                   float size, int tint) {
        float cx = left + size * 0.5f;
        float cy = top + size * 0.42f;
        float r = size * 0.24f;
        switch (cat) {
            case 0: // 晴：太阳 + 光线
                p.setColor(tint == 0xFFFFFFFF ? 0xFFFFD54F : tint);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, cy, r, p);
                p.setStrokeWidth(size * 0.045f);
                p.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float x1 = cx + (float) Math.cos(a) * r * 1.45f;
                    float y1 = cy + (float) Math.sin(a) * r * 1.45f;
                    float x2 = cx + (float) Math.cos(a) * r * 1.85f;
                    float y2 = cy + (float) Math.sin(a) * r * 1.85f;
                    cv.drawLine(x1, y1, x2, y2, p);
                }
                break;
            case 1: // 多云：小太阳 + 白云
                p.setColor(tint == 0xFFFFFFFF ? 0xFFFFD54F : tint);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx - size * 0.16f, cy - size * 0.12f, r * 0.8f, p);
                drawCloud(cv, p, cx + size * 0.10f, cy + size * 0.16f, size * 0.62f,
                        0xFFF5F7FA);
                break;
            case 2: // 阴：灰云
                drawCloud(cv, p, cx, cy + size * 0.10f, size * 0.78f, 0xFFB0BEC5);
                break;
            case 3: // 雨：白云 + 蓝雨丝
                drawCloud(cv, p, cx, cy, size * 0.72f, 0xFFECEFF1);
                p.setColor(0xFF64B5F6);
                p.setStrokeWidth(size * 0.05f);
                p.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < 3; i++) {
                    float x = left + size * (0.24f + i * 0.26f);
                    cv.drawLine(x, top + size * 0.68f, x - size * 0.06f, top + size * 0.88f, p);
                }
                break;
            case 4: // 雪：白云 + 雪点
                drawCloud(cv, p, cx, cy, size * 0.72f, 0xFFECEFF1);
                p.setColor(0xFFE3F2FD);
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 4; i++) {
                    float x = left + size * (0.22f + i * 0.20f);
                    float y = top + size * (0.72f + (i % 2) * 0.10f);
                    cv.drawCircle(x, y, size * 0.045f, p);
                }
                break;
            case 5: // 雷：暗云 + 闪电
                drawCloud(cv, p, cx, cy, size * 0.72f, 0xFF90A4AE);
                p.setColor(0xFFFFCA28);
                p.setStyle(Paint.Style.FILL);
                android.graphics.Path bolt = new android.graphics.Path();
                float bx = cx;
                float by = top + size * 0.58f;
                bolt.moveTo(bx + size * 0.02f, by);
                bolt.lineTo(bx - size * 0.10f, by + size * 0.22f);
                bolt.lineTo(bx + size * 0.02f, by + size * 0.20f);
                bolt.lineTo(bx - size * 0.04f, by + size * 0.42f);
                bolt.lineTo(bx + size * 0.14f, by + size * 0.14f);
                bolt.lineTo(bx + size * 0.02f, by + size * 0.16f);
                bolt.lineTo(bx + size * 0.12f, by);
                bolt.close();
                cv.drawPath(bolt, p);
                break;
            default: // 雾：灰云 + 横线
                drawCloud(cv, p, cx, cy - size * 0.08f, size * 0.70f, 0xFFCFD8DC);
                p.setColor(0xFFB0BEC5);
                p.setStrokeWidth(size * 0.045f);
                p.setStyle(Paint.Style.STROKE);
                cv.drawLine(left + size * 0.15f, top + size * 0.80f,
                        left + size * 0.85f, top + size * 0.80f, p);
                cv.drawLine(left + size * 0.25f, top + size * 0.92f,
                        left + size * 0.75f, top + size * 0.92f, p);
                break;
        }
    }

    /** 白云：三个圆 + 底部圆角矩形 */
    private static void drawCloud(Canvas cv, Paint p, float cx, float cy, float w, int color) {
        float h = w * 0.62f;
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        cv.drawCircle(cx - w * 0.22f, cy, h * 0.34f, p);
        cv.drawCircle(cx, cy - h * 0.16f, h * 0.44f, p);
        cv.drawCircle(cx + w * 0.24f, cy + h * 0.02f, h * 0.30f, p);
        android.graphics.RectF body = new android.graphics.RectF(
                cx - w * 0.40f, cy + h * 0.02f, cx + w * 0.40f, cy + h * 0.34f);
        cv.drawRoundRect(body, h * 0.18f, h * 0.18f, p);
    }

    /** 单元素绘制：基线坐标 + dp 偏移，字号 = 基线字号 × 倍率，全部左对齐 */
    private static void drawEl(Canvas cv, Paint p, int w, int h, float pxPerDp,
                               WidgetLayout layout, String key, String text) {
        if (text == null || text.isEmpty()) return;
        WidgetLayout.El e = layout.el(key);
        if (!e.on) return;
        p.setTextSize(Math.max(8f, WidgetLayout.baseS(key) * h * e.s));
        p.setTypeface(typefaceFor(e.w));
        p.setColor(e.c);
        p.setTextAlign(Paint.Align.LEFT);
        cv.drawText(text, WidgetLayout.baseX(key) * w + e.dx * pxPerDp,
                WidgetLayout.baseY(key) * h + e.dy * pxPerDp, p);
    }

    private static Typeface typefaceFor(int w) {
        if (w <= 0) return Typeface.create("sans-serif-light", Typeface.NORMAL);
        if (w == 1) return Typeface.create("sans-serif", Typeface.NORMAL);
        if (w == 2) return Typeface.create("sans-serif-medium", Typeface.BOLD);
        return Typeface.create("sans-serif-black", Typeface.BOLD);
    }

    // ---------- 天气：系统 provider → 网络兜底（IP 定位 + Open-Meteo） ----------

    private static String getWeatherText(Context ctx) {
        long now = System.currentTimeMillis();
        if (wxCache != null && now - wxAt < 60000) return wxCache;
        String sys = querySystemWeather(ctx);
        if (!sys.isEmpty()) {
            wxCache = sys;
            wxAt = now;
            return sys;
        }
        String net = loadNetWeatherCache(ctx);
        if (!net.isEmpty()) {
            wxCache = net;
            wxAt = now;
            return net;
        }
        startNetFetch(ctx);
        return "";
    }

    private static String querySystemWeather(Context ctx) {
        try {
            android.database.Cursor cur = ctx.getContentResolver().query(
                    android.net.Uri.parse("content://weather/weatherData/1/46000"),
                    null, null, null, null);
            String r = "";
            if (cur != null && cur.moveToFirst()) {
                int wtIdx = cur.getColumnIndex("weather_type");
                int tempIdx = cur.getColumnIndex("temperature");
                if (tempIdx >= 0) r = cur.getInt(tempIdx) + "°";
                if (wtIdx >= 0) {
                    String[] types = {"晴","多云","阴","雨","暴雨","雷雨","雪"};
                    int wt = cur.getInt(wtIdx);
                    if (wt < types.length) r = types[wt] + " " + r;
                    // 0晴 1多云 2阴 3雨 4暴雨 5雷雨 6雪 → 图标类别
                    wxCat = (wt == 4) ? 3 : (wt == 5) ? 5 : (wt == 6) ? 4 : wt;
                }
            }
            if (cur != null) cur.close();
            return r;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static File wxCacheFile(Context ctx) {
        return new File(ctx.getFilesDir(), "weather_cache.json");
    }

    private static String loadNetWeatherCache(Context ctx) {
        try {
            File f = wxCacheFile(ctx);
            if (!f.exists()) return "";
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int n = in.read(buf);
            in.close();
            if (n <= 0) return "";
            JSONObject o = new JSONObject(new String(buf, StandardCharsets.UTF_8));
            if (System.currentTimeMillis() - o.optLong("t", 0) > 30L * 60 * 1000) return "";
            return o.optString("text", "");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void startNetFetch(final Context ctx) {
        if (wxFetching) return;
        wxFetching = true;
        new Thread(() -> {
            String text = "";
            try {
                JSONObject g = httpJson("https://ipwho.is/");
                if (g == null || !g.has("latitude"))
                    g = httpJson("https://ipapi.co/json/");
                if (g == null || !g.has("latitude")) return;
                double lat = g.getDouble("latitude"), lon = g.getDouble("longitude");
                JSONObject w = httpJson("https://api.open-meteo.com/v1/forecast?latitude="
                        + lat + "&longitude=" + lon + "&current_weather=true");
                if (w == null || !w.has("current_weather")) return;
                JSONObject cw = w.getJSONObject("current_weather");
                int code = cw.optInt("weathercode", 0);
                text = wmoText(code)
                        + " " + Math.round(cw.optDouble("temperature", 0)) + "°";
                wxCatNet = wmoCat(code);
            } catch (Throwable ignored) {
            } finally {
                wxFetching = false;
            }
            if (text.isEmpty()) return;
            if (wxCat < 0 && wxCatNet >= 0) wxCat = wxCatNet; // 系统源没给就用网络的
            try {
                JSONObject o = new JSONObject();
                o.put("t", System.currentTimeMillis());
                o.put("text", text);
                FileOutputStream out = new FileOutputStream(wxCacheFile(ctx));
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
                out.close();
            } catch (Throwable ignored) {
            }
            wxCache = null; // 失效短缓存，下次渲染取网络结果
            renderAll(ctx);
        }, "hic-wx").start();
    }

    private static JSONObject httpJson(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "HyperIceCream/3.1");
            InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** WMO 天气代码 → 中文 */
    private static String wmoText(int code) {
        if (code == 0) return "晴";
        if (code == 1 || code == 2) return "多云";
        if (code == 3) return "阴";
        if (code == 45 || code == 48) return "雾";
        if (code >= 51 && code <= 57) return "毛毛雨";
        if (code >= 61 && code <= 67) return "雨";
        if (code >= 71 && code <= 77) return "雪";
        if (code >= 80 && code <= 82) return "阵雨";
        if (code == 85 || code == 86) return "阵雪";
        if (code >= 95) return "雷雨";
        return "多云";
    }

    /** WMO 天气代码 → 图标类别（0晴 1多云 2阴 3雨 4雪 5雷 6雾） */
    private static int wmoCat(int code) {
        if (code == 0) return 0;
        if (code == 1 || code == 2) return 1;
        if (code == 3) return 2;
        if (code == 45 || code == 48) return 6;
        if (code >= 51 && code <= 67) return 3;
        if (code >= 71 && code <= 77) return 4;
        if (code >= 80 && code <= 82) return 3;
        if (code == 85 || code == 86) return 4;
        if (code >= 95) return 5;
        return 1;
    }

    private static String getLunarString() {
        try {
            ChineseCalendar cc = new ChineseCalendar(new Date());
            int month = cc.get(ChineseCalendar.MONTH) + 1;
            int day = cc.get(ChineseCalendar.DAY_OF_MONTH);
            String[] months = {"正月","二月","三月","四月","五月","六月",
                    "七月","八月","九月","十月","冬月","腊月"};
            String[] days = {"初一","初二","初三","初四","初五","初六","初七","初八","初九","初十",
                    "十一","十二","十三","十四","十五","十六","十七","十八","十九","二十",
                    "廿一","廿二","廿三","廿四","廿五","廿六","廿七","廿八","廿九","三十"};
            return months[(month - 1) % 12] + days[day - 1];
        } catch (Throwable t) {
            return "";
        }
    }
}
