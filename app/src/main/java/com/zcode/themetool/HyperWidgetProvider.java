package com.zcode.themetool;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.View;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Hyper Ice Cream Widget（Plan B v1）：独立于 MIUI Gadget 体系的主题时钟小部件。
 *  - 尺寸自由缩放（Launcher 原生 resize），按尺寸分桶重排版（非拉伸）
 *  - 素材来自已部署主题的数字时钟贴图（部署时由 root 导出到应用私有目录）
 *  - 无素材时降级为系统字体数字时钟 */
public class HyperWidgetProvider extends AppWidgetProvider {

    private static final String ASSET_DIR = "widget_assets/clock_2x4/src/num/white/type_0";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) render(ctx, mgr, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle opts) {
        render(ctx, mgr, id); // 尺寸变化 → 按新桶重排版
    }

    static void renderAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, HyperWidgetProvider.class));
        for (int id : ids) render(ctx, mgr, id);
    }

    private static void render(Context ctx, AppWidgetManager mgr, int id) {
        Bundle o = mgr.getAppWidgetOptions(id);
        int wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110);
        int hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 40);

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_clock);

        // 桶判定（重排版而非拉伸）
        String bucket;
        if (wDp >= 250 && hDp >= 180) bucket = "large";
        else if (wDp >= 200) bucket = "wide";
        else bucket = "small";

        float timeSize, dateSize;
        if ("large".equals(bucket)) { timeSize = 84f; dateSize = 20f; }
        else if ("wide".equals(bucket)) { timeSize = 56f; dateSize = 15f; }
        else { timeSize = 34f; dateSize = 12f; }

        // 日期与时间
        String date = new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date());
        String hhmm = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());

        rv.setTextViewText(R.id.widget_date, "small".equals(bucket) ? "" : date);
        rv.setTextViewTextSize(R.id.widget_date, android.util.TypedValue.COMPLEX_UNIT_SP, dateSize);
        rv.setTextViewText(R.id.widget_weather, "");
        // 降级文本时间（素材缺失时用）
        rv.setTextViewText(R.id.widget_digits, ""); // digits 是 LinearLayout；下面用图片或文本

        // 数字贴图（来自主题；缩放到桶高）
        File dir = new File(ctx.getFilesDir(), ASSET_DIR);
        List<Bitmap> digitBmps = new ArrayList<>();
        if (dir.isDirectory()) {
            for (char ch : hhmm.toCharArray()) {
                String fn = ch == ':' ? "num_dot.png" : ("num_" + ch + ".png");
                File f = new File(dir, fn);
                if (!f.isFile()) { digitBmps.clear(); break; }
                Bitmap raw = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (raw == null) { digitBmps.clear(); break; }
                float targetH = timeSize * ctx.getResources().getDisplayMetrics().density * 0.7f;
                int tw = Math.max(1, Math.round(raw.getWidth() * targetH / raw.getHeight()));
                digitBmps.add(Bitmap.createScaledBitmap(raw, tw, Math.round(targetH), true));
            }
        }

        int[] slots = {R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4};
        if (!digitBmps.isEmpty() && digitBmps.size() <= slots.length) {
            for (int i = 0; i < slots.length; i++) {
                if (i < digitBmps.size()) {
                    rv.setViewVisibility(slots[i], View.VISIBLE);
                    rv.setImageViewBitmap(slots[i], digitBmps.get(i));
                } else {
                    rv.setViewVisibility(slots[i], View.GONE);
                }
            }
        } else {
            // 降级：隐藏图片槽，把时间文本塞进 date 行下方（复用 weather 行当时间文本）
            for (int s : slots) rv.setViewVisibility(s, View.GONE);
            rv.setTextViewText(R.id.widget_weather, hhmm);
            rv.setTextViewTextSize(R.id.widget_weather, android.util.TypedValue.COMPLEX_UNIT_SP, timeSize * 0.8f);
            rv.setTextColor(R.id.widget_weather, Color.WHITE);
        }

        mgr.updateAppWidget(id, rv);
    }
}
