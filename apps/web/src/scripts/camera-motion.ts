/** 连续相位与滚动速度独立于演示阶段，重入不重置。 */
export const initialHalo = () => ({ angle: 0, speed: 0, arc: 70, opacity: 0 });
export type Halo = ReturnType<typeof initialHalo>;
export function haloFrame(
  previous: Halo,
  { dt, delta, active, reduced = false }: { dt: number; delta: number; active: boolean; reduced?: boolean },
): Halo {
  if (reduced) return { ...previous, angle: 0, speed: 0, arc: 120, opacity: active ? 1 : 0 };
  const elapsed = Math.max(0, Math.min(64, dt));
  const velocity = elapsed ? Math.min(3000, (Math.abs(delta) * 1000) / elapsed) : 0;
  const speed = previous.speed + (velocity - previous.speed) * (1 - Math.exp(-elapsed / 120));
  const opacity = Math.max(0, Math.min(1, previous.opacity + ((active ? 1 : -1) * elapsed) / 150));
  return {
    angle: previous.angle + (active || previous.opacity > 0 ? (240 * elapsed) / 1000 : 0),
    speed,
    arc: 70 + 190 * Math.min(1, speed / 1500),
    opacity,
  };
}
