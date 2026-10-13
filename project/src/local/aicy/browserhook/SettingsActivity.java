package local.aicy.browserhook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.Date;

/** Lightweight native MD2-style screen: 56dp toolbar, 4dp cards/buttons, MD2 typography. */
public final class SettingsActivity extends Activity {
    private static final int PURPLE = Color.rgb(98, 0, 238);
    private static final int INK = Color.rgb(33, 33, 33);
    private static final int MUTED = Color.rgb(97, 97, 97);
    private TextView statusText;
    private SharedPreferences prefs;
    private Switch launcherSwitch;
    private EditText searchTemplateInput;
    private EditText browserPackageInput;
    private Spinner browserChoicesInput;
    private final ScrollView[] pages = new ScrollView[2];
    private final LinearLayout[] navigationItems = new LinearLayout[2];
    private final TextView[] navigationLabels = new TextView[2];
    private final ImageView[] navigationIcons = new ImageView[2];
    private int activePage;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = ConfigProvider.preferences(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        View statusScrim = new View(this);
        statusScrim.setBackgroundColor(Color.rgb(55, 0, 179));
        root.addView(statusScrim, new LinearLayout.LayoutParams(-1, 0));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets system = insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(system.left, 0, system.right, system.bottom);
            android.view.ViewGroup.LayoutParams scrimParams = statusScrim.getLayoutParams();
            if (scrimParams.height != system.top) {
                scrimParams.height = system.top;
                statusScrim.setLayoutParams(scrimParams);
            }
            return insets;
        });
        TextView toolbar = text("Aicy 浏览器入口", 20, Color.WHITE);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(20), 0, dp(16), 0);
        toolbar.setBackgroundColor(PURPLE);
        toolbar.setElevation(dp(4));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));
        LinearLayout content = addPage(root, 0);
        LinearLayout statusContent = addPage(root, 1);
        addBottomNavigation(root);

        LinearLayout settings = card(content);
        Switch enabled = new Switch(this);
        enabled.setText("启用浏览器重定向");
        enabled.setTextSize(16);
        enabled.setTextColor(INK);
        enabled.setMinHeight(dp(48));
        enabled.setChecked(prefs.getBoolean("enabled", true));
        enabled.setOnCheckedChangeListener((view, checked) -> prefs.edit().putBoolean("enabled", checked).apply());
        settings.addView(enabled, new LinearLayout.LayoutParams(-1, -2));
        Switch webSearch = new Switch(this);
        webSearch.setText("显示网页搜索入口");
        webSearch.setTextSize(16);
        webSearch.setTextColor(INK);
        webSearch.setMinHeight(dp(48));
        webSearch.setChecked(prefs.getBoolean("web_search_entry", true));
        webSearch.setOnCheckedChangeListener((view, checked) -> prefs.edit().putBoolean("web_search_entry", checked).apply());
        settings.addView(webSearch, new LinearLayout.LayoutParams(-1, -2));
        settings.addView(text("在识屏的“分词”面板中选择文字，再点“网页搜索”。搜索引擎可在下方选择；关闭重定向时新增入口也隐藏。", 12, MUTED));
        TextView label = text("目标浏览器", 14, MUTED);
        label.setPadding(0, dp(20), 0, dp(8));
        settings.addView(label);
        Spinner browsers = new Spinner(this, Spinner.MODE_DIALOG);
        browserChoicesInput = browsers;
        browsers.setPrompt("选择浏览器");
        browsers.setContentDescription("目标浏览器");
        browsers.setMinimumHeight(dp(48));
        String[] browserLabels = new String[BrowserChoices.count()];
        for (int i = 0; i < browserLabels.length; i++) {
            String choice = BrowserChoices.idAt(i);
            boolean installed = RoutingPolicy.DEFAULT.equals(choice) || BrowserChoices.CUSTOM.equals(choice) || available(choice);
            browserLabels[i] = BrowserChoices.labelAt(i) + (installed ? "" : "（未安装）");
        }
        ArrayAdapter<String> browserOptions = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, browserLabels);
        browserOptions.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        browsers.setAdapter(browserOptions);
        int browserSelection = BrowserChoices.indexOf(prefs.getString("choice", RoutingPolicy.CHROME));
        if (state != null) browserSelection = state.getInt("browser_draft_selection", browserSelection);
        browsers.setSelection(browserSelection);
        settings.addView(browsers, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout customBrowser = new LinearLayout(this);
        customBrowser.setOrientation(LinearLayout.VERTICAL);
        customBrowser.setPadding(0, dp(12), 0, 0);
        customBrowser.addView(text("浏览器应用包名", 14, MUTED));
        browserPackageInput = new EditText(this);
        browserPackageInput.setTextSize(14);
        browserPackageInput.setTextColor(INK);
        browserPackageInput.setSingleLine(true);
        browserPackageInput.setHint("com.android.chrome");
        browserPackageInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        browserPackageInput.setContentDescription("自定义浏览器应用包名");
        String savedPackage = prefs.getString("custom_browser_package", "");
        browserPackageInput.setText(state == null ? savedPackage : state.getString("browser_package_draft", savedPackage));
        customBrowser.addView(browserPackageInput, new LinearLayout.LayoutParams(-1, -2));
        TextView customHelp = text("仅支持已安装并启用、声明了浏览器入口且能处理通用 HTTP/HTTPS 网页的应用。请先测试网页，再保存包名。", 12, MUTED);
        customHelp.setPadding(0, dp(4), 0, dp(12));
        customBrowser.addView(customHelp);
        Button saveBrowser = new Button(this);
        saveBrowser.setText("保存包名");
        saveBrowser.setTextSize(14);
        saveBrowser.setTextColor(Color.WHITE);
        saveBrowser.setAllCaps(false);
        saveBrowser.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape(PURPLE, 4), null));
        saveBrowser.setElevation(dp(2));
        saveBrowser.setOnClickListener(view -> {
            String packageName = browserPackageInput.getText().toString().trim();
            String error = BrowserValidator.error(getPackageManager(), packageName);
            if (error != null) {
                browserPackageInput.setError(error);
                browserPackageInput.requestFocus();
                return;
            }
            String appName = packageName;
            try { appName = getPackageManager().getApplicationInfo(packageName, 0).loadLabel(getPackageManager()).toString(); }
            catch (PackageManager.NameNotFoundException ignored) { }
            new AlertDialog.Builder(this).setTitle("确认自定义浏览器")
                    .setMessage("应用：" + appName + "\n包名：" + packageName
                            + "\n\n该应用将接收 Aicy 网页链接和网页搜索网址。请确认它是你要使用的浏览器，并已正常打开测试网页。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确认保存", (dialog, which) -> saveCustomBrowser(packageName))
                    .show();
        });
        Button testBrowser = new Button(this);
        testBrowser.setText("测试打开网页");
        testBrowser.setTextSize(14);
        testBrowser.setTextColor(PURPLE);
        testBrowser.setAllCaps(false);
        testBrowser.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1F6200EE), shape(Color.WHITE, 4), null));
        testBrowser.setOnClickListener(view -> testCustomBrowser());
        LinearLayout browserActions = new LinearLayout(this);
        browserActions.setGravity(Gravity.END);
        browserActions.addView(testBrowser, new LinearLayout.LayoutParams(-2, dp(48)));
        LinearLayout.LayoutParams browserSaveParams = new LinearLayout.LayoutParams(-2, dp(48));
        browserSaveParams.leftMargin = dp(8);
        browserSaveParams.gravity = Gravity.END;
        browserActions.addView(saveBrowser, browserSaveParams);
        customBrowser.addView(browserActions, new LinearLayout.LayoutParams(-1, -2));
        customBrowser.setVisibility(BrowserChoices.CUSTOM.equals(BrowserChoices.idAt(browserSelection)) ? View.VISIBLE : View.GONE);
        settings.addView(customBrowser, new LinearLayout.LayoutParams(-1, -2));
        browsers.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String choice = BrowserChoices.idAt(position);
                boolean custom = BrowserChoices.CUSTOM.equals(choice);
                customBrowser.setVisibility(custom ? View.VISIBLE : View.GONE);
                if (!custom || BrowserValidator.error(getPackageManager(), prefs.getString("custom_browser_package", "")) == null)
                    prefs.edit().putString("choice", choice).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        TextView hint = text("预置选项自动保存；自定义浏览器需保存包名。所选浏览器不可用时，尝试使用系统默认浏览器。", 12, MUTED);
        hint.setPadding(0, dp(12), 0, 0);
        settings.addView(hint);

        addSearchSettings(content, state);

        LinearLayout appearance = card(content);
        launcherSwitch = new Switch(this);
        launcherSwitch.setText("显示桌面入口");
        launcherSwitch.setTextSize(16);
        launcherSwitch.setTextColor(INK);
        launcherSwitch.setMinHeight(dp(48));
        launcherSwitch.setChecked(isLauncherVisible());
        launcherSwitch.setOnCheckedChangeListener((view, checked) -> {
            if (checked == isLauncherVisible()) return;
            try {
                // Disable only the launcher alias; keep the LSPosed settings activity available.
                getPackageManager().setComponentEnabledSetting(launcherComponent(), checked
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            } catch (RuntimeException failure) {
                launcherSwitch.setChecked(isLauncherVisible());
                Toast.makeText(this, "桌面入口设置未成功，请重试", Toast.LENGTH_SHORT).show();
            }
        });
        appearance.addView(launcherSwitch, new LinearLayout.LayoutParams(-1, -2));
        appearance.addView(text("隐藏后可从 LSPosed 的模块页面打开设置；重新开启即可恢复桌面图标。", 12, MUTED));

        content.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        TextView signature = text("Codex 敬上 · 美工水平随缘，甩锅功能未启用。", 12, Color.rgb(117, 117, 117));
        signature.setGravity(Gravity.CENTER);
        signature.setPadding(dp(8), dp(8), dp(8), 0);
        content.addView(signature, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout status = card(statusContent);
        status.addView(title("Hook 状态"));
        statusText = text("", 14, MUTED);
        statusText.setLineSpacing(dp(4), 1);
        statusText.setPadding(0, dp(12), 0, dp(16));
        status.addView(statusText);
        Button refresh = new Button(this);
        refresh.setText("刷新状态");
        refresh.setTextSize(14);
        refresh.setTextColor(Color.WHITE);
        refresh.setAllCaps(false);
        GradientDrawable shape = shape(PURPLE, 4);
        refresh.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null));
        refresh.setElevation(dp(2));
        refresh.setOnClickListener(view -> refreshStatus());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-2, dp(40));
        buttonParams.gravity = Gravity.END;
        refresh.setPadding(dp(16), 0, dp(16), 0);
        status.addView(refresh, buttonParams);

        LinearLayout instructions = card(statusContent);
        instructions.addView(title("首次启用"));
        TextView help = text("1. 在 LSPosed 中启用本模块。\n2. 勾选 Aicy 识屏（com.meizu.picker）和语音助手（com.meizu.voiceassistant）。\n3. 重启手机，或重启这两个进程。\n4. 用普通网页二维码或网页卡片测试。", 14, MUTED);
        help.setLineSpacing(dp(5), 1);
        help.setPadding(0, dp(12), 0, dp(12));
        instructions.addView(help);
        instructions.addView(text("新增“网页搜索”使用设置中的搜索引擎；选“跟随 Aicy”时沿用原搜索模板。AI 搜索仍调用语音助手；已有网页跳转保留原始网址与参数，旧搜图功能保持原样。\n\n状态表示调用已改写，是否成功显示网页需要实际点击验证。状态只保存入口、目标包名和次数，不记录搜索词或访问网址。自定义网址模板作为设置保存在本机。", 12, MUTED));
        setContentView(root);
        root.requestApplyInsets();
        selectPage(state == null ? 0 : state.getInt("active_page", 0));
        if (state != null) {
            for (int i = 0; i < pages.length; i++) {
                final ScrollView page = pages[i];
                final int position = state.getInt("page_scroll_" + i, 0);
                page.post(() -> page.scrollTo(0, position));
            }
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if (launcherSwitch != null) launcherSwitch.setChecked(isLauncherVisible());
        if (statusText != null) refreshStatus();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("active_page", activePage);
        if (searchTemplateInput != null) state.putString("search_template_draft", searchTemplateInput.getText().toString());
        if (browserPackageInput != null) state.putString("browser_package_draft", browserPackageInput.getText().toString());
        if (browserChoicesInput != null) state.putInt("browser_draft_selection", browserChoicesInput.getSelectedItemPosition());
        for (int i = 0; i < pages.length; i++) state.putInt("page_scroll_" + i, pages[i].getScrollY());
    }
    private void addSearchSettings(LinearLayout content, Bundle state) {
        LinearLayout search = card(content);
        search.addView(title("搜索引擎"));
        TextView help = text("用于新增的“网页搜索”。切换立即保存，无需重启。", 12, MUTED);
        help.setPadding(0, dp(8), 0, dp(8));
        search.addView(help);
        Spinner engines = new Spinner(this, Spinner.MODE_DIALOG);
        engines.setPrompt("选择搜索引擎");
        engines.setMinimumHeight(dp(48));
        String[] labels = new String[SearchEngines.count()];
        for (int i = 0; i < labels.length; i++) labels[i] = SearchEngines.labelAt(i);
        ArrayAdapter<String> options = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        engines.setAdapter(options);
        int selected = SearchEngines.indexOf(prefs.getString("search_engine", SearchEngines.AICY));
        engines.setSelection(selected);
        search.addView(engines, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout custom = new LinearLayout(this);
        custom.setOrientation(LinearLayout.VERTICAL);
        custom.setPadding(0, dp(12), 0, 0);
        custom.addView(text("网址模板", 14, MUTED));
        searchTemplateInput = new EditText(this);
        searchTemplateInput.setTextSize(14);
        searchTemplateInput.setTextColor(INK);
        searchTemplateInput.setHint("https://example.com/search?q={query}");
        searchTemplateInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchTemplateInput.setMinLines(2);
        searchTemplateInput.setMaxLines(4);
        searchTemplateInput.setGravity(Gravity.TOP | Gravity.START);
        searchTemplateInput.setContentDescription("自定义搜索网址模板");
        String savedTemplate = prefs.getString("search_template", "");
        searchTemplateInput.setText(state == null ? savedTemplate : state.getString("search_template_draft", savedTemplate));
        custom.addView(searchTemplateInput, new LinearLayout.LayoutParams(-1, -2));
        TextView format = text("用 {query} 代替搜索词，只支持 http / https。搜索词会自动编码，无需预先转义。修改后点“保存模板”。", 12, MUTED);
        format.setPadding(0, dp(4), 0, dp(12));
        custom.addView(format);
        Button save = new Button(this);
        save.setText("保存模板");
        save.setTextSize(14);
        save.setTextColor(Color.WHITE);
        save.setAllCaps(false);
        save.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape(PURPLE, 4), null));
        save.setElevation(dp(2));
        save.setPadding(dp(16), 0, dp(16), 0);
        save.setOnClickListener(view -> {
            String template = SearchEngines.trimTemplate(searchTemplateInput.getText().toString());
            String error = SearchEngines.templateError(template);
            if (error != null) {
                searchTemplateInput.setError(error);
                searchTemplateInput.requestFocus();
                return;
            }
            prefs.edit().putString("search_template", template).apply();
            searchTemplateInput.setText(template);
            searchTemplateInput.setError(null);
            Toast.makeText(this, "搜索网址模板已保存", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-2, dp(48));
        saveParams.gravity = Gravity.END;
        custom.addView(save, saveParams);
        custom.setVisibility(SearchEngines.CUSTOM.equals(SearchEngines.idAt(selected)) ? View.VISIBLE : View.GONE);
        search.addView(custom, new LinearLayout.LayoutParams(-1, -2));
        engines.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String engine = SearchEngines.idAt(position);
                prefs.edit().putString("search_engine", engine).apply();
                custom.setVisibility(SearchEngines.CUSTOM.equals(engine) ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }
    private void addBottomNavigation(LinearLayout root) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(dp(8));
        String[] names = {"设置", "状态"};
        int[] icons = {R.drawable.ic_settings, R.drawable.ic_status};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(6), 0, dp(6));
            item.setFocusable(true);
            item.setContentDescription(names[i]);
            item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1F6200EE), null, shape(Color.WHITE, 0)));
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView label = text(names[i], 12, MUTED);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            label.setSingleLine(true);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
            labelParams.topMargin = dp(2);
            item.addView(label, labelParams);
            item.setOnClickListener(view -> selectPage(index));
            bar.addView(item, new LinearLayout.LayoutParams(dp(168), -1));
            navigationItems[i] = item;
            navigationLabels[i] = label;
            navigationIcons[i] = icon;
        }
        bar.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int itemWidth = Math.min(dp(168), (right - left) / navigationItems.length);
            for (LinearLayout item : navigationItems) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) item.getLayoutParams();
                if (params.width != itemWidth) { params.width = itemWidth; item.setLayoutParams(params); }
            }
        });
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));
    }
    private LinearLayout addPage(LinearLayout root, int index) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(250, 250, 250));
        scroll.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(20), dp(16), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        pages[index] = scroll;
        return content;
    }
    private void selectPage(int index) {
        activePage = index == 1 ? 1 : 0;
        for (int i = 0; i < pages.length; i++) {
            boolean selected = i == activePage;
            pages[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            navigationItems[i].setSelected(selected);
            navigationItems[i].setStateDescription(selected ? "已选择" : "未选择");
            int color = selected ? PURPLE : MUTED;
            navigationLabels[i].setTextColor(color);
            navigationLabels[i].setTextSize(selected ? 14 : 12);
            navigationIcons[i].setImageTintList(ColorStateList.valueOf(color));
        }
        if (activePage == 1) refreshStatus();
    }
    private ComponentName launcherComponent() { return new ComponentName(this, getPackageName() + ".Launcher"); }
    private boolean isLauncherVisible() {
        int state = getPackageManager().getComponentEnabledSetting(launcherComponent());
        return state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT || state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }
    private boolean available(String pkg) {
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")).setPackage(pkg);
        return getPackageManager().resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY) != null;
    }
    private void saveCustomBrowser(String packageName) {
        String error = BrowserValidator.error(getPackageManager(), packageName);
        if (error != null) {
            browserPackageInput.setError(error);
            browserPackageInput.requestFocus();
            return;
        }
        prefs.edit().putString("custom_browser_package", packageName).putString("choice", BrowserChoices.CUSTOM).apply();
        browserPackageInput.setText(packageName);
        browserPackageInput.setError(null);
        Toast.makeText(this, "自定义浏览器已保存", Toast.LENGTH_SHORT).show();
    }
    private void testCustomBrowser() {
        String packageName = browserPackageInput.getText().toString().trim();
        String error = BrowserValidator.error(getPackageManager(), packageName);
        if (error != null) {
            browserPackageInput.setError(error);
            browserPackageInput.requestFocus();
            return;
        }
        Intent source = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"));
        source.addCategory(Intent.CATEGORY_BROWSABLE);
        Intent test = BrowserResolver.resolve(getPackageManager(), source, BrowserChoices.CUSTOM, packageName);
        try {
            if (test == null) throw new android.content.ActivityNotFoundException();
            startActivity(test);
            browserPackageInput.setError(null);
        } catch (RuntimeException failure) {
            browserPackageInput.setError("无法打开测试网页，请检查浏览器状态");
            browserPackageInput.requestFocus();
        }
    }
    private void refreshStatus() {
        SharedPreferences status = getSharedPreferences("status", MODE_PRIVATE);
        long loaded = status.getLong("loaded_at", 0);
        String result = loaded == 0 ? "尚未收到 Aicy 的加载报告。\n请先启用模块，再重启 Aicy。" :
                "加载报告：" + time(loaded) + "\n进程包名：" + status.getString("package", "未知")
                + "\nAicy 版本：" + status.getString("version", "未知")
                + "\n已挂接入口：" + status.getInt("hooks", 0) + " / 2"
                + "\n已覆盖网页路径：" + status.getInt("markers", 0) + " / 4"
                + "\n分词面板挂接：" + status.getInt("panel_hooks", 0) + " / 3";
        long routed = status.getLong("route_at", 0);
        if (routed != 0) result += "\n\n最近改写：" + time(routed)
                + "\n入口：" + status.getString("entry", "")
                + "\n目标：" + status.getString("target", "")
                + (status.getBoolean("fallback", false) ? "（回退系统默认）" : "")
                + "\n累计改写：" + status.getInt("count", 0) + " 次";
        long error = status.getLong("error_at", 0);
        if (error > routed) result += "\n\n最近错误：" + status.getString("error", "");
        statusText.setText(result);
    }
    private String time(long value) { return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(value)); }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(shape(Color.WHITE, 4));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(16);
        parent.addView(card, params);
        return card;
    }
    private TextView title(String value) { TextView title = text(value, 16, INK); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return title; }
    private TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view; }
    private GradientDrawable shape(int color, int radius) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
