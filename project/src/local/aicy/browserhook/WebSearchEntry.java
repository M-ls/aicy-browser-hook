package local.aicy.browserhook;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.TypedValue;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Adds an independent button to the native word-selection panel. */
public final class WebSearchEntry {
    private static final String PANEL = "com.meizu.picker.ParticipleWordActivity";
    private static final String TAG = "local.aicy.browserhook.web_search";
    private WebSearchEntry() {}

    public static int install(ClassLoader loader, String jumpClass, String searchMethod, boolean modern) {
        final String webMethod = modern ? "b0" : "U";
        XC_MethodHook refresh = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try { refresh((Activity) param.thisObject, loader, jumpClass, searchMethod, webMethod); }
                catch (Throwable failure) { log("panel update", failure); }
            }
        };
        int hooks = 0;
        hooks += hook(loader, "onCreate", Bundle.class, refresh);
        hooks += hook(loader, "onResume", null, refresh);
        hooks += hook(loader, modern ? "r0" : "U", boolean.class, refresh);
        return hooks;
    }

    private static int hook(ClassLoader loader, String method, Class<?> type, XC_MethodHook callback) {
        try {
            if (type == null) XposedHelpers.findAndHookMethod(PANEL, loader, method, callback);
            else XposedHelpers.findAndHookMethod(PANEL, loader, method, type, callback);
            return 1;
        } catch (Throwable failure) { log("missing panel method " + method, failure); return 0; }
    }

    private static void refresh(Activity activity, ClassLoader loader, String jumpClass, String searchMethod, String webMethod) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        View bottom = namedView(activity, "popup_bottom_container");
        View words = namedView(activity, "participle_word_layout");
        View aiItem = namedView(activity, "common_item_1");
        View aiText = namedView(activity, "common_text_1");
        View aiImage = namedView(activity, "common_image_1");
        if (!(bottom instanceof LinearLayout) || words == null || !(aiText instanceof TextView)
                || aiItem == null || !(aiItem.getParent() instanceof LinearLayout) || aiImage == null) return;
        LinearLayout container = (LinearLayout) bottom;
        LinearLayout row = (LinearLayout) aiItem.getParent();
        TextView reference = (TextView) aiText;
        LinearLayout button = container.findViewWithTag(TAG);
        if (button == null) {
            button = new LinearLayout(activity);
            button.setTag(TAG);
            button.setOrientation(LinearLayout.VERTICAL);
            button.setGravity(Gravity.CENTER);
            button.setMinimumHeight(dp(activity, 48));
            button.setContentDescription("网页搜索");
            button.setFocusable(true);
            GradientDrawable rippleMask = new GradientDrawable();
            rippleMask.setColor(Color.WHITE);
            rippleMask.setCornerRadius(dp(activity, 4));
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), null, rippleMask));
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(new SearchIcon(dp(activity, 28)));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            button.addView(icon, new LinearLayout.LayoutParams((LinearLayout.LayoutParams) aiImage.getLayoutParams()));
            TextView label = new TextView(activity);
            label.setText("网页搜索");
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, reference.getTextSize());
            label.setTypeface(reference.getTypeface());
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            button.addView(label, new LinearLayout.LayoutParams((LinearLayout.LayoutParams) reference.getLayoutParams()));
            button.setOnClickListener(view -> search(activity, words, loader, jumpClass, searchMethod, webMethod));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) aiItem.getLayoutParams());
            row.addView(button, row.indexOfChild(aiItem), params);
            for (int i = 0; i < row.getChildCount(); i++) {
                View action = row.getChildAt(i);
                LinearLayout.LayoutParams equal = (LinearLayout.LayoutParams) action.getLayoutParams();
                equal.width = 0;
                equal.weight = 1;
                action.setLayoutParams(equal);
            }
            XposedBridge.log("[AicyBrowserHook] web search button added in " + activity.getPackageName());
        }
        Bundle config = activity.getContentResolver().call(ConfigProvider.URI, "get", null, null);
        boolean visible = config != null && config.getBoolean("enabled", true) && config.getBoolean("web_search_entry", true);
        button.setVisibility(visible ? View.VISIBLE : View.GONE);
        button.setEnabled(!TextUtils.isEmpty(selectedText(words).trim()));
        int color = reference.getCurrentTextColor();
        ((TextView) button.getChildAt(1)).setTextColor(color);
        ((SearchIcon) ((ImageView) button.getChildAt(0)).getDrawable()).setInk(color);
    }

    private static void search(Activity activity, View words, ClassLoader loader, String jumpClass, String searchMethod, String webMethod) {
        try {
            Bundle config = activity.getContentResolver().call(ConfigProvider.URI, "get", null, null);
            if (config == null || !config.getBoolean("enabled", true) || !config.getBoolean("web_search_entry", true)) return;
            String selected = selectedText(words);
            if (TextUtils.isEmpty(selected.trim())) {
                Toast.makeText(activity, "请先选择要搜索的文字", Toast.LENGTH_SHORT).show();
                return;
            }
            String url = SearchEngines.buildUrl(config.getString("search_engine", SearchEngines.AICY),
                    config.getString("search_template", ""), selected);
            Class<?> jump = XposedHelpers.findClass(jumpClass, loader);
            if (url == null) {
                // Preserve Aicy's configured engine, parameters and existing query encoding.
                XposedHelpers.callStaticMethod(jump, searchMethod, activity, Uri.encode(selected));
            } else {
                // Use Aicy's known web-URL path so existing browser routing remains in force.
                BrowserEntryScope.enter("TextSearch");
                try { XposedHelpers.callStaticMethod(jump, webMethod, activity, url); }
                finally { BrowserEntryScope.exit(); }
            }
        } catch (IllegalArgumentException invalidTemplate) {
            Toast.makeText(activity, invalidTemplate.getMessage() + "，请在模块设置中检查", Toast.LENGTH_LONG).show();
        } catch (Throwable failure) {
            log("web search click", failure);
            Toast.makeText(activity, "网页搜索未成功，请检查模块状态", Toast.LENGTH_SHORT).show();
        }
    }

    private static String selectedText(View words) {
        Object result = XposedHelpers.callMethod(words, "getSelectedText");
        return result == null ? "" : result.toString();
    }
    private static View namedView(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }
    private static int dp(Activity activity, int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private static final class SearchIcon extends Drawable {
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;
        SearchIcon(int size) {
            this.size = size;
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeWidth(1.8f);
            ink.setStrokeCap(Paint.Cap.ROUND);
        }
        void setInk(int color) { ink.setColor(color); invalidateSelf(); }
        @Override public void draw(Canvas canvas) {
            int saved = canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            canvas.drawCircle(10, 10, 6.5f, ink);
            canvas.drawOval(new RectF(7, 3.5f, 13, 16.5f), ink);
            canvas.drawLine(3.5f, 10, 16.5f, 10, ink);
            canvas.drawLine(15, 15, 21, 21, ink);
            canvas.restoreToCount(saved);
        }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int alpha) { ink.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { ink.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
    private static void log(String operation, Throwable failure) {
        XposedBridge.log("[AicyBrowserHook] " + operation + ": " + failure.getClass().getSimpleName());
    }
}
