import type { SiteContent } from "./schema";
export const en = {
  locale: "en",
  title: "GazeKeep - Stay in the moment",
  description:
    "Offline gaze-based screen keeping for Android. Brief checks near timeout keep your screen awake while you look. Uncertain results let it sleep.",
  ogImageAlt: "GazeKeep website hero: Stay in the moment, with a phone showing the GazeKeep home screen",
  nav: ["How it works", "Backends", "Privacy", "FAQ"],
  download: "Download APK",
  source: "View source",
  theme: { label: "Change theme", system: "System", light: "Light", dark: "Dark" },
  menu: "Navigation menu",
  hero: {
    title: ["Stay in the moment.", "Keep the light."],
    subtitle:
      "Keep reading, without another tap.\nA little understanding for Android devices without built-in gaze detection.",
    pills: ["On-device processing", "No internet permission", "Brief checks near timeout"],
    explore: "See how it works",
  },
  story: {
    title: "A little attention.\nOnly when needed.",
    intro: "A brief check. A considered decision. One less interruption.",
    steps: [
      {
        title: "Almost asleep? A quick check.",
        text: "The front camera wakes briefly near screen timeout, then closes as soon as the check finishes. Your reading keeps its rhythm.",
        route: "home",
        state: "sampling",
        effect: "camera-pulse",
      },
      {
        title: "Still looking? Keep reading.",
        text: "When you are facing the screen, GazeKeep delays sleep. Stay with the article, document or recipe in front of you.",
        route: "privacy",
        state: "gaze_detected",
        effect: "screen-glow",
      },
      {
        title: "Uncertain? Let it rest.",
        text: "Low light, a turned head, multiple faces or an unavailable camera let the system follow its normal timeout.",
        route: "privacy",
        state: "dim_fallback",
        effect: "screen-dim",
      },
    ],
  },
  backends: {
    title: "Your device.\nYour way to connect.",
    intro: "Three backends, one gaze experience. Automatic mode selects a ready backend for your environment.",
    items: [
      {
        id: "lsposed",
        name: "LSPosed",
        tag: "System integration",
        text: "Hooks into the system screen-timeout flow. Built for devices with an existing LSPosed setup.",
        requirement: "Root, LSPosed and system framework scope required",
      },
      {
        id: "shizuku",
        name: "Shizuku",
        tag: "No root needed",
        text: "Works through the authorized Shizuku service. For users comfortable with wireless debugging.",
        requirement: "Start Shizuku and grant permission",
      },
      {
        id: "accessibility",
        name: "Accessibility",
        tag: "Simple setup",
        text: "Enable the accessibility service in system settings for another way to use GazeKeep.",
        requirement: "Manually enable the accessibility service",
      },
    ],
  },
  whitelist: {
    title: "Give your focus\na place to stay.",
    text: "Choose your reader, browser and document apps. GazeKeep acts only when the foreground app is confirmed in your list. Your list starts empty after installation; the phone demo lists a few open-source apps as examples.",
    limit: "30 minutes",
    detail: "Default protection limit · Customizable, including unlimited",
  },
  privacy: {
    title: "Keep the moment.\nNever the picture.",
    text: "Processing stays on your device. The app has no internet permission. Read the commitments, then explore the source.",
    labels: ["Brief checks", "Frames stay in memory", "Sleep when uncertain"],
    permission: "Merged app permissions · Internet permission removed",
  },
  start: {
    title: "Start with your next read.",
    steps: [
      { title: "Download and install", text: "Get the APK from GitHub Releases and install it." },
      {
        title: "Connect and check",
        text: "Set up a backend, allow the camera, then complete both self-test steps: looking and looking away.",
      },
      { title: "Choose apps and enable", text: "Select your apps, set a protection limit and turn on GazeKeep." },
    ],
  },
  faq: {
    title: "A few things to know.",
    items: [
      {
        question: "Is the camera always on?",
        answer:
          "The front camera opens briefly near screen timeout and closes after checking. Battery use varies with the device, backend and usage.",
      },
      {
        question: "What about low light or glasses?",
        answer:
          "Light, angles and occlusion affect detection. Uncertain results let the screen sleep. Run the self-test in your usual environment.",
      },
      {
        question: "Which backend should I choose?",
        answer:
          "Automatic mode tries ready backends in this order: LSPosed, Shizuku, Accessibility. You can also lock a backend. Behavior depends on your system and permissions.",
      },
      {
        question: "What about background restrictions?",
        answer:
          "Allow the background operation required by GazeKeep and your selected backend. If something stops working, check backend readiness on the home page and run the camera self-test.",
      },
      {
        question: "Why does the screen still sleep in some apps?",
        answer:
          "Only selected apps are enabled by default. Check your app list, self-test and backend status. Uncertain detection or reaching the protection limit will also let the screen sleep.",
      },
    ],
  },
  footer: {
    title: "More attention\nfor your world.",
    text: "An open-source Android utility. Yours to control, explore and improve.",
    docs: "User guide",
    license: "License",
  },
  demo: "Real miuix interface · Demo data",
  loading: "Preparing the live interface",
  error: "The live interface could not load. You can keep exploring the page.",
  retry: "Try again",
  status: {
    idle: "Ready",
    sampling: "Near timeout · Brief check",
    gaze_detected: "Looking · Sleep delayed",
    dim_fallback: "Uncertain · Normal sleep",
    backend_lsposed: "LSPosed · Demo ready",
    backend_shizuku: "Shizuku · Demo ready",
    backend_accessibility: "Accessibility · Demo ready",
  },
  ui: {
    skip: "Skip to content",
    productNote: "A little understanding. For Android.",
    kickers: {
      workflow: "Considered by design",
      backends: "Three ways to connect",
      whitelist: "Your focus, your rules",
      privacy: "Privacy starts with restraint.",
    },
    limitLabel: "Protection limit",
    limitLink: "View settings",
    tagline: "Stay in the moment.",
    switchLanguage: "切换至中文",
    languageShort: "中文",
    languageName: "中文",
  },
} satisfies SiteContent;
