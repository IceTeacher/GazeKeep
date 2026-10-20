const screenWidth = 392,
  screenHeight = 848,
  bezel = 12,
  radius = 50;
export const PHONE = Object.freeze({
  width: screenWidth + bezel * 2,
  height: screenHeight + bezel * 2,
  depth: 40,
  radius,
  bezel,
  screenRadius: radius - bezel,
  screenWidth,
  screenHeight,
  appWidth: 392,
  appHeight: 784,
  statusHeight: 40,
  navHeight: 24,
});
/** @param {number} height */
export function fitPhone(height) {
  return { scale: height / PHONE.height, width: (height * PHONE.width) / PHONE.height, height };
}
/** 屏幕矩形到相机投影四边形的单应变换。 @param {number} width @param {number} height @param {number[][]} corners */
export function projectPlane(width, height, corners) {
  const source = [
      [0, 0],
      [width, 0],
      [width, height],
      [0, height],
    ],
    rows = [];
  for (let i = 0; i < 4; i++) {
    const [x, y] = source[i],
      [u, v] = corners[i];
    rows.push([x, y, 1, 0, 0, 0, -u * x, -u * y, u], [0, 0, 0, x, y, 1, -v * x, -v * y, v]);
  }
  for (let col = 0; col < 8; col++) {
    let pivot = col;
    for (let r = col + 1; r < 8; r++) if (Math.abs(rows[r][col]) > Math.abs(rows[pivot][col])) pivot = r;
    [rows[col], rows[pivot]] = [rows[pivot], rows[col]];
    const divisor = rows[col][col];
    if (Math.abs(divisor) < 1e-12) throw new Error("Degenerate screen projection");
    for (let c = col; c < 9; c++) rows[col][c] /= divisor;
    for (let r = 0; r < 8; r++) {
      if (r === col) continue;
      const factor = rows[r][col];
      for (let c = col; c < 9; c++) rows[r][c] -= factor * rows[col][c];
    }
  }
  const [a, b, c, d, e, f, g, h] = rows.map((row) => row[8]);
  return [a, d, 0, g, b, e, 0, h, 0, 0, 1, 0, c, f, 0, 1];
}
/** @param {number[]} m @param {number} x @param {number} y */
export function mapPlanePoint(m, x, y) {
  const z = m[3] * x + m[7] * y + m[15];
  return [(m[0] * x + m[4] * y + m[12]) / z, (m[1] * x + m[5] * y + m[13]) / z];
}
