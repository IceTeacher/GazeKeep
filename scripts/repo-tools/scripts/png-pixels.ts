import { createHash } from "node:crypto";
import { inflateSync } from "node:zlib";

function crc32(bytes: Uint8Array) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function paeth(left: number, above: number, upperLeft: number) {
  const p = left + above - upperLeft;
  const a = Math.abs(p - left),
    b = Math.abs(p - above),
    c = Math.abs(p - upperLeft);
  return a <= b && a <= c ? left : b <= c ? above : upperLeft;
}

/** 仅解码当前图标使用的非交错 8 位 RGB/RGBA；其他格式明确失败，不当作通过。
 * 过滤与 CRC 按 https://www.w3.org/TR/png-3/ 校验。 */
export function pngPixelHash(bytes: Buffer) {
  if (!bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))) throw new Error("不是 PNG 图标");
  let header: Buffer | undefined;
  let ended = false;
  const data: Buffer[] = [];
  for (let offset = 8; offset < bytes.length;) {
    if (offset + 12 > bytes.length) throw new Error("PNG chunk 不完整");
    const length = bytes.readUInt32BE(offset);
    const end = offset + 8 + length;
    if (end + 4 > bytes.length) throw new Error("PNG chunk 数据不完整");
    if (crc32(bytes.subarray(offset + 4, end)) !== bytes.readUInt32BE(end)) throw new Error("PNG CRC 不匹配");
    const type = bytes.toString("ascii", offset + 4, offset + 8);
    const content = bytes.subarray(offset + 8, end);
    if (type === "IHDR") {
      if (header || offset !== 8 || length !== 13) throw new Error("PNG IHDR 不正确");
      header = content;
    } else if (type === "IDAT") data.push(content);
    else if (type === "tRNS" || type === "acTL") throw new Error("PNG 图标格式不受支持");
    else if (type === "IEND") {
      if (length || end + 4 !== bytes.length) throw new Error("PNG IEND 不正确");
      ended = true;
    }
    offset = end + 4;
  }
  if (!header || !ended || !data.length) throw new Error("PNG 图标缺少必要 chunk");
  const width = header.readUInt32BE(0),
    height = header.readUInt32BE(4);
  const channels = header[9] === 2 ? 3 : header[9] === 6 ? 4 : 0;
  if (!channels || header[8] !== 8 || header[10] || header[11] || header[12])
    throw new Error("PNG 仅支持非交错 8 位 RGB/RGBA 图标");
  if (!width || !height || width * height > 4_000_000) throw new Error("PNG 图标尺寸不受支持");
  const stride = width * channels;
  const raw = inflateSync(Buffer.concat(data), { maxOutputLength: (stride + 1) * height });
  if (raw.length !== (stride + 1) * height) throw new Error("PNG 解压尺寸不匹配");
  const decoded = Buffer.alloc(stride * height);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    if (filter > 4) throw new Error("PNG 过滤类型不正确");
    for (let x = 0; x < stride; x++) {
      const index = y * stride + x;
      const left = x >= channels ? decoded[index - channels] : 0;
      const above = y ? decoded[index - stride] : 0;
      const upperLeft = y && x >= channels ? decoded[index - stride - channels] : 0;
      const predictor = [0, left, above, Math.floor((left + above) / 2), paeth(left, above, upperLeft)][filter];
      decoded[index] = (raw[y * (stride + 1) + x + 1] + predictor) & 255;
    }
  }
  const rgba = Buffer.alloc(width * height * 4);
  for (let pixel = 0; pixel < width * height; pixel++) {
    const source = pixel * channels,
      target = pixel * 4;
    const alpha = channels === 4 ? decoded[source + 3] : 255;
    // aapt2 会归零完全透明像素的隐藏颜色；可见颜色和 alpha 仍严格比较。
    if (alpha) decoded.copy(rgba, target, source, source + 3);
    rgba[target + 3] = alpha;
  }
  return createHash("sha256").update(header.subarray(0, 8)).update(rgba).digest("hex");
}
