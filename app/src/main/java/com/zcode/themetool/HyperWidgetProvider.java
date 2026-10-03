package com.zcode.themetool;

import android.app.Activity;
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

/** Hyper Ice Cream Widget v2 —— "拆解复现"渲染引擎（用户方案）。
 *  解析主题时钟 manifest 的元素间距比例（#vh 分数锚点/字号/左边距），
 *  将数字贴图与日期文字按这些比例重新排版到系统实际分配的画布上。
 *  桌面把 Widget 拉到 2×4 ≈ 原始布局 ×2 的复现；拉到 4×2 则横向铺开。不拉伸贴图。 */
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
    static void pinClockWidget(Activity act) {
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
        int wDp = Math.max(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110),
                o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 110));
        int hDp = Math.max(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 60),
                o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 60));
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

    /** 按主题比例把时钟元素复现在 w×h 画布上。无素材/解析失败返回 null（走字体降级）。 */
    private static Bitmap renderClockCanvas(Context ctx, int w, int h) {
        File assetDir = new File(ctx.getFilesDir(), ASSET_DIR);
        File manifest = new File(assetDir, "manifest.xml");
        File numDir = new File(assetDir, "src/num/white/type_0");
        if (!manifest.isFile() || !numDir.isDirectory()) return null;

        try {
            String mx = readText(manifest);
            float fDate = frac(mx, "week_en", 0.12f);
            float fTime = frac(mx, "t2_1", 0.42f);
            float baseX = num(mx, "x=\"(\\d+)", 60f);
            float dateSize = num(mx, "size=\"(\\d+)\"[^>]*textExp=\"@week", 72f);
            int designW = (int) num(mx, "screenWidth=\"(\\d+)\"", 1080);

            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            float s = (float) w / designW;

            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.WHITE);
            p.setShadowLayer(6 * s, 0, 2 * s, 0x99000000);

            // 1) 日期行（位置/字号按主题比例）
            String date = new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date());
            p.setTextSize(dateSize * s);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            cv.drawText(date, baseX * s, fDate * h, p);

            // 2) 时间数字贴图行（HH:mm），贴图高度 = (天气锚点 - 时间锚点) 的 55%
            List<Bitmap> digits = new ArrayList<>();
            String hhmm = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
            for (char ch : hhmm.toCharArray()) {
                String fn = ch == ':' ? "num_dot.png" : ("num_" + ch + ".png");
                File f = new File(numDir, fn);
                if (!f.isFile()) { digits.clear(); break; }
                Bitmap raw = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (raw != null) digits.add(raw);
            }
            if (!digits.isEmpty()) {
                float fWx = frac(mx, "weather", 0.88f);
                float digitH = Math.max(24, (fWx - fTime) * h * 0.55f);
                float x = baseX * s;
                float y = fTime * h;
                for (Bitmap d : digits) {
                    float dw = d.getWidth() * digitH / d.getHeight();
                    cv.drawBitmap(d, null, new android.graphics.RectF(x, y, x + dw, y + digitH), p);
                    x += dw;
                }
            } else {
                // 无贴图：字体时间
                p.setTextSize(dateSize * s * 1.6f);
                cv.drawText(hhmm, baseX * s, fTime * h + dateSize * s, p);
            }

            // 3) 天气行：主题有 weather 贴图与文案位，但数据源需系统查询（v3 接入），此处留白
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 从 manifest 提取 marker 附近的 #vh 分数锚点 */
    private static float frac(String mx, String marker, float def) {
        int i = mx.indexOf(marker);
        if (i < 0) return def;
        int from = Math.max(0, i - 400);
        int to = Math.min(mx.length(), i + 400);
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
