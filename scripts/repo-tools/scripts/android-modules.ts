export const ANDROID_MODULE_DEPS: Record<string, readonly string[]> = {
  android: ["shared", "engine", "backend:a11y", "backend:shizuku", "backend:lsposed", "xposed"],
  // 自插桩实机套件只通过 targetProjectPath 指向 :android，不引入任何模块依赖。
  e2e: [],
  shared: ["core"],
  core: [],
  engine: ["core"],
  "backend:a11y": ["core"],
  "backend:shizuku": ["core"],
  "backend:lsposed": ["core", "bridge:protocol"],
  xposed: ["bridge:protocol", "hidden-api-stubs"],
  "hidden-api-stubs": [],
  "bridge:protocol": [],
};

export const MODULE_TEST_TASKS: Record<string, readonly string[]> = {
  core: [":core:testAndroidHostTest"],
  engine: [":engine:testDebugUnitTest"],
  "backend:a11y": [":backend:a11y:testDebugUnitTest"],
  "backend:shizuku": [":backend:shizuku:testDebugUnitTest"],
  "backend:lsposed": [":backend:lsposed:testDebugUnitTest"],
  xposed: [":xposed:testDebugUnitTest"],
  android: [":android:testDebugUnitTest"],
  shared: [":shared:testAndroidHostTest"],
  "bridge:protocol": [],
  "hidden-api-stubs": [],
  e2e: [],
};

export const CORE_WEB_TEST_TASKS: readonly string[] = [":core:jsBrowserTest", ":core:wasmJsBrowserTest"];

export const ANDROID_HOST_TEST_TASKS: readonly string[] = [
  ":core:testAndroidHostTest",
  ":engine:testDebugUnitTest",
  ":backend:a11y:testDebugUnitTest",
  ":backend:shizuku:testDebugUnitTest",
  ":backend:lsposed:testDebugUnitTest",
  ":xposed:testDebugUnitTest",
  ":android:testDebugUnitTest",
  ":shared:testAndroidHostTest",
];

export const ANDROID_FULL_VERIFY_TASKS: readonly string[] = [
  ":android:assembleDebug",
  ":android:lintDebug",
  ":e2e:assembleDebug",
  ...ANDROID_HOST_TEST_TASKS,
];

const MODULE_DIRS: readonly [prefix: string, module: string][] = [
  ["backend/a11y/", "backend:a11y"],
  ["backend/shizuku/", "backend:shizuku"],
  ["backend/lsposed/", "backend:lsposed"],
  ["bridge/protocol/", "bridge:protocol"],
  ["android/", "android"],
  ["e2e/", "e2e"],
  ["shared/", "shared"],
  ["core/", "core"],
  ["engine/", "engine"],
  ["xposed/", "xposed"],
  ["hidden-api-stubs/", "hidden-api-stubs"],
];

const FULL_ANDROID_TOOL_FILES = new Set([
  "scripts/repo-tools/scripts/android-task.ts",
  "scripts/repo-tools/scripts/android-modules.ts",
]);

/** 沿着模块依赖图反向求传递闭包，得到直接改动所影响的全部模块。 */
export function affectedAndroidModules(directModules: Iterable<string>): Set<string> {
  const reverse = new Map<string, string[]>();
  for (const [module, deps] of Object.entries(ANDROID_MODULE_DEPS)) {
    for (const dep of deps) {
      const dependents = reverse.get(dep) ?? [];
      dependents.push(module);
      reverse.set(dep, dependents);
    }
  }
  const visited = new Set<string>();
  const queue = [...directModules].filter((m) => Object.hasOwn(ANDROID_MODULE_DEPS, m));
  while (queue.length) {
    const current = queue.shift()!;
    if (visited.has(current)) continue;
    visited.add(current);
    for (const dependent of reverse.get(current) ?? []) {
      if (!visited.has(dependent)) queue.push(dependent);
    }
  }
  return visited;
}

function classifyAppFile(path: string): string | "full" | "ignore" | undefined {
  if (FULL_ANDROID_TOOL_FILES.has(path)) return "full";
  if (!path.startsWith("apps/app/")) return undefined;
  const rest = path.slice("apps/app/".length);
  if (rest.startsWith("distribution/")) return "ignore";
  if (!rest.includes("/")) return rest.endsWith(".md") ? "ignore" : "full";
  if (rest.startsWith("gradle/") || rest.startsWith("kotlin-js-store/")) return "full";
  for (const [prefix, module] of MODULE_DIRS) {
    if (rest.startsWith(prefix)) return module;
  }
  return "full";
}

/** 按改动文件与模块依赖图裁剪所需的 Gradle 验证任务，未知范围保守回退全量任务。 */
export function selectAndroidVerifyTasks(changedFiles: readonly string[] | null, full: boolean): string[] {
  if (full || changedFiles === null) return [...ANDROID_FULL_VERIFY_TASKS];
  const direct = new Set<string>();
  for (const file of changedFiles) {
    const target = classifyAppFile(file);
    if (target === "full") return [...ANDROID_FULL_VERIFY_TASKS];
    if (target && target !== "ignore") direct.add(target);
  }
  if (direct.size === 0) return [];
  if (direct.size === 1 && direct.has("e2e")) return [":e2e:assembleDebug"];

  const closure = affectedAndroidModules(direct);
  const selected = new Set<string>();
  if (closure.has("android")) {
    selected.add(":android:assembleDebug");
    selected.add(":android:lintDebug");
  }
  // e2e 通过 targetProjectPath 指向 :android 且直接引用 :shared 字符串资源，相关改动须编过实机套件。
  if (direct.has("e2e") || direct.has("android") || closure.has("shared")) {
    selected.add(":e2e:assembleDebug");
  }
  for (const module of Object.keys(MODULE_TEST_TASKS)) {
    if (!closure.has(module)) continue;
    for (const task of MODULE_TEST_TASKS[module]) selected.add(task);
  }
  return ANDROID_FULL_VERIFY_TASKS.filter((task) => selected.has(task));
}
