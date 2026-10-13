package local.aicy.browserhook;

/** Stable package IDs shared by the settings UI and browser routing. */
public final class BrowserChoices {
    public static final String CUSTOM = "custom";
    private static final String[][] CHOICES = {
        {"com.android.chrome", "Chrome"},
        {"mark.via", "Via"},
        {"com.microsoft.emmx", "Microsoft Edge"},
        {"org.mozilla.firefox", "Firefox"},
        {"com.brave.browser", "Brave"},
        {"com.sec.android.app.sbrowser", "Samsung Internet"},
        {"com.opera.browser", "Opera"},
        {"com.vivaldi.browser", "Vivaldi"},
        {"com.duckduckgo.mobile.android", "DuckDuckGo"},
        {"com.quark.browser", "夸克"},
        {"com.UCMobile", "UC 浏览器"},
        {"com.tencent.mtt", "QQ 浏览器"},
        {"default", "系统默认浏览器"},
        {CUSTOM, "自定义浏览器"}
    };
    private BrowserChoices() {}

    public static int count() { return CHOICES.length; }
    public static String idAt(int index) { return CHOICES[index][0]; }
    public static String labelAt(int index) { return CHOICES[index][1]; }
    public static int indexOf(String id) {
        for (int i = 0; i < CHOICES.length; i++) if (CHOICES[i][0].equals(id)) return i;
        return 0;
    }
    public static String normalize(String id) { return idAt(indexOf(id)); }
}
