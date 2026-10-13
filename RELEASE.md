# 1.1.3 发布说明

新增可配置的搜索引擎和浏览器。分词面板的“网页搜索”可选择 17 家预置服务或自定义网址；已有 Aicy 网页链接可交给 12 款预置浏览器、系统默认或通过检查的自定义应用。原有 AI 搜索和网页链接参数保持原处理方式。

版本 `1.1.3` / versionCode `15`，包名 `local.aicy.browserhook`。沿用原签名证书，同签名覆盖安装保留浏览器选择、搜索设置及隐藏桌面入口状态。

## 变更

- 搜索引擎：百度、必应、Google、360、搜狗、神马、DuckDuckGo、Brave Search、Startpage、Ecosia、Qwant、Yahoo、Yandex、Mojeek、Kagi、Swisscows、Yep；另可跟随 Aicy 或自定义含 `{query}` 的 HTTP/HTTPS 模板。搜索词按 UTF-8 编码，模板保存前检查格式。
- 浏览器：Chrome、Via、Microsoft Edge、Firefox、Brave、Samsung Internet、Opera、Vivaldi、DuckDuckGo、夸克、UC 浏览器、QQ 浏览器；另有系统默认和自定义包名。MD2 选择对话框标注未安装的预置应用。
- 自定义浏览器检查安装及启用状态、标准浏览器入口和通用 HTTP/HTTPS 的可用入口，提供“测试打开网页”。保存前显示应用名称、包名和接收网页链接 / 搜索网址的说明；取消或检查失败保留此前保存的设置，运行时失效尝试系统默认浏览器。
- 规范选项备注：跟随 Aicy（使用系统配置，如百度）、Qwant（仅部分地区可用）、Kagi（首次使用需在浏览器中登录）。跟随 Aicy 以系统配置为准，不强制使用百度。
- 保留 MD2 底部“设置 / 状态”分页、隐藏桌面入口开关及灰色署名。预置选择即时保存，自定义包名与网址模板需另外保存。

搜索引擎仅影响模块新增的“网页搜索”，与目标浏览器独立配置。Kagi 登录及 Cookie 由浏览器处理，模块不提取账号 token；自定义模板是普通共享配置，不应放入登录凭据。

## 验证与兼容范围

复用现有 Docker 环境，资源打包、Java 编译、D8、zipalign 和签名验证通过；36 项路由 / 浏览器配置 / 线程作用域检查及 129 项搜索网址检查通过。

设备：魅族 21 Pro / Android 16 / Flyme 12.6.0.0A，Aicy 识屏 12.2.34、Aicy 助手 12.1.46。覆盖安装本版成功，484 项设备 Intent 检查通过。Chrome / Via 的真实声明合格，实际安装的淘宝 / GitHub 被拒绝；自定义 Chrome 的测试按钮实际显示网页，保存确认、取消与重开后的配置均已核对。设备只安装了 Chrome、Via，其他 10 款预置按缺失分支检查，未逐个验证页面。

此前对全部 17 家预置搜索网址及自定义必应模板核对了 Chrome 返回页面。经延长等待、Yandex 人工验证及更换网络出口后，15 家预置显示“小猫”结果；Mojeek 不支持该中文查询，但英文 `kittens` 有结果；Kagi 返回登录页，未测试登录持久性。已有识屏网页跳转到 Chrome / Via、选中 `cat` 后打开普通搜索等功能由此前实机测试确认。本版未逐项重测所有真实 Aicy 点击链路，也未验证其他 Aicy / Flyme 版本。详细记录见 [设备验证](https://github.com/M-ls/aicy-browser-hook/blob/v1.1.3/DEVICE_TESTS.md)。

应用能解析通用网址不等于它是浏览器：本机淘宝具有通用网页处理器，但缺少标准浏览器主入口，因此自定义检查拒绝它。判断依据应用声明，不使用淘宝 / GitHub 包名黑名单，也不能认证应用可信或证明网页一定显示正常；请实际测试后确认目标应用。

## 附件

- `AicyBrowserHook-v1.1.3.apk`：已签名，可直接安装。
- `AicyBrowserHook-v1.1.3-GitHub-source.zip`：对应标签的源码与公共文档，不含 SDK 缓存、签名私钥、设备日志或截图。
- `SHA256SUMS.txt`：本次发布附件的 SHA256；远端构建与本地实测 APK 的文件校验值可能不同，以对应附件为准。

签名证书 SHA256：`6d12194bb7ae583fc0facdc7a1038a6139beb5e459aaeb9f33bd60dfb2042d61`。安装后在 LSPosed 同时勾选 Aicy 识屏与 Aicy 助手作用域，再重启这两个应用的进程或手机。最低 Android 11，暂未添加源码许可证。
