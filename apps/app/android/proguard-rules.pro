# 双入口由 assets/xposed_init 和 META-INF/xposed/java_init.list 按类名反射加载。
-keep class cn.rainafter.app.gazekeep.xposed.entry.** { *; }
-keep class cn.rainafter.app.gazekeep.xposed.power.** { *; }
-keep class cn.rainafter.app.gazekeep.xposed.protocol.** { *; }

# Shizuku UserService / Provider 与系统 Binder 的外部名称不能改变。
-keep class cn.rainafter.app.gazekeep.backend.shizuku.ShellService { *; }
-keep class cn.rainafter.app.gazekeep.backend.shizuku.protocol.** { *; }
-keep class rikka.shizuku.ShizukuProvider { *; }
# 框架提供 compileOnly API；只抑制这两组已明确由框架加载的缺失类。
-dontwarn de.robv.android.xposed.**
-dontwarn io.github.libxposed.**

# Android boot classpath 提供此隐藏类；编译存根明确不打包。
-dontwarn android.app.ActivityThread
