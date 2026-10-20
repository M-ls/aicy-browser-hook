# 网页入口与版本适配

模块作用于 `com.meizu.picker` 和 `com.meizu.voiceassistant`，在 `Application.attach` 后安装 Hook，每个进程初始化一次。原生网页跳转和模块新增的搜索按钮共用浏览器设置。

## 原生网页入口

旧版优先使用已有映射，挂接前核对方法的参数、返回类型和静态声明：

| 入口 | 独立识屏 12.2.34 | 助手内嵌识屏 12.1.46 |
| --- | --- | --- |
| 普通文字搜索 | `d1.C(Context, String)` | `nc.b.J(Context, String)` |
| 直接网页 | `d1.U(Context, String)` | `nc.b.b0(Context, String)` |
| 默认浏览器网页 | `d1.V(Context, String)` | `nc.b.c0(Context, String)` |
| 启动方法 | `d1.G(Context, Intent, Bundle)` | `nc.b.N(Context, Intent, Bundle)` |

这两个版本还使用 `com.flyme.sdk.scan.action.util.WebUtil.openBrowser(Context, String)` 和 `com.flyme.sdk.architecture.utils.AppUtil.startActivityCheckIsOpenSmallWindow(Context, Intent)`。

旧映射不匹配时，`DexRoutes` 读取当前应用的 base APK 和 split APK，按以下特征查找网页工具类：

- 直接网页：静态 `void(Context, String)`，引用 ACTION_VIEW 和系统浏览器组件。
- 默认浏览器网页：静态 `void(Context, String)`，引用 ACTION_VIEW 和默认浏览器查询方法名。
- 启动方法：静态 `boolean(Context, Intent, Bundle)`，引用 `disable_splash_ad`。

三个方法必须各找到一个、属于同一类，而且整组候选只能有一组。随后再用反射核对签名。扫描按单个 DEX 限制为 64 MiB；无法读取或匹配时保留原生调用。

Aicy 13.0.51 的这三个入口位于 `com.google.android.gms.internal.mlkit_vision_common.U2`，方法名依次为 `u`、`v`、`m`。扫描失败时，versionCode 为 `13000052` 的版本还可使用这组固定映射。原生文字搜索监听器则通过搜索模板键、ACTION_VIEW、浏览器组件和 `void(View)` 签名识别；候选唯一时加入网页入口标记。

入口标记按线程保存，嵌套调用或异常返回后恢复。模块只在这些入口内改写 HTTP/HTTPS 的 ACTION_VIEW Intent；非网页 URI、分享和带 selector 的调用保留。AI 搜索入口 `d1.a` / `nc.b.a` 不挂接。

## 分词面板

`WebSearchEntry` 在 `ParticipleWordActivity.onCreate` 和 `onResume` 后加入“网页搜索”按钮。按钮复用原生操作栏的尺寸、字体和颜色。

选词状态从 `getSelectedText` 读取，在面板重绘前同步到按钮；颜色或可用状态变化时才更新。观察器随视图附着、移除，因此无需依赖各版本名称不同的 `U` / `r0` / `t` 刷新方法，也不占用原生选词监听器。

点击时读取 Provider 中的当前设置，生成搜索网址，交给 `BrowserResolver` 打开。浏览器使用 NEW_TASK，启动成功后关闭浮动分词面板，避免 Chrome 跳转后又回到面板。模块关闭或隐藏网页入口时，按钮不可见。

## 搜索网址

搜索引擎设置只影响新增按钮，原生二维码和网页卡片保留原网址。

- **跟随 Aicy**：通过 `com.meizu.common.alphame.AlphaMe` 的 `invoke(String, Object[])` 读取 `getCdnInfo("tag_search_engine_url")`，保留模板固定参数，替换 `searchTerms`。配置为空时使用 Aicy 的 360 搜索模板。旧版配置接口不可用时，回退到 `d1.C` / `nc.b.J`，按原方式传入 `Uri.encode` 后的文字。
- **预置引擎**：使用对应的 GET 搜索网址。
- **自定义网址**：替换模板中的 `{query}`。模板须使用 HTTP/HTTPS 和完整主机名；占位符可放在路径、参数或片段中。账号密码、主机名占位符、错误转义和过长模板会被拒绝。

关键词统一按 UTF-8 编码一次。未知或缺失的引擎 ID 使用“跟随 Aicy”；无效模板会提示检查设置，不启动搜索。

预置网址的参数说明可参考 [DuckDuckGo](https://duckduckgo.com/duckduckgo-help-pages/settings/params)、[Startpage](https://support.startpage.com/hc/en-us/articles/4520913488148-Search-strings)、[Qwant](https://help.qwant.com/hc/en-us/articles/51233217804689-Install-Qwant-on-my-computer-Windows-Mac-Linux)、[Kagi](https://help.kagi.com/kagi/getting-started/setting-default.html)、[Swisscows](https://swisscows.com/en/default-search)。

## 浏览器与 Intent

`BrowserChoices` 维护设置页和路由共用的浏览器 ID。系统默认为 `default`，自定义为 `custom`，自定义包名另存于 `custom_browser_package`。原 Chrome / Via / 默认 ID 保持兼容；未知 ID 回落到 Chrome。

重定向使用 Intent 副本，保留 URI、MIME、flags、categories、extras 和 ClipData。浏览器不匹配卡片 MIME 时，先用不带 MIME 的查询找到组件，实际启动仍保留原 MIME。所选浏览器不可用时尝试系统默认；原生入口若仍无法处理，则继续使用原始 Intent。

自定义包名在保存和每次跳转时都经过 `BrowserValidator` 检查：应用已安装且启用，具有标准浏览器主入口，并能处理通用 HTTP、HTTPS 网页。处理 Activity 必须属于目标包、已启用、可导出，且不要求额外权限。限定域名、路径、scheme-specific part 或 URI-relative filter group 的网页处理器不作为通用浏览器入口。

查询使用 `GET_RESOLVED_FILTER` 读取实际声明；主入口查询不加 `MATCH_DEFAULT_ONLY`，兼容 Via 未声明 DEFAULT 分类的情况。检查依据应用声明，设置页提供 `example.com` 测试按钮供用户确认实际打开效果；测试不保存配置。无效设置不覆盖原值，运行时也不会改用应用首页作为回退。

Android 接口说明：[Intent](https://developer.android.com/reference/android/content/Intent)、[PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager)、[IntentFilter](https://developer.android.com/reference/android/content/IntentFilter)。

## 状态报告

不同版本的入口数量不同，状态页使用当前适配方案的预期数量显示挂接情况。入口缺失时报告不足，便于定位 Aicy 更新带来的变化。挂接成功表示方法已找到，网页是否加载仍由浏览器和网络决定。

状态和日志记录入口、浏览器包名、计数及错误类别，不记录搜索文字、网址或 Intent extras。
