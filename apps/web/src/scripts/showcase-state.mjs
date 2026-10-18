/** @param {boolean} supportsWasm */
export const chooseRuntime = (supportsWasm) => (supportsWasm ? "wasm" : "js");
export function createShowcaseAttempts() {
  let current = 0;
  let failed = false;
  return {
    begin() {
      current += 1;
      failed = false;
      return current;
    },
    ready(attempt) {
      return attempt === current && !failed;
    },
    fail(attempt) {
      if (attempt !== current || failed) return false;
      failed = true;
      return true;
    },
  };
}
export function createShowcaseState() {
  let value = { route: "home", status: "idle", locale: "zh", dark: true, phase: "loading" };
  let bridge;
  const replay = () => {
    if (!bridge) return;
    bridge.setLocaleAndTheme(value.locale, value.dark);
    bridge.setScreen(value.route);
    bridge.setDemoStatus(value.status);
  };
  return {
    update(patch) {
      value = { ...value, ...patch };
      replay();
    },
    attach(next) {
      bridge = next;
      value.phase = "ready";
      replay();
    },
    fail() {
      bridge = undefined;
      value.phase = "error";
    },
    loading() {
      bridge = undefined;
      value.phase = "loading";
    },
    snapshot() {
      return { ...value };
    },
  };
}
