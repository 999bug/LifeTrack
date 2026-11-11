# R8 规则（仅 release 构建生效）
#
# 原则：只写「第三方库自带 consumer rules 覆盖不到」的部分，不整包 -keep。
# Room、kotlinx-serialization、SQLCipher 的 AAR 都自带 proguard.txt，重复 keep
# 会把刚瘦下来的包体再撑回去（material-icons-extended 单个库就有 11,105 个类）。

# 枚举：项目里大量用枚举名做持久化键与备份字段（ThemeMode / StatsPeriod /
# AppLockTimeout / ReleaseOrigin 等），反查依赖编译器生成的枚举映射表；R8 的
# enum unboxing 在部分形态下会改写这张表，保留这两个成员最省心。
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留注解与泛型签名：Room 与 kotlinx-serialization 运行时要读 @Entity /
# @Serializable 的注解，并依赖字段的泛型信息还原集合类型。
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# 协程对 JDK 可选 API 的引用（java.lang.instrument 等）在 Android 上不存在，
# 只影响构建期警告，不影响运行。
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**
