import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";

/** harfbuzz-subset.wasm 的原始导出，只声明用到的部分；指针均为 wasm 线性内存偏移。 */
interface HarfBuzzSubset {
  memory: WebAssembly.Memory;
  _initialize(): void;
  malloc(size: number): number;
  free(pointer: number): void;
  hb_blob_create(data: number, length: number, mode: number, user: number, destroy: number): number;
  hb_blob_destroy(blob: number): void;
  hb_blob_get_data(blob: number, length: number): number;
  hb_blob_get_length(blob: number): number;
  hb_face_create(blob: number, index: number): number;
  hb_face_destroy(face: number): void;
  hb_face_reference_blob(face: number): number;
  hb_set_add(set: number, value: number): void;
  hb_set_clear(set: number): void;
  hb_set_invert(set: number): void;
  hb_subset_input_create_or_fail(): number;
  hb_subset_input_destroy(input: number): void;
  hb_subset_input_set(input: number, kind: number): number;
  hb_subset_input_unicode_set(input: number): number;
  hb_subset_or_fail(face: number, input: number): number;
}
const HB_MEMORY_MODE_WRITABLE = 2;
const HB_SUBSET_SETS_NAME_ID = 4;
const HB_SUBSET_SETS_NAME_LANG_ID = 5;

/** 用 HarfBuzz hb-subset 按字符裁剪字体集合中的一个字体；保留全部 name 记录与语言，其余沿用默认选项。 */
export async function subsetFace(font: Uint8Array, index: number, text: string) {
  const wasm = await readFile(createRequire(import.meta.url).resolve("harfbuzzjs/dist/harfbuzz-subset.wasm"));
  const hb = (await WebAssembly.instantiate(wasm)).instance.exports as unknown as HarfBuzzSubset;
  hb._initialize();
  const input = hb.hb_subset_input_create_or_fail();
  if (!input) throw new Error("hb_subset_input_create_or_fail 失败");
  const data = hb.malloc(font.byteLength);
  new Uint8Array(hb.memory.buffer).set(font, data);
  const blob = hb.hb_blob_create(data, font.byteLength, HB_MEMORY_MODE_WRITABLE, 0, 0);
  const face = hb.hb_face_create(blob, index);
  hb.hb_blob_destroy(blob);
  try {
    for (const kind of [HB_SUBSET_SETS_NAME_ID, HB_SUBSET_SETS_NAME_LANG_ID]) {
      const set = hb.hb_subset_input_set(input, kind);
      hb.hb_set_clear(set);
      hb.hb_set_invert(set);
    }
    const unicodes = hb.hb_subset_input_unicode_set(input);
    for (const char of text) hb.hb_set_add(unicodes, char.codePointAt(0)!);
    const subset = hb.hb_subset_or_fail(face, input);
    if (!subset) throw new Error("hb_subset_or_fail 失败，源字体或索引可能不正确");
    const result = hb.hb_face_reference_blob(subset);
    const offset = hb.hb_blob_get_data(result, 0),
      length = hb.hb_blob_get_length(result);
    // 子集化期间内存可能增长，读取结果时重新取视图。
    const output = Buffer.from(new Uint8Array(hb.memory.buffer, offset, length));
    hb.hb_blob_destroy(result);
    hb.hb_face_destroy(subset);
    if (!length) throw new Error("子集字体为空");
    return output;
  } finally {
    hb.hb_subset_input_destroy(input);
    hb.hb_face_destroy(face);
    hb.free(data);
  }
}

export interface SfntTable {
  tag: string;
  sum: number;
  offset: number;
  length: number;
}
export function sfntTables(font: Buffer): SfntTable[] {
  return Array.from({ length: font.readUInt16BE(4) }, (_, i) => {
    const at = 12 + i * 16;
    return {
      tag: font.toString("latin1", at, at + 4),
      sum: font.readUInt32BE(at + 4),
      offset: font.readUInt32BE(at + 8),
      length: font.readUInt32BE(at + 12),
    };
  });
}
function checksum(bytes: Buffer) {
  let sum = 0;
  for (let i = 0; i < bytes.length; i += 4) {
    let word = 0;
    for (let k = 0; k < 4; k++) word = word * 256 + (bytes[i + k] ?? 0);
    sum = (sum + word) >>> 0;
  }
  return sum;
}
function table(font: Buffer, tag: string) {
  const entry = sfntTables(font).find((t) => t.tag === tag);
  if (!entry) throw new Error(`字体缺少 ${tag} 表`);
  return font.subarray(entry.offset, entry.offset + entry.length);
}

export interface NameRecord {
  platform: number;
  encoding: number;
  language: number;
  id: number;
  bytes: Buffer;
}
function nameRecords(name: Buffer) {
  const count = name.readUInt16BE(2),
    storage = name.readUInt16BE(4);
  const records: NameRecord[] = Array.from({ length: count }, (_, i) => {
    const [platform, encoding, language, id, length, offset] = [0, 1, 2, 3, 4, 5].map((k) =>
      name.readUInt16BE(6 + i * 12 + k * 2),
    );
    return { platform, encoding, language, id, bytes: name.subarray(storage + offset, storage + offset + length) };
  });
  // 格式 1 的语言标签同样引用字符串存储区，重建时一并保留。
  const tagCount = name.readUInt16BE(0) === 1 ? name.readUInt16BE(6 + count * 12) : 0;
  const tags = Array.from({ length: tagCount }, (_, i) => {
    const at = 8 + count * 12 + i * 4;
    const length = name.readUInt16BE(at),
      offset = name.readUInt16BE(at + 2);
    return name.subarray(storage + offset, storage + offset + length);
  });
  return { format: name.readUInt16BE(0), records, tags };
}
const decode = ({ platform, bytes }: NameRecord) =>
  platform === 1 ? bytes.toString("latin1") : Buffer.from(bytes).swap16().toString("utf16le");
export function readNames(font: Buffer) {
  return nameRecords(table(font, "name")).records.map((record) => ({ ...record, value: decode(record) }));
}
function encodeName(record: NameRecord, value: string) {
  if (record.platform === 0 || record.platform === 3) return Buffer.from(value, "utf16le").swap16();
  if (record.platform === 1 && /^[\x20-\x7e]*$/.test(value)) return Buffer.from(value, "latin1");
  throw new Error(`无法为 Mac 或其他平台记录编码名称：${value}`);
}

/** 替换指定 nameID 的全部平台与语言记录，再按标签顺序重排表并重算校验和。 */
export function renameFont(font: Buffer, names: Record<number, string>) {
  const { format, records, tags } = nameRecords(table(font, "name"));
  const strings = [...records.map((r) => (Object.hasOwn(names, r.id) ? encodeName(r, names[r.id]) : r.bytes)), ...tags];
  const header = Buffer.alloc(6 + records.length * 12 + (format === 1 ? 2 + tags.length * 4 : 0));
  header.writeUInt16BE(format, 0);
  header.writeUInt16BE(records.length, 2);
  header.writeUInt16BE(header.length, 4);
  let offset = 0;
  strings.forEach((bytes, i) => {
    const fields =
      i < records.length
        ? [records[i].platform, records[i].encoding, records[i].language, records[i].id, bytes.length, offset]
        : [bytes.length, offset];
    const at = i < records.length ? 6 + i * 12 : 8 + records.length * 12 + (i - records.length) * 4;
    fields.forEach((value, k) => header.writeUInt16BE(value, at + k * 2));
    offset += bytes.length;
  });
  if (format === 1) header.writeUInt16BE(tags.length, 6 + records.length * 12);
  const replaced = new Map(
    sfntTables(font).map((t) => [t.tag, Buffer.from(font.subarray(t.offset, t.offset + t.length))]),
  );
  replaced.set("name", Buffer.concat([header, ...strings]));
  return assemble(font.readUInt32BE(0), replaced);
}
function assemble(version: number, tables: Map<string, Buffer>) {
  const tags = [...tables.keys()].sort();
  const directory = Buffer.alloc(12 + tags.length * 16);
  const power = 2 ** Math.floor(Math.log2(tags.length));
  directory.writeUInt32BE(version, 0);
  directory.writeUInt16BE(tags.length, 4);
  directory.writeUInt16BE(power * 16, 6);
  directory.writeUInt16BE(Math.log2(power), 8);
  directory.writeUInt16BE(tags.length * 16 - power * 16, 10);
  const head = tables.get("head");
  if (head) head.writeUInt32BE(0, 8);
  let offset = directory.length;
  const bodies = tags.map((tag, i) => {
    const body = tables.get(tag)!;
    const at = 12 + i * 16;
    directory.write(tag, at, "latin1");
    directory.writeUInt32BE(checksum(body), at + 4);
    directory.writeUInt32BE(offset, at + 8);
    directory.writeUInt32BE(body.length, at + 12);
    const padded = Buffer.concat([body, Buffer.alloc((4 - (body.length % 4)) % 4)]);
    offset += padded.length;
    return padded;
  });
  const font = Buffer.concat([directory, ...bodies]);
  if (head) {
    const at = directory.readUInt32BE(12 + tags.indexOf("head") * 16 + 8);
    font.writeUInt32BE((0xb1b0afba - checksum(font)) >>> 0, at + 8);
  }
  return font;
}
