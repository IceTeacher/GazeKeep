import { PHONE, fitPhone, projectPlane } from "./phone-geometry.mjs";
import { gripPaths, projectedHull, polygonPath } from "./grip-geometry.mjs";
import type * as Three from "three";
export interface PhonePose {
  rotateX: number;
  rotateY: number;
  rotateZ: number;
  scale?: number;
  offsetY?: number;
  gripProgress?: number;
  /** 设备容器当前的水平平移（CSS 像素），用于推算宿主视口位置而不读取布局。 */
  shift?: number;
}

/** 实体机身与真实 DOM 屏幕共用相机投影；只在变化时绘制。 */
export function initPhoneDisplay() {
  const host = document.querySelector<HTMLElement>("#phone-visual")!;
  const rig = document.querySelector<HTMLElement>(".device-rig")!;
  const screen = document.querySelector<HTMLElement>("#phone-chassis")!;
  const canvas = document.querySelector<HTMLCanvasElement>("#phone-body")!;
  const gripLayers = [...host.querySelectorAll<SVGSVGElement>("svg.grip-layer")],
    gripPathElements = [...host.querySelectorAll<SVGPathElement>("[data-grip-path]")];
  rig.parentElement!.style.setProperty("--phone-ratio", String(PHONE.width / PHONE.height));
  host.style.setProperty("--screen-radius", `${PHONE.screenRadius}px`);
  host.style.setProperty("--screen-width", `${PHONE.screenWidth}px`);
  host.style.setProperty("--screen-height", `${PHONE.screenHeight}px`);
  host.style.setProperty("--status-height", `${PHONE.statusHeight}px`);
  host.style.setProperty("--nav-height", `${PHONE.navHeight}px`);
  let pose: PhonePose = { rotateX: 7, rotateY: -12, rotateZ: 6 };
  let library: typeof Three | undefined,
    renderer: Three.WebGLRenderer | undefined,
    scene: Three.Scene | undefined,
    camera: Three.PerspectiveCamera | undefined,
    body: Three.Group | undefined,
    projection: Three.Matrix4 | undefined,
    bodyProjection: Three.Matrix4 | undefined,
    combined: Three.Matrix4 | undefined;
  let disposed = false,
    posed = false,
    visible = true,
    raf = 0,
    failed = false;
  const cleanups: (() => void)[] = [];
  /** 机身已按叙事姿态绘制或已确认降级后，才显示首屏设备。 */
  const reveal = () => {
    if (document.documentElement.dataset.phone === "pending") document.documentElement.dataset.phone = "ready";
  };
  const occlusionVertices = new Map<Three.Mesh, Float32Array>();
  // 宿主尺寸与扣除设备平移后的左边界在尺寸变化时缓存，滚动帧不读取布局。
  let hostWidth = 0,
    hostHeight = 0,
    hostLeft = 0,
    rigHeight = 0,
    appliedShift = 0;
  function measure() {
    const box = host.getBoundingClientRect();
    hostWidth = box.width;
    hostHeight = box.height;
    const transform = getComputedStyle(rig).transform;
    hostLeft = box.left - (transform === "none" ? 0 : new DOMMatrix(transform).m41);
    rigHeight = rig.clientHeight;
  }
  const corners = [
    [-PHONE.screenWidth / 2, PHONE.screenHeight / 2],
    [PHONE.screenWidth / 2, PHONE.screenHeight / 2],
    [PHONE.screenWidth / 2, -PHONE.screenHeight / 2],
    [-PHONE.screenWidth / 2, -PHONE.screenHeight / 2],
  ];
  function render() {
    raf = 0;
    if (disposed) return;
    const width = hostWidth,
      height = hostHeight,
      fit = fitPhone(rigHeight);
    const displayScale = fit.scale * (pose.scale ?? 1);
    if (!width || !height) return;
    let points: number[][];
    if (library && camera && body && projection && bodyProjection && combined && !failed) {
      const T = library;
      camera.aspect = width / height;
      camera.fov = T.MathUtils.radToDeg(2 * Math.atan(height / (2 * displayScale * 1800)));
      camera.updateProjectionMatrix();
      camera.updateMatrixWorld();
      body.position.x = 0;
      body.position.y = -(pose.offsetY ?? 0) / displayScale;
      body.rotation.set(
        ...([-pose.rotateX, pose.rotateY, -pose.rotateZ].map(T.MathUtils.degToRad) as [number, number, number]),
      );
      body.updateMatrixWorld(true);
      const center = new T.Vector3(0, 0, PHONE.depth / 2 + 2).applyMatrix4(body.matrixWorld);
      body.position.x = -center.x * (pose.gripProgress ?? 0);
      body.updateMatrixWorld(true);
      const viewProjection = projection.multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse);
      // 合并矩阵后逐点投影，不为每个顶点分配对象。
      const toScreen = (m: number[], x: number, y: number, z: number) => {
        const w = m[3] * x + m[7] * y + m[11] * z + m[15];
        return [
          (((m[0] * x + m[4] * y + m[8] * z + m[12]) / w + 1) * width) / 2,
          ((1 - (m[1] * x + m[5] * y + m[9] * z + m[13]) / w) * height) / 2,
        ];
      };
      const bodyMatrix = bodyProjection.multiplyMatrices(viewProjection, body.matrixWorld).elements;
      const project = (x: number, y: number, z: number) =>
        toScreen(bodyMatrix, x - PHONE.screenWidth / 2, PHONE.screenHeight / 2 - y, z);
      if (visible && !document.hidden && (pose.gripProgress ?? 0) > 0) {
        // 每个凸实体取实际几何顶点的投影外轮廓，包含倒角、玻璃和侧键。
        const occluders: string[] = [],
          meshProjection = combined;
        body.traverse((object) => {
          if (!(object instanceof T.Mesh)) return;
          let vertices = occlusionVertices.get(object);
          if (!vertices) {
            const positions = object.geometry.getAttribute("position");
            const unique = new Map<string, number[]>();
            for (let i = 0; i < positions.count; i++) {
              const v = [positions.getX(i), positions.getY(i), positions.getZ(i)];
              unique.set(v.join(","), v);
            }
            vertices = new Float32Array([...unique.values()].flat());
            occlusionVertices.set(object, vertices);
          }
          const m = meshProjection.multiplyMatrices(viewProjection, object.matrixWorld).elements;
          const projected: number[][] = [];
          for (let i = 0; i < vertices.length; i += 3)
            projected.push(toScreen(m, vertices[i], vertices[i + 1], vertices[i + 2]));
          occluders.push(polygonPath(projectedHull(projected)));
        });
        const { joints: _joints, ...grip } = gripPaths(project, hostLeft + appliedShift, pose.gripProgress ?? 0);
        const paths = { ...grip, occluder: occluders.join(" ") };
        const viewBox = `0 0 ${width} ${height}`;
        for (const svg of gripLayers) if (svg.getAttribute("viewBox") !== viewBox) svg.setAttribute("viewBox", viewBox);
        for (const el of gripPathElements) el.setAttribute("d", paths[el.dataset.gripPath as keyof typeof paths]);
      }
      points = corners.map(([x, y]) => toScreen(bodyMatrix, x, y, PHONE.depth / 2 + 2));
      if (renderer && visible && !document.hidden) {
        const dpr = Math.min(devicePixelRatio, innerWidth <= 760 ? 1.5 : 2);
        if (renderer.getPixelRatio() !== dpr) renderer.setPixelRatio(dpr);
        const size = renderer.getSize(new T.Vector2());
        if (size.x !== width || size.y !== height) renderer.setSize(width, height, false);
        renderer.render(scene!, camera);
        if (posed) reveal();
      }
    } else {
      const w = PHONE.screenWidth * fit.scale,
        h = PHONE.screenHeight * fit.scale,
        left = (width - w) / 2,
        top = (height - h) / 2;
      points = [
        [left, top],
        [left + w, top],
        [left + w, top + h],
        [left, top + h],
      ];
    }
    const matrix = projectPlane(PHONE.screenWidth, PHONE.screenHeight, points);
    screen.style.transform = `matrix3d(${matrix.join(",")})`;
    host.dataset.projection = JSON.stringify(points);
    if (failed) reveal();
  }
  function schedule() {
    if (!disposed && !raf) raf = requestAnimationFrame(render);
  }
  function fallback() {
    failed = true;
    host.dataset.renderer = "fallback";
    renderer?.dispose();
    renderer = undefined;
    canvas.hidden = true;
    schedule();
  }
  const observer = new ResizeObserver(() => {
    measure();
    schedule();
  });
  const resize = () => {
    measure();
    schedule();
  };
  window.addEventListener("resize", resize);
  observer.observe(host);
  observer.observe(rig);
  const visibility = new IntersectionObserver(([entry]) => {
    visible = entry.isIntersecting;
    if (visible) schedule();
  });
  visibility.observe(host);
  document.addEventListener("visibilitychange", schedule);
  canvas.addEventListener("webglcontextlost", fallback);
  async function initialize() {
    try {
      const T = await import("three");
      if (disposed) return;
      library = T;
      projection = new T.Matrix4();
      bodyProjection = new T.Matrix4();
      combined = new T.Matrix4();
      scene = new T.Scene();
      camera = new T.PerspectiveCamera(30, 1, 1, 5000);
      camera.position.z = 1800;
      body = new T.Group();
      scene.add(body);
      function rounded(w: number, h: number, r: number) {
        const shape = new T.Shape(),
          x = -w / 2,
          y = -h / 2;
        shape.moveTo(x + r, y);
        shape.lineTo(x + w - r, y);
        shape.quadraticCurveTo(x + w, y, x + w, y + r);
        shape.lineTo(x + w, y + h - r);
        shape.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
        shape.lineTo(x + r, y + h);
        shape.quadraticCurveTo(x, y + h, x, y + h - r);
        shape.lineTo(x, y + r);
        shape.quadraticCurveTo(x, y, x + r, y);
        return shape;
      }
      const alloy = new T.MeshPhysicalMaterial({ color: 0x79847f, metalness: 0.75, roughness: 0.23, clearcoat: 0.7 });
      const black = new T.MeshStandardMaterial({ color: 0x101814, metalness: 0.25, roughness: 0.3 });
      const shell = new T.ExtrudeGeometry(rounded(PHONE.width - 8, PHONE.height - 8, PHONE.radius - 4), {
        depth: PHONE.depth - 8,
        bevelEnabled: true,
        bevelSegments: 4,
        steps: 1,
        bevelSize: 4,
        bevelThickness: 4,
        curveSegments: 16,
      });
      shell.translate(0, 0, -PHONE.depth / 2 + 4);
      body.add(new T.Mesh(shell, alloy));
      const face = new T.ShapeGeometry(rounded(PHONE.width - 10, PHONE.height - 10, PHONE.radius - 5), 24);
      const glass = new T.Mesh(face, black);
      glass.position.z = PHONE.depth / 2 + 0.5;
      body.add(glass);
      const keyGeometry = new T.BoxGeometry(6, 80, 18),
        powerGeometry = new T.BoxGeometry(6, 40, 18);
      const volume = new T.Mesh(keyGeometry, alloy);
      volume.position.set(PHONE.width / 2 + 1, 150, 0);
      body.add(volume);
      const power = new T.Mesh(powerGeometry, alloy);
      power.position.set(PHONE.width / 2 + 1, 40, 0);
      body.add(power);
      scene.add(new T.HemisphereLight(0xf1fff7, 0x37463e, 3));
      for (const [color, intensity, x, y, z] of [
        [0xffffff, 5, -500, 600, 1000],
        [0xb7f2da, 3, 600, 100, 600],
        [0xffffff, 4, -400, -200, -300],
      ]) {
        const light = new T.DirectionalLight(color, intensity);
        light.position.set(x, y, z);
        scene.add(light);
      }
      cleanups.push(() => {
        shell.dispose();
        face.dispose();
        keyGeometry.dispose();
        powerGeometry.dispose();
        alloy.dispose();
        black.dispose();
      });
      renderer = new T.WebGLRenderer({ canvas, alpha: true, antialias: true, powerPreference: "low-power" });
      renderer.setClearColor(0x000000, 0);
      renderer.outputColorSpace = T.SRGBColorSpace;
      renderer.toneMapping = T.ACESFilmicToneMapping;
      host.dataset.renderer = "webgl";
      schedule();
    } catch {
      fallback();
    }
  }
  host.dataset.renderer = "loading";
  measure();
  schedule();
  void initialize();
  return {
    /** 在滚动帧内同步绘制，避免设备姿态落后 CSS 平移一帧。 */
    update(next: PhonePose) {
      pose = next;
      posed = true;
      appliedShift = next.shift ?? 0;
      cancelAnimationFrame(raf);
      render();
    },
    dispose() {
      disposed = true;
      cancelAnimationFrame(raf);
      observer.disconnect();
      window.removeEventListener("resize", resize);
      visibility.disconnect();
      document.removeEventListener("visibilitychange", schedule);
      canvas.removeEventListener("webglcontextlost", fallback);
      renderer?.dispose();
      cleanups.forEach((fn) => fn());
      occlusionVertices.clear();
    },
  };
}
