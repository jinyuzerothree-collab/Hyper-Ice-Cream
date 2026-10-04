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
        mgr.updateAppWidget(id, rv);
    }

    /** 拆解复现渲染：按主题 manifest 间距比例重排到 w×h 画布。 */
    private static Bitmap renderClockCanvas(Context ctx, int w, int h) {
        File assetDir = new File(ctx.getFilesDir(), ASSET_DIR);
        File manifest = new File(assetDir, "manifest.xml");
        File numDir = new File(assetDir, "src/num/white/type_0");
        if (!manifest.isFile() || !numDir.isDirectory()) return null;

        try {
            String mx = readText(manifest);
            float fDate = fracAfter(mx, "week_en", 0.12f);
            float fTime = fracAfter(mx, "t2_1", 0.42f);
            float fWx = fracAfter(mx, "weather_description", 0.88f);
            if (fTime > fWx) { float tmp = fTime; fTime = fWx; fWx = tmp; }
            float baseX = num(mx, "x=\"(\\d+)", 60f);
            float dateSize = num(mx, "size=\"(\\d+)\"[^>]*textExp=\"@week", 72f);
            int designW = (int) num(mx, "screenWidth=\"(\\d+)\"", 1080);
            if (designW <= 0) designW = 1080;

            float s = (float) w / designW;
            float x0 = baseX * s;

            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.WHITE);
            p.setShadowLayer(5 * s, 0, 2 * s, 0x88000000);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

            // 行 1：公历日期
            String solar = new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date());
            float solarSize = dateSize * s;
            p.setTextSize(solarSize);
            cv.drawText(solar, x0, fDate * h, p);

            // 行 1b：农历
            String lunar = getLunarString();
            if (!lunar.isEmpty()) {
                p.setTextSize(solarSize * 0.55f);
                cv.drawText(lunar, x0, fDate * h + solarSize * 1.3f, p);
            }

            // 行 2：时间数字贴图（或字体降级）
            String hhmm = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
            List<Bitmap> digits = new ArrayList<>();
            for (char ch : hhmm.toCharArray()) {
                String fn = ch == ':' ? "num_dot.png" : ("num_" + ch + ".png");
                File f = new File(numDir, fn);
                if (!f.isFile()) { digits.clear(); break; }
                Bitmap raw = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (raw != null) digits.add(raw);
            }
            if (!digits.isEmpty()) {
                float digitH = Math.max(30, (fWx - fTime) * h * 0.65f);
                float x = x0;
                float y = fTime * h;
                for (Bitmap d : digits) {
                    float dw = d.getWidth() * digitH / d.getHeight();
                    cv.drawBitmap(d, null, new android.graphics.RectF(x, y, x + dw, y + digitH), p);
                    x += dw;
                }
            } else {
                p.setTextSize(dateSize * s * 1.5f);
                cv.drawText(hhmm, x0, fTime * h + dateSize * s, p);
            }

            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 农历字符串（ICU ChineseCalendar） */
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
            return "农历" + lm + ld;
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
