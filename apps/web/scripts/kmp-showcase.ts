/** webpack 自动 publicPath 运行时的报错文本；出现即说明 wasm 地址会随页面最后一个脚本漂移。 */
const AUTO_PUBLIC_PATH = "Automatic publicPath is not supported";

/** 拒绝使用自动 publicPath 的 KMP 入口；固定值在 apps/app/shared/webpack.config.d 中配置。 */
export function assertFixedPublicPath(bundle: string, target: string) {
  if (bundle.includes(AUTO_PUBLIC_PATH))
    throw new Error(`${target} 入口仍使用 webpack 自动 publicPath，托管方注入的脚本会改写 wasm 地址`);
}
