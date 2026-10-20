package local.aicy.browserhook;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.net.Uri;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private int expectedHooks;
    private int expectedMarkers;
    private boolean initialized;
    private final Set<Method> markedMethods = new HashSet<>();
    private final Set<Method> hookedMethods = new HashSet<>();
    private Method legacySearch;

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!RoutingPolicy.isAicyPackage(lp.packageName)) return;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Context context = (Context) param.args[0];
                if (initialized || !lp.packageName.equals(context.getPackageName())) return;
                initialized = true;
                installHooks(context, lp);
            }
        });
    }

    private void installHooks(Context context, XC_LoadPackage.LoadPackageParam lp) {
        long started = android.os.SystemClock.elapsedRealtime();
        boolean embeddedPicker = RoutingPolicy.VOICE.equals(lp.packageName);
        boolean legacy = installMappedRoutes(lp.classLoader, embeddedPicker ? "nc.b" : "d1",
                embeddedPicker ? "J" : "C", embeddedPicker ? "b0" : "U",
                embeddedPicker ? "c0" : "V", embeddedPicker ? "N" : "G");
        boolean discovered = !legacy && discoverRoutes(context, lp.classLoader);
        if (legacy) {
            markOptionalEntry(lp.classLoader, "com.flyme.sdk.scan.action.util.WebUtil", "openBrowser", "ScanWebUrl");
            installOptionalHook(lp.classLoader, "com.flyme.sdk.architecture.utils.AppUtil",
                    "startActivityCheckIsOpenSmallWindow", Context.class, Intent.class);
            expectedHooks = Math.max(2, expectedHooks);
            expectedMarkers = Math.max(4, expectedMarkers);
        } else if (!discovered) {
            try {
                if (context.getPackageManager().getPackageInfo(lp.packageName, 0).getLongVersionCode() == 13000052L) {
                    installMappedRoutes(lp.classLoader, "com.google.android.gms.internal.mlkit_vision_common.U2",
                            null, "u", "v", "m");
                }
            } catch (Exception ignored) { }
        }
        expectedHooks = Math.max(1, expectedHooks);
        expectedMarkers = Math.max(2, expectedMarkers);
        panelHooks = WebSearchEntry.install(lp.classLoader, legacySearch);
        String adaptation = discovered ? "特征识别" : legacy ? "旧版兼容" : "兼容映射 / 部分入口未匹配";
        reportLoaded(context, lp.packageName, adaptation);
        XposedBridge.log(PREFIX + "loaded in " + lp.processName + "; hooks=" + installedHooks + "/" + expectedHooks
                + "; paths=" + markerHooks + "/" + expectedMarkers + "; panel=" + panelHooks
                + "; scan_ms=" + (android.os.SystemClock.elapsedRealtime() - started));
    }

    private void reportLoaded(Context context, String packageName, String adaptation) {
        Bundle event = new Bundle();
        event.putString("event", "loaded");
        event.putString("package", packageName);
        event.putInt("hooks", installedHooks);
        event.putInt("markers", markerHooks);
        event.putInt("panel_hooks", panelHooks);
        event.putInt("expected_panel_hooks", 2);
        event.putInt("expected_hooks", expectedHooks);
        event.putInt("expected_markers", expectedMarkers);
        event.putString("adaptation", adaptation);
        try { event.putString("version", context.getPackageManager().getPackageInfo(packageName, 0).versionName); }
        catch (Exception ignored) { event.putString("version", "未知"); }
        report(context, event);
    }

    private boolean discoverRoutes(Context context, ClassLoader loader) {
        boolean hasLaunchHook = false;
        try {
            List<File> apks = new ArrayList<>();
            apks.add(new File(context.getApplicationInfo().sourceDir));
            String[] splits = context.getApplicationInfo().splitSourceDirs;
            if (splits != null) {
                for (String split : splits) apks.add(new File(split));
            }
            List<DexRoutes.Target> targets = DexRoutes.find(apks.toArray(new File[0]));
            // 先核对整组方法，避免匹配到一半才发现签名不符。
            List<Method> methods = new ArrayList<>();
            for (DexRoutes.Target target : targets) {
                Class<?>[] parameters;
                if (DexRoutes.SEARCH.equals(target.kind)) parameters = new Class<?>[] {android.view.View.class};
                else if (DexRoutes.START.equals(target.kind)) parameters = new Class<?>[] {Context.class, Intent.class, Bundle.class};
                else parameters = new Class<?>[] {Context.class, String.class};
                Method method = findMethod(loader, target.className, target.methodName, parameters);
                boolean start = DexRoutes.START.equals(target.kind);
                if (method.getReturnType() != (start ? boolean.class : void.class)
                        || (!DexRoutes.SEARCH.equals(target.kind) && !Modifier.isStatic(method.getModifiers()))) return false;
                methods.add(method);
            }
            for (int i = 0; i < targets.size(); i++) {
                DexRoutes.Target target = targets.get(i);
                if (DexRoutes.START.equals(target.kind)) {
                    install(methods.get(i));
                    hasLaunchHook = true;
                } else {
                    mark(methods.get(i), target.kind);
                }
            }
        } catch (Throwable failure) { XposedBridge.log(PREFIX + "feature scan unavailable: " + failure.getClass().getSimpleName()); }
        return hasLaunchHook;
    }

    private static Method findMethod(ClassLoader loader, String className, String name, Class<?>... types) throws Exception {
        Method method = Class.forName(className, false, loader).getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }
    private boolean installMappedRoutes(ClassLoader loader, String owner, String search, String web, String defaults, String start) {
        try {
            Method launch = findMethod(loader, owner, start, Context.class, Intent.class, Bundle.class);
            Method webMethod = findMethod(loader, owner, web, Context.class, String.class);
            Method defaultMethod = findMethod(loader, owner, defaults, Context.class, String.class);
            if (!Modifier.isStatic(launch.getModifiers()) || launch.getReturnType() != boolean.class
                    || !Modifier.isStatic(webMethod.getModifiers()) || webMethod.getReturnType() != void.class
                    || !Modifier.isStatic(defaultMethod.getModifiers()) || defaultMethod.getReturnType() != void.class) return false;
            install(launch);
            mark(webMethod, "WebUrl");
            mark(defaultMethod, "DefaultBrowserUrl");
            if (search != null) {
                Method method = findMethod(loader, owner, search, Context.class, String.class);
                if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == void.class) {
                    legacySearch = method;
                    mark(method, "TextSearch");
                }
            }
            return true;
        } catch (Throwable ignored) { return false; }
    }
    private void markOptionalEntry(ClassLoader loader, String owner, String name, String entry) {
        try { mark(findMethod(loader, owner, name, Context.class, String.class), entry); }
        catch (Throwable ignored) { }
    }
    private void installOptionalHook(ClassLoader loader, String owner, String name, Class<?>... types) {
        try { install(findMethod(loader, owner, name, types)); }
        catch (Throwable ignored) { }
    }
    private void mark(Method method, final String entry) {
        if (!markedMethods.add(method)) return;
        expectedMarkers++;
        try {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) { BrowserEntryScope.enter(entry); }
                @Override protected void afterHookedMethod(MethodHookParam param) { BrowserEntryScope.exit(); }
            });
            markerHooks++;
        } catch (Throwable failure) {
            XposedBridge.log(PREFIX + "could not mark " + method.getDeclaringClass().getName() + "." + method.getName());
        }
    }

    private void install(Method method) {
        if (!hookedMethods.add(method)) return;
        expectedHooks++;
        XC_MethodHook hook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try { redirect(param); }
                catch (Throwable failure) {
                    XposedBridge.log(PREFIX + "redirect: " + failure.getClass().getSimpleName());
                }
            }
        };
        try {
            XposedBridge.hookMethod(method, hook);
            installedHooks++;
        } catch (Throwable failure) {
            XposedBridge.log(PREFIX + "could not hook " + method.getDeclaringClass().getName() + "." + method.getName());
        }
    }

    private void redirect(XC_MethodHook.MethodHookParam param) {
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
        Route route = route(context, original, config);
        if (route == null) {
            error(context, "没有可处理此网址的浏览器；保留原始调用");
            return;
        }
        param.args[1] = route.intent;
        routed(context, browserEntry, route);
    }

    private static Route route(Context context, Intent original, Bundle config) {
        String choice = RoutingPolicy.normalizeChoice(config.getString("choice", RoutingPolicy.CHROME));
        Intent redirected = BrowserResolver.resolve(context.getPackageManager(), original, choice,
                config.getString("custom_browser_package", ""));
        boolean fallback = false;
        if (redirected == null && !RoutingPolicy.DEFAULT.equals(choice)) {
            redirected = BrowserResolver.resolve(context.getPackageManager(), original, RoutingPolicy.DEFAULT);
            fallback = true;
        }
        if (redirected == null) return null;
        ResolveInfo resolver = context.getPackageManager().resolveActivity(redirected, PackageManager.MATCH_DEFAULT_ONLY);
        return resolver == null || resolver.activityInfo == null ? null : new Route(redirected, resolver.activityInfo.packageName, fallback);
    }

    static void openSearch(Context context, Bundle config, String url) {
        Intent original = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        original.addCategory(Intent.CATEGORY_BROWSABLE);
        // 分词面板是浮动 Activity，让浏览器使用自己的任务栈。
        original.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Route route = route(context, original, config);
        if (route == null) throw new android.content.ActivityNotFoundException("No browser handles web searches");
        context.startActivity(route.intent);
        routed(context, "TextSearch", route);
    }
    private static final class Route {
        final Intent intent;
        final String target;
        final boolean fallback;
        Route(Intent intent, String target, boolean fallback) {
            this.intent = intent;
            this.target = target;
            this.fallback = fallback;
        }
    }
    private static void routed(Context context, String entry, Route route) {
        Bundle event = new Bundle();
        event.putString("event", "route");
        event.putString("target", route.target);
        event.putString("entry", entry);
        event.putBoolean("fallback", route.fallback);
        report(context, event);
        // Do not log the URL, search text, or extras.
        XposedBridge.log(PREFIX + entry + " -> " + route.target + (route.fallback ? " (fallback)" : ""));
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
