# 降亮前检测与真实熄屏时刻

仅在 off−lead 检测会让长超时先降亮再恢复，因此保留真实 off deadline，并在已知 dim−lead 执行。未知 dim 回退 off−lead，不伪造精确性、不改系统设置。桥接 v2 和不同 oracle 的精确/估算边界由这一取舍产生。

来源：既有架构 §0/相关实现决议，整理日期 2026-10-08。当前实现与验证范围见[维护导航](../maintenance/overview.md)。
