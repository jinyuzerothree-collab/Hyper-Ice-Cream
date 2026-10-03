package com.zcode.themetool;

import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/** 假信号浮层（显示层，保证可见）：在状态栏右上角绘制信号格图标。
 *  纯视觉伪装——不产生真实网络。位置/显隐由 SharedPreferences 控制。 */
public class FakeSignalService extends Service {
    private View overlay;
    private WindowManager wm;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new SignalView(this);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        int sb = getResources().getIdentifier("status_bar_height", "dimen", "android");
        lp.y = sb > 0 ? getResources().getDimensionPixelSize(sb) / 4 : 4;
        lp.x = getSharedPreferences("fake_signal", MODE_PRIVATE).getInt("offset_x", 260);
        try {
            wm.addView(overlay, lp);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (overlay != null && wm != null) wm.removeView(overlay);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    /** 自绘信号图标：四格 + 4G 文本 */
    private static class SignalView extends View {
        SignalView(android.content.Context c) { super(c); }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.WHITE);
            int w = getWidth(), h = getHeight();
            if (w == 0) { w = 120; h = 34; }
            int bars = 4;
            float bw = w * 0.16f;
            float gap = w * 0.05f;
            float bh = h * 0.62f;
            float bottom = h * 0.9f;
            for (int i = 0; i < bars; i++) {
                float left = i * (bw + gap);
                float bhi = bh * (0.35f + 0.21f * i);
                cv.drawRect(left, bottom - bhi, left + bw, bottom, p);
            }
            p.setTextSize(h * 0.42f);
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            cv.drawText("4G", w * 0.70f, bottom - bh * 0.1f, p);
        }

        @Override
        protected void onMeasure(int wms, int hms) {
            setMeasuredDimension(130, 36);
        }
    }
}
