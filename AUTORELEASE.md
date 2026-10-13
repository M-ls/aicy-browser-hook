# GitHub 自动发布

仓库：[M-ls/aicy-browser-hook](https://github.com/M-ls/aicy-browser-hook)。程序源码在 `project/`，发布文档与构建入口位于仓库根目录。远端复用唯一的 `project/Dockerfile`，不新增 Docker 环境文件。

## 发布一次新版本

1. 修改 `project/AndroidManifest.xml` 的 `android:versionName`，并递增 `android:versionCode`。
2. 更新 `README.md` 的当前版本，将 `RELEASE.md` 首行改为 `# 版本号 发布说明`，写入该版本的变更与实际验证范围。
3. 提交并推送到 `main`，确认普通构建检查通过。
4. 在这个提交上创建 `versionCode-versionName` 标签并推送。例如下一版若为 versionCode `16`、versionName `1.1.4`：

```powershell
git tag -a 16-1.1.4 -m 'Release 1.1.4 (16)'
git push origin 16-1.1.4
```

[Publish signed APK](https://github.com/M-ls/aicy-browser-hook/actions/workflows/release.yml) 自动检查标签格式、Manifest 的两个版本字段、发布说明及提交是否属于 `main`，然后使用 Docker 编译。APK 经 zipalign、原密钥签名、签名验证与固定证书指纹比对后，自动创建 [Release](https://github.com/M-ls/aicy-browser-hook/releases)，发布标题使用 `versionName`。

仓库整理和文档修改不递增版本、不创建标签。现有 `v1.1.3` 标签与 Release 保留；本次只规范后续发布流程，不为同一版本另发一个 `15-1.1.3`。下一次实际发布 APK 时再递增版本并采用新格式。

发布附件：

- `AicyBrowserHook-版本标签.apk`：可直接安装，同包名、同签名支持覆盖更新。
- `AicyBrowserHook-版本标签-GitHub-source.zip`：个人源码仓库中该标签的应用源码、构建文件与公共文档。打包前核对允许发布的目录和文件，出现额外文件会终止发布。
- `SHA256SUMS.txt`：以上两个附件的 SHA256 校验值。

普通分支提交和 PR 只生成无签名构建产物，不读取发布密钥。GitHub Actions 不运行本地测试源码或手机上的实机检查；发布说明仅保留必要的兼容范围与验证摘要，独立测试文档、测试源码、原始日志和截图只留在本地。

## Xposed 模块仓库收录

以下要求来自 [官方收录说明](https://github.com/Xposed-Modules-Repo/submission#repository-structure)，适用于收录后 `Xposed-Modules-Repo` 下的发布仓库。个人源码仓库继续用于开发，不整体转移给模块仓库。

- 仓库名使用 APK 的 Application ID；Description 填模块名称 `Aicy 浏览器入口`，Home Page 填支持链接。
- `SUMMARY` 放模块摘要，`README.md` 放完整功能与使用说明。APK 内的 `xposeddescription` 是功能简介，与仓库 Description 各有用途。
- Release 标题为 `versionName`，正文为更新说明，标签为 `versionCode-versionName`，附件至少包含一个有效 APK。
- 官方发布仓库只上传 `SUMMARY`、`README.md` 及 APK，不上传程序源码、源码 ZIP、测试资料或构建缓存。源码保留在本个人仓库。

当前尚未申请收录。现有 Application ID 为 `local.aicy.browserhook`，不满足官方包名归属要求。没有自有域名时可采用 GitHub 账号对应前缀；按官方机器人对账号 `M-ls` 的转换，候选为 `io.github.m_ls.aicybrowserhook`，参见 [机器人校验代码](https://github.com/Xposed-Modules-Repo/submission/blob/master/src/bot.ts)。

更换 Application ID 会使 Android 将其视为新应用，不能直接覆盖旧包，原配置也不会自动继承；旧模块需要停用以避免两个模块同时挂接。正式迁移前需确认新包名与配置迁移安排。

## 签名与权限

仓库 Actions Secrets 已配置三个值：

| 名称 | 用途 |
| --- | --- |
| `ANDROID_SIGNING_KEYSTORE_BASE64` | 现有 `signing.jks` 文件的 Base64 编码 |
| `ANDROID_SIGNING_STORE_PASSWORD` | 密钥库密码 |
| `ANDROID_SIGNING_KEY_PASSWORD` | 私钥密码 |

密钥别名为 `aicy-local`。发布证书 SHA256 为：

```text
6d12194bb7ae583fc0facdc7a1038a6139beb5e459aaeb9f33bd60dfb2042d61
```

这是原已安装 APK 的签名证书。密钥缺失、密码错误或指纹不符会终止构建，发布模式不会生成替代密钥。密钥只恢复到 runner 临时目录、以只读文件挂载到容器，签名步骤结束即移除临时密钥；SDK 缓存只包含三个已校验的公开依赖文件。

只有发布 job 获得 `contents: write`，通过内置 `GITHUB_TOKEN` 创建 Release，无需额外保存发布用 PAT。签名私钥不进入 Git、源码 ZIP、构建缓存或 Release 附件。本机原密钥在 `work/aicy-hook-build/runtime/signing.jks`，仍需自行备份，避免把含密钥的整个 `work/` 当作普通缓存清空。

## 失败恢复

在 Actions 查看失败步骤。修复 Secrets 后可直接重新运行失败的 job；也可选择发布工作流的 **Run workflow**，输入已经存在且版本匹配的标签。首次创建 Release 时会先上传附件再公开；若只留下草稿，重新运行会补齐附件后发布。已经公开的版本会拒绝重复覆盖，应递增版本并创建新标签。

工作流不会自动创建版本标签，也不会在每次提交时发版。推送标签前确认应用代码和发布说明已准备好。

GitHub 官方参考：[Secrets 加密](https://docs.github.com/en/rest/guides/encrypting-secrets-for-the-rest-api)、[GITHUB_TOKEN 权限](https://docs.github.com/en/actions/tutorials/authenticate-with-github_token)、[gh release create](https://cli.github.com/manual/gh_release_create)。Android 签名参考：[apksigner](https://developer.android.com/tools/apksigner)。
