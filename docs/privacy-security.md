# 隐私与安全

[Android 工程规则](../apps/app/AGENTS.md#产品安全承诺) 中的十条安全承诺是产品边界，现有实现差距不能改变承诺。本页维护隐私文案和各进程的安全要求。

## 隐私文案真源

1. 只在快要熄屏时打开前置摄像头，看完立刻关闭。
2. 画面只在内存里判断「在不在看」，不保存、不上传。
3. 看不清、没看清、旁边有别人，都会让屏幕正常熄灭。

资源 `apps/app/shared/src/commonMain/composeResources/values/strings.xml` 中 `privacy_line_1`～`privacy_line_3` 必须与本页逐字一致。改文案同时维护本页和资源，保持唯一真源。

## 数据与权限边界

推理只在 App 进程。`ImageProxy` 不离开 analyzer 回调，不保存、长持缓冲、写日志或跨进程；每帧 finally close。唯一预览位于有步骤的自检页面，离页/取消即释放。

禁止 INTERNET、存储/相册、QUERY_ALL_PACKAGES、SYSTEM_ALERT_WINDOW、READ_PHONE_STATE。依赖合并带入网络权限必须移除；禁止广告、统计或崩溃上报 SDK。无障碍只用事件类型与包名，保持 canRetrieveWindowContent=false，不访问 event.source 或文本。

## 失败与特权进程

Unknown、暗光、多脸、相机失败和任何链路中断都不保亮。上限默认 30 分钟，到点必须熄一次。当前 Hold 释放路径的差距见[任务清单](../tasks/todo.md)，不能将“不再 Extend”的单测等同于“实机已熄”。

system_server 候选逐个解析，签名失配不安装，异常不抛回系统；回调整体捕获 Throwable。PMS 锁内只计算与 Handler 投递，不调用 Binder、不取其他锁。禁止协程、DataStore、Compose、模型、相机与文件 I/O。

不修改用户系统设置，唯独 Shizuku 超时退路允许临时修改；必须先持久化原值，在服务重启、binder 重连及息屏后恢复。现有 apply() 的异步写入尚不能证明满足此顺序，保留为安全债务。

## 日志与证据

允许时间、包名、Attention/原因枚举、后端、是否 extend、耗时。禁止图像、框、角度、概率、身份、密钥与原始系统 dump。调试日志默认关，24 小时滚动删除是产品要求；完整留存当前尚未实现。

源码、测试 fixture 与设备记录均不得包含人脸图片、序列号或签名材料。异常通知同类 10 分钟最多一次；无法发通知时必须在验收中记录权限限制。
