# 采集与分类

本页定义 CameraX 帧所有权、分类边界与纯投票规则，检测结果仅用于本轮保亮决策。

## 帧生命周期

AttentionCaptureSession 在 FrameSource 的 analyzer 同步回调内调用分类器，结果只保留判定枚举；ImageProxy 不进入结果对象。CameraX 每帧 finally close，采集结束 unbindAll，取消异常继续传播。只有自检可附加 Preview。

当前 CameraX 参数：640×480、YUV420、KEEP_ONLY_LATEST；150 ms 帧间隔；预热在少于两帧且未满 200 ms 时丢帧。采集最多三帧，投票已有可信结论可提前结束。打开时间预算和实际设备延迟不能混称，验收时分别记录相机打开与整次采集延迟，不能混用。

相机占用、禁用、权限丢失、截止或错误中止时，已有 Looking 样本也不允许 Extend。异常释放与默认熄屏优先于获得新样本。

## ML Kit 与纯判定

只用 bundled face-detection：FAST、分类 ALL、不启用 landmark/contour，minFaceSize=.15。Y 平面步长 8 采样均值，小于 25 判 TooDark，避免继续调用模型。

显著脸过滤阈值为 .18×.6；judgeFeatures 依次检查无脸、多脸、脸太小、头部姿态、宽松模式、双眼。标准阈值 yaw/pitch 25°、双眼概率 >=.6、脸宽 >=.18；缺失眼睛概率按 0。宽松模式仍要求单脸、足够脸宽和正视，只跳过眼睛检查。概率/角度仅用于当帧判断，不进入日志或长期状态。

AttentionVoter 默认三帧取两票：两帧 Looking 才 Looking。NoFace、FaceTooSmall、HeadTurned、EyesClosed 属于 Away；MultipleFaces、TooDark、ClassifierError 属于 Unknown。未形成多数或最终平票返回 Unknown，一律不保亮。

## 验证与变更

改分类器、阈值或投票必须先改活动设计和最小失败用例，重跑[分类计数场景](../testing.md#分类与页面场景)。正常光正视 10 次至少 9 次 Looking、转头 90° 十次均非 Looking、闭眼不 Looking；另外覆盖眼镜、暗光、多脸、侧脸与相机抢占。

每次验收附代码/APK 版本与设备条件，结果不推广到其他 ROM。MediaPipe、Camera2 备用实现尚未交付，当前版本的实机性能仍待验证。
