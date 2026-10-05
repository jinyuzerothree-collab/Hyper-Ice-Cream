package com.hyper.icecream;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 时钟小组件布局编辑器（实验性）：六元素数字微调，顶部实时预览。
 * 每元素：左右偏移 / 上下偏移（dp）、大小（%，100=原始）、字体粗细（细/常规/粗/特粗）、
 * 颜色色片、显隐。所有数值相对原有布局基线。
 */
public class WidgetLayoutActivity extends Activity {

    private static final int[] PALETTE = {
            0xFFFFFFFF, 0xFF000000, 0xFFFF80AB, 0xFF82B1FF, 0xFFFFE57F, 0xFF69F0AE};

    private WidgetLayout layout;
    private ImageView preview;
    private final java.util.HashMap<String, LinearLayout> cards = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        L10n.init(this);
        layout = WidgetLayout.load(this);

        boolean night = isNight();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                night ? new int[]{0xFF1B2438, 0xFF241B33, 0xFF0F141F}
                      : new int[]{0xFFFFD3E2, 0xFFE3D4FF, 0xFFFFEDF0}));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView title = new TextView(this);
        title.setText(L10n.t(this, "wl_title"));
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setTextColor(night ? 0xFFFFFFFF : 0xFF111111);
        body.addView(title);

        TextView sub = new TextView(this);
        sub.setText(L10n.t(this, "wl_sub"));
        sub.setTextSize(12);
        sub.setTextColor(night ? 0xB3FFFFFF : 0x99111111);
        sub.setPadding(0, dp(4), 0, dp(8));
        body.addView(sub);

        // 预览面板（深色圆角底，模拟桌面深色壁纸）
        FrameLayout pvWrap = new FrameLayout(this);
        GradientDrawable pvBg = new GradientDrawable();
        pvBg.setColor(0xFF23262E);
        pvBg.setCornerRadius(dp(16));
        pvWrap.setBackground(pvBg);
        pvWrap.setPadding(dp(10), dp(14), dp(10), dp(14));
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(true);
        pvWrap.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300), Gravity.CENTER));
        body.addView(pvWrap);

        TextView note = new TextView(this);
        note.setText(L10n.t(this, "wl_note"));
        note.setTextSize(11);
        note.setTextColor(night ? 0x80FFFFFF : 0x80111111);
        note.setPadding(0, dp(6), 0, dp(4));
        body.addView(note);

        for (String k : WidgetLayout.KEYS) body.addView(buildCard(k, night));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button reset = new Button(this);
        reset.setText(L10n.t(this, "wl_reset"));
        reset.setOnClickListener(v -> {
            layout.resetDefault();
            refreshAllControls();
            renderPreview();
        });
        Button preset = new Button(this);
        preset.setText(L10n.t(this, "wl_preset_theme"));
        preset.setOnClickListener(v -> {
            layout.applyThemePreset();
            refreshAllControls();
            renderPreview();
        });
        btnRow.addView(reset, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnRow.addView(preset, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams brlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brlp.topMargin = dp(8);
        body.addView(btnRow, brlp);

        Button apply = new Button(this);
        apply.setText(L10n.t(this, "wl_apply"));
        apply.setOnClickListener(v -> {
            layout.save(this);
            HyperWidgetProvider.renderAll(this);
            Toast.makeText(this, L10n.t(this, "wl_applied"), Toast.LENGTH_LONG).show();
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(6);
        body.addView(apply, alp);

        setContentView(root);
        renderPreview();
    }

    /** 单元素卡：显隐 / 左右偏移 / 上下偏移 / 大小 / 粗细 / 颜色（全部数字输入） */
    private LinearLayout buildCard(final String key, boolean night) {
        WidgetLayout.El e = layout.el(key);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(night ? 0x33FFFFFF : 0x59FFFFFF);
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(10);
        card.setLayoutParams(clp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(L10n.t(this, "wl_el_" + key));
        name.setTextSize(15);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        name.setTextColor(night ? 0xFFFFFFFF : 0xFF111111);
        head.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        CheckBox show = new CheckBox(this);
        show.setText(L10n.t(this, "wl_show"));
        show.setChecked(e.on);
        show.setOnCheckedChangeListener((b, on) -> {
            layout.el(key).on = on;
            renderPreview();
        });
        head.addView(show);
        card.addView(head);

        // 左右偏移 / 上下偏移（dp，可负）
        card.addView(numRow(key, night, L10n.t(this, "wl_pos_x"), String.valueOf(e.dx),
                L10n.t(this, "wl_unit_dp"), true, v -> layout.el(key).dx = v));
        card.addView(numRow(key, night, L10n.t(this, "wl_pos_y"), String.valueOf(e.dy),
                L10n.t(this, "wl_unit_dp"), true, v -> layout.el(key).dy = v));
        // 大小（%，100 = 原始）
        card.addView(numRow(key, night, L10n.t(this, "wl_size"),
                String.valueOf(Math.round(e.s * 100)), L10n.t(this, "wl_unit_pct"),
                false, v -> layout.el(key).s = v));
        // 字体粗细
        card.addView(weightRow(key, e.w));
        // 颜色
        card.addView(colorRow(key));

        cards.put(key, card);
        return card;
    }

    /** 数字输入行：标签 + 输入框 + 单位 */
    private LinearLayout numRow(final String key, boolean night, String label, String value,
                                String unit, boolean signed, final IntSetter setter) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView lb = smallLabel(label);
        row.addView(lb, new LinearLayout.LayoutParams(dp(72),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        final EditText et = new EditText(this);
        et.setText(value);
        et.setInputType(signed
                ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED)
                : InputType.TYPE_CLASS_NUMBER);
        et.setTextSize(13);
        et.setSingleLine();
        et.setGravity(Gravity.CENTER);
        et.setPadding(dp(4), dp(2), dp(4), dp(2));
        GradientDrawable eg = new GradientDrawable();
        eg.setColor(night ? 0x22FFFFFF : 0x2E000000);
        eg.setCornerRadius(dp(8));
        et.setBackground(eg);
        row.addView(et, new LinearLayout.LayoutParams(dp(72),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView u = smallLabel(unit);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ulp.leftMargin = dp(6);
        row.addView(u, ulp);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                try {
                    setter.set(Integer.parseInt(s.toString().trim()));
                } catch (Throwable bad) {
                    try {
                        setter.set((int) Float.parseFloat(s.toString().trim()));
                    } catch (Throwable bad2) { }
                }
                renderPreview();
            }
        });
        return row;
    }

    /** 字体粗细单选行：细 / 常规 / 粗 / 特粗 */
    private LinearLayout weightRow(final String key, int cur) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(smallLabel(L10n.t(this, "wl_weight")),
                new LinearLayout.LayoutParams(dp(72),
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        final String[] ws = {L10n.t(this, "wl_thin"), L10n.t(this, "wl_regular"),
                L10n.t(this, "wl_wbold"), L10n.t(this, "wl_wblack")};
        for (int i = 0; i < ws.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(ws[i]);
            rb.setId(400 + i);
            rb.setChecked(cur == i);
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((g, id) -> {
            layout.el(key).w = id - 400;
            renderPreview();
        });
        LinearLayout.LayoutParams rglp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rglp.leftMargin = dp(6);
        row.addView(rg, rglp);
        return row;
    }

    /** 颜色色片行 */
    private LinearLayout colorRow(final String key) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(smallLabel(L10n.t(this, "wl_color")),
                new LinearLayout.LayoutParams(dp(72),
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        for (final int col : PALETTE) {
            View chip = new View(this);
            GradientDrawable cg = new GradientDrawable();
            cg.setColor(col);
            cg.setShape(GradientDrawable.OVAL);
            cg.setStroke(dp(1), col == layout.el(key).c ? 0xFFFF4081 : 0x44000000);
            chip.setBackground(cg);
            LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(dp(26), dp(26));
            clp2.leftMargin = dp(8);
            chip.setLayoutParams(clp2);
            chip.setOnClickListener(v -> {
                layout.el(key).c = col;
                refreshAllControls();
                renderPreview();
            });
            row.addView(chip);
        }
        return row;
    }

    private interface IntSetter { void set(int v); }

    private TextView smallLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** 全量刷新所有控件（预设/重置/换色后调用）：整卡原地重建 */
    private void refreshAllControls() {
        for (String k : WidgetLayout.KEYS) {
            LinearLayout card = cards.get(k);
            if (card == null) continue;
            ViewGroup parent = (ViewGroup) card.getParent();
            if (parent == null) continue;
            int pos = parent.indexOfChild(card);
            parent.removeView(card);
            parent.addView(buildCard(k, isNight()), pos);
        }
    }

    private void renderPreview() {
        try {
            int w = 480, h = 960, wDp = 480;
            try {
                // 优先用桌面已添加组件的真实宽高（解决预览与桌面不同步）
                android.appwidget.AppWidgetManager mgr =
                        android.appwidget.AppWidgetManager.getInstance(this);
                int[] ids = mgr.getAppWidgetIds(
                        new android.content.ComponentName(this, HyperWidgetProvider.class));
                if (ids.length > 0) {
                    android.os.Bundle o = mgr.getAppWidgetOptions(ids[0]);
                    float den = getResources().getDisplayMetrics().density;
                    int wDpR = Math.max(o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110),
                            o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 110));
                    int hDpR = Math.max(o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 220),
                            o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220));
                    w = Math.max(120, Math.min(1200, (int) (wDpR * den)));
                    h = Math.max(240, Math.min(2400, (int) (hDpR * den)));
                    wDp = Math.max(1, wDpR);
                }
            } catch (Throwable ig) {
            }
            Bitmap b = HyperWidgetProvider.renderCanvas(this, w, h, wDp);
            preview.setImageBitmap(b);
        } catch (Throwable ignored) {
        }
    }

    private boolean isNight() {
        int m = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
