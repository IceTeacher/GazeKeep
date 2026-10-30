/** 手机可见部分低于该比例即视为离开视口。 */
const VISIBLE_RATIO = 0.25;

export const demoVisible = (ratio: number, hidden: boolean) => !hidden && ratio >= VISIBLE_RATIO;

/**
 * 手机可见部分不足 25% 或标签页隐藏时通知暂停演示，回到 25% 以上且标签页可见时通知继续；只在变化时回调。
 * 移动端换区时的淡出仍在视口内，不计为离开。
 */
export function watchDemoVisibility(target: Element, onChange: (active: boolean) => void) {
  let ratio = 1,
    active = true;
  const update = () => {
    const next = demoVisible(ratio, document.hidden);
    if (next === active) return;
    active = next;
    onChange(next);
  };
  const observer = new IntersectionObserver(
    ([entry]) => {
      ratio = entry.intersectionRatio;
      update();
    },
    { threshold: [0, VISIBLE_RATIO, 0.5, 1] },
  );
  observer.observe(target);
  document.addEventListener("visibilitychange", update);
  return () => {
    observer.disconnect();
    document.removeEventListener("visibilitychange", update);
  };
}
