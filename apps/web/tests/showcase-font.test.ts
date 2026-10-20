import test from "node:test";
import assert from "node:assert/strict";
import { readNames, renameFont, sfntTables } from "../scripts/showcase-font.ts";

type Name = { platform: number; encoding: number; language: number; id: number; value: string };
const encode = ({ platform, value }: Name) =>
  platform === 1 ? Buffer.from(value, "latin1") : Buffer.from(Buffer.from(value, "utf16le").swap16());
function nameTable(names: Name[]) {
  const strings = names.map(encode);
  const table = Buffer.alloc(6 + names.length * 12);
  table.writeUInt16BE(names.length, 2);
  table.writeUInt16BE(table.length, 4);
  let offset = 0;
  names.forEach((name, i) => {
    const at = 6 + i * 12;
    [name.platform, name.encoding, name.language, name.id, strings[i].length, offset].forEach((v, k) =>
      table.writeUInt16BE(v, at + k * 2),
    );
    offset += strings[i].length;
  });
  return Buffer.concat([table, ...strings]);
}
function sfnt(tables: Record<string, Buffer>) {
  const tags = Object.keys(tables).sort();
  const header = Buffer.alloc(12 + tags.length * 16);
  header.writeUInt32BE(0x4f54544f, 0);
  header.writeUInt16BE(tags.length, 4);
  let offset = header.length;
  const bodies = tags.map((tag, i) => {
    const body = Buffer.concat([tables[tag], Buffer.alloc((4 - (tables[tag].length % 4)) % 4)]);
    header.write(tag, 12 + i * 16, "latin1");
    header.writeUInt32BE(offset, 12 + i * 16 + 8);
    header.writeUInt32BE(tables[tag].length, 12 + i * 16 + 12);
    offset += body.length;
    return body;
  });
  return Buffer.concat([header, ...bodies]);
}
const checksum = (bytes: Uint8Array) => {
  const view = Buffer.concat([Buffer.from(bytes), Buffer.alloc((4 - (bytes.length % 4)) % 4)]);
  let sum = 0;
  for (let i = 0; i < view.length; i += 4) sum = (sum + view.readUInt32BE(i)) >>> 0;
  return sum;
};
const source: Name[] = [
  { platform: 1, encoding: 0, language: 0, id: 1, value: "Noto Sans CJK SC" },
  { platform: 3, encoding: 1, language: 0x409, id: 1, value: "Noto Sans CJK SC" },
  { platform: 3, encoding: 1, language: 0x409, id: 2, value: "Regular" },
  { platform: 3, encoding: 1, language: 0x804, id: 4, value: "思源黑体 Regular" },
  { platform: 3, encoding: 1, language: 0x409, id: 6, value: "NotoSansCJKsc-Regular" },
  { platform: 3, encoding: 1, language: 0x409, id: 16, value: "Noto Sans CJK SC" },
];

test("展示字体改名覆盖全部平台与语言的家族、全名、PostScript 与排版家族名", () => {
  const font = renameFont(sfnt({ head: Buffer.alloc(54), name: nameTable(source) }), {
    1: "GazeKeep Showcase Sans",
    4: "GazeKeep Showcase Sans",
    6: "GazeKeepShowcaseSans",
    16: "GazeKeep Showcase Sans",
  });
  assert.deepEqual(
    readNames(font).map(({ platform, language, id, value }) => [platform, language, id, value]),
    [
      [1, 0, 1, "GazeKeep Showcase Sans"],
      [3, 0x409, 1, "GazeKeep Showcase Sans"],
      [3, 0x409, 2, "Regular"],
      [3, 0x804, 4, "GazeKeep Showcase Sans"],
      [3, 0x409, 6, "GazeKeepShowcaseSans"],
      [3, 0x409, 16, "GazeKeep Showcase Sans"],
    ],
  );
});

test("改名后表目录有序、四字节对齐，表校验和与整体校验调整值有效", () => {
  const font = renameFont(sfnt({ head: Buffer.alloc(54), name: nameTable(source), post: Buffer.alloc(32) }), {
    6: "GazeKeepShowcaseSans",
  });
  const tables = sfntTables(font);
  assert.deepEqual(
    tables.map((t) => t.tag),
    ["head", "name", "post"],
  );
  for (const { tag, offset, length, sum } of tables) {
    assert.equal(offset % 4, 0, tag);
    const body = font.subarray(offset, offset + length);
    if (tag === "head") {
      const zeroed = Buffer.from(body);
      zeroed.writeUInt32BE(0, 8);
      assert.equal(sum, checksum(zeroed), tag);
    } else assert.equal(sum, checksum(body), tag);
  }
  assert.equal(checksum(font), 0xb1b0afba);
});

test("Mac 平台记录无法以单字节表示新名称时明确失败", () => {
  const font = sfnt({ head: Buffer.alloc(54), name: nameTable(source) });
  assert.throws(() => renameFont(font, { 1: "注视常亮" }), /Mac/);
  assert.throws(() => renameFont(sfnt({ head: Buffer.alloc(54) }), { 1: "x" }), /name/);
});
