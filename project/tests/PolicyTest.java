import local.aicy.browserhook.RoutingPolicy;
import local.aicy.browserhook.BrowserEntryScope;

public final class PolicyTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        String view = "android.intent.action.VIEW";
        for (String scheme : new String[] {"http", "https", "HTTPS"})
            check(RoutingPolicy.isBrowserView(view, scheme, false), "web route");
        for (String scheme : new String[] {null, "alipay", "weixin", "content", "file", "rtsp", "javascript"})
            check(!RoutingPolicy.isBrowserView(view, scheme, false), "only web browser URLs");
        check(!RoutingPolicy.isBrowserView("android.intent.action.SEND", "https", false), "share must survive");
        check(!RoutingPolicy.isBrowserView(view, "https", true), "selector must survive");
        check(RoutingPolicy.VIA.equals(RoutingPolicy.normalizeChoice(RoutingPolicy.VIA)), "Via config");
        check(RoutingPolicy.DEFAULT.equals(RoutingPolicy.normalizeChoice(RoutingPolicy.DEFAULT)), "default config");
        check(RoutingPolicy.CHROME.equals(RoutingPolicy.normalizeChoice("unexpected")), "invalid config bounded");
        check(BrowserEntryScope.current() == null, "generic app launches outside browser entry");
        BrowserEntryScope.enter("V");
        check("V".equals(BrowserEntryScope.current()), "browser entry marked");
        BrowserEntryScope.enter("U");
        check("V".equals(BrowserEntryScope.current()), "nested fallback retains outer entry");
        BrowserEntryScope.exit();
        check("V".equals(BrowserEntryScope.current()), "inner exit preserves outer context");
        final String[] otherThread = new String[1];
        Thread worker = new Thread(() -> otherThread[0] = BrowserEntryScope.current());
        worker.start(); worker.join();
        check(otherThread[0] == null, "browser entry cannot leak across threads");
        BrowserEntryScope.exit();
        check(BrowserEntryScope.current() == null, "scope cleared after return");
        try { BrowserEntryScope.enter("C"); throw new IllegalStateException("test"); }
        catch (IllegalStateException expected) { }
        finally { BrowserEntryScope.exit(); }
        check(BrowserEntryScope.current() == null, "scope cleared on exceptional return");
        BrowserEntryScope.exit();
        check(BrowserEntryScope.current() == null, "empty cleanup is safe");
        System.out.println("PASS " + checks + " routing and browser-entry checks");
    }
    private static void check(boolean result, String name) { checks++; if (!result) throw new AssertionError(name); }
}
