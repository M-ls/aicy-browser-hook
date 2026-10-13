# GitHub 自动发布

仓库：[M-ls/aicy-browser-hook](https://github.com/M-ls/aicy-browser-hook)。程序源码在 `project/`，发布文档与构建入口位于仓库根目录。远端复用唯一的 `project/Dockerfile`，不新增 Docker 环境文件。

## 发布一次新版本

1. 修改 `project/AndroidManifest.xml` 的 `android:versionName`，并递增 `android:versionCode`。
2. 更新 `README.md` 的当前版本，将 `RELEASE.md` 首行改为 `# 版本号 发布说明`，写入该版本的变更与实际验证范围。
3. 提交并推送到 `main`，确认普通构建检查通过。
4. 在这个提交上创建对应版本标签并推送。例如本版 1.1.3：

```powershell
git tag -a v1.1.3 -m 'Release v1.1.3'
git push origin v1.1.3
```

[Publish signed APK](https://github.com/M-ls/aicy-browser-hook/actions/workflows/release.yml) 自动检查标签格式、Manifest 版本、发布说明及提交是否属于 `main`，然后使用 Docker 编译并运行 36 项路由 / 浏览器配置检查与 129 项搜索网址检查。APK 经 zipalign、原密钥签名、签名验证与固定证书指纹比对后，自动创建 [Release](https://github.com/M-ls/aicy-browser-hook/releases)。

发布附件：

- `AicyBrowserHook-v版本号.apk`：可直接安装，同签名支持覆盖更新。
- `AicyBrowserHook-v版本号-GitHub-source.zip`：该标签的 Git 跟踪源码与公共文档。
- `SHA256SUMS.txt`：以上两个附件的 SHA256 校验值。

普通分支提交和 PR 只生成无签名检查产物，不读取发布密钥。GitHub Actions 不执行手机上的 Android Intent 或 LSPosed 实机检查；每版的设备验证需另外完成，并如实记录在发布说明中。

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
