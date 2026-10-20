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
}

/** 实体机身与真实 DOM 屏幕共用相机投影；只在变化时绘制。 */
export function initPhoneDisplay() {
  const host = document.querySelector<HTMLElement>("#phone-visual")!;
  const rig = document.querySelector<HTMLElement>(".device-rig")!;
  const screen = document.querySelector<HTMLElement>("#phone-chassis")!;
  const canvas = document.querySelector<HTMLCanvasElement>("#phone-body")!;
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
    body: Three.Group | undefined;
  let disposed = false,
    visible = true,
    raf = 0,
    failed = false;
  const cleanups: (() => void)[] = [];
  const occlusionVertices = new Map<Three.Mesh, Three.Vector3[]>();
  const corners = [
    [-PHONE.screenWidth / 2, PHONE.screenHeight / 2],
    [PHONE.screenWidth / 2, PHONE.screenHeight / 2],
    [PHONE.screenWidth / 2, -PHONE.screenHeight / 2],
    [-PHONE.screenWidth / 2, -PHONE.screenHeight / 2],
  ];
  function render() {
    raf = 0;
    if (disposed) return;
    const { width, height } = host.getBoundingClientRect(),
      fit = fitPhone(rig.clientHeight);
    const displayScale = fit.scale * (pose.scale ?? 1);
    if (!width || !height) return;
    let points: number[][];
    if (library && camera && body && !failed) {
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
      const project = (x: number, y: number, z: number) => {
        const p = new T.Vector3(x - PHONE.screenWidth / 2, PHONE.screenHeight / 2 - y, z)
          .applyMatrix4(body!.matrixWorld)
          .project(camera!);
        return [((p.x + 1) * width) / 2, ((1 - p.y) * height) / 2];
      };
      if (visible && !document.hidden && (pose.gripProgress ?? 0) > 0) {
        // 每个凸实体取实际几何顶点的投影外轮廓，包含倒角、玻璃和侧键。
        const occluders: string[] = [];
        body.traverse((object) => {
          if (!(object instanceof T.Mesh)) return;
          let vertices = occlusionVertices.get(object);
          if (!vertices) {
            const positions = object.geometry.getAttribute("position");
            const unique = new Map<string, Three.Vector3>();
            for (let i = 0; i < positions.count; i++) {
              const v = new T.Vector3().fromBufferAttribute(positions, i);
              unique.set(v.toArray().join(","), v);
            }
            vertices = [...unique.values()];
            occlusionVertices.set(object, vertices);
          }
          const projected: number[][] = [];
          for (const vertex of vertices) {
            const v = vertex.clone().applyMatrix4(object.matrixWorld).project(camera!);
            projected.push([((v.x + 1) * width) / 2, ((1 - v.y) * height) / 2]);
          }
          occluders.push(polygonPath(projectedHull(projected)));
        });
        const { joints: _joints, ...grip } = gripPaths(
          project,
          host.getBoundingClientRect().left,
          pose.gripProgress ?? 0,
        );
        const paths = { ...grip, occluder: occluders.join(" ") };
        for (const svg of host.querySelectorAll("svg.grip-layer"))
          svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
        for (const el of host.querySelectorAll<SVGPathElement>("[data-grip-path]"))
          el.setAttribute("d", paths[el.dataset.gripPath as keyof typeof paths]);
      }
      points = corners.map(([x, y]) => {
        const p = new T.Vector3(x, y, PHONE.depth / 2 + 2).applyMatrix4(body!.matrixWorld).project(camera!);
        return [((p.x + 1) * width) / 2, ((1 - p.y) * height) / 2];
      });
      if (renderer && visible && !document.hidden) {
        const dpr = Math.min(devicePixelRatio, innerWidth <= 760 ? 1.5 : 2);
        if (renderer.getPixelRatio() !== dpr) renderer.setPixelRatio(dpr);
        const size = renderer.getSize(new T.Vector2());
        if (size.x !== width || size.y !== height) renderer.setSize(width, height, false);
        renderer.render(scene!, camera);
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
  const observer = new ResizeObserver(schedule);
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
  schedule();
  void initialize();
  return {
    update(next: PhonePose) {
      pose = next;
      schedule();
    },
    dispose() {
      disposed = true;
      cancelAnimationFrame(raf);
      observer.disconnect();
      visibility.disconnect();
      document.removeEventListener("visibilitychange", schedule);
      canvas.removeEventListener("webglcontextlost", fallback);
      renderer?.dispose();
      cleanups.forEach((fn) => fn());
      occlusionVertices.clear();
    },
  };
}
