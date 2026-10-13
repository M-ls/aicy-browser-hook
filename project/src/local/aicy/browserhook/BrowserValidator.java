package local.aicy.browserhook;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;

/** Accept only installed apps declaring unrestricted, exported web handlers. */
public final class BrowserValidator {
    private BrowserValidator() {}

    public static String packageError(String value) {
        if (value == null || value.isEmpty()) return "请输入浏览器应用包名";
        if (value.length() > 255 || !value.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))
            return "包名格式不正确，例如 com.android.chrome";
        return null;
    }
    public static String error(PackageManager manager, String packageName) {
        String format = packageError(packageName);
        if (format != null) return format;
        try {
            ApplicationInfo app = manager.getApplicationInfo(packageName, 0);
            if (!app.enabled) return "该应用已停用，请先启用浏览器";
            Intent browserEntry = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER).setPackage(packageName);
            boolean declaresBrowser = false;
            for (ResolveInfo candidate : manager.queryIntentActivities(browserEntry, PackageManager.GET_RESOLVED_FILTER)) {
                if (isBrowserEntry(candidate, packageName)) { declaresBrowser = true; break; }
            }
            if (!declaresBrowser) return "该应用未声明浏览器入口，不能作为自定义浏览器";
            for (String scheme : new String[] {"http", "https"}) {
                Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse(scheme + "://example.com/"));
                probe.addCategory(Intent.CATEGORY_BROWSABLE);
                probe.setPackage(packageName);
                boolean generic = false;
                for (ResolveInfo candidate : manager.queryIntentActivities(probe,
                        PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER)) {
                    if (isGenericHandler(candidate, packageName, scheme)) { generic = true; break; }
                }
                if (!generic) return "该应用不支持通用 HTTP/HTTPS 网页打开，不能作为自定义浏览器";
            }
            return null;
        } catch (PackageManager.NameNotFoundException missing) {
            return "未找到可用浏览器，请确认包名正确、应用已安装并提供浏览器入口";
        } catch (RuntimeException unavailable) {
            return "无法检查该应用，请稍后重试";
        }
    }
    static boolean isGenericHandler(ResolveInfo candidate, String packageName, String scheme) {
        if (!isLaunchable(candidate, packageName) || candidate.filter == null) return false;
        IntentFilter filter = candidate.filter;
        if (Build.VERSION.SDK_INT >= 35 && filter.countUriRelativeFilterGroups() != 0) return false;
        return filter.hasAction(Intent.ACTION_VIEW) && filter.hasCategory(Intent.CATEGORY_DEFAULT)
                && filter.hasCategory(Intent.CATEGORY_BROWSABLE) && filter.hasDataScheme(scheme)
                && filter.countDataAuthorities() == 0 && filter.countDataPaths() == 0
                && filter.countDataSchemeSpecificParts() == 0;
    }
    static boolean isBrowserEntry(ResolveInfo candidate, String packageName) {
        return isLaunchable(candidate, packageName) && candidate.filter != null
                && candidate.filter.hasAction(Intent.ACTION_MAIN)
                && candidate.filter.hasCategory(Intent.CATEGORY_APP_BROWSER);
    }
    private static boolean isLaunchable(ResolveInfo candidate, String packageName) {
        return candidate != null && candidate.activityInfo != null && candidate.activityInfo.exported
                && candidate.activityInfo.enabled && candidate.activityInfo.permission == null
                && packageName.equals(candidate.activityInfo.packageName);
    }
}
