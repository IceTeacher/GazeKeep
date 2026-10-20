## Development

When starting the dev server, use background mode:

```
astro dev --background
```

Manage the background server with `astro dev stop`, `astro dev status`, and `astro dev logs`.

## Documentation

Full documentation: https://docs.astro.build

Consult these guides before working on related tasks:

- [Adding pages, dynamic routes, or middleware](https://docs.astro.build/en/guides/routing/)
- [Working with Astro components](https://docs.astro.build/en/basics/astro-components/)
- [Using React, Vue, Svelte, or other framework components](https://docs.astro.build/en/guides/framework-components/)
- [Adding or managing content](https://docs.astro.build/en/guides/content-collections/)
- [Adding styles or using Tailwind](https://docs.astro.build/en/guides/styling/)
- [Supporting multiple languages](https://docs.astro.build/en/guides/internationalization/)

## 官网工程

继承根目录规则。官网使用纯 Astro/TypeScript 与双语数据源，不添加 React 或统计服务。KMP 生成资源通过 `pnpm --filter web build` 同步，禁止提交生成物；保留 Gradle 的依赖锁文件。产品事实、隐私三句以产品文档和 Compose 资源为准。

改动后运行 `pnpm --filter web test`、`pnpm --filter web lint`、`pnpm --filter web check`、完整构建及受影响的浏览器场景。相关设计见 [官网架构](../../docs/architecture/web/website.md)。
