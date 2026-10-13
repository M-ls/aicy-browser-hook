package local.aicy.browserhook;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;

/** Runs with app_process on the phone; constructs Intents without launching any activity. */
public final class AndroidIntentTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        String[] urls = {
            "https://m.so.com/s?q=宠物%20医保&src=home&srcg=cs_meizu_11&nav=1",
            "https://example.com/image?token=A%2BB%2F%3D&url=https%3A%2F%2Fa.b#image",
            "http://example.com/search?q=猫+dog%26x&empty=&q=second"
        };
        for (String url : urls) {
            Intent original = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            original.setClassName(RoutingPolicy.STOCK, "com.android.browser.BrowserActivity");
            original.addCategory(Intent.CATEGORY_BROWSABLE);
            original.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            original.putExtra("com.android.browser.application_id", RoutingPolicy.PICKER);
            original.putExtra("disable_splash_ad", true);
            original.setClipData(ClipData.newPlainText("test", "payload"));
            check(IntentAdapter.eligible(original), "stock browser eligible");
            for (int i = 0; i < BrowserChoices.count(); i++) {
                String choice = BrowserChoices.idAt(i);
                if (BrowserChoices.CUSTOM.equals(choice)) continue;
                Intent routed = IntentAdapter.retarget(original, choice);
                check(url.equals(routed.getDataString()), "URL must be byte-for-byte identical");
                check(routed.getComponent() == null, "stock component removed");
                check(RoutingPolicy.DEFAULT.equals(choice) ? routed.getPackage() == null : choice.equals(routed.getPackage()), "configured package");
                check(original.getFlags() == routed.getFlags(), "flags preserved");
                check(original.getAction().equals(routed.getAction()), "action preserved");
                check(routed.hasCategory(Intent.CATEGORY_BROWSABLE), "category preserved");
                check(RoutingPolicy.PICKER.equals(routed.getStringExtra("com.android.browser.application_id")), "caller extra preserved");
                check(routed.getBooleanExtra("disable_splash_ad", false), "boolean extra preserved");
                check("payload".contentEquals(routed.getClipData().getItemAt(0).getText()), "clip data preserved");
                check(original.getComponent() != null, "original intent unchanged");
            }
        }
        Intent mime = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("https://example.com/"), "*/*");
        check("*/*".equals(IntentAdapter.retarget(mime, RoutingPolicy.VIA).getType()), "MIME type preserved");
        Intent alipay = new Intent(Intent.ACTION_VIEW, Uri.parse("https://qr.alipay.com/test")).setPackage("com.eg.android.AlipayGphone");
        check(IntentAdapter.eligible(alipay), "web URLs have no destination app blacklist");
        Intent wechat = new Intent(Intent.ACTION_VIEW, Uri.parse("weixin://test"));
        check(!IntentAdapter.eligible(wechat), "WeChat URI untouched");
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepareMainLooper();
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Object thread = threadClass.getMethod("systemMain").invoke(null);
        Context context = (Context) threadClass.getMethod("getSystemContext").invoke(thread);
        for (int i = 0; i < BrowserChoices.count(); i++) {
            String choice = BrowserChoices.idAt(i);
            if (BrowserChoices.CUSTOM.equals(choice)) continue;
            Intent availability = new Intent(Intent.ACTION_VIEW, Uri.parse(urls[0]));
            if (!RoutingPolicy.DEFAULT.equals(choice)) availability.setPackage(choice);
            boolean installed = context.getPackageManager().resolveActivity(availability, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY) != null;
            for (String type : new String[] {null, "*/*"}) {
                Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(urls[0]), type);
                intent.setClassName(RoutingPolicy.STOCK, "com.android.browser.BrowserActivity");
                Intent routed = BrowserResolver.resolve(context.getPackageManager(), intent, choice);
                if (!installed) {
                    check(routed == null, "unavailable browser does not resolve: " + choice);
                    continue;
                }
                check(routed != null, "installed browser must resolve normal and MIME cards");
                check(RoutingPolicy.DEFAULT.equals(choice) || choice.equals(routed.getPackage()), "installed browser uses selected package");
                check(urls[0].equals(routed.getDataString()), "resolved browser URL preserved");
                check(type == null ? routed.getType() == null : type.equals(routed.getType()), "resolved browser MIME preserved");
                check(context.getPackageManager().resolveActivity(routed, 0) != null, "actual intent resolves");
                check(intent.getComponent().getPackageName().equals(RoutingPolicy.STOCK), "resolver does not mutate source");
            }
        }
        for (String invalid : new String[] {null, "", "github.com/search?q=cat", "https://github.com", "com.github.android;am start", "com..browser", ".com.browser", "com.browser "})
            check(BrowserValidator.packageError(invalid) != null, "invalid package name rejected");
        check(BrowserValidator.packageError("com.UCMobile.intl") == null, "valid browser package format");
        ResolveInfo browserEntry = handler("test.browser");
        browserEntry.filter = new IntentFilter(Intent.ACTION_MAIN);
        browserEntry.filter.addCategory(Intent.CATEGORY_APP_BROWSER);
        check(BrowserValidator.isBrowserEntry(browserEntry, "test.browser"), "declared browser app entry accepted");
        browserEntry.filter = new IntentFilter(Intent.ACTION_MAIN);
        check(!BrowserValidator.isBrowserEntry(browserEntry, "test.browser"), "ordinary app entry rejected");
        ResolveInfo generic = handler("test.browser");
        check(BrowserValidator.isGenericHandler(generic, "test.browser", "http"), "generic HTTP browser accepted");
        check(BrowserValidator.isGenericHandler(generic, "test.browser", "https"), "generic HTTPS browser accepted");
        ResolveInfo github = handler("com.github.android");
        github.filter.addDataAuthority("github.com", null);
        check(!BrowserValidator.isGenericHandler(github, "com.github.android", "https"), "GitHub domain-only handler rejected");
        ResolveInfo taobao = handler("com.taobao.taobao");
        taobao.filter.addDataAuthority("*.taobao.com", null);
        check(!BrowserValidator.isGenericHandler(taobao, "com.taobao.taobao", "https"), "Taobao domain-only handler rejected");
        ResolveInfo pathOnly = handler("test.browser");
        pathOnly.filter.addDataPath("/search", android.os.PatternMatcher.PATTERN_LITERAL);
        check(!BrowserValidator.isGenericHandler(pathOnly, "test.browser", "https"), "path-restricted handler rejected");
        ResolveInfo schemePartOnly = handler("test.browser");
        schemePartOnly.filter.addDataSchemeSpecificPart("//example.com/", android.os.PatternMatcher.PATTERN_LITERAL);
        check(!BrowserValidator.isGenericHandler(schemePartOnly, "test.browser", "https"), "scheme-part-restricted handler rejected");
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            ResolveInfo relative = handler("test.browser");
            relative.filter.addUriRelativeFilterGroup(new android.content.UriRelativeFilterGroup(android.content.UriRelativeFilterGroup.ACTION_ALLOW));
            check(!BrowserValidator.isGenericHandler(relative, "test.browser", "https"), "URI-relative restrictions rejected");
        }
        generic.activityInfo.exported = false;
        check(!BrowserValidator.isGenericHandler(generic, "test.browser", "http"), "private activity rejected");
        generic.activityInfo.exported = true;
        generic.activityInfo.enabled = false;
        check(!BrowserValidator.isGenericHandler(generic, "test.browser", "http"), "disabled activity rejected");
        generic.activityInfo.enabled = true;
        generic.activityInfo.permission = "android.permission.WRITE_SECURE_SETTINGS";
        check(!BrowserValidator.isGenericHandler(generic, "test.browser", "http"), "permission-protected activity rejected");
        generic.activityInfo.permission = null;
        check(!BrowserValidator.isGenericHandler(generic, "different.package", "http"), "foreign package activity rejected");
        check(BrowserValidator.error(context.getPackageManager(), RoutingPolicy.CHROME) == null, "Chrome accepts custom-browser validation");
        check(BrowserValidator.error(context.getPackageManager(), RoutingPolicy.VIA) == null, "Via accepts custom-browser validation");
        for (String nonBrowser : args) {
            context.getPackageManager().getApplicationInfo(nonBrowser, 0);
            check(BrowserValidator.error(context.getPackageManager(), nonBrowser) != null, "installed non-browser rejected: " + nonBrowser);
        }
        Intent customSource = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(urls[0]), "*/*");
        customSource.setClassName(RoutingPolicy.STOCK, "com.android.browser.BrowserActivity");
        Intent custom = BrowserResolver.resolve(context.getPackageManager(), customSource, BrowserChoices.CUSTOM, RoutingPolicy.CHROME);
        check(custom != null && RoutingPolicy.CHROME.equals(custom.getPackage()), "valid custom package used");
        check(urls[0].equals(custom.getDataString()) && "*/*".equals(custom.getType()), "custom route preserves URL and MIME");
        check(RoutingPolicy.STOCK.equals(customSource.getComponent().getPackageName()), "custom route does not mutate source");
        check(BrowserResolver.resolve(context.getPackageManager(), customSource, BrowserChoices.CUSTOM, "") == null, "missing custom package rejected");
        check(BrowserResolver.resolve(context.getPackageManager(), customSource, BrowserChoices.CUSTOM, "local.aicy.browserhook") == null, "installed non-browser rejected");
        check(BrowserResolver.resolve(context.getPackageManager(), customSource, RoutingPolicy.DEFAULT) != null, "system fallback remains available");
        System.out.println("PASS " + checks + " Android Intent and custom-browser checks; no activities launched");
        // ActivityThread can start OEM worker threads; stop this standalone test process.
        System.exit(0);
    }
    private static ResolveInfo handler(String packageName) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = packageName;
        info.activityInfo.name = "TestBrowserActivity";
        info.activityInfo.exported = true;
        info.activityInfo.enabled = true;
        info.filter = new IntentFilter(Intent.ACTION_VIEW);
        info.filter.addCategory(Intent.CATEGORY_DEFAULT);
        info.filter.addCategory(Intent.CATEGORY_BROWSABLE);
        info.filter.addDataScheme("http");
        info.filter.addDataScheme("https");
        return info;
    }
    private static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
}
