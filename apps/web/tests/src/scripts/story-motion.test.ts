import { describe, expect, test } from "vitest";
import {
  demoScene,
  featurePose,
  featureReveal,
  featureSpan,
  followPose,
  heroDrift,
  heroPose,
  mobileFeaturePhoneHeight,
  mobileReadingLayout,
  mobileStoryFollow,
  mobileStoryProgress,
  mobileStorySnaps,
  readingOpacity,
  sceneState,
  screenDim,
  selectSection,
  storyPose,
} from "../../../src/scripts/story-motion.ts";

describe("首屏与三幕姿态", () => {
  test("首屏保留旧版倾斜，滚出后逐轴回正", () => {
    expect(heroPose(0)).toEqual({ rotateX: 7, rotateY: -12, rotateZ: 6 });
    expect(heroPose(0, true)).toEqual({ rotateX: 5, rotateY: -10, rotateZ: 5 });
    expect(heroPose(1)).toEqual({ rotateX: 0, rotateY: 0, rotateZ: 0 });
    const mid = heroPose(0.5);
    expect(mid.rotateX > 0 && mid.rotateX < 7).toBe(true);
    expect(mid.rotateY < 0 && mid.rotateY > -12).toBe(true);
  });

  test("首屏电影式漂移：缓慢、有界、连续，滚出首屏后归零", () => {
    const samples = Array.from({ length: 600 }, (_, i) => heroDrift(i * 0.1, 0));
    for (const d of samples) {
      expect(Math.abs(d.rotateX)).toBeLessThanOrEqual(2.5);
      expect(Math.abs(d.rotateY)).toBeLessThanOrEqual(4.5);
      expect(Math.abs(d.rotateZ)).toBeLessThanOrEqual(1.5);
      expect(Math.abs(d.x)).toBeLessThanOrEqual(0.02);
      expect(d.scale).toBeGreaterThanOrEqual(0);
      expect(d.scale).toBeLessThanOrEqual(0.03);
    }
    // 漂移确实发生，且 100ms 内的变化很小（缓慢平移而非抖动）。
    expect(Math.max(...samples.map((d) => d.rotateY)) - Math.min(...samples.map((d) => d.rotateY))).toBeGreaterThan(4);
    for (let i = 1; i < samples.length; i++)
      expect(Math.abs(samples[i]!.rotateY - samples[i - 1]!.rotateY)).toBeLessThan(0.2);
    expect(heroDrift(12, 1)).toEqual({ x: 0, rotateX: 0, rotateY: 0, rotateZ: 0, scale: 0 });
    const half = heroDrift(12, 0.5);
    expect(Math.abs(half.rotateY)).toBeLessThan(Math.abs(heroDrift(12, 0).rotateY));
    expect(Math.abs(heroDrift(12, 0, true).rotateY)).toBeLessThan(Math.abs(heroDrift(12, 0).rotateY));
  });

  test("工作流标题停靠时（进度约 0.07）手机完全回正且不露出手部", () => {
    for (const progress of [0, 0.065, 0.15]) {
      for (const mobile of [false, true]) {
        const pose = storyPose(progress, mobile);
        expect(pose.grip).toBe(0);
        expect(pose.scene).toBe(0);
        expect(pose.turn).toBe(0);
        expect(pose.rotateX).toBe(0);
        expect(pose.rotateZ).toBe(0);
        expect(pose.scale).toBe(1);
        expect(pose.x).toBe(storyPose(0, mobile).x);
      }
    }
    // 第一幕仍按原样完整进入持机姿态。
    expect(storyPose(1).grip).toBe(1);
    expect(storyPose(1).turn).toBe(-28);
  });

  test("三幕位置在边界连续，侧面仍保留正面投影", () => {
    expect(storyPose(-1).turn).toBe(0);
    expect(storyPose(1).turn).toBe(-28);
    expect(storyPose(1).x).toBeLessThan(0);
    expect(storyPose(2).grip).toBe(0);
    expect(storyPose(2).scale).toBe(1);
    expect(storyPose(3).x).toBe(storyPose(0).x);
    expect(storyPose(2).turn).toBe(0);
    expect(storyPose(3).turn).toBe(0);
    expect(storyPose(3).dim).toBe(1);
    for (const boundary of [1, 2, 2.25, 3]) {
      const before = storyPose(boundary - 0.00001);
      const after = storyPose(boundary + 0.00001);
      for (const key of Object.keys(before) as (keyof typeof before)[])
        expect(Math.abs(before[key] - after[key]), key).toBeLessThan(0.002);
    }
    expect(Math.cos((storyPose(1).turn * Math.PI) / 180)).toBeGreaterThan(0.6);
  });

  test("正反滚动和直接跳转使用相同姿态，小屏缩短侧转", () => {
    const positions = [0, 0.5, 1, 1.8, 2.5, 3];
    const forward = positions.map((p) => storyPose(p));
    expect(
      positions
        .toReversed()
        .map((p) => storyPose(p))
        .toReversed(),
    ).toEqual(forward);
    expect(storyPose(1, true).turn).toBe(-20);
    expect(storyPose(3, true).turn).toBe(0);
  });

  test("阶段由位置确定，快速反向与重叠区不依赖观察器回调顺序", () => {
    const starts = [0, 100, 200, 300];
    expect(selectSection(starts, 250)).toBe(2);
    expect(selectSection(starts, 100)).toBe(1);
    expect(selectSection(starts, -20)).toBe(0);
    expect(selectSection(starts, 999)).toBe(3);
  });

  test("持机三轴与缩放统一插值，结束后恢复正面", () => {
    const held = storyPose(1),
      mobile = storyPose(1, true);
    expect(held.rotateX).toBe(3);
    expect(held.rotateZ).toBe(-6);
    expect(Math.abs(held.scale - 0.82)).toBeLessThan(1e-10);
    expect(Math.abs(held.x + (0.38 * 1280) / 1200)).toBeLessThan(1e-10);
    expect(mobile.rotateX).toBe(2);
    expect(mobile.rotateZ).toBe(-4);
    expect(Math.abs(mobile.scale - 0.78)).toBeLessThan(1e-10);
    expect(Math.abs(mobile.x + 0.06)).toBeLessThan(1e-10);
    for (const p of [0, 3]) {
      const pose = storyPose(p);
      expect(pose.rotateX).toBe(0);
      expect(pose.rotateZ).toBe(0);
      expect(pose.scale).toBe(1);
    }
    expect(storyPose(2.25).grip).toBe(0);
    expect(storyPose(1.8).turn).toBe(-28);
  });

  test("持机投影中心按视口比例定位，首尾保留原位置", () => {
    for (const width of [320, 390, 768, 1280, 1920]) {
      const mobile = width <= 760,
        held = storyPose(1, mobile, width);
      const unit = (mobile ? width : Math.min(width, 1200)) / 2;
      expect(Math.abs(width / 2 + held.x * unit - width * (mobile ? 0.47 : 0.31))).toBeLessThan(1e-8);
      expect(storyPose(0, mobile, width).x).toBe(mobile ? 0.12 : 0.52);
      expect(storyPose(3, mobile, width).x).toBe(storyPose(0, mobile, width).x);
    }
  });

  test("黑屏连续进入并在后端区域前恢复", () => {
    expect(storyPose(2).dim).toBe(0);
    expect(storyPose(2.25).dim > 0 && storyPose(2.25).dim < 1).toBe(true);
    expect(storyPose(2.5).dim).toBe(1);
    expect(storyPose(4).dim).toBe(0);
  });

  test("横移以倾斜包络过渡，静止段回正且方向随运动", () => {
    for (const p of [-1, 0, 1, 1.8, 2, 2.25, 3, 4]) expect(storyPose(p).travel, String(p)).toBe(0);
    // 第一幕横移在标题停靠区（进度 0.2）之后开始，倾斜峰值位于 0.6。
    expect(storyPose(0.6).travel).toBeCloseTo(-1, 12);
    expect(storyPose(2.625).travel).toBe(1);
    expect(storyPose(0.6).turn < -28 * 0.5 && storyPose(0.6).rotateZ < 0).toBe(true);
    expect(storyPose(2.625).turn > 0 && storyPose(2.625).rotateZ > 0).toBe(true);
    expect(storyPose(2.625).scale).toBeLessThan(1);
    expect(storyPose(0.5, true).travel).toBe(0);
    // 缓动两端速度为零：起步与收尾都比线性慢。
    const start = storyPose(0).x,
      end = storyPose(1).x;
    expect(Math.abs(storyPose(0.05).x - start)).toBeLessThan(Math.abs(end - start) * 0.05);
    expect(Math.abs(storyPose(0.95).x - end)).toBeLessThan(Math.abs(end - start) * 0.05);
    let last = start;
    for (let p = 0; p <= 1; p += 0.05) {
      const x = storyPose(p).x;
      expect(x).toBeLessThanOrEqual(last + 1e-12);
      last = x;
    }
  });

  test("专注范围区手机移到左侧，过渡中倾斜并在两端回正", () => {
    const right = featurePose(0, 1280),
      left = featurePose(1, 1280);
    expect(right.x).toBe(storyPose(4).x);
    expect(left.x).toBe(-right.x);
    for (const pose of [right, left]) {
      expect(pose.turn).toBe(0);
      expect(pose.rotateZ).toBe(0);
      expect(pose.scale).toBe(1);
    }
    const mid = featurePose(0.5, 1280);
    expect(mid.travel).toBe(-1);
    expect(mid.turn < 0 && mid.rotateZ < 0 && mid.scale < 1).toBe(true);
    expect(featurePose(-1, 1280)).toEqual(right);
    expect(featurePose(2, 1280)).toEqual(left);
    for (const width of [800, 1280, 1920]) {
      const unit = Math.min(width, 1200) / 2;
      expect(width / 2 + featurePose(1, width).x * unit).toBeLessThan(width / 2);
    }
  });

  test("专注范围到设置区镜像横移：手机由左回到右侧，朝右侧转并在两端回正", () => {
    const left = featurePose(0, 1280, 1),
      right = featurePose(1, 1280, 1);
    expect(left.x).toBe(featurePose(1, 1280).x);
    expect(right.x).toBe(featurePose(0, 1280).x);
    for (const pose of [left, right]) expect([pose.turn, pose.rotateZ, pose.scale]).toEqual([0, 0, 1]);
    const mid = featurePose(0.5, 1280, 1),
      mirror = featurePose(0.5, 1280);
    expect(mid.travel).toBe(1);
    expect(mid.turn).toBe(-mirror.turn);
    expect(mid.rotateZ).toBe(-mirror.rotateZ);
    expect(mid.scale).toBe(mirror.scale);
    // 设置文案在右侧手机越过中线回到右侧后才淡入。
    const reveal = (p: number) => featureReveal(featurePose(p, 1280, 1).x, 1280, 1);
    expect(reveal(0)).toBe(0);
    expect(reveal(0.5)).toBe(0);
    expect(reveal(1)).toBe(1);
  });

  test("专注范围横移在吸附减速段完成：终点为范围停靠点，起点在运行方式列表离开设备之后", () => {
    const base = { listBottom: 5700, headingTop: 6547, stageTop: 100, phoneHeight: 621, snapTop: 100, viewport: 900 };
    const span = featureSpan(base);
    // 与第二幕到第三幕一致：吸附停靠时横移刚好结束，而不是在吸附中途一闪而过。
    expect(span.end).toBe(base.headingTop - base.snapTop);
    expect(span.start).toBe(base.listBottom - (base.stageTop + base.phoneHeight * 0.12));
    // 范围文案随横移后半程淡入，手机掠过时不压住清晰文字，停靠时完全显示。
    const reveal = (p: number) => featureReveal(featurePose(p, 1280).x, 1280);
    expect(reveal(0)).toBe(0);
    expect(reveal(0.5)).toBe(0);
    expect(reveal(0.75) > 0 && reveal(0.75) < 1).toBe(true);
    expect(reveal(1)).toBe(1);
    // 区间过短时向前延伸，保证横移可见。
    const tight = featureSpan({ ...base, listBottom: 6400 });
    expect(tight.end - tight.start).toBe(base.viewport * 0.25);
    expect(tight.end).toBe(base.headingTop - base.snapTop);
  });

  test("阻尼跟随收敛到位置决定的姿态，长间隔直接对齐", () => {
    const target = { x: 1, turn: -20, rotateX: 0, rotateZ: 4, scale: 0.9 };
    let pose = { x: 0, turn: 0, rotateX: 0, rotateZ: 0, scale: 1 };
    const first = followPose(pose, target, 16);
    expect(first.x > 0 && first.x < 1).toBe(true);
    for (let i = 0; i < 120; i++) pose = followPose(pose, target, 16);
    expect(pose).toEqual(target);
    expect(followPose({ ...target, x: -5 }, target, 500)).toEqual(target);
    expect(followPose(undefined, target, 16)).toEqual(target);
  });
});

describe("场景路由与阅读布局", () => {
  test("隐私先换页，手部后淡出；后端只在全黑期间切换", () => {
    expect(sceneState(1.4).route).toBe("privacy");
    expect(sceneState(1.4).grip).toBe(1);
    expect(sceneState(2).grip).toBe(0);
    expect(sceneState(3.39).route).toBe("privacy");
    expect(sceneState(3.41).route).toBe("backend");
    for (const p of [3.39, 3.41]) expect(sceneState(p).dim).toBe(1);
    expect(sceneState(4).dim).toBe(0);
  });

  test("文案拥有完整阅读区间，正反滚动结果一致", () => {
    for (const p of [0.7, 1, 1.3]) expect(readingOpacity(p, 1)).toBe(1);
    const p = [1, 1.4, 1.6, 2, 2.5, 3.4, 4];
    expect(p.map((v) => sceneState(v))).toEqual(
      p
        .toReversed()
        .map((v) => sceneState(v))
        .toReversed(),
    );
  });

  test("各区段的内屏演示场景：标题处自动浏览，第一幕检测、第二幕滚动隐私页，功能区各自演示", () => {
    expect(demoScene({ route: "home", status: "idle", tour: false })).toBe("none");
    expect(demoScene({ route: "home", status: "idle", tour: true })).toBe("tour");
    expect(demoScene({ route: "home", status: "sampling", tour: true })).toBe("detect");
    expect(demoScene({ route: "privacy", status: "gaze_detected", tour: false })).toBe("privacy");
    // 第三幕为黑屏，不演示。
    expect(demoScene({ route: "privacy", status: "dim_fallback", tour: false })).toBe("none");
    expect(demoScene({ route: "backend", status: "backend_shizuku", tour: false })).toBe("backends");
    expect(demoScene({ route: "whitelist", status: "idle", tour: false })).toBe("apps");
    expect(demoScene({ route: "settings", status: "idle", tour: false })).toBe("settings");
  });

  test("移动端设备与最长正文分区，短屏与大字号改为自然阅读", () => {
    for (const height of [740, 844, 932]) {
      const layout = mobileReadingLayout(height, 220);
      expect(layout.compact).toBe(false);
      expect(layout.copyTop).toBeGreaterThan(layout.phoneHeight + 16);
      expect(layout.copyTop + 220).toBeLessThan(height - 60);
    }
    expect(mobileReadingLayout(390, 220).compact).toBe(true);
    expect(mobileReadingLayout(740, 500).compact).toBe(true);
  });

  test("移动端常规高度下手机占据更多上半屏，正文下移但不越过底部留白", () => {
    const layout = mobileReadingLayout(875, 220);
    expect(layout.compact).toBe(false);
    expect(layout.phoneHeight, `phoneHeight=${layout.phoneHeight}`).toBeGreaterThanOrEqual(420);
    expect(layout.copyTop + 220).toBeLessThan(875 - 60);
    expect(mobileReadingLayout(2000, 220).phoneHeight).toBeLessThanOrEqual(460);
  });

  test("移动端后端与范围设备槽不受正文挤压，比三幕手机更大且有上限", () => {
    for (const height of [740, 875, 932]) {
      const feature = mobileFeaturePhoneHeight(height);
      expect(feature).toBeGreaterThan(mobileReadingLayout(height, 220).phoneHeight);
      expect(feature).toBeLessThanOrEqual((height - 60) * 0.7 + 1e-9);
    }
    expect(mobileFeaturePhoneHeight(2000)).toBe(560);
    expect(mobileFeaturePhoneHeight(300)).toBe(210);
  });

  test("移动端三幕吸附点落在整数幕，且都在面板固定期间", () => {
    for (const height of [740, 875, 932]) {
      const snaps = mobileStorySnaps(height, 3);
      expect(snaps.length).toBe(3);
      snaps.forEach((offset, i) => {
        expect(Math.abs(mobileStoryProgress(1000 + offset, 1000, height) - (i + 1))).toBeLessThan(1e-9);
        expect(readingOpacity(i + 1, i + 1)).toBe(1);
      });
      // 三幕区高 365svh，固定面板在剩余一屏前释放。
      expect(snaps[2]).toBeLessThan(height * 3.65 - height);
    }
  });
});

describe("屏幕遮罩与移动端跟随", () => {
  test("第三幕至后端区：进入后端区时仍全黑，随滚动连续渐亮，正反一致", () => {
    const at = (progress: number) => screenDim({ progress, inBackend: progress >= 3.5 });
    expect(at(3.4)).toBe(1);
    expect(at(3.5)).toBe(1);
    expect(at(3.75) > 0.1 && at(3.75) < 0.9).toBe(true);
    expect(at(4.05)).toBe(0);
    let last = 1;
    for (let p = 3.5; p <= 4.05; p += 0.01) {
      const d = at(p);
      expect(d).toBeLessThanOrEqual(last + 1e-9);
      last = d;
    }
    expect(screenDim({ progress: 3.6, inWhitelist: true })).toBe(0);
  });

  test("移动端后端设备槽从视口下方进入时由黑渐亮", () => {
    const at = (slotTop: number) => screenDim({ progress: 3.6, mobile: true, inBackend: true, slotTop, viewport: 844 });
    expect(at(844)).toBe(1);
    expect(at(844 * 0.62) > 0 && at(844 * 0.62) < 1).toBe(true);
    expect(at(844 * 0.4)).toBe(0);
    // 三幕面板释放后、后端设备槽出现前保持黑屏，不在离场的设备上提前亮屏
    for (const progress of [3.3, 3.6, 3.9, 4.2]) expect(screenDim({ progress, mobile: true })).toBe(1);
  });

  test("紧凑模式只按阶段状态显示黑屏", () => {
    expect(screenDim({ progress: 3, compact: true, status: "dim_fallback" })).toBe(1);
    expect(screenDim({ progress: 3, compact: true, status: "backend_lsposed" })).toBe(0);
  });

  test("移动端视口固定段位置与滚动无关，前后边界与文档锚定连续", () => {
    const storyTop = 2000,
      releaseAt = 4200,
      header = 60;
    expect(mobileStoryFollow(storyTop - header - 1, storyTop, releaseAt, header).mode).toBe("before");
    const fixed = [storyTop - header, 2600, 3333, releaseAt].map((y) =>
      mobileStoryFollow(y, storyTop, releaseAt, header),
    );
    for (const f of fixed) expect(f).toEqual({ mode: "fixed", top: header + 16 });
    const after = [releaseAt + 1, releaseAt + 500].map((y) => mobileStoryFollow(y, storyTop, releaseAt, header));
    expect(after[0]).toEqual(after[1]);
    // 释放瞬间文档坐标对应的视口位置与固定段相同
    expect(after[0]).toEqual({ mode: "absolute", y: releaseAt + header + 16 });
  });
});
