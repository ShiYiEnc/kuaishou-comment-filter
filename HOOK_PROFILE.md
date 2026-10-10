# 固定版本适配记录

## 输入版本

- 主 APK：快手 `14.8.40.50567`，versionCode `50567`。
- 主 APK SHA-256：`1AEA3180F86D97A92EBBF0418C71D7FFA9C1E56808D8A573AC14F4C66422DCAE`。
- 评论插件：`assets/dva_feature/comment_detail-master.zip`。
- 插件 SHA-256：`57ADBD9562AC0FF2F13B7BB58B225F359613FC4D94EF0E313C35122603FEBA13`。

## 加载与过滤

入口仅处理快手主进程，在 `Application.attach(Context)` 后取得宿主 Context。校验版本后 Hook `com.kwai.plugin.dva.feature.core.loader.FeaturePluginLoader.b(PluginInfo, Application, ClassLoader, PluginConfig, lhd.d)`；只在插件名为 `comment_detail` 时使用其 ClassLoader 安装评论 Hook。不 Hook 全局 ClassLoader 方法。

列表方法：`com.yxcorp.gifshow.comment.adapter.CommentAdapterImpl2.a3(java.util.List, u8g.h): java.util.List`。静态分析确认它从输入列表复制展示列表，再进入宿主的差分更新路径；模块在返回之后投影为新列表，保留原顺序及非评论行，不修改传入列表和共享响应。

构造参数下标 2 为评论 Fragment。仅处理 `com.yxcorp.gifshow.comment.common.CommonCommentsFragment` 中有 `mQPhoto` 且无 secondary panel 的视频主评论列表。

`qs(Bundle)` 与 `onPageSelect()` 建立会话；`onPageUnSelect()` 与 `onDestroyView()` 结束会话。后台读取一次配置快照，读取完成后重新提交保存的原列表。列表追加和重新提交经过同一展示方法。

## 模型字段

`com.kuaishou.android.model.mix.QComment`：

| 字段 | 类型 | 用途 |
|---|---|---|
| `mId` | String | 评论 ID |
| `mAuthorId` | String | 作者 ID |
| `mComment` | String | 正文 |
| `mAuthorArea` | String | 展示属地，序列化名 `authorArea` |
| `mType` | int | 一级评论类型值 1 |
| `mParent` | QComment | 回复归属 |

静态证据：`QComment.isRoot()` 判断 `mParent == null && mType == 1`；插件 `j9g.f_f.d(vnb.d)` 将 `getKAuthorArea()` 的值用于评论时间旁的属地展示。提取器先校验字段类型，追踪父链时检测循环并限制深度。

## 真机记录与限制

Android 16 / LSPosed 2.1.0（7769）：启动不卡首屏，评论 Hook 执行；白名单仅显示北京的实际列表计数为 30 条、隐藏 29 条、属地缺失 0 条。配置版本为用户原配置 revision 5。用户确认评论区只剩北京属地的一级评论。

首次过滤未执行的原因是 HMA 对快手启用可见应用白名单并隐藏了模块。放行模块、打开模块 APP、重启快手后配置读取及过滤状态恢复。排查用的全局接口探针和备用绑定服务均未保留。

当前记录确认列表路径与属地规则执行，不代表所有评论页面、用户 ID 格式、分页和交互已完成完整回归。其他快手版本不启用过滤。

## 0.2.0 性别字段适配

`QComment.mUser` 类型为 `com.kwai.framework.model.user.User`。其 `mId` 和 `mSex` 均为 String。`QCommentDeserializer.deserialize()` 从同一评论 JSON 的 `author_id` 填入 `mUser.mId` 和 `mAuthorId`，将 `user_sex` 填入 `mUser.mSex`，缺失时默认 `U`。`User.isMale()` 精确比较 `M`，`isFemale()` 精确比较 `F`。

模块校验上述字段类型，仅在 `mUser.mId == mAuthorId` 且作者 ID 非空时读取性别；其他情况归为未知。仅使用经静态验证的 `M/F` 映射，不扩展数字或大小写别名。根评论与附带回复的过滤路径沿用原适配。

新增诊断只记录展示列表中男、女、未知的一级评论计数，不记录性别与用户 ID 的对应关系。配置升级到 schema 2，读取 schema 1 时两份名单的性别条件均为空。

真机样本：用户清空其他白名单条件，仅选择 `FEMALE` 并设置 `SHOW_MATCHED`，revision 11。展示列表 `seen=65`、`female=49`、`male=7`、`unknownGender=9`、`hidden=16`、`error` 为空。过滤计数符合仅保留女性的规则。其他性别与动作组合经过 JVM 规则测试，尚未逐项进行真机回归。
