type Schedule = (fn: () => void, ms: number) => () => void;

/**
 * 按固定周期在 count 项之间循环：第一幕在检测中与检测成功间往复，运行方式在三种后端间轮换。
 * 访客选中某项时暂停，释放后等待 resumeDelay 再从该项继续；手机离开视口时另行暂停，回来后重新计满一个周期。
 * 两种暂停互不解除，只保留一个计时任务。
 */
export function createAutoCycle({
  count,
  period = 1500,
  resumeDelay = 2500,
  schedule = (fn, ms) => {
    const id = setTimeout(fn, ms);
    return () => clearTimeout(id);
  },
  onChange,
}: {
  count: number;
  period?: number;
  resumeDelay?: number;
  schedule?: Schedule;
  onChange: (index: number) => void;
}) {
  let index = 0,
    running = false,
    held = false,
    paused = false,
    cancel: (() => void) | undefined;
  const clear = () => {
    cancel?.();
    cancel = undefined;
  };
  const arm = (ms: number) => {
    clear();
    cancel = schedule(tick, ms);
  };
  function tick() {
    cancel = undefined;
    if (!running || held || paused) return;
    index = (index + 1) % count;
    onChange(index);
    arm(period);
  }
  return {
    /** 从指定项开始计时；不通知当前项，初始状态由调用方设置。开始前已暂停时保持暂停。 */
    start(from = 0) {
      running = true;
      index = from;
      if (held || paused) clear();
      else arm(period);
    },
    stop() {
      running = false;
      held = false;
      clear();
    },
    /** 访客选中时暂停；传入选中项后，恢复时从该项继续。 */
    hold(at?: number) {
      held = true;
      if (at !== undefined) index = at;
      clear();
    },
    release() {
      held = false;
      if (running && !paused) arm(resumeDelay);
    },
    /** 手机离开视口或标签页隐藏时暂停；不影响访客选中的暂停。 */
    pause() {
      paused = true;
      clear();
    },
    resume() {
      if (!paused) return;
      paused = false;
      if (running && !held) arm(period);
    },
    index: () => index,
  };
}
