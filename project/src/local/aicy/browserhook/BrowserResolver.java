package local.aicy.browserhook;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

public final class BrowserResolver {
    private BrowserResolver() {}
    /** Returns a resolvable browser Intent without changing its URI or original MIME payload. */
    public static Intent resolve(PackageManager manager, Intent original, String choice) {
        Intent routed = IntentAdapter.retarget(original, choice);
        if (manager.resolveActivity(routed, PackageManager.MATCH_DEFAULT_ONLY) != null) return routed;
        if (original.getType() == null) return null;
        // Aicy URI cards can carry */*. Resolve a separate web-only probe, then address
        // that browser explicitly; the actual Intent retains its original MIME type.
        Intent probe = new Intent(routed);
        probe.setDataAndType(original.getData(), null);
        ResolveInfo browser = manager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
        if (browser == null || browser.activityInfo == null || !browser.activityInfo.exported) return null;
        // A resolver dialog is not a browser; explicitly launching it would not solve MIME matching.
        String pkg = browser.activityInfo.packageName;
        if ("android".equals(pkg) || "com.android.intentresolver".equals(pkg)) return null;
        routed.setComponent(new ComponentName(pkg, browser.activityInfo.name));
        return routed;
    }
}
