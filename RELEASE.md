# 1.1.4 发布说明

版本 `1.1.4` / versionCode `16`，标签 `16-1.1.4`。本版提供两个功能相同、使用原签名证书的 APK，分别用于 LSPosed 仓库收录和旧版覆盖升级。

## 变更

- 收录版包名改为 `io.github.m_ls.aicybrowserhook`，使用 GitHub 账号对应前缀，满足 Xposed 模块仓库的包名归属要求。
- 原包名版保留 `local.aicy.browserhook`，附件名带 `legacy`，供已安装旧版的用户直接覆盖升级并保留配置。此版本仅在个人 Releases 提供。
- 同步配置 Provider 地址、设置组件与资源引用。两个包名使用独立配置，Hook 入口与浏览器 / 搜索引擎逻辑沿用 1.1.3。
- 一次标签发布同时编译、对齐、签名并验证两个 APK。两版复用同一 Dockerfile 和签名密钥；源码包只包含应用源码、构建文件和公共文档。

继续提供 12 款预置浏览器、系统默认及自定义浏览器，17 家预置搜索服务、跟随 Aicy 及自定义网址模板。搜索引擎仅影响模块新增的“网页搜索”，与目标浏览器独立配置。

## 安装与迁移

- **旧用户保留配置升级**：安装 `AicyBrowserHook-16-1.1.4-legacy.apk`，沿用原模块及作用域，再重启 Aicy 识屏和 Aicy 助手进程。
- **首次安装或使用 LSPosed 仓库**：安装 `AicyBrowserHook-16-1.1.4.apk`，在 LSPosed 启用并勾选 `com.meizu.picker`、`com.meizu.voiceassistant`，然后重启两个应用进程。
- **旧包切换到收录版**：先记下旧设置并在 LSPosed 停用旧模块，再安装新包、重新配置与选择作用域。新包不能覆盖旧包，旧配置不会自动继承。确认正常后可卸载旧包；不要同时启用两个模块。

## 验证与兼容范围

两版发布流程均执行资源打包、Java 编译、D8、zipalign、APK 包名核对、签名验证和固定证书指纹比对。公开 CI 不执行手机实测。

1.1.3 的已验证环境：魅族 21 Pro / Android 16 / Flyme 12.6.0.0A，Aicy 识屏 12.2.34、Aicy 助手 12.1.46。1.1.4 未重新执行实机验证，尤其是新包名的 LSPosed 加载、配置读取及桌面入口行为仍需在设备上核对。其他浏览器与 Aicy / Flyme 版本未逐个验证；搜索服务可用性受地区、账号和服务自身限制影响。

## 附件

- `AicyBrowserHook-16-1.1.4.apk`：LSPosed 收录版，包名 `io.github.m_ls.aicybrowserhook`。
- `AicyBrowserHook-16-1.1.4-legacy.apk`：原包名版，包名 `local.aicy.browserhook`，供旧版覆盖升级。
- `AicyBrowserHook-16-1.1.4-GitHub-source.zip`：对应标签的源码、双包名构建入口与公共文档。
- `SHA256SUMS.txt`：以上三个附件的 SHA256。

两版签名证书 SHA256：`6d12194bb7ae583fc0facdc7a1038a6139beb5e459aaeb9f33bd60dfb2042d61`。最低 Android 11，暂未添加源码许可证。LSPosed 官方发布仓库只提供收录版 APK、SUMMARY 与 README，不上传原包名 APK、源码 ZIP 或本地验证资料。
