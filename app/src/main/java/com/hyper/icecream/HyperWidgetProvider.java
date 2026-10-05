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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.icu.util.ChineseCalendar;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Hyper 时钟小组件 v6 —— 画布自由布局版。
 * 六元素（星期/时段/时间/日期/农历/天气）位置字号颜色全由 WidgetLayout 驱动；
 * 时间用每分钟 Alarm 精确刷新（setExactAndAllowWhileIdle，降级 setWindow）。
 */
public class HyperWidgetProvider extends AppWidgetProvider {

    static final String ACTION_MINUTE_TICK = "com.hyper.icecream.WIDGET_MINUTE_TICK";
    private static final int TICK_RC = 2001;

    // 天气查询 60s 缓存（编辑器拖动预览时避免频繁 binder 查询）
    private static volatile String wxCache = null;
    private static volatile long wxAt = 0;

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
        Bitmap canvas = renderCanvas(ctx, wPx, hPx);
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

    /** 画布渲染：WidgetLayout 六元素自由摆放（编辑器预览与桌面渲染共用此入口） */
    public static Bitmap renderCanvas(Context ctx, int w, int h) {
        WidgetLayout layout = WidgetLayout.load(ctx);
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

        drawEl(cv, p, w, h, layout, "dow",
                new SimpleDateFormat("EEEE", Locale.CHINA).format(new Date()));
        drawEl(cv, p, w, h, layout, "period", period);
        drawEl(cv, p, w, h, layout, "time",
                new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
        drawEl(cv, p, w, h, layout, "date",
                new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date()));
        drawEl(cv, p, w, h, layout, "lunar", getLunarString());
        drawEl(cv, p, w, h, layout, "weather", getWeatherText(ctx));
        return out;
    }

    /** 单元素绘制：x 为锚边留白占比（中/右对齐时为距中/距右位置占比） */
    private static void drawEl(Canvas cv, Paint p, int w, int h,
                               WidgetLayout layout, String key, String text) {
        if (text == null || text.isEmpty()) return;
        WidgetLayout.El e = layout.el(key);
        if (!e.on) return;
        p.setTextSize(Math.max(8f, e.s * h));
        p.setTypeface(Typeface.create(e.b ? "sans-serif-black" : "sans-serif", e.b
                ? Typeface.BOLD : Typeface.NORMAL));
        p.setColor(e.c);
        if (e.a == 1) {
            p.setTextAlign(Paint.Align.CENTER);
            cv.drawText(text, e.x * w, e.y * h, p);
        } else if (e.a == 2) {
            p.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(text, w - e.x * w, e.y * h, p);
        } else {
            p.setTextAlign(Paint.Align.LEFT);
            cv.drawText(text, e.x * w, e.y * h, p);
        }
    }

    private static String getWeatherText(Context ctx) {
        long now = System.currentTimeMillis();
        String c = wxCache;
        if (c != null && now - wxAt < 60000) return c;
        String r = "";
        try {
            android.database.Cursor cur = ctx.getContentResolver().query(
                    android.net.Uri.parse("content://weather/weatherData/1/46000"),
                    null, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                int wtIdx = cur.getColumnIndex("weather_type");
                int tempIdx = cur.getColumnIndex("temperature");
                if (tempIdx >= 0) r = cur.getInt(tempIdx) + "°";
                if (wtIdx >= 0) {
                    String[] types = {"晴","多云","阴","雨","暴雨","雷雨","雪"};
                    int wt = cur.getInt(wtIdx);
                    if (wt < types.length) r = types[wt] + " " + r;
                }
            }
            if (cur != null) cur.close();
        } catch (Throwable ignored) {
        }
        wxCache = r;
        wxAt = now;
        return r;
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
