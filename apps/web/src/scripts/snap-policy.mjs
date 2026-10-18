/** @param {number[]} anchors @param {number} pageHeight @param {number} maxScroll */
export function buildSnapStops(anchors, pageHeight, maxScroll) {
  const end = Math.max(0, maxScroll);
  const points = [...new Set([0, ...anchors, end].map((value) => Math.max(0, Math.min(end, Math.round(value)))))].sort(
    (a, b) => a - b,
  );
  const stops = [0];
  for (const point of points.slice(1)) {
    while (point - stops.at(-1) > Math.max(1, pageHeight) + 1) stops.push(stops.at(-1) + Math.max(1, pageHeight));
    if (point - stops.at(-1) > 2 && (point === end || point - stops.at(-1) >= pageHeight * 0.35)) stops.push(point);
  }
  return stops;
}
/** @param {number[]} stops @param {number} current @param {number} direction */
export function nextSnapStop(stops, current, direction) {
  return direction > 0
    ? (stops.find((value) => value > current + 3) ?? stops.at(-1) ?? 0)
    : ([...stops].reverse().find((value) => value < current - 3) ?? 0);
}
export function createWheelGesture() {
  let last = -Infinity,
    sum = 0,
    consumed = false,
    blockedUntil = 0;
  return {
    /** @param {number} delta @param {number} now @param {boolean} moving */
    push(delta, now, moving) {
      if (now - last > 350) {
        sum = 0;
        consumed = false;
      }
      last = now;
      if (moving || now < blockedUntil) {
        consumed = true;
        return 0;
      }
      if (consumed) return 0;
      if (sum && Math.sign(sum) !== Math.sign(delta)) sum = 0;
      sum += delta;
      if (Math.abs(sum) < 36) return 0;
      consumed = true;
      blockedUntil = now + 950;
      return Math.sign(sum);
    },
  };
}

/** @param {number} current @param {number} direction @param {number} end */
export function shouldSnap(current, direction, end) {
  return current < end - 2 || (direction < 0 && current <= end + 2);
}
