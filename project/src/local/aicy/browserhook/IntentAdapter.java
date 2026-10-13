package local.aicy.browserhook;

import android.content.Intent;

public final class IntentAdapter {
    private IntentAdapter() {}

    public static boolean eligible(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        return RoutingPolicy.isBrowserView(intent.getAction(), intent.getData().getScheme(),
                intent.getSelector() != null);
    }

    /** Copy before changing only component/package; keep the exact original Uri and payload. */
    public static Intent retarget(Intent original, String choice) {
        String normalized = RoutingPolicy.normalizeChoice(choice);
        if (BrowserChoices.CUSTOM.equals(normalized)) throw new IllegalArgumentException("Custom browser requires a validated package");
        return retargetPackage(original, RoutingPolicy.DEFAULT.equals(normalized) ? null : normalized);
    }
    static Intent retargetPackage(Intent original, String packageName) {
        Intent result = new Intent(original);
        result.setComponent(null);
        result.setPackage(packageName);
        return result;
    }
}
