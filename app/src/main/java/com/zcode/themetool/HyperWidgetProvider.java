package com.zcode.themetool;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.icu.util.ChineseCalendar;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hyper Ice Cream Widget v3 —— 拆解复现渲染引擎。
 *  解析主题 manifest 的 #vh 分数锚点 → 日期/农历/时间/天气行按比例重排到实际画布。
 *  新增：农历（ICU ChineseCalendar）、天气行（贴图预留）。 */
public class HyperWidgetProvider extends AppWidgetProvider {

    private static final String ASSET_DIR = "widget_assets/clock_2x4";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) render(ctx, mgr, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle opts) {
        render(ctx, mgr, id);
    }

    static void renderAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, HyperWidgetProvider.class));
        for (int id : ids) render(ctx, mgr, id);
    }

    /** 一键钉选时钟小组件到桌面（系统确认框） */
    static void pinClockWidget(android.app.Activity act) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(act);
            boolean ok = mgr.requestPinAppWidget(
                    new ComponentName(act, HyperWidgetProvider.class), null, null);
            if (!ok) throw new RuntimeException("桌面不支持钉选");
        } catch (Throwable t) {
            throw new RuntimeException("请到 桌面长按→小部件 手动添加 (" + t.getMessage() + ")");
        }
    }

    private static void render(Context ctx, AppWidgetManager mgr, int id) {
        Bundle o = mgr.getAppWidgetOptions(id);
        float density = ctx.getResources().getDisplayMetrics().density;
        int wDp = Math.max(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180),
                o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 180));
        int hDp = Math.max(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 280),
                o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 280));
        int wPx = Math.max(64, Math.min(2048, (int) (wDp * density)));
        int hPx = Math.max(64, Math.min(2048, (int) (hDp * density)));

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_clock);
        Bitmap canvas = renderClockCanvas(ctx, wPx, hPx);
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
        // 点击打开时钟
        try {
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(ctx, 0,
                    ctx.getPackageManager().getLaunchIntentForPackage("com.android.deskclock"),
                    android.app.PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.widget_canvas, pi);
        } catch (Throwable ignored) {
        }
        mgr.updateAppWidget(id, rv);
    }

    private static String getSystemWeather(Context ctx) {
        try {
            android.database.Cursor c = ctx.getContentResolver().query(
                    android.net.Uri.parse("content://weather/weatherData/1/46000"),
                    null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int wtIdx = c.getColumnIndex("weather_type");
                int tempIdx = c.getColumnIndex("temperature");
                String result = "";
                if (tempIdx >= 0) result = c.getInt(tempIdx) + "°";
                if (wtIdx >= 0) {
                    String[] types = {"晴","多云","阴","雨","暴雨","雷雨","雪"};
                    int wt = c.getInt(wtIdx);
                    if (wt < types.length) result = types[wt] + " " + result;
                }
                c.close();
                return result;
            }
            if (c != null) c.close();
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** 拆解复现渲染 v4：垂直排列全部元素，系统粗体字，自适应任意尺寸。 */
    private static Bitmap renderClockCanvas(Context ctx, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setShadowLayer(6, 0, 3, 0x99000000);

        // 时段
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hr = cal.get(java.util.Calendar.HOUR_OF_DAY);
        String period;
        if (hr >= 5 && hr < 8) period = "早上";
        else if (hr >= 8 && hr < 12) period = "上午";
        else if (hr >= 12 && hr < 14) period = "中午";
        else if (hr >= 14 && hr < 18) period = "下午";
        else period = "晚上";

        String dow = new SimpleDateFormat("EEEE", Locale.CHINA).format(new Date());
        String date = new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date());
        String lunar = getLunarString();
        String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        String weather = getSystemWeather(ctx);

        float pad = w * 0.05f;
        float y = h * 0.08f;

        // 行 1: 星期X + 时段（中号粗体）
        float l1Size = h * 0.08f;
        p.setTextSize(l1Size);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setFakeBoldText(true);
        cv.drawText(dow + " " + period, pad, y + l1Size, p);
        y += l1Size * 2.0f;

        // 行 2: 时间（特大粗体）
        float l2Size = h * 0.28f;
        p.setTextSize(l2Size);
        p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        p.setFakeBoldText(true);
        p.setStrokeWidth(1.5f);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        cv.drawText(time, pad, y + l2Size, p);
        y += l2Size * 1.4f;

        // 行 3: 日期 + 农历（无前缀）
        float l3Size = h * 0.07f;
        p.setTextSize(l3Size);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setFakeBoldText(false);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(0);
        cv.drawText(date + "  " + lunar, pad, y + l3Size, p);
        y += l3Size * 2.0f;

        // 行 4: 天气（有数据则显示）
        if (!weather.isEmpty()) {
            float l4Size = h * 0.06f;
            p.setTextSize(l4Size);
            cv.drawText(weather, pad, y + l4Size, p);
        }

        return out;
    }

    /** 农历（无"农历"前缀，直接"八月廿四"格式） */
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
            String lm = months[(month - 1) % 12];
            String ld = day <= 10 ? days[day - 1]
                    : day <= 19 ? "十" + days[day - 11]
                    : day == 20 ? "二十"
                    : day <= 29 ? "廿" + days[day - 21]
                    : "三十";
            return lm + ld;
        } catch (Throwable t) {
            return "";
        }
    }

    private static float fracAfter(String mx, String marker, float def) {
        int i = mx.indexOf(marker);
        if (i < 0) return def;
        int from = Math.max(0, i - 500);
        int to = Math.min(mx.length(), i + 500);
        Matcher m = Pattern.compile("#vh\\*([0-9.]+)").matcher(mx.substring(from, to));
        return m.find() ? Float.parseFloat(m.group(1)) : def;
    }

    private static float num(String mx, String regex, float def) {
        Matcher m = Pattern.compile(regex).matcher(mx);
        return m.find() ? Float.parseFloat(m.group(1)) : def;
    }

    private static String readText(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }
}
