# 隐藏 API 编译存根

继承根 [AGENTS.md](../../../AGENTS.md) 与 [Android 工程规则](../AGENTS.md)。仅保存最小签名，无运行实现，只允许 compileOnly 引用。禁止将存根打包进 APK；运行端仍须捕获缺失类或签名错误。构建后检查最终 DEX 不含本模块类定义。
