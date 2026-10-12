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

输出 `dist/AicyBrowserHook.apk`。过程包括资源打包、Java 编译、D8、zipalign、签名验证以及 23 项 JVM 路由 / 线程作用域检查。Android Intent 检查 JAR 生成在 `work/aicy-hook-build/runtime/intent-tests.jar`，需另外在设备运行。

无签名构建：

```powershell
./build.ps1 -Unsigned
```

输出 `dist/AicyBrowserHook-unsigned.apk`，经过相同编译、对齐及 JVM 检查，不生成或读取签名密钥。GitHub Actions 使用这个模式；它是构建检查产物，不能直接安装。

## 签名

本地模式复用 `work/aicy-hook-build/runtime/signing.jks`。不存在时会创建一把本地开发密钥（别名 `aicy-local`，密码 `android`）。它用于个人构建，不作为公共 CI 的发布密钥。

现有已验证 APK 使用的原密钥留在本机 `work/`，不包含在源码包。持续发布能覆盖安装的 APK，应始终使用同一密钥并自行保存备份；CI 产物请在可信的本地环境用自己的固定发布密钥签名。APK 签名证书指纹可以公开，私钥不应提交到仓库。

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

## 可选设备检查

该脚本构造 Intent、检查字段保留及浏览器解析，不启动 Activity。Chrome、Via 与默认浏览器均可用的已测魅族环境通过 126 项检查；其他设备可能缺少对应浏览器。

```bash
adb push work/aicy-hook-build/runtime/intent-tests.jar /data/local/tmp/aicyhook-intent-tests.jar
adb shell 'CLASSPATH=/data/local/tmp/aicyhook-intent-tests.jar app_process /system/bin local.aicy.browserhook.AndroidIntentTest'
```

这个检查不能代替 LSPosed 实际加载、Aicy 识屏及网页点击测试。

## 文件清理

可删除的下载与编译文件集中在 `work/`，但其中 `signing.jks` 是覆盖更新所需密钥，应先备份。APK 输出在 `dist/`；二者已加入 `.gitignore`。设备检查的临时 JAR 不属于模块运行依赖。
