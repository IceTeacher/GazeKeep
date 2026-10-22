// 展示页由 iframe 打开并与生成资源同目录；固定相对 publicPath，避免 webpack 自动推断时
// 在 module 脚本中取不到 currentScript，转而采用托管方注入的页尾脚本（如 Cloudflare 统计 beacon）的源。
config.output = config.output || {};
config.output.publicPath = "./";
