# 实机自动化套件

本文件继承根 [AGENTS.md](../../../AGENTS.md) 和 [Android 工程规则](../AGENTS.md)，只补充本模块职责与门禁，不重复共同安全承诺。

## 职责与边界

入口：e2e（DeviceSession 与各旅程用例）。

`com.android.test` 自插桩模块，通过 `targetProjectPath` 指向 `:android`，测试运行在独立进程，撤销权限或强停被测应用不会连带结束 instrumentation。只经 UiAutomator、shell 设置与可见文案驱动被测应用，不依赖任何模块代码，不读取帧、人脸或日志正文；测试依赖只进入本模块 APK。用例只修改自身登记的设置并在结束时恢复，前提不满足时以 assumption 跳过。

相关专题：[测试规范](../../../docs/development/testing.md#实机入口)；设计、运行或评审实机旅程时加载 [testing-e2e](../../../.agents/skills/testing-e2e/SKILL.md)。

## 验证

在仓库根执行：

```bash
pnpm verify:architecture
pnpm --filter @gazekeep/app exec ./gradlew :e2e:assembleDebug
```

运行实机套件使用 `pnpm --filter @gazekeep/app test:device`（保留安装，避免结束后卸载被测应用），运行前核对设备操作授权；编译通过、跳过与未运行都不计作实机通过。
