package local.aicy.browserhook;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class BrowserHook implements IXposedHookLoadPackage {
    private static final String PREFIX = "[AicyBrowserHook] ";
    private int installedHooks;
    private int markerHooks;
    private int panelHooks;

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!RoutingPolicy.isAicyPackage(lp.packageName)) return;
        boolean modern = RoutingPolicy.VOICE.equals(lp.packageName);
        String jumpClass = modern ? "nc.b" : "d1";
        mark(lp.classLoader, jumpClass, modern ? "J" : "C", "TextSearch");
        mark(lp.classLoader, jumpClass, modern ? "b0" : "U", "WebUrl");
        mark(lp.classLoader, jumpClass, modern ? "c0" : "V", "DefaultBrowserUrl");
        mark(lp.classLoader, "com.flyme.sdk.scan.action.util.WebUtil", "openBrowser", "ScanWebUrl");
        install(lp.classLoader, jumpClass, modern ? "N" : "G", "ActivityJumpTool", Context.class, Intent.class, Bundle.class);
        install(lp.classLoader, "com.flyme.sdk.architecture.utils.AppUtil",
                "startActivityCheckIsOpenSmallWindow", "ScanWebUrl", Context.class, Intent.class);
        panelHooks = WebSearchEntry.install(lp.classLoader, jumpClass, modern ? "J" : "C", modern);
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Context context = (Context) param.args[0];
                if (!lp.packageName.equals(context.getPackageName())) return;
                Bundle event = new Bundle();
                event.putString("event", "loaded");
                event.putString("package", lp.packageName);
                event.putInt("hooks", installedHooks);
                event.putInt("markers", markerHooks);
                event.putInt("panel_hooks", panelHooks);
                try { event.putString("version", context.getPackageManager().getPackageInfo(lp.packageName, 0).versionName); }
                catch (Exception ignored) { event.putString("version", "未知"); }
                report(context, event);
            }
        });
        XposedBridge.log(PREFIX + "loaded in " + lp.processName + "; hooks=" + installedHooks + "; paths=" + markerHooks + "; panel=" + panelHooks);
    }

    private void mark(ClassLoader loader, String className, String method, final String entry) {
        try {
            XposedHelpers.findAndHookMethod(className, loader, method, Context.class, String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) { BrowserEntryScope.enter(entry); }
                @Override protected void afterHookedMethod(MethodHookParam param) { BrowserEntryScope.exit(); }
            });
            markerHooks++;
        } catch (Throwable failure) {
            XposedBridge.log(PREFIX + "missing browser path " + className + "." + method);
        }
    }

    private void install(ClassLoader loader, String className, String method, final String entry, Object... types) {
        Object[] arguments = new Object[types.length + 1];
        System.arraycopy(types, 0, arguments, 0, types.length);
        arguments[types.length] = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try { redirect(param, entry); }
                catch (Throwable failure) {
                    XposedBridge.log(PREFIX + entry + ": " + failure.getClass().getSimpleName());
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod(className, loader, method, arguments);
            installedHooks++;
        } catch (Throwable failure) {
            XposedBridge.log(PREFIX + "missing entry " + className + "." + method);
        }
    }

    private void redirect(XC_MethodHook.MethodHookParam param, String entry) {
        String browserEntry = BrowserEntryScope.current();
        if (browserEntry == null) return;
        Context context = (Context) param.args[0];
        Intent original = (Intent) param.args[1];
        if (context == null || !IntentAdapter.eligible(original)) return;
        Bundle config;
        try { config = context.getContentResolver().call(ConfigProvider.URI, "get", null, null); }
        catch (Exception failure) {
            error(context, "读取设置失败；保留原始调用");
            return;
        }
        if (config == null || !config.getBoolean("enabled", true)) return;
        String choice = RoutingPolicy.normalizeChoice(config.getString("choice", RoutingPolicy.CHROME));
        Intent redirected = BrowserResolver.resolve(context.getPackageManager(), original, choice);
        boolean fallback = false;
        if (redirected == null && !RoutingPolicy.DEFAULT.equals(choice)) {
            redirected = BrowserResolver.resolve(context.getPackageManager(), original, RoutingPolicy.DEFAULT);
            fallback = true;
        }
        if (redirected == null) {
            error(context, "没有可处理此网址的浏览器；保留原始调用");
            return;
        }
        ResolveInfo resolver = context.getPackageManager().resolveActivity(redirected, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolver == null) return;
        param.args[1] = redirected;
        Bundle event = new Bundle();
        event.putString("event", "route");
        event.putString("target", resolver.activityInfo.packageName);
        event.putString("entry", browserEntry);
        event.putBoolean("fallback", fallback);
        report(context, event);
        // Do not log the URL, search text, or extras.
        XposedBridge.log(PREFIX + browserEntry + " -> " + resolver.activityInfo.packageName + (fallback ? " (fallback)" : ""));
    }
    private static void error(Context context, String message) {
        Bundle event = new Bundle();
        event.putString("event", "error");
        event.putString("error", message);
        report(context, event);
    }
    private static void report(Context context, Bundle event) {
        try { context.getContentResolver().call(ConfigProvider.URI, "status", null, event); }
        catch (Exception ignored) { }
    }
}
