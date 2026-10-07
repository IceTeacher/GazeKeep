# GazeKeep 产品设计上下文

## Register

product

## Users

使用 Android 手机阅读、浏览，想在正视屏幕时自动延后熄屏的用户。当前验收机为小米 12 Pro / HyperOS。

## Product Purpose

临近系统降亮或熄屏时短暂检测注视；只有可信的注视结果才延后一次超时。设计依据为 docs/architecture 下的规划文档与架构设计，冲突时以后者 §0 的决议为准。

## Brand Personality

克制、清晰、安静。采用规划指定的 HyperOS 设置层级与 miuix 原生交互。

## Anti-references

不沿用 M0 / M1 调试按钮堆叠成主界面，不暴露分类参数、角度或概率，不在成功保亮时弹提示。

## Design Principles

- 主界面提供总开关、后端状态，以及检测、范围、保护、隐私入口。
- 设置项必须对应实际引擎行为；不可用能力说明原因并提供可执行的授权入口。
- Unknown 按 Away 处理，任何中断都让系统正常熄屏。
- 自检是有明确步骤的授权与校验流程，预览只在自检页面。
- 隐私三句话与规划文档逐字一致。

## Accessibility & Inclusion

使用平台字体和 miuix 控件语义；长文字可换行，设置页面可滚动，适应系统字体大小和明暗主题。选中、未就绪及失败状态同时用文字表达。
