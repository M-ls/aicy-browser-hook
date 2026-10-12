# 第三方内容

## Google Material Icons

`project/res/drawable/ic_settings.xml` 和 `ic_status.xml` 使用 Google Material Icons 的图形，按 Apache License 2.0 分发。项目保留完整许可于 `project/assets/material-icons-LICENSE.txt`，它也包含在 APK 中。

来源：[Google Material Design Icons](https://github.com/google/material-design-icons)。底部导航尺寸参考 Material Components Android 1.4.0 的 MD2 规范；没有依赖或打包 Material 3 库。

## 编译依赖

- [Xposed API 82](https://api.xposed.info/)：仅供编译引用，不包含在模块 APK。
- [Android SDK](https://developer.android.com/studio/terms)：从 Google 官方仓库下载，不包含在源码包。
- [Eclipse Temurin](https://adoptium.net/) 与 [Maven 官方镜像](https://hub.docker.com/_/maven)：容器内的构建工具，不包含在 APK。

本仓库不包含 Aicy、Flyme 或其他手机系统应用的反编译代码及 APK。第三方许可不构成本项目自身的源码许可证。
