# 架构总览

当前以唯一 APK 交付，App 进程完成检测与决策，特权侧只报告系统时序并执行保亮。术语见 [CONTEXT.md](../../CONTEXT.md)。

```text
androidApp → shared → core
androidApp → engine → core
androidApp → backend:{a11y,shizuku,lsposed} → core
backend:lsposed → bridge:protocol ← xposed
androidApp → xposed（唯一 APK 打包）
```

## 九模块职责

| 模块            | 所有权与边界                                                                           |
| --------------- | -------------------------------------------------------------------------------------- |
| androidApp      | AppGraph 装配、controller、selftest、applist、platform、runtime、service、notification |
| shared          | KMP UI：contract、navigation、screens、components、text；只认识 core 和平台契约        |
| core            | commonMain 纯 Kotlin：model、backend 接口、engine reducer、policy                      |
| engine          | Android runtime/capture/camera/classify/gate/oracle/config；不依赖具体后端             |
| backend:a11y    | 无障碍服务、overlay、同步事件订阅                                                      |
| backend:shizuku | ShellService、client/protocol/oracle/restore                                           |
| backend:lsposed | App 侧桥接客户端与 Binder resolver                                                     |
| bridge:protocol | LSPosed 常量与 AIDL，保留原 .xposed.protocol 包名                                      |
| xposed          | system_server 的 entry/hook/power，不依赖 core/engine/UI                               |

## 进程与入口

主进程包含 Activity、GazeService、GazeA11yService、引擎及 App 侧后端。Shizuku UserService 在 shell 身份进程运行；LSPosed 模块在 system_server 运行，不能把主进程库和模型带进去。

包名根 cn.rainafter.app.gazekeep；applicationId、Shizuku authority 与 LSPosed scope 同一身份，不使用 debug 后缀。MainActivity、GazeService、GazeA11yService、ShellService、LegacyEntry 等外部入口类名保持稳定，普通类目录与包名一致。

接口变化先改设计及真源再改调用方。AIDL、descriptor、transaction 编号、协议版本与字段顺序属于跨进程兼容边界。hidden-api 存根和现代 libxposed 入口尚未建立，不添加空模块伪装实现。

资源所有者和源码导航见[维护导航](../maintenance/overview.md)，各模块细节按[索引](../index.md)加载。现有分层取舍见 [ADR 0001](../adr/0001-process-module-boundaries.md)。
