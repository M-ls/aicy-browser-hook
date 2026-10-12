package local.aicy.browserhook;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
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
            for (String choice : new String[] {RoutingPolicy.CHROME, RoutingPolicy.VIA, RoutingPolicy.DEFAULT}) {
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
        for (String choice : new String[] {RoutingPolicy.CHROME, RoutingPolicy.VIA, RoutingPolicy.DEFAULT}) {
            for (String type : new String[] {null, "*/*"}) {
                Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(urls[0]), type);
                intent.setClassName(RoutingPolicy.STOCK, "com.android.browser.BrowserActivity");
                Intent routed = BrowserResolver.resolve(context.getPackageManager(), intent, choice);
                check(routed != null, "installed browser must resolve normal and MIME cards");
                check(urls[0].equals(routed.getDataString()), "resolved browser URL preserved");
                check(type == null ? routed.getType() == null : type.equals(routed.getType()), "resolved browser MIME preserved");
                check(context.getPackageManager().resolveActivity(routed, 0) != null, "actual intent resolves");
                check(intent.getComponent().getPackageName().equals(RoutingPolicy.STOCK), "resolver does not mutate source");
            }
        }
        System.out.println("PASS " + checks + " Android Intent checks; no activities launched");
    }
    private static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
}
