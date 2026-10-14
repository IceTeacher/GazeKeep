# 双入口由 assets/xposed_init 和 META-INF/xposed/java_init.list 按类名反射加载。
# 此包也包含具名 Hooker；intercept 由外部框架调用，必须保留实现方法。
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

# SDK consumer 规则已保留注册器类；full mode 还需显式保留 metadata 反射调用的构造器。
-keepclassmembers,allowoptimization class com.google.mlkit.** implements com.google.firebase.components.ComponentRegistrar {
  public <init>();
}
