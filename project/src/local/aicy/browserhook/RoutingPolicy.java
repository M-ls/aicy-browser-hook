package local.aicy.browserhook;

/** Routing decisions are independent of URL construction and Android launch APIs. */
public final class RoutingPolicy {
    public static final String PICKER = "com.meizu.picker";
    public static final String VOICE = "com.meizu.voiceassistant";
    public static final String CHROME = "com.android.chrome";
    public static final String VIA = "mark.via";
    public static final String STOCK = "com.android.browser";
    public static final String DEFAULT = "default";
    private RoutingPolicy() {}

    public static boolean isAicyPackage(String packageName) {
        return PICKER.equals(packageName) || VOICE.equals(packageName);
    }

    public static String normalizeChoice(String choice) {
        return VIA.equals(choice) || DEFAULT.equals(choice) ? choice : CHROME;
    }

    public static boolean isBrowserView(String action, String scheme, boolean hasSelector) {
        if (hasSelector || !"android.intent.action.VIEW".equals(action)) return false;
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        return true;
    }
}
