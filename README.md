# Aicy 浏览器入口

为魅族 Aicy 识屏提供浏览器选择和普通网页搜索入口的 Xposed / LSPosed 模块。

- 已有 HTTP/HTTPS 网页跳转可选择 Chrome、Via 或系统默认浏览器。
- 在原生“分词”操作栏增加“网页搜索”，沿用 Aicy 的搜索引擎与搜索模板。
- 保留原有 AI 搜索；不修改系统 APK。
- MD2 风格设置页，底部“设置 / 状态”导航，支持隐藏桌面入口。
- 设置立即保存，切换浏览器无需重新加载 Aicy 进程。

当前版本：**1.0.10**（versionCode 11），包名 `local.aicy.browserhook`。

## 兼容范围

最低 Android 11（API 30）。已验证环境：魅族 21 Pro、Android 16、Flyme 12.6.0.0A、LSPosed IT 2.1；Aicy 识屏 `com.meizu.picker` 12.2.34、Aicy 助手 `com.meizu.voiceassistant` 12.1.46。

Hook 使用上述版本的类名和方法名，其他 Aicy / Flyme 版本需检查状态，不能保证兼容。模块针对 Aicy，不为其他品牌手机提供通用识屏功能。旧搜图入口在所测 Aicy 版本中不可用，本模块不启用它。

## 安装

1. 从 [Releases](https://github.com/M-ls/aicy-browser-hook/releases) 下载并安装已签名的 `AicyBrowserHook-v版本号.apk`。版本发布由 GitHub Actions 自动完成，沿用原签名密钥；普通构建检查的 `unsigned` 产物需要自行签名。
2. 在 LSPosed 启用模块，作用域同时勾选 **Aicy 识屏**（`com.meizu.picker`）和 **Aicy 助手**（`com.meizu.voiceassistant`）。
3. 重启这两个应用的进程，或重启手机。
4. 从桌面或 LSPosed 打开模块设置，选择浏览器。默认选择 Chrome，默认开启重定向与网页搜索入口。

隐藏桌面入口后，仍可从 LSPosed 的模块页面打开设置并恢复图标。

## 使用

识屏后，对普通网页二维码点击“打开”，应进入所选浏览器。文字搜索：打开“分词”，选择文字，再点新增的“网页搜索”；没有选中文字时按钮禁用。

浏览器选择不会更换搜索引擎。例如 Aicy 原来使用百度，切换成 Via 后仍打开百度结果。原“AI 搜索”继续使用 Aicy 助手。

所选浏览器不可用时尝试系统默认；默认也无法处理时保留 Aicy 原始调用。关闭重定向后恢复原处理，并隐藏新增搜索入口。

## 状态与问题定位

状态页通常显示启动方法 **2/2**、网页路径 **4/4**、分词面板 **3/3**。挂接数只说明方法存在，累计改写也不等于网页已经加载成功。

- 完全无效：检查模块启用、两个作用域及进程是否重启。
- Aicy 更新后数量不足：提供两个 Aicy 应用的版本和缺失入口日志，需重新适配方法名。
- 网页打不开：先确认对应浏览器已安装；尝试“跟随系统默认”。
- 覆盖安装失败：新旧 APK 必须使用相同签名证书。

提交问题时可附状态页与应用版本；请省略搜索内容、完整网址和个人信息。

## 开发与构建

使用 Docker 编译，无需在宿主机安装 Java、Android Studio 或 Android SDK。Windows 使用 PowerShell 7；Linux 可使用 `pwsh` 或文档中的 Docker 命令。

```powershell
./build.ps1
# CI / 无需签名的构建
./build.ps1 -Unsigned
```

源码在 `project/`，唯一环境文件为 `project/Dockerfile`。输出在 `dist/`，下载缓存、编译临时文件及本地签名密钥统一在 `work/`。详见 [BUILDING.md](BUILDING.md)。推送 `v*` 版本标签会自动构建、签名并发布 APK、源码包与校验文件，操作步骤见 [AUTORELEASE.md](AUTORELEASE.md)。

[验证范围与发布说明](RELEASE.md) · [Hook 入口说明](HOOKS.md) · [第三方说明](THIRD_PARTY.md)

## 数据与许可

模块不申请联网权限，不保存搜索词或完整网址。设置及状态保存在本机；状态日志包含入口名称、包名、计数与错误类别。设置通过只读 Provider 供 Hook 读取，外部应用不能通过该 Provider 修改设置；状态写入限模块自身与两个 Aicy 包。

本项目**暂未添加源码许可证**。第三方图标的 Apache 2.0 许可独立保留，见 [THIRD_PARTY.md](THIRD_PARTY.md)。
