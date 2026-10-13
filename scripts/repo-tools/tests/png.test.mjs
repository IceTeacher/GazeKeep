import assert from "node:assert/strict";
import test from "node:test";
import { pngPixelHash } from "../scripts/png-pixels.mjs";
const fixtures = {
  filter0:
    "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAGklEQVR4nGMQ0bD5H5WS95+BkYn5v1tA1H8ANiwGlzh650wAAAAASUVORK5CYII=",
  filter1:
    "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAGklEQVR4nGMU0bD572ZjxMDIyMT839UvnAEAJcoEHVKnwcoAAAAASUVORK5CYII=",
  filter2:
    "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAG0lEQVR4nGMS0bD5H5WS95/p7a3jDG/evGEAAEfYCPlKjt2uAAAAAElFTkSuQmCC",
  filter3:
    "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAGklEQVR4nGMW0bD5HxAQ0MD8/d3TBklZJQYAPQQGkFHqc9kAAAAASUVORK5CYII=",
  filter4:
    "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAGklEQVR4nGMR0bD572ZjxMDy9tZxhjcacgwANLgF9B1Qjv0AAAAASUVORK5CYII=",
  rgb: "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFklEQVR4nGMQ0bCJSsljYGRidguIAgAR5gKbNWkZZAAAAABJRU5ErkJggg==",
  transparent: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGPgEpFjAAAApQA9aZiIvAAAAABJRU5ErkJggg==",
  transparentZero: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAXpeqz8AAAAASUVORK5CYII=",
  visible: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGPgEpFjBAAApgA+aUYlSQAAAABJRU5ErkJggg==",
};
const image = (key) => Buffer.from(fixtures[key], "base64");

test("PNG 不同过滤和 RGB/RGBA 编码保留相同像素", () => {
  const expected = pngPixelHash(image("filter0"));
  for (const key of ["filter1", "filter2", "filter3", "filter4", "rgb"])
    assert.equal(pngPixelHash(image(key)), expected);
});
test("透明像素隐藏颜色变化允许，但可见像素和透明度变化拒绝", () => {
  assert.equal(pngPixelHash(image("transparent")), pngPixelHash(image("transparentZero")));
  assert.notEqual(pngPixelHash(image("transparent")), pngPixelHash(image("visible")));
});
test("PNG 损坏和不支持的格式不能写成图标通过", () => {
  const corrupted = image("filter0");
  corrupted[corrupted.length - 1] ^= 1;
  assert.throws(() => pngPixelHash(corrupted), /CRC/);
  assert.throws(() => pngPixelHash(Buffer.from("not a PNG")), /PNG/);
  assert.throws(() => pngPixelHash(image("filter0").subarray(0, 35)), /PNG/);
});
