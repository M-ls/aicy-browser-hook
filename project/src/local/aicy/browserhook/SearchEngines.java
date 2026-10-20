package local.aicy.browserhook;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

/** Builds ordinary web searches from raw selected text; independent of browsers. */
public final class SearchEngines {
    public static final String AICY = "aicy";
    public static final String CUSTOM = "custom";
    public static final String QUERY = "{query}";
    private static final String[][] ENGINES = {
        {AICY, "跟随 Aicy（使用系统配置，如百度）", null},
        {"baidu", "百度", "https://www.baidu.com/s?wd={query}&ie=utf-8"},
        {"bing", "必应", "https://www.bing.com/search?q={query}"},
        {"google", "Google", "https://www.google.com/search?q={query}"},
        {"360", "360 搜索", "https://m.so.com/s?q={query}"},
        {"sogou", "搜狗", "https://www.sogou.com/web?query={query}&ie=utf8"},
        {"shenma", "神马", "https://m.sm.cn/s?q={query}"},
        {"duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q={query}"},
        {"brave", "Brave Search", "https://search.brave.com/search?q={query}"},
        {"startpage", "Startpage", "https://www.startpage.com/sp/search?query={query}"},
        {"ecosia", "Ecosia", "https://www.ecosia.org/search?q={query}"},
        {"qwant", "Qwant（仅部分地区可用）", "https://www.qwant.com/?q={query}&t=web"},
        {"yahoo", "Yahoo", "https://search.yahoo.com/search?p={query}"},
        {"yandex", "Yandex", "https://yandex.com/search/?text={query}"},
        {"mojeek", "Mojeek", "https://www.mojeek.com/search?q={query}"},
        {"kagi", "Kagi（首次使用需在浏览器中登录）", "https://kagi.com/search?q={query}"},
        {"swisscows", "Swisscows", "https://swisscows.com/web?query={query}"},
        {"yep", "Yep", "https://yep.com/web?q={query}"},
        {CUSTOM, "自定义网址模板", null}
    };
    private SearchEngines() {}

    public static int count() { return ENGINES.length; }
    public static String idAt(int index) { return ENGINES[index][0]; }
    public static String labelAt(int index) { return ENGINES[index][1]; }
    public static int indexOf(String id) {
        for (int i = 0; i < ENGINES.length; i++) if (ENGINES[i][0].equals(id)) return i;
        return 0;
    }
    public static String normalize(String id) { return idAt(indexOf(id)); }

    /** null means the unchanged Aicy search path. The text must not be pre-encoded. */
    public static String buildUrl(String engine, String customTemplate, String rawQuery) {
        int index = indexOf(engine);
        if (AICY.equals(idAt(index))) return null;
        if (rawQuery == null || rawQuery.trim().isEmpty()) throw new IllegalArgumentException("请先选择要搜索的文字");
        String template = CUSTOM.equals(idAt(index)) ? trimTemplate(customTemplate) : ENGINES[index][2];
        String error = templateError(template);
        if (error != null) throw new IllegalArgumentException(error);
        try {
            // Encode exactly once; literal '+', '&', '#', '%' and spaces cannot become URL syntax.
            String encoded = URLEncoder.encode(rawQuery, "UTF-8").replace("+", "%20");
            return template.replace(QUERY, encoded);
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }
    public static String trimTemplate(String template) { return template == null ? "" : template.trim(); }
    /** 沿用 Aicy 的模板和固定参数，关键词仍由 buildUrl 统一编码。 */
    public static String buildAicyUrl(String nativeTemplate, String rawQuery) {
        String template = trimTemplate(nativeTemplate);
        if (template.isEmpty()) template = "https://m.so.com/s?q=searchTerms&src=home&srcg=cs_meizu_11&nav=1";
        if (!template.contains("searchTerms")) throw new IllegalArgumentException("无法识别 Aicy 搜索配置，请选择预置搜索引擎");
        return buildUrl(CUSTOM, template.replace("searchTerms", QUERY), rawQuery);
    }
    public static String templateError(String template) {
        if (template == null || template.isEmpty()) return "请输入自定义搜索网址";
        if (template.length() > 4096) return "网址模板过长";
        if (!template.contains(QUERY)) return "网址模板需要包含 {query}";
        try {
            URI probe = new URI(template.replace(QUERY, "query"));
            String scheme = probe.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return "仅支持 http 或 https 网址";
            if (probe.getHost() == null || probe.getHost().isEmpty()) return "请输入完整网址，例如 https://example.com/search?q={query}";
            if (probe.getPort() > 65535) return "网址端口必须在 0 到 65535 之间";
            int authorityEnd = template.indexOf('/', template.indexOf("://") + 3);
            int queryStart = template.indexOf('?');
            if (authorityEnd < 0 || (queryStart >= 0 && queryStart < authorityEnd)) authorityEnd = queryStart;
            int fragmentStart = template.indexOf('#');
            if (authorityEnd < 0 || (fragmentStart >= 0 && fragmentStart < authorityEnd)) authorityEnd = fragmentStart;
            if (authorityEnd < 0 || template.indexOf(QUERY) < authorityEnd) return "请将 {query} 放在网址路径、参数或片段中";
            if (probe.getUserInfo() != null) return "网址模板不能包含用户名或密码";
            return null;
        } catch (URISyntaxException invalid) {
            return "网址格式不正确；固定参数中的空格和特殊字符需要编码";
        }
    }
}
