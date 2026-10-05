package com.hyper.icecream;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 时钟小组件布局编辑器（实验性）：六元素自由调整，顶部实时预览。
 * 预览画布与桌面渲染共用 HyperWidgetProvider.renderCanvas。
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

    /** 单元素卡：显隐 / X / Y / 字号 / 对齐 / 颜色 / 粗体 */
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

        addSlider(card, key, night, "wl_pos_x", e.x, v -> layout.el(key).x = v);
        addSlider(card, key, night, "wl_pos_y", e.y, v -> layout.el(key).y = v);
        addSlider(card, key, night, "wl_size", e.s, v -> layout.el(key).s = v);

        // 对齐
        LinearLayout aRow = new LinearLayout(this);
        aRow.setGravity(Gravity.CENTER_VERTICAL);
        aRow.addView(smallLabel(L10n.t(this, "wl_align")));
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        final String[] as = {L10n.t(this, "wl_left"), L10n.t(this, "wl_center"), L10n.t(this, "wl_right")};
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(as[i]);
            rb.setId(300 + i);
            rb.setChecked(e.a == i);
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((g, id) -> {
            layout.el(key).a = id - 300;
            renderPreview();
        });
        LinearLayout.LayoutParams rglp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rglp.leftMargin = dp(8);
        aRow.addView(rg, rglp);
        card.addView(aRow);

        // 颜色
        LinearLayout cRow = new LinearLayout(this);
        cRow.setGravity(Gravity.CENTER_VERTICAL);
        cRow.addView(smallLabel(L10n.t(this, "wl_color")));
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
            cRow.addView(chip);
        }
        card.addView(cRow);

        CheckBox bold = new CheckBox(this);
        bold.setText(L10n.t(this, "wl_bold"));
        bold.setChecked(e.b);
        bold.setOnCheckedChangeListener((b, on) -> {
            layout.el(key).b = on;
            renderPreview();
        });
        card.addView(bold);

        cards.put(key, card);
        return card;
    }

    /** 带标签和百分比读数的滑杆 */
    private void addSlider(LinearLayout parent, final String key, boolean night,
                           String labelKey, float init, final Setter setter) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView lb = smallLabel(L10n.t(this, labelKey));
        row.addView(lb);
        final TextView val = new TextView(this);
        val.setTextSize(11);
        val.setTextColor(night ? 0xB3FFFFFF : 0x99111111);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = dp(4);
        row.addView(val, vlp);
        SeekBar sb = new SeekBar(this);
        sb.setMax(1000);
        sb.setProgress((int) (init * 1000));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        slp.leftMargin = dp(8);
        row.addView(sb, slp);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                setter.set(p / 1000f);
                val.setText((p / 10) + "%");
                if (fromUser) renderPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        val.setText((sb.getProgress() / 10) + "%");
        parent.addView(row);
    }

    private interface Setter { void set(float v); }

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
            Bitmap b = HyperWidgetProvider.renderCanvas(this, 480, 960);
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
