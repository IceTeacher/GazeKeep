import { expect, test } from "vitest";
import { createAutoCycle } from "../../../src/scripts/demo-cycle.ts";

/** 可控时钟：只在 advance 时按到期顺序执行计时任务。 */
function fakeClock() {
  let now = 0;
  let tasks: { at: number; fn: () => void }[] = [];
  return {
    schedule(fn: () => void, ms: number) {
      const task = { at: now + ms, fn };
      tasks.push(task);
      return () => void (tasks = tasks.filter((t) => t !== task));
    },
    advance(ms: number) {
      const end = now + ms;
      for (;;) {
        const next = tasks.filter((t) => t.at <= end).sort((a, b) => a.at - b.at)[0];
        if (!next) break;
        tasks = tasks.filter((t) => t !== next);
        now = next.at;
        next.fn();
      }
      now = end;
    },
    pending: () => tasks.length,
  };
}

function setup(count = 3) {
  const clock = fakeClock();
  const seen: number[] = [];
  const cycle = createAutoCycle({
    count,
    period: 2000,
    resumeDelay: 4000,
    schedule: clock.schedule,
    onChange: (i) => seen.push(i),
  });
  return { clock, seen, cycle };
}

test("每个周期切换一次并循环回到第一项", () => {
  const { clock, seen, cycle } = setup();
  cycle.start(0);
  // 开始时不重复通知当前项，由调用方负责初始状态。
  expect(seen).toEqual([]);
  clock.advance(1999);
  expect(seen).toEqual([]);
  clock.advance(1);
  expect(seen).toEqual([1]);
  clock.advance(4000);
  expect(seen).toEqual([1, 2, 0]);
  expect(cycle.index()).toBe(0);
});

test("默认 1.5 秒切换一次，访客松手 2.5 秒后继续", () => {
  const clock = fakeClock();
  const seen: number[] = [];
  const cycle = createAutoCycle({ count: 3, schedule: clock.schedule, onChange: (i) => seen.push(i) });
  cycle.start(0);
  clock.advance(1500);
  expect(seen).toEqual([1]);
  cycle.hold();
  cycle.release();
  clock.advance(2499);
  expect(seen).toEqual([1]);
  clock.advance(1);
  expect(seen).toEqual([1, 2]);
});

test("访客选中某项时暂停，离开后等待恢复延迟再从该项继续", () => {
  const { clock, seen, cycle } = setup();
  cycle.start(0);
  clock.advance(1000);
  cycle.hold(2);
  clock.advance(10_000);
  expect(seen).toEqual([]);
  expect(cycle.index()).toBe(2);
  cycle.release();
  clock.advance(3999);
  expect(seen).toEqual([]);
  clock.advance(1);
  expect(seen).toEqual([0]);
  clock.advance(2000);
  expect(seen).toEqual([0, 1]);
});

test("停止后不再切换，停止期间的释放也不会重新启动", () => {
  const { clock, seen, cycle } = setup(2);
  cycle.start(1);
  cycle.stop();
  cycle.hold();
  cycle.release();
  clock.advance(20_000);
  expect(seen).toEqual([]);
  expect(clock.pending()).toBe(0);
  // 重新开始时从指定项起步，并清除此前的暂停。
  cycle.start(1);
  clock.advance(2000);
  expect(seen).toEqual([0]);
});

test("重复开始只保留一个计时任务", () => {
  const { clock, seen, cycle } = setup(2);
  cycle.start(0);
  cycle.start(0);
  expect(clock.pending()).toBe(1);
  clock.advance(2000);
  expect(seen).toEqual([1]);
});

test("进入场景前访客已选中某项（指针停在卡片上滚入）时，开始后保持暂停直到释放", () => {
  const { clock, seen, cycle } = setup();
  cycle.hold(1);
  cycle.start(1);
  clock.advance(10_000);
  expect(seen).toEqual([]);
  cycle.release();
  clock.advance(4000);
  expect(seen).toEqual([2]);
});

test("离开视口时暂停，回来后重新计满一个周期再切换", () => {
  const { clock, seen, cycle } = setup();
  cycle.start(0);
  clock.advance(1500);
  cycle.pause();
  clock.advance(10_000);
  expect(seen).toEqual([]);
  cycle.resume();
  clock.advance(1999);
  expect(seen).toEqual([]);
  clock.advance(1);
  expect(seen).toEqual([1]);
});

test("离开视口与访客选中叠加时，两者都解除才继续", () => {
  const { clock, seen, cycle } = setup();
  cycle.start(0);
  cycle.hold(1);
  cycle.pause();
  cycle.release();
  clock.advance(10_000);
  expect(seen).toEqual([]);
  cycle.resume();
  clock.advance(2000);
  expect(seen).toEqual([2]);
  // 反过来：先回到视口，访客仍选中时保持暂停。
  cycle.pause();
  cycle.hold(0);
  cycle.resume();
  clock.advance(10_000);
  expect(seen).toEqual([2]);
  cycle.release();
  clock.advance(4000);
  expect(seen).toEqual([2, 1]);
});

test("离开视口期间换场景，开始后保持暂停；停止后回到视口不会重新启动", () => {
  const { clock, seen, cycle } = setup();
  cycle.pause();
  cycle.start(0);
  clock.advance(10_000);
  expect(seen).toEqual([]);
  cycle.stop();
  cycle.resume();
  clock.advance(10_000);
  expect(seen).toEqual([]);
  expect(clock.pending()).toBe(0);
});
