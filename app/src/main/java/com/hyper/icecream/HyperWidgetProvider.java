package com.hyper.icecream;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.icu.util.ChineseCalendar;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Hyper Ice Cream 时钟小组件 v4 —— 完整复现主题时钟：星期+时段 / 时间 / 日期+农历 / 天气。 */
public class HyperWidgetProvider extends AppWidgetProvider {

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

    private static void render(Context ctx, AppWidgetManager mgr, int id) {
        Bundle o = mgr.getAppWidgetOptions(id);
        float density = ctx.getResources().getDisplayMetrics().density;
        int wPx = Math.max(64, Math.min(2048,
                (int) (Math.max(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180),
                       o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 180)) * density)));
        int hPx = Math.max(64, Math.min(2048,
                (int) (Math.max(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 280),
                       o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 280)) * density)));

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
        try {
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(ctx, 0,
                    ctx.getPackageManager().getLaunchIntentForPackage("com.android.deskclock"),
                    android.app.PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.widget_canvas, pi);
        } catch (Throwable ignored) {
        }
        mgr.updateAppWidget(id, rv);
    }

    /** v4 渲染：完整复现主题时钟所有元素，垂直排列，自适应尺寸。 */
    private static Bitmap renderClockCanvas(Context ctx, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setShadowLayer(4, 0, 2, 0x88000000);

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
        String weather = getWeatherText(ctx);

        float pad = w * 0.06f;
        float y = h * 0.12f; // 居中：从12%开始

        float bodySize = h * 0.065f;
        float timeSize = h * 0.16f;
        float weatherSize = bodySize * 0.8f;

        // 行 1: 星期X + 时段
        p.setTextSize(bodySize);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        cv.drawText(dow + " " + period, pad, y + bodySize, p);
        y += bodySize * 2.0f;

        // 行 2: 时间（特大粗体，方方正正）
        p.setTextSize(timeSize);
        p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        p.setFakeBoldText(true);
        cv.drawText(time, pad, y + timeSize, p);
        y += timeSize * 1.4f;

        // 行 3: 日期 + 农历
        p.setTextSize(bodySize * 0.85f);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        cv.drawText(date + "  " + lunar, pad, y + bodySize, p);
        y += bodySize * 1.5f;

        // 行 4: 天气
        if (!weather.isEmpty()) {
            p.setTextSize(weatherSize);
            cv.drawText(weather, pad, y + weatherSize, p);
        }

        return out;
    }

    private static String getWeatherText(Context ctx) {
        try {
            android.database.Cursor c = ctx.getContentResolver().query(
                    android.net.Uri.parse("content://weather/weatherData/1/46000"),
                    null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int wtIdx = c.getColumnIndex("weather_type");
                int tempIdx = c.getColumnIndex("temperature");
                String r = "";
                if (tempIdx >= 0) r = c.getInt(tempIdx) + "°";
                if (wtIdx >= 0) {
                    String[] types = {"晴","多云","阴","雨","暴雨","雷雨","雪"};
                    int wt = c.getInt(wtIdx);
                    if (wt < types.length) r = types[wt] + " " + r;
                }
                c.close();
                return r;
            }
            if (c != null) c.close();
        } catch (Throwable ignored) {
        }
        return "";
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
            String lm = months[(month - 1) % 12];
            String ld = days[day - 1];
            return lm + ld;
        } catch (Throwable t) {
            return "";
        }
    }
}
