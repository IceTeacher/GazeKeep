import type { SiteContent } from "./schema";
export const zh = {
  locale: "zh",
  title: "GazeKeep - 注视保亮",
  description: "面向 Android 的端侧离线注视保亮工具。临近熄屏短时采样，注视时延后熄屏，看不清时正常熄屏。",
  ogImageAlt: "GazeKeep 官网首屏：目光所及，屏幕常明，右侧为显示 GazeKeep 主页的手机",
  nav: ["工作方式", "运行后端", "隐私", "常见问题"],
  download: "下载 APK",
  source: "查看源码",
  theme: { label: "切换主题", system: "跟随系统", light: "浅色", dark: "深色" },
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
    text: "在阅读器、浏览器和文档里启用。默认仅对白名单应用生效，安装后名单为空；也可改为排除黑名单应用，只跳过视频、相机、导航等应用。无法确认当前应用时不保亮；手机演示会展示列表勾选、搜索与黑白名单切换。",
    scopeLabel: "生效范围演示模式",
    modes: [
      { id: "whitelist", label: "仅白名单应用", summary: "默认只在勾选的阅读、文档类应用中保亮" },
      { id: "blacklist", label: "排除黑名单应用", summary: "全局开启保亮，跳过黑名单里的视频与导航应用" },
    ],
  },
  settings: {
    title: "每一处细节，\n都合你的习惯。",
    text: "检测提前量、灵敏度与单次最长保亮都可以随时调整；使用 LSPosed 后端时，还能隐藏采样时的相机隐私指示器。手机演示依次修改这些设置。",
    items: [
      { id: "lead", label: "检测提前量", detail: "在系统降亮前留出检测时间 · 5 秒、8 秒或自定义" },
      { id: "sensitivity", label: "灵敏度", detail: "标准需正脸并睁眼 · 宽松有正脸即可" },
      { id: "max_hold", label: "单次最长保亮", detail: "到达上限后按系统超时熄屏一次 · 可自定义或设为无限制" },
      { id: "hide_indicator", label: "隐藏相机隐私指示器", detail: "实验性 · 仅 LSPosed 后端生效，默认关闭" },
    ],
    values: { seconds: "{n} 秒", minutes: "{n} 分钟", standard: "标准", loose: "宽松", on: "开启", off: "关闭" },
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
      { title: "选好范围，开启保亮", text: "选择生效范围并勾选应用，设置保护上限，再开启总开关。" },
    ],
  },
  faq: {
    title: "你可能还想知道。",
    items: [
      {
        question: "会一直开着摄像头吗？耗电高吗？右上角的绿点能关掉吗？",
        answer:
          "不会常驻开启。平时只靠后端监听熄屏倒计时，临近降亮或熄屏且门控（亮屏、未锁屏、在生效范围、非通话与相机未占用）全部通过后，才会用约 1 秒读取接近/光照/加速度传感器，确认未放入口袋或倒扣再短暂唤醒前摄采样 1～3 帧（实测整轮约 0.6～0.9 秒），拿到结论立即关闭。\n\n连续两次确认注视后，后续会自动缩短提前量并减少采样帧数；「省电模式暂停」与「低电量暂停阈值」（默认低于 15% 且未充电）也会默认帮你省电。\n\nAndroid 12 及以上在采样瞬间状态栏右上角会出现系统相机隐私指示器（绿点），这是系统安全机制，说明只在那一刻调用了相机。如果你已在使用 LSPosed 后端，可在「设置 → 隐藏相机隐私指示器」（实验性）中仅针对 GazeKeep 自身隐藏绿点，不影响其他应用。",
      },
      {
        question: "戴眼镜、逆光或光线很暗时识别不准怎么办？",
        answer:
          "「标准」灵敏度同时要求正脸与双眼睁开；如果你佩戴反光较强的眼镜、镜框遮挡或睁眼判定不稳，请进入「设置 → 灵敏度」切换为「宽松：有正脸即可」，并到「设置 → 相机自检」在常用光线下重新通过两步测试。\n\n为了避免暗处瞎亮屏或反复唤醒相机，画面平均亮度过低时会直接放行熄屏；当连续两次因环境过暗未看清且环境光极低时，GazeKeep 会暂停打开相机，直到环境变亮后再自动恢复。",
      },
      {
        question: "LSPosed、Shizuku 和无障碍三种后端怎么选？各自有什么限制？",
        answer:
          "默认「自动选择」按 LSPosed → Shizuku → 无障碍 的顺序使用首个就绪后端；手动锁定某个后端时，失效后只会发通知提醒，不会静默切换：\n• LSPosed（需要 Root）：直接接入系统电源超时与前台任务，时序最准、开销最低，且支持隐藏相机绿点。请在 LSPosed 管理器勾选「系统框架」作用域并重启系统；每次更新 GazeKeep 后也需重启生效（不支持 API 100）。\n• Shizuku（免 Root）：通过无线调试或电脑 ADB 启动 Shizuku 并授权即可。前台识别准确、稳定性高于无障碍，适合未解锁 Bootloader 的设备；手机重启后需重新启动 Shizuku 服务。\n• 无障碍（零门槛）：直接在系统无障碍设置开启，适合备用机或平板。由于无障碍窗口事件的系统限制，从桌面应用跳入少数无桌面入口的系统全屏页时前台包名可能沿用上一应用。",
      },
      {
        question: "Android 13 及以上开启「无障碍」时开关是灰色的怎么办？",
        answer:
          "Android 13 起，系统会默认限制非应用商店安装的 APK 开启无障碍服务。\n\n请在 GazeKeep「设置 → 当前后端」页点击「打开应用信息（受限设置）」（或到系统「设置 → 应用管理」找到 GazeKeep），点击右上角「⋮」菜单选择「允许受限制的设置」，再返回系统无障碍设置即可正常开启。",
      },
      {
        question: "后台怎么设置才不会被杀？小米 / HyperOS 用过系统相机后就失效怎么办？",
        answer:
          "• 小米 / HyperOS（重要）：系统默认的「智能」省电策略会在你打开一次系统相机后冻结后台的 GazeKeep，且退出相机后不会解冻——仅设置“忽略电池优化”无法解决。请进入 GazeKeep「设置 → 后台运行」，把省电策略改为「无限制」；修改时系统会强制停止一次应用，改完重新打开一次 GazeKeep 即可恢复。\n• 其他机型：在多任务界面锁定 GazeKeep，并在系统设置里允许自启动与无限制后台运行；Android 13 及以上建议授予通知权限，后端掉线或权限被收回时才能及时收到提醒。\n• 快捷暂停与恢复：可在下拉控制中心添加「注视保亮」快捷磁贴一键启停（Android 14 及以上从磁贴恢复时，为满足系统前台相机权限要求会短暂拉起应用）。",
      },
      {
        question: "明明打开了总开关，为什么有些应用里还是会熄屏？怎么排查？",
        answer:
          "请按以下顺序定位原因：\n1. 先看首页状态卡：首页会直接显示「最近检测」或「最近跳过」的具体原因（如不在白名单、省电模式已开启、电量低于阈值、环境过暗等）。\n2. 核对生效范围与名单：默认是「仅白名单应用」且名单为空；应用列表默认隐藏预装系统应用，如需勾选系统阅读器或浏览器，请在列表右上角点击「更多选项 → 显示系统应用」。相机类应用在任何模式下都会自动避让。\n3. 检查省电门控：默认开启了「省电模式暂停」与「低电量暂停阈值（低于 15% 且未充电）」，可在「设置」中按需调整。\n4. 检查提前量与保亮上限：如果手机降亮阶段很短或前摄启动较慢，建议将「检测提前量」从 8 秒适当调大（如 10～15 秒）；连续保亮达到「单次最长保亮」（默认 30 分钟）后也会按设计强制熄屏一次，触摸屏幕后自动重置。\n5. 查看本地日志：若仍不确定，开启「设置 → 详细日志」后再现一次，即可查看每轮判定与跳过原因（仅保留最近 24 小时，关闭后立即清除）。",
      },
      {
        question: "GitHub Releases 里有好几个 APK，应该下载哪一个？升级会丢失设置吗？",
        answer:
          "• arm64-v8a（推荐）：绝大多数现代 Android 手机与平板。\n• armeabi-v7a：早期老旧 32 位 Android 设备。\n• x86_64：PC Android 模拟器。\n• universal：全架构通用包，不确定设备架构时选它。\n\n正式版各架构包共用同一签名与版本号，直接安装即可覆盖升级并保留全部设置和黑白名单。如果你此前安装过本地构建的 Debug 包或不同签名版本，需先卸载再安装正式版；使用 LSPosed 后端时，升级 APK 后请记得重启系统使新钩子生效。",
      },
    ],
  },
  footer: {
    title: "把注意力，\n留给你在看的世界。",
    text: "开源的 Android 注视保亮工具。由你掌控，也欢迎你参与。",
    docs: "使用文档",
    license: "开源协议",
    openSource: "端侧离线 · MIT 开源协议",
    navTitle: "页面导航",
    linksTitle: "项目与版本",
  },
  changelog: {
    nav: "更新日志",
    title: "更新日志 - GazeKeep",
    description: "查看 GazeKeep 各版本功能新增、体验优化、问题修复与启用步骤。",
    kicker: "版本演进",
    heading: "更新日志",
    intro: "记录 GazeKeep 每次迭代的功能新增、性能调优、问题修复与启用指南。",
    backHome: "返回首页",
    githubReleases: "前往 GitHub Releases",
    releaseTagLabel: "在 GitHub 查看此版本",
    latestBadge: "最新",
    setupTitle: "查看启用步骤与参数建议",
    emptyNotice: "当前暂无可展示的更新日志。",
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
    scope_whitelist: "仅白名单应用 · 演示中",
    scope_blacklist: "排除黑名单应用 · 演示中",
  },
  hint: {
    touch: "轻点可操作 · 长按片刻后上下滑动可滚动",
    mouse: "点击可操作 · 按住鼠标上下拖动可滚动",
  },
  tour: "注视不熄屏",
  ui: {
    skip: "跳至正文",
    productNote: "为 Android 而生的注视默契",
    kickers: {
      workflow: "恰到好处的工作方式",
      backends: "三种连接，一份默契",
      whitelist: "你的范围，你的节奏",
      settings: "细节，由你调校",
      privacy: "隐私，始于克制。",
    },
    tagline: "目光所及，屏幕常明。",
    switchLanguage: "Switch to English",
    languageShort: "EN",
    languageName: "English",
  },
} satisfies SiteContent;
