# 采集与分类

本页定义 CameraX 帧所有权、分类边界与纯投票规则，检测结果仅用于本轮保亮决策。

## 帧生命周期

AttentionCaptureSession 在 FrameSource 的 analyzer 同步回调内调用分类器，结果只保留判定枚举；ImageProxy 不进入结果对象。CameraX 每帧 finally close，采集结束只解绑本轮 ImageAnalysis/Preview；绑定、解绑与生命周期变更均在主线程执行。取消异常在清理后继续传播。只有自检可附加 Preview。

core 的 CapturePlan 是采集计划唯一类型，包含帧数、间隔、绝对截止和灵敏度，直接传到采集会话与 FrameSource。中止原因统一使用 core 类型；正常完成不重复保存帧数，回调异常必须中止，不能当作正常结束。SDK 自行取消按分类错误处理；调用方取消在释放后继续传播。

当前 CameraX 参数：640×480、YUV420、KEEP_ONLY_LATEST；150 ms 帧间隔；预热在少于两帧且未满 200 ms 时丢帧。采集最多三帧，投票已有可信结论可提前结束。打开时间预算和实际设备延迟不能混称，验收时分别记录相机打开与整次采集延迟，不能混用。

相机占用、禁用、权限丢失、截止或错误中止时，已有 Looking 样本也不允许 Extend。异常释放与默认熄屏优先于获得新样本。

## 采集所有权与取消

进程共享 CaptureOwnershipCoordinator，由 AppGraph 注入自检与服务；自检的独立租约优先于服务检测，取得租约立即取消在途服务采集，新自检等待旧采集完成资源交接。服务在租约存在或其他采集未清理时返回取消中止，不排队打开相机。旧租约释放不能清除新租约。

每个 CameraXFrameSource 串行采集，close 幂等取消等待 provider 或正在采集的操作，关闭后拒绝新采集。共享 provider future 不由调用方取消。绝对截止覆盖 provider 等待与绑定准备，过期不绑定。结束先禁止新帧、清除 analyzer 并解绑本轮 use cases，再等待 analyzer 队列排空后交还所有权；清理独立于调用方取消，单项失败不能跳过其他资源清理。

分类仍在同步 analyzer 回调内完成；取消不得通过中断线程提前关闭正在被 ML Kit 使用的图像。解绑先执行，在途任务结束后 finally 关闭图像；分类器 close 延迟到在途 classify 结束。迟到分类结果不能发布给调用方。1600 ms 是采集预算，不是硬件释放承诺；分别记录解绑、客户端消失与再次可用耗时，不由绿点显示时长推断占用。

## ML Kit 与纯判定

只用 bundled face-detection：FAST、分类 ALL、不启用 landmark/contour，minFaceSize=.15。Y 平面步长 8 采样均值，小于 25 判 TooDark，避免继续调用模型。

显著脸过滤阈值为 .18×.6；judgeFeatures 依次检查无脸、多脸、脸太小、头部姿态、宽松模式、双眼。标准阈值 yaw/pitch 25°、双眼概率 >=.6、脸宽 >=.18；缺失眼睛概率按 0。宽松模式仍要求单脸、足够脸宽和正视，只跳过眼睛检查。概率/角度仅用于当帧判断，不进入日志或长期状态。

AttentionVoter 默认三帧取两票：两帧 Looking 才 Looking。NoFace、FaceTooSmall、HeadTurned、EyesClosed 属于 Away；MultipleFaces、TooDark、ClassifierError 属于 Unknown。未形成多数或最终平票返回 Unknown，一律不保亮。

## 验证与变更

改分类器、阈值或投票必须先改活动设计和最小失败用例，重跑[分类计数场景](../development/testing.md#分类与页面场景)。正常光正视 10 次至少 9 次 Looking、转头 90° 十次均非 Looking、闭眼不 Looking；另外覆盖眼镜、暗光、多脸、侧脸与相机抢占。

每次验收附代码/APK 版本与设备条件，结果不推广到其他 ROM。MediaPipe、Camera2 备用实现尚未交付，当前版本的实机性能仍待验证。

## 运行门控

会话拥有接近、光照、加速度和 CameraManager 可用性订阅；息屏暂停传感器，关闭全部注销。接近为距离小于 min(5 cm, maximumRange)；归一化 Z 小于 −0.8 且持续 300 ms 为朝下。加速度超过 5 秒未更新为未知；光照/接近是 on-change 传感器，值在本次监听周期内有效。缺失输入保持未知，不声称该设备具备全部保护。

必须收到前摄可用回调且其他公开逻辑摄像头都可用，才通过相机门控；后摄扫码或系统相机优先。检测自身打开相机后的 unavailable 不误当外部抢占；打开结果和 CameraX 中止路径仍是最终依据。实际 ROM 的占用通知与让出效果需设备验收；系统隐私指示器与无感取舍见[LSPosed 隐私指示器设计](backends/lsposed.md#相机隐私指示器)。
