package com.hyper.icecream;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.icu.util.ChineseCalendar;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Hyper Ice Cream 时钟小组件 v5 —— TextClock 实时同步无时差。 */
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
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_clock);

        // 行 3: 日期 + 农历
        String lunar = getLunarString();
        String date = new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date());
        rv.setTextViewText(R.id.wc_datelunar, date + "  " + lunar);

        // 行 4: 天气
        String weather = getWeatherText(ctx);
        rv.setTextViewText(R.id.wc_weather, weather);

        mgr.updateAppWidget(id, rv);
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
