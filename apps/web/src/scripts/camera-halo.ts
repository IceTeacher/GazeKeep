import { haloFrame, initialHalo } from "./camera-motion.mjs";

/** 仅采样提示可见时连续绘制；后台恢复保留相位并丢弃旧速度。 */
export function initCameraHalo() {
  const phone = document.querySelector<HTMLElement>("#phone-chassis")!;
  const halo = document.querySelector<SVGElement>(".camera-halo")!;
  const arc = halo.querySelector("circle")!;
  const reduced = matchMedia("(prefers-reduced-motion: reduce)");
  let state = initialHalo(),
    raf = 0,
    time = 0,
    y = scrollY,
    visible = false,
    disposed = false;
  const active = () => phone.dataset.effect === "camera-pulse";
  function render(now: number) {
    raf = 0;
    if (disposed || !visible || document.hidden) return;
    state = haloFrame(state, {
      dt: time ? now - time : 16,
      delta: scrollY - y,
      active: active(),
      reduced: reduced.matches,
    });
    time = now;
    y = scrollY;
    halo.style.opacity = String(state.opacity);
    halo.style.transform = `rotate(${state.angle % 360}deg)`;
    const circumference = 2 * Math.PI * 12;
    const length = (circumference * state.arc) / 360;
    arc.setAttribute("stroke-dasharray", `${length} ${circumference - length}`);
    if (!reduced.matches && (active() || state.opacity > 0)) schedule();
  }
  function schedule() {
    if (!disposed && !raf && visible && !document.hidden) raf = requestAnimationFrame(render);
  }
  function resume() {
    time = 0;
    y = scrollY;
    state.speed = 0;
    cancelAnimationFrame(raf);
    raf = 0;
    schedule();
  }
  const mutation = new MutationObserver(resume);
  mutation.observe(phone, { attributes: true, attributeFilter: ["data-effect"] });
  const observer = new IntersectionObserver(([entry]) => {
    visible = entry.isIntersecting;
    resume();
  });
  observer.observe(phone);
  document.addEventListener("visibilitychange", resume);
  reduced.addEventListener("change", resume);
  return () => {
    disposed = true;
    cancelAnimationFrame(raf);
    mutation.disconnect();
    observer.disconnect();
    document.removeEventListener("visibilitychange", resume);
    reduced.removeEventListener("change", resume);
  };
}
