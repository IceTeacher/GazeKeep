import type { SiteContent } from "./schema";
export const zh = {
  locale: "zh",
  title: "GazeKeep · 目光所及，屏幕常明",
  description: "面向 Android 的端侧离线注视保亮工具。临近熄屏短时采样，注视时延后熄屏，看不清时正常熄屏。",
  nav: ["工作方式", "运行后端", "隐私", "常见问题"],
  download: "下载 APK",
  source: "查看源码",
  theme: "切换明暗主题",
  menu: "导航菜单",
  hero: {
    title: ["目光所及，", "屏幕常明。"],
    subtitle: "读到兴起，不必再伸手点亮屏幕。\n为缺少系统级注视保亮的 Android 设备，添一份恰到好处的默契。",
    pills: ["端侧离线", "零网络权限", "临近熄屏短时采样"],
    explore: "探索它如何工作",
  },
  story: {
    title: "只在需要时，\n回应你的目光。",
    intro: "让亮屏回归自然。一次短暂的检测，一个克制的决定。",
    steps: [
      {
        title: "快要熄屏，才看一眼。",
        text: "临近系统熄屏时，短暂唤醒前置相机进行检测。完成后立即释放相机，让日常阅读少一些打断。",
        route: "home",
        state: "sampling",
        effect: "camera-pulse",
      },
      {
        title: "你还在看，就继续亮着。",
        text: "检测到正视屏幕后延后熄屏。阅读长文、查阅文档、照着菜谱下厨，把注意力留给眼前。",
        route: "privacy",
        state: "gaze_detected",
        effect: "screen-glow",
      },
      {
        title: "这次没看清，就自然熄屏。",
        text: "暗光、侧脸、多张脸或相机不可用时，让系统按原有超时熄屏。每次判断，都以正常熄屏为起点。",
        route: "privacy",
        state: "dim_fallback",
        effect: "screen-dim",
      },
    ],
  },
  backends: {
    title: "你的设备，\n有自己的运行方式。",
    intro: "三种后端，一套注视体验。根据设备环境选择，自动模式优先使用已就绪的后端。",
    items: [
      {
        id: "lsposed",
        name: "LSPosed",
        tag: "系统集成",
        text: "通过系统框架 Hook 接入熄屏时序，适合已有 LSPosed 环境的设备。",
        requirement: "需要 Root、LSPosed 与系统框架作用域",
      },
      {
        id: "shizuku",
        name: "Shizuku",
        tag: "免 Root",
        text: "借助 Shizuku 的授权服务工作，适合愿意配置无线调试的用户。",
        requirement: "需要启动 Shizuku 并授予权限",
      },
      {
        id: "accessibility",
        name: "无障碍",
        tag: "轻松启用",
        text: "在系统设置中开启无障碍服务，为普通设备提供另一种接入方式。",
        requirement: "需要手动开启无障碍服务",
      },
    ],
  },
  whitelist: {
    title: "让专注，\n有自己的范围。",
    text: "在阅读器、浏览器和文档里启用。只有确认当前应用在白名单内，才进行保亮。安装后白名单默认开启且为空；手机演示使用示例应用。",
    categories: ["阅读", "浏览器", "文档", "代码", "漫画"],
    limit: "30 分钟",
    detail: "默认单次保护上限 · 可自定义或设为无限制",
  },
  privacy: {
    title: "目光留下。\n画面不留。",
    text: "端侧完成判断，应用不申请网络权限。隐私承诺写在产品里，也公开在源码里。",
    labels: ["按需短暂采样", "画面仅在内存", "看不清就熄屏"],
    permission: "应用合并权限清单 · 网络权限已移除",
  },
  start: {
    title: "从下一次阅读开始。",
    steps: [
      { title: "下载安装", text: "前往 GitHub Releases，下载 APK 并安装。" },
      { title: "选择后端并自检", text: "配置可用后端，授予相机权限，完成正视与转头两步自检。" },
      { title: "选好范围，开启保亮", text: "勾选白名单应用，设置保护上限，再开启总开关。" },
    ],
  },
  faq: {
    title: "你可能还想知道。",
    items: [
      {
        question: "会一直开着摄像头吗？",
        answer: "只在临近熄屏的检测窗口短暂使用前置相机，完成后立即关闭。耗电表现会随设备、后端和使用场景变化。",
      },
      {
        question: "光线很暗，或戴眼镜时会怎样？",
        answer: "检测结果受光线、角度和遮挡影响。看不清时正常熄屏，建议在自己的日常环境完成自检。",
      },
      {
        question: "三个后端怎么选择？",
        answer:
          "自动模式按 LSPosed、Shizuku、无障碍的顺序选择就绪后端，也可锁定某个后端。具体体验取决于系统和授权状态。",
      },
      {
        question: "需要怎样设置后台运行？",
        answer:
          "按照设备系统的后台限制，为 GazeKeep 和所选后端保留必要的运行条件。遇到异常，可先检查主页的后端状态，再运行相机自检。",
      },
      {
        question: "为什么启用后，某些应用里仍然会熄屏？",
        answer:
          "默认只对白名单内的应用生效。确认已勾选应用、完成自检且后端就绪；看不清或达到保护上限时，也会让屏幕正常熄灭。",
      },
    ],
  },
  footer: {
    title: "把注意力，\n留给你在看的世界。",
    text: "开源的 Android 注视保亮工具。由你掌控，也欢迎你参与。",
    docs: "使用文档",
    license: "开源协议",
  },
  demo: "真实 miuix 界面 · 演示数据",
  loading: "正在准备实时界面",
  error: "实时界面加载失败，页面内容仍可浏览。",
  retry: "重新加载",
  status: {
    idle: "准备就绪",
    sampling: "临近熄屏 · 短时采样",
    gaze_detected: "注视了屏幕 · 延后熄屏",
    dim_fallback: "这次没看清 · 正常熄屏",
    backend_lsposed: "LSPosed · 演示已就绪",
    backend_shizuku: "Shizuku · 演示已就绪",
    backend_accessibility: "无障碍 · 演示已就绪",
  },
} satisfies SiteContent;
