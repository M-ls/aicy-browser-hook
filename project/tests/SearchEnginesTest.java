import local.aicy.browserhook.SearchEngines;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.Set;

/** Validate URLs and decoded queries without contacting search providers. */
public final class SearchEnginesTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        String query = "猫 咪 +&=#%/? 😀";
        String[][] providers = {
            {"baidu", "www.baidu.com", "wd"},
            {"bing", "www.bing.com", "q"},
            {"google", "www.google.com", "q"},
            {"360", "m.so.com", "q"},
            {"sogou", "www.sogou.com", "query"},
            {"shenma", "m.sm.cn", "q"},
            {"duckduckgo", "duckduckgo.com", "q"},
            {"brave", "search.brave.com", "q"},
            {"startpage", "www.startpage.com", "query"},
            {"ecosia", "www.ecosia.org", "q"},
            {"qwant", "www.qwant.com", "q"},
            {"yahoo", "search.yahoo.com", "p"},
            {"yandex", "yandex.com", "text"},
            {"mojeek", "www.mojeek.com", "q"},
            {"kagi", "kagi.com", "q"},
            {"swisscows", "swisscows.com", "query"},
            {"yep", "yep.com", "q"}
        };
        check(SearchEngines.count() == providers.length + 2, "all presets plus follow and custom");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < SearchEngines.count(); i++) {
            check(ids.add(SearchEngines.idAt(i)), "engine IDs are unique");
            check(SearchEngines.indexOf(SearchEngines.idAt(i)) == i, "selection ID round trip");
        }
        for (String[] provider : providers) {
            URI url = new URI(SearchEngines.buildUrl(provider[0], null, query));
            check("https".equals(url.getScheme()), provider[0] + " uses HTTPS");
            check(provider[1].equals(url.getHost()), provider[0] + " target host");
            check(query.equals(parameter(url, provider[2])), provider[0] + " raw query round trip");
        }
        check(SearchEngines.buildUrl(SearchEngines.AICY, "bad", query) == null, "follow Aicy does not build a URL");
        check(SearchEngines.buildUrl(null, null, query) == null, "missing config retains Aicy");
        check(SearchEngines.buildUrl("future-engine", null, query) == null, "unknown config retains Aicy");
        check(SearchEngines.AICY.equals(SearchEngines.normalize("future-engine")), "unknown selection normalized");
        check("literal %E4%B8%AD".equals(parameter(new URI(SearchEngines.buildUrl("bing", null, "literal %E4%B8%AD")), "q")), "literal percent encoding is text");
        check(" a b ".equals(parameter(new URI(SearchEngines.buildUrl("bing", null, " a b ")), "q")), "selected whitespace is preserved");
        check("猫\n咪".equals(parameter(new URI(SearchEngines.buildUrl("bing", null, "猫\n咪")), "q")), "line breaks are encoded");
        check("/web".equals(new URI(SearchEngines.buildUrl("swisscows", null, "cat")).getPath()), "Swisscows official OpenSearch route");

        String path = SearchEngines.buildUrl(SearchEngines.CUSTOM, " https://example.com/find/{query}?fixed=yes#top ", "a/b &+");
        check("https://example.com/find/a%2Fb%20%26%2B?fixed=yes#top".equals(path), "path search preserves fixed URL parts");
        URI repeated = new URI(SearchEngines.buildUrl(SearchEngines.CUSTOM, "https://example.com/?q={query}&again={query}#fixed", query));
        check(query.equals(parameter(repeated, "q")) && query.equals(parameter(repeated, "again")), "all placeholders replaced");
        check("fixed".equals(repeated.getRawFragment()), "query characters do not inject a fragment");
        check("https://example.com/#/find/a%2Fb".equals(SearchEngines.buildUrl(SearchEngines.CUSTOM, "https://example.com/#/find/{query}", "a/b")), "fragment-based search");
        check("http://localhost:8080/?q=cat".equals(SearchEngines.buildUrl(SearchEngines.CUSTOM, "http://localhost:8080/?q={query}", "cat")), "custom HTTP endpoint and port");
        check("https://[::1]/?q=cat".equals(SearchEngines.buildUrl(SearchEngines.CUSTOM, "https://[::1]/?q={query}", "cat")), "custom IPv6 endpoint");
        check(SearchEngines.templateError("HTTPS://example.com/?q={query}") == null, "case-insensitive web scheme");
        check(SearchEngines.templateError("https://example.com/?q={query}&fixed=a%20b") == null, "fixed percent-encoded parameter accepted");

        String[] invalid = {null, "", "https://example.com/?q=cat", "intent://example.com/?q={query}",
            "javascript:{query}", "ftp://example.com/{query}", "https:example.com/{query}",
            "//example.com/?q={query}", "https:///find/{query}", "https://{query}.example.com/search",
            "https://example.com:{query}/search", "https://user:password@example.com/?q={query}",
            "https://user-{query}@example.com/search", "https://example.com/?q={query}&fixed=a b",
            "https://example.com/?q={query}&fixed=%zz", "https://example.com/?q={query}\n",
            "https://example.com/?q={query}&other={unknown}", "https://example.com:65536/?q={query}"};
        for (String template : invalid) check(SearchEngines.templateError(template) != null, "invalid template rejected");
        StringBuilder tooLong = new StringBuilder("https://example.com/?q={query}&fixed=");
        while (tooLong.length() <= 4096) tooLong.append('a');
        check(SearchEngines.templateError(tooLong.toString()) != null, "oversized template rejected");
        expectInvalid(SearchEngines.CUSTOM, "https://example.com/", query);
        expectInvalid(SearchEngines.CUSTOM, "", query);
        expectInvalid("bing", null, "  ");
        expectInvalid("bing", null, null);
        System.out.println("PASS " + checks + " search-engine and URL-template checks");
    }
    private static String parameter(URI url, String key) throws Exception {
        String value = null;
        for (String part : url.getRawQuery().split("&")) {
            String[] pair = part.split("=", 2);
            if (key.equals(pair[0])) {
                if (value != null) throw new AssertionError("duplicate query parameter");
                value = URLDecoder.decode(pair.length == 2 ? pair[1] : "", "UTF-8");
            }
        }
        return value;
    }
    private static void expectInvalid(String engine, String template, String query) {
        boolean rejected = false;
        try { SearchEngines.buildUrl(engine, template, query); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid search cannot produce a URL");
    }
    private static void check(boolean result, String name) { checks++; if (!result) throw new AssertionError(name); }
}
