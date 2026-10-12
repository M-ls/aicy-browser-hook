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
        Intent result = new Intent(original);
        result.setComponent(null);
        String normalized = RoutingPolicy.normalizeChoice(choice);
        result.setPackage(RoutingPolicy.DEFAULT.equals(normalized) ? null : normalized);
        return result;
    }
}
