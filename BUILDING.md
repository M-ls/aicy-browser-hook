# Docker 构建

仓库根目录包含文档与 `build.ps1`，程序根目录是 `project/`。只保留 `project/Dockerfile` 一个环境文件。

## 环境与依赖

- Docker Desktop / Docker Engine，支持 BuildKit 命名构建上下文，使用 Linux 容器（已验证 amd64）。
- PowerShell 7（Windows / Linux 的统一构建入口）。
- 容器内使用 Temurin 8 JDK 编译 Java，Temurin 21 JRE 运行 D8 与 apksigner。
- Android SDK Platform 35、Build Tools 35.0.0、Xposed API 82，仅下载至 `work/`。

基础镜像固定摘要。`prepare-dependencies.ps1` 下载官方文件并核对固定校验值；有效缓存会复用。Xposed API 只用于编译，不打入 APK。Android SDK 的使用受其官方条款约束。

## 推荐命令

在仓库根目录执行：

```powershell
./build.ps1
```

脚本检查 Docker，准备依赖，优先复用 `aicy-browser-hook-builder:1`。不存在时才根据现有 Dockerfile 构建镜像。容器使用 `--rm`，退出后移除；源码挂载为只读，不修改其他容器。

输出 `dist/AicyBrowserHook.apk`。过程包括资源打包、Java 编译、D8、zipalign 和签名验证。公开构建入口只依赖仓库中的应用源码，不生成测试 JAR；测试源码和设备记录不随仓库或发布附件分发。

默认构建收录版 `io.github.m_ls.aicybrowserhook`。构建供旧用户覆盖升级的原包名版：

```powershell
./build.ps1 -LegacyPackage
./build.ps1 -Unsigned -LegacyPackage
```

分别输出 `dist/AicyBrowserHook-legacy.apk`、`dist/AicyBrowserHook-legacy-unsigned.apk`。脚本向同一容器环境传入 `BUILD_VARIANT=legacy`，只在 `work/aicy-hook-build/runtime/legacy-project/` 复制 Manifest、源码、资源与 assets，替换 Application ID、Provider 地址与 R 类引用；不修改工作区源码，不复制或新建 Dockerfile。两个版本的版本号和签名一致，但应用身份及配置独立。覆盖旧包必须使用 `legacy` APK，切换收录版需重新配置并停用旧模块。

无签名构建：

```powershell
./build.ps1 -Unsigned
```

输出 `dist/AicyBrowserHook-unsigned.apk`，经过相同编译与对齐检查，不生成或读取签名密钥。GitHub Actions 的分支提交与 PR 检查使用这个模式；它是构建检查产物，不能直接安装。

## 签名

本地模式复用 `work/aicy-hook-build/runtime/signing.jks`。不存在时会创建一把本地开发密钥（别名 `aicy-local`，密码 `android`）。新生成的密钥不能覆盖安装此前已发布的版本。

现有已验证 APK 使用的原密钥留在本机 `work/`，不包含在源码包。仓库所有者已授权将同一密钥及其密码存入本仓库 Actions Secrets，版本标签工作流沿用它自动发布可安装 APK。应保存离线备份，持续发布始终使用同一密钥。APK 签名证书指纹可以公开，私钥不应提交到仓库。

发布模式使用 `./build.ps1 -Release -SigningKeyPath <现有密钥文件>`，要求设置 `SIGNING_STORE_PASSWORD`、`SIGNING_KEY_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_CERT_SHA256` 环境变量。密钥以只读方式挂载，密码通过环境变量传入 apksigner；不会自动生成密钥，证书指纹不匹配会阻止发布。完整远端操作步骤见 [AUTORELEASE.md](AUTORELEASE.md)。

发布原包名版时在相同命令追加 `-LegacyPackage`。两次构建复用同一密钥和镜像；GitHub Actions 会依次执行这两次构建。

独立签名命令参考 Android 官方 [apksigner](https://developer.android.com/tools/apksigner)。

## 不使用 PowerShell 的 Linux 命令

先根据 `project/prepare-dependencies.ps1` 中的 URL 与校验值，将三个依赖文件下载到 `work/aicy-hook-build/`。随后在仓库根目录执行：

```bash
mkdir -p work/aicy-hook-build/runtime dist
docker build --build-context sdkcache="$PWD/work/aicy-hook-build" \
  -t aicy-browser-hook-builder:1 -f project/Dockerfile project
docker run --rm -e SIGNING_MODE=unsigned \
  --mount "type=bind,src=$PWD/project,dst=/project,readonly" \
  --mount "type=bind,src=$PWD/work/aicy-hook-build/runtime,dst=/build" \
  --mount "type=bind,src=$PWD/dist,dst=/out" \
  aicy-browser-hook-builder:1
```

已有符合 Dockerfile 的镜像可跳过镜像构建。Dockerfile 会再次校验依赖。

## 文件清理

下载与编译文件集中在 `work/`，但其中 `signing.jks` 是覆盖更新所需密钥，应先备份。APK 输出在 `dist/`；二者已加入 `.gitignore`。本机测试源码与记录也保存在忽略目录中，不属于模块运行或公开构建依赖。
