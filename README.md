# 快手评论过滤

Android 11+ 的传统 Xposed / LSPosed 模块。已知适配 `com.smile.gifmaker` 的 `14.8.40.50567`（versionCode `50567`）。可获取并过滤评论展示的 IP 属地，可过滤关键词、发言人昵称、发言人性别。

[下载 v0.2.0 测试 APK](https://github.com/ShiYiEnc/kuaishou-comment-filter/releases/tag/v0.2.0) · [自动构建状态](https://github.com/ShiYiEnc/kuaishou-comment-filter/actions/workflows/android.yml)

## 启用

1. 安装测试 APK，并打开一次“快手评论过滤”设置页。
2. 在 LSPosed 中启用模块，作用域勾选快手。
3. **使用 HMA 等隐藏应用列表模块时，必须让快手能够看到 `dev.shiyi.kuaishoufilter`。** 在 HMA 的快手配置中将本模块加入可见白名单；不要把本模块加入隐藏名单。保存后完全退出并重新启动快手。
4. 打开模块设置，编辑规则，再打开总开关。重新打开视频评论区采用新配置。

HMA 的可见应用白名单与模块的评论黑白名单是两种不同配置。HMA 隐藏模块时，快手无法访问配置 ContentProvider，日志显示 `Unknown authority dev.shiyi.kuaishoufilter.config`，模块会保留全部评论。

## 规则

- 黑名单与白名单独立保存，只有当前选择的名单参与判断。
- “隐藏命中项”隐藏匹配评论；“仅显示命中项”隐藏不匹配评论。
- 属地、关键词、用户 ID 和作者性别任意一项命中即可。用户昵称仅作备注。
- 作者性别支持男、女、未知多选，两份名单独立保存。不选择性别时该条件不参与匹配。
- 性别来自评论接口的 `user_sex` 字段：`M` 为男、`F` 为女；缺失、不支持的值或作者身份无法核对时为未知。不根据头像、昵称或正文推断，也不请求用户主页。
- 例如白名单中同时选择“辽宁”和“女”，会显示辽宁作者或女性作者的评论。只想按性别过滤时，清空该名单的其他条件。
- 属地精确匹配，支持行政区全称别名；不推断城市所在省份。
- 关键词包含匹配，忽略英文大小写；不支持正则表达式。
- 空名单在“隐藏命中项”下保留全部，在“仅显示命中项”下隐藏全部。
- 仅过滤视频一级评论及其附带回复预览。保留评论总数和服务端分页信息；全部隐藏时提供“加载更多”入口。
- 版本不符、配置不可用或 Hook 出错时保留原评论。

## 构建

需要 JDK 17、Android SDK（Android 34 平台和对应构建工具）。在 `local.properties` 设置本机 `sdk.dir`，或使用 `ANDROID_HOME`。首次构建需要访问 Google Maven、Maven Central、Xposed API 仓库和 Gradle 分发源。

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console plain
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。测试包使用开发签名，保留相同签名才能覆盖升级。

Linux / macOS 可运行：

```sh
bash ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console plain
```

GitHub Actions 在提交和 Pull Request 时执行构建、单元测试与 Lint，成功后可在该次运行的 Artifacts 中下载测试 APK。Actions 使用临时开发签名，与本地测试包可能不同，不能保证覆盖安装。

固定版本：Gradle 8.7（包含 SHA-256 校验）、AGP 8.5.2、Kotlin 2.0.21、Xposed API 82、compile/target SDK 34、min SDK 30。源码和构建文件均在本目录；APK 分析产物不属于源码。

## 诊断与验证

设置页显示最近一次过滤状态、配置版本、一级评论计数、隐藏数量和属地缺失数量。默认诊断不保存正文、用户 ID 或完整评论。

2026-10-05 在 OPPO OPD2404、Android 16、LSPosed 2.1.0（7769）上验证模块加载和真实评论过滤：白名单仅显示北京时，单次展示列表读取 30 条一级评论、隐藏 29 条，缺失属地 0 条；用户确认界面只剩北京一级评论。19 项 JVM 测试通过，配置持久化和 Provider 的设备测试通过。Lint 为 0 错误；有导出 Provider（运行时 UID 校验）、备份配置和本地化方面的提示。

完整的分页、热门/最新切换、置顶、图片评论、本地新增评论、长按和回复回归尚需补充；Android 11 至 15 未做真机验证。此包属于测试版本。

具体 Hook 定位与适配证据见 [HOOK_PROFILE.md](HOOK_PROFILE.md)。

## 0.2.0 性别规则

新增作者性别条件和男/女/未知诊断计数。配置 schema 2 兼容原 schema 1，升级后原有黑白名单和开关保留，性别默认不选。

26 项 JVM 测试通过，覆盖性别的两种名单与处理方式、多选、或条件、未知值、作者身份不一致、回复归属、旧配置迁移和非法配置；真机配置保存及 Provider 往返测试通过。

2026-10-05，同一 Android 16 平板上验证仅选择女性的白名单“仅显示命中项”：展示列表读取 65 条一级评论，女性 49 条、男性 7 条、未知 9 条，隐藏 16 条，配置 revision 11，无适配错误。这确认评论接口能够提供部分作者的性别，不代表所有作者都会提供可识别值。

## 源码范围

本仓库包含模块源码、测试、Gradle Wrapper 与适配记录。不包含快手 APK、反编译源码、设备日志、本机 SDK 路径或签名密钥。`scripts/InspectDex.java` 是可选的离线 DEX 引用检查工具，依赖另行准备的 JADX 1.5.3，不参与模块构建。

## 许可证

Copyright (c) 2026 Kuaishou Comment Filter contributors

模块源码采用 [GNU Affero General Public License v3.0](LICENSE)（SPDX：`AGPL-3.0-only`）。第三方依赖及 Gradle Wrapper 遵循各自许可证。本项目与快手官方无隶属关系。
