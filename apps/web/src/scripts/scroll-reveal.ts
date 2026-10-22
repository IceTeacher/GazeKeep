/**
 * 元素进入视口时依次显现：带 data-reveal 的元素在根元素 data-motion="on" 期间隐藏，
 * 首次进入视口加上 is-revealed 播放入场动画。只有本模块运行时才隐藏，脚本失败不会让内容消失；
 * 减少动态时不隐藏也不播放。
 */
export function initScrollReveal() {
  const root = document.documentElement;
  const reduce = matchMedia("(prefers-reduced-motion: reduce)");
  const targets = [...document.querySelectorAll<HTMLElement>("[data-reveal]")];
  const io = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        entry.target.classList.add("is-revealed");
        io.unobserve(entry.target);
      }
    },
    { rootMargin: "0px 0px -8% 0px", threshold: 0.12 },
  );
  const apply = () => {
    if (reduce.matches) {
      root.dataset.motion = "off";
      io.disconnect();
      targets.forEach((target) => target.classList.add("is-revealed"));
      return;
    }
    root.dataset.motion = "on";
    targets.filter((target) => !target.classList.contains("is-revealed")).forEach((target) => io.observe(target));
  };
  apply();
  reduce.addEventListener("change", apply);
  return () => {
    io.disconnect();
    reduce.removeEventListener("change", apply);
    delete root.dataset.motion;
  };
}
