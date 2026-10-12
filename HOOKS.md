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

新增搜索按钮挂接 `com.meizu.picker.ParticipleWordActivity` 的 `onCreate`、`onResume` 及选词更新方法（旧 `U(boolean)`，新 `r0(boolean)`）。按钮读取 `getSelectedText`，编码查询值，调用已有普通搜索方法。

重定向使用 Intent 副本，保留 URI、MIME、flags、categories、extras 及 ClipData。浏览器不匹配卡片 MIME 时，使用无 MIME 的临时查询 Intent 查找显式组件；实际启动 Intent 仍保留原始 MIME。

缺失方法报告挂接不足，不自动扩大 Hook 到系统或其他应用。适配新版时需分别检查识屏入口、搜索参数编码、Intent 保留及所选浏览器实际显示结果。
