package local.aicy.browserhook;

import java.util.ArrayDeque;

/** Marks only the synchronous, known browser entry paths; no destination app blacklist. */
public final class BrowserEntryScope {
    private static final ThreadLocal<ArrayDeque<String>> ENTRIES = new ThreadLocal<ArrayDeque<String>>() {
        @Override protected ArrayDeque<String> initialValue() { return new ArrayDeque<String>(); }
    };
    private BrowserEntryScope() {}
    public static void enter(String entry) { ENTRIES.get().push(entry); }
    public static void exit() {
        ArrayDeque<String> stack = ENTRIES.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) ENTRIES.remove();
    }
    public static String current() { return ENTRIES.get().peekLast(); }
}
