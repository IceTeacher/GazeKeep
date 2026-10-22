export const site = {
  name: "GazeKeep",
  /** 生产域名：分享元数据的 og:url 与图片使用绝对地址，预览部署也指向生产页。 */
  url: "https://gazekeep.rainafter.cn",
  repository: "https://github.com/IceTeacher/GazeKeep",
  download: "https://github.com/IceTeacher/GazeKeep/releases/latest",
  docs: "https://github.com/IceTeacher/GazeKeep/blob/main/docs/product/usage.md",
  license: "https://github.com/IceTeacher/GazeKeep/blob/main/LICENSE",
  /** 无脚本时浏览器界面色，与 tokens.css 的 --bg 一致；脚本运行后按实际主题读取 --bg 更新。 */
  themeColor: { dark: "#080d18", light: "#f4f7fa" },
  /** Umami 站点 ID；脚本与上报经 functions/stats 同站代理到自建实例。 */
  umamiWebsiteId: "35f452cf-4935-4581-9b9a-fac2c27cdd07",
};
