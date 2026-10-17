# GazeKeep 产品设计上下文

## Register

product

## Users

在 Android 手机阅读和浏览，希望正视时延后熄屏的用户。

## Product Purpose

只有可信注视才延后一次超时，默认放行系统熄屏。产品范围以 [产品定义](docs/product/overview.md) 为准，安全承诺以 [Android 工程规则](apps/app/AGENTS.md#产品安全承诺) 为准；实现缺口见[任务清单](docs/plans/todo.md)。

## Brand Personality

克制、清晰、安静，采用 HyperOS 设置层级与 miuix 交互。

## Anti-references

不把调试按钮堆成主界面，不向用户暴露角度/概率，不在成功保亮时提示。

## Design Principles

页面、自检和平台边界见 [UI 架构](docs/architecture/ui.md)。设置必须对应实际能力，未实现或不可用能力明确说明；隐私文案引用[唯一真源](docs/product/privacy-security.md)。

## Accessibility & Inclusion

使用平台字体和 miuix 语义，长文字换行、页面可滚动，兼容字体大小和明暗主题；选中、未就绪、失败同时以文字表达。
