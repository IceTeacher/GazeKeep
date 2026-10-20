import { PHONE } from "./phone-geometry.mjs";
import { REFERENCE, REFERENCE_PALM } from "./grip-reference.mjs";
export { REFERENCE };
/** 镜像参考，以屏幕宽度等比缩放手部，接触锚点由真实机身校准。 */
export function referenceToScreen(x, y) {
  const scale = PHONE.screenWidth / (REFERENCE.screenRight - REFERENCE.screenLeft);
  return [(REFERENCE.screenRight - x) * scale, (y - REFERENCE.screenTop) * scale];
}
export const PALM_CONTACT = Object.freeze({
  inset: 24,
  top: PHONE.screenHeight * 0.45,
  bottom: PHONE.screenHeight * 0.9,
});
export const GRIP_SURFACE = Object.freeze({ rear: -PHONE.depth / 2 - 2, front: PHONE.depth / 2 + 2, inset: 4 });
export const thumbContact = Object.freeze({ innerX: 0, tipY: 72 });
export const FINGERS = Object.freeze(
  [0.33, 0.46, 0.6, 0.73].map((ratio, i) => ({
    center: PHONE.screenHeight * ratio,
    height: PHONE.screenHeight * [0.118, 0.122, 0.115, 0.105][i],
    innerX: PHONE.screenWidth - 4,
    outerX: [432, 438, 430, 422][i],
    rootX: -80,
  })),
);
/** 只延展前臂，掌部和手指不随视口拉伸。 */
export function forearmExtension(top, bottom, hostLeft, progress) {
  return {
    left: Math.min(-24 - hostLeft, top[0] + (-24 - hostLeft - top[0]) * Math.max(0, Math.min(1, progress))),
    top: top[1],
    bottom: bottom[1],
  };
}

/** 凸实体的投影轮廓；去掉内部顶点并统一顺时针方向，复合填充不产生孔洞。每帧调用，避免字符串去重。 */
export function projectedHull(points) {
  const sorted = points.toSorted((a, b) => a[0] - b[0] || a[1] - b[1]);
  let n = 0;
  for (const p of sorted) if (!n || p[0] !== sorted[n - 1][0] || p[1] !== sorted[n - 1][1]) sorted[n++] = p;
  sorted.length = n;
  if (n < 3) return sorted;
  const cross = (a, b, c) => (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
  const hull = [];
  for (const pass of [sorted, sorted.toReversed()]) {
    const start = hull.length;
    for (const p of pass) {
      while (hull.length > start + 1 && cross(hull[hull.length - 2], hull[hull.length - 1], p) <= 0) hull.pop();
      hull.push(p);
    }
    hull.pop();
  }
  return hull;
}
export const polygonPath = (points) => (points.length ? `M ${points.map((p) => p.join(" ")).join(" L ")} Z` : "");
/** 前后深度仅影响投影；所有皮肤合在一个复合路径中填充一次。 */
export function gripPaths(project, hostLeft, progress) {
  const rear = GRIP_SURFACE.rear,
    front = GRIP_SURFACE.front;
  const p = (x, y, z = rear) => project(x, y, z),
    q = (x, y, z = rear) => p(x, y, z).join(" ");
  const src = (x, y) => p(...referenceToScreen(x, y));
  const top = src(...REFERENCE_PALM[0][1]),
    bottom = src(...REFERENCE_PALM[10][1]),
    arm = forearmExtension(top, bottom, hostLeft, progress);
  // 将同一条三次曲线在中点拆分，接点同时保持一阶和二阶连续。
  const split = (a, b, c, d) => {
    const mid = (u, v) => u.map((x, i) => (x + v[i]) / 2);
    const ab = mid(a, b),
      bc = mid(b, c),
      cd = mid(c, d),
      abc = mid(ab, bc),
      bcd = mid(bc, cd),
      join = mid(abc, bcd);
    return {
      first: [ab, abc, join],
      second: [bcd, cd, d],
      joint: { before: abc, join, after: bcd, previous: ab, next: cd },
    };
  };
  const upper = split(
    [arm.left, arm.top],
    [arm.left + (top[0] - arm.left) * 0.65, arm.top],
    p(-190, 490),
    p(-160, 410),
  );
  const lowerStart = p(-110, 790),
    lowerBefore = p(-40, 770);
  const lower = split(
    lowerStart,
    lowerStart.map((v, i) => v + (v - lowerBefore[i]) * 0.8),
    [arm.left + (bottom[0] - arm.left) * 0.65, arm.bottom],
    [arm.left, arm.bottom],
  );
  const curve = (segment) => segment.map((v) => v.join(" ")).join(" ");
  // 实拍校正：短拇指在机身外侧，虎口向完整掌心连续展开。
  const referenceContour = `C ${q(-130, 330)} ${q(-80, 240)} ${q(-57, 124)} C ${q(-55, 92)} ${q(-50, thumbContact.tipY)} ${q(-34, thumbContact.tipY)} C ${q(-12, 72)} ${q(-5, 100)} ${q(-8, 136)} C ${q(-12, 215)} ${q(-28, 280)} ${q(-16, 345)} C ${q(12, 470)} ${q(115, 615)} ${q(100, 735)} C ${q(64, 785)} ${q(-40, 770)} ${q(-110, 790)}`;
  const palm = `M ${arm.left} ${arm.top} C ${curve(upper.first)} C ${curve(upper.second)} ${referenceContour} C ${curve(lower.first)} C ${curve(lower.second)} Z`;
  const fingers = [],
    contacts = [];
  for (const f of FINGERS) {
    const h = f.height / 2,
      ellipse = [],
      body = [],
      wrap = [];
    const outer = PHONE.screenWidth + PHONE.bezel + 20,
      cx = (f.innerX + outer) / 2,
      rx = (outer - f.innerX) / 2;
    for (let i = 0; i < 48; i++) {
      const t = (i * Math.PI * 2) / 48;
      ellipse.push(p(cx + rx * Math.cos(t), f.center + h * Math.sin(t), front));
      // 后方指身与前方指腹只取共同外轮廓，不留下独立的上下闭合边。
      const right = Math.cos(t) >= 0;
      const x = (right ? f.outerX - h : f.rootX + h) + h * Math.cos(t);
      const v = p(x, f.center + h * Math.sin(t), rear);
      body.push(v);
      if (x >= PHONE.screenWidth + PHONE.bezel - 3) wrap.push(v);
    }
    fingers.push(polygonPath(projectedHull([...body, ...ellipse])));
    contacts.push(polygonPath(projectedHull([...ellipse, ...wrap])));
  }
  // 腕掌连成柔和弧面；屏前仅露出上移后的掌根内弧。
  const palmPoints = [];
  const segments = [
    [
      [-70, 390],
      [-65, 365],
      [-12, 390],
      [0, 430],
    ],
    [
      [0, 430],
      [32, 470],
      [32, 570],
      [0, 640],
    ],
    [
      [0, 640],
      [-12, 705],
      [-28, 755],
      [-110, 790],
    ],
    [
      [-110, 790],
      [-100, 720],
      [-100, 470],
      [-70, 390],
    ],
  ];
  for (const [a, b, c, d] of segments)
    for (let i = 0; i < 32; i++) {
      const t = i / 32,
        u = 1 - t;
      const v = [0, 1].map((k) => u * u * u * a[k] + 3 * u * u * t * b[k] + 3 * u * t * t * c[k] + t * t * t * d[k]);
      palmPoints.push(p(...v, front));
    }
  const palmContact = polygonPath(palmPoints);
  const palmWindow = polygonPath([
    p(-1000, PALM_CONTACT.top, front),
    p(PALM_CONTACT.inset, PALM_CONTACT.top, front),
    p(PALM_CONTACT.inset, PALM_CONTACT.bottom, front),
    p(-1000, PALM_CONTACT.bottom, front),
  ]);
  const inset = GRIP_SURFACE.inset,
    r = PHONE.screenRadius - inset,
    w = PHONE.screenWidth - inset,
    h = PHONE.screenHeight - inset;
  const protection = `M ${q(inset + r, inset, front)} L ${q(w - r, inset, front)} Q ${q(w, inset, front)} ${q(w, inset + r, front)} L ${q(w, h - r, front)} Q ${q(w, h, front)} ${q(w - r, h, front)} L ${q(inset + r, h, front)} Q ${q(inset, h, front)} ${q(inset, h - r, front)} L ${q(inset, inset + r, front)} Q ${q(inset, inset, front)} ${q(inset + r, inset, front)} Z`;
  return {
    silhouette: [palm, ...fingers, palmContact].join(" "),
    palmContact,
    palmWindow,
    contacts: contacts.join(" "),
    protection,
    joints: [upper.joint, lower.joint],
  };
}
