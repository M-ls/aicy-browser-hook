# Hook 范围与适配

作用域仅为 `com.meizu.picker` 与 `com.meizu.voiceassistant`。只在已标记的网页入口内部改写 HTTP/HTTPS 的 ACTION_VIEW Intent，非网页 URI、分享及带 selector 的调用保留。

| 入口 | 独立识屏 12.2.34 | 助手内嵌识屏 12.1.46 |
| --- | --- | --- |
| 普通文字搜索 | `d1.C(Context, String)` | `nc.b.J(Context, String)` |
| 直接网页 | `d1.U(Context, String)` | `nc.b.b0(Context, String)` |
| 默认浏览器网页 | `d1.V(Context, String)` | `nc.b.c0(Context, String)` |
| 启动汇入口 | `d1.G(Context, Intent, Bundle)` | `nc.b.N(Context, Intent, Bundle)` |

两者还标记 `com.flyme.sdk.scan.action.util.WebUtil.openBrowser(Context, String)`，并挂接 `com.flyme.sdk.architecture.utils.AppUtil.startActivityCheckIsOpenSmallWindow(Context, Intent)`。

网页入口标记按线程保存，嵌套调用与异常返回恢复作用域。AI 搜索 `d1.a` / `nc.b.a` 不挂接。

新增搜索按钮挂接 `com.meizu.picker.ParticipleWordActivity` 的 `onCreate`、`onResume` 及选词更新方法（旧 `U(boolean)`，新 `r0(boolean)`）。按钮读取 `getSelectedText`，点击时读取 Provider 中的 `search_engine`、`search_template`：

- 默认“跟随 Aicy”：保持原来的 `Uri.encode`，调用 `d1.C` / `nc.b.J`，保留 Aicy 配置的模板及参数。
- 预置或自定义引擎：`SearchEngines` 从原始选中文字生成完整网址，按 UTF-8 编码一次并替换 `{query}`。调用 `d1.U` / `nc.b.b0`，沿用现有网页 Intent 与浏览器重定向，不依赖各浏览器的搜索专用接口。
- 新网址调用以 `TextSearch` 标记外层作用域；嵌套网页入口保留该标记，调用结束或抛出异常均恢复作用域。实际二维码和网页卡片入口不替换网址。

仅模块新增按钮读取搜索引擎设置；直接调用原普通搜索方法的其他 Aicy 功能继续使用原模板。未知或缺失的引擎 ID 回落到“跟随 Aicy”。自定义模板须包含 `{query}`，只允许 HTTP/HTTPS、完整主机名及路径 / 参数 / 片段占位符；拒绝账号密码、错误转义、主机名占位符与过长模板。无效模板不会启动搜索，会提示用户检查模块设置。

预置搜索网址统一使用 GET 参数。搜索字符串格式参考各服务的官方说明：[DuckDuckGo](https://duckduckgo.com/duckduckgo-help-pages/settings/params)、[Startpage](https://support.startpage.com/hc/en-us/articles/4520913488148-Search-strings)、[Qwant](https://help.qwant.com/hc/en-us/articles/51233217804689-Install-Qwant-on-my-computer-Windows-Mac-Linux)、[Kagi](https://help.kagi.com/kagi/getting-started/setting-default.html)、[Swisscows](https://swisscows.com/en/default-search)。各服务的实际访问结果需在浏览器中验证。

重定向使用 Intent 副本，保留 URI、MIME、flags、categories、extras 及 ClipData。浏览器不匹配卡片 MIME 时，使用无 MIME 的临时查询 Intent 查找显式组件；实际启动 Intent 仍保留原始 MIME。

`BrowserChoices` 保存预置浏览器 ID，与设置列表及路由共用。系统默认 ID 为 `default`，自定义 ID 为 `custom`；实际包名另存 `custom_browser_package`，通过只读 Provider 获取。原 Chrome / Via / 默认 ID 不变，未知 ID 继续回落 Chrome。

自定义浏览器在设置保存、确认保存、测试打开和每次重定向时均调用 `BrowserValidator`。先检查包名、安装和启用状态，再查询 `ACTION_MAIN` / `CATEGORY_APP_BROWSER` 的导出入口，以及 HTTP、HTTPS 两套 `ACTION_VIEW` / `CATEGORY_DEFAULT` / `CATEGORY_BROWSABLE` 处理器。入口须属于目标包、启用且不要求额外权限；网页过滤器不能限定 authority、path、scheme-specific part，API 35 及以上也不能限定 URI-relative filter group。使用 `GET_RESOLVED_FILTER` 读取匹配声明；浏览器主入口查询不使用 `MATCH_DEFAULT_ONLY`，兼容 Via 主入口没有 DEFAULT 分类的情况。API 定义见 Android 官方 [Intent](https://developer.android.com/reference/android/content/Intent)、[PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager)、[IntentFilter](https://developer.android.com/reference/android/content/IntentFilter)。

网页处理器声明本身不足以辨认浏览器：所测淘宝也具有通用网页处理器，因此另检查标准浏览器主入口。声明检查仍无法认证应用可信；设置页提供不保存配置的 Example Domain 测试按钮，保存时显示应用名称、包名及接收网页链接 / 搜索网址的说明，需用户确认。无效自定义设置不覆盖原配置，运行时不可用则沿用默认浏览器回退；不使用应用首页启动 Intent 作为兜底，也不按淘宝、GitHub 等品牌设置黑名单。

缺失方法报告挂接不足，不自动扩大 Hook 到系统或其他应用。适配新版时需分别检查识屏入口、搜索参数编码、Intent 保留及所选浏览器实际显示结果。
