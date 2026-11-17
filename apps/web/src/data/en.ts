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
    text: "Choose your reader, browser and document apps. By default GazeKeep acts only in apps on your list, which starts empty after installation; or exclude apps such as video, camera and navigation instead. If the foreground app cannot be confirmed, the screen sleeps. The phone demo switches between whitelist and blacklist while toggling and searching apps.",
    scopeLabel: "App scope demo mode",
    modes: [
      {
        id: "whitelist",
        label: "Whitelist only",
        summary: "Keep the screen awake only in selected reading and document apps",
      },
      {
        id: "blacklist",
        label: "Blacklist",
        summary: "Keep awake globally while excluding video, camera and navigation apps",
      },
    ],
  },
  settings: {
    title: "Every detail,\nthe way you like it.",
    text: "Adjust the detection lead time, sensitivity and maximum keep-awake time at any time. With the LSPosed backend, you can also hide the camera privacy indicator while sampling. The phone demo changes these settings one by one.",
    items: [
      { id: "lead", label: "Detection lead time", detail: "Time to detect before dimming · 5 s, 8 s or custom" },
      { id: "sensitivity", label: "Sensitivity", detail: "Standard: face forward, eyes open · Relaxed: face forward" },
      {
        id: "max_hold",
        label: "Maximum keep-awake time",
        detail: "Sleeps once on the system timeout at the limit · Customizable, including unlimited",
      },
      {
        id: "hide_indicator",
        label: "Hide camera privacy indicator",
        detail: "Experimental · LSPosed backend only, off by default",
      },
    ],
    values: { seconds: "{n} s", minutes: "{n} min", standard: "Standard", loose: "Relaxed", on: "On", off: "Off" },
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
      {
        title: "Choose apps and enable",
        text: "Choose an app scope and select apps, set a protection limit, then turn on GazeKeep.",
      },
    ],
  },
  faq: {
    title: "A few things to know.",
    items: [
      {
        question: "Is the camera always on? Does it drain battery, and can I hide the green dot?",
        answer:
          'No. GazeKeep stays idle until near dim or screen timeout. Only after non-camera checks pass (screen unlocked, app in scope, not on a call, camera free) does it briefly read proximity, light and accelerometer sensors (~1 s), then open the front camera for 1–3 frames (~0.6–0.9 s total) and close it immediately.\n\nAfter two consecutive "Looking" checks, GazeKeep shortens the lead time and samples fewer frames. "Pause on Battery Saver" and "Low battery threshold" (15% or below while unplugged) are also enabled by default.\n\nThe green camera dot in the status bar on Android 12+ is a system privacy indicator confirming the camera only opens at that moment. If you use the LSPosed backend, you can enable the experimental "Hide camera privacy indicator" option in Settings to suppress the dot for GazeKeep alone.',
      },
      {
        question: "What if detection is inconsistent with glasses, backlighting or low light?",
        answer:
          'Standard sensitivity requires both facing forward and eyes open. If reflective lenses or frames make eye-open detection unreliable, go to Settings → Sensitivity and switch to "Relaxed: facing forward", then re-run Settings → Camera self-test in your usual lighting.\n\nTo avoid keeping the screen on blindly or waking the camera repeatedly in the dark, very dark frames let the screen sleep. After two consecutive dark checks in very low ambient light, GazeKeep pauses opening the camera until lighting improves.',
      },
      {
        question: "How do I choose between LSPosed, Shizuku and Accessibility?",
        answer:
          "Automatic mode picks the first ready backend in this order: LSPosed → Shizuku → Accessibility. Locking a specific backend disables silent fallback and sends an alert if it disconnects:\n• LSPosed (Root): Hooks system timeout timing directly with the lowest overhead and supports hiding the camera green dot. Enable the System Framework scope and reboot—also required after updating GazeKeep (API 100 is unsupported).\n• Shizuku (No root): Start Shizuku via wireless debugging or ADB and authorize GazeKeep. More stable than Accessibility with accurate foreground detection, though Shizuku must be restarted after a phone reboot.\n• Accessibility (Zero setup): Enable in system settings—ideal for spare phones or tablets. Due to system window-event limits, full-screen pages without a launcher entry may keep the previous app's package name; use Shizuku or LSPosed if strict app matching matters.",
      },
      {
        question: "Why is the Accessibility toggle grayed out on Android 13 or later?",
        answer:
          'Starting with Android 13, Android restricts Accessibility access for sideloaded APKs by default.\n\nIn GazeKeep, open Settings → Backend and tap "Open app info (Restricted settings)" (or open GazeKeep in System Settings → Apps), tap the top-right "⋮" menu, select "Allow restricted settings", then return to Accessibility settings to turn it on.',
      },
      {
        question:
          "How do I keep it running in the background? Why does it stop on Xiaomi / HyperOS after using the Camera app?",
        answer:
          '• Xiaomi / HyperOS (Important): The default "Battery saver" policy freezes GazeKeep in the background once you open the system Camera app and never unfreezes it—ignoring battery optimization alone is not enough. Open GazeKeep → Settings → Background activity and set it to "No restrictions". Changing this force-stops the app, so reopen GazeKeep once afterward.\n• Other ROMs: Lock GazeKeep in Recents and allow auto-start / unrestricted background activity. On Android 13+, grant notification permission so disconnect alerts can reach you.\n• Quick toggle: Add the "GazeKeep" Quick Settings tile to pause or resume anytime (on Android 14+, resuming from the tile briefly brings the app forward to satisfy foreground camera rules).',
      },
      {
        question: "GazeKeep is enabled, so why does the screen still sleep in some apps?",
        answer:
          'Check in this order:\n1. Check the Home status card: It shows the exact "Last check" or "Last skip" reason (not whitelisted, Battery Saver active, low battery, too dark, etc.).\n2. Check App scope & list: "Whitelist only" is active by default with an empty list. Preinstalled system apps are hidden by default—tap "More options → Show system apps" in the App list if needed. Camera apps are always skipped.\n3. Check power gates: "Pause on Battery Saver" and "Low battery threshold" (15% or below while unplugged) are enabled by default.\n4. Tune lead time & max hold: If your device dims quickly or wakes the camera slowly, increase "Detection lead time" above 8 s. Reaching "Maximum keep-awake time" (30 min by default) intentionally lets the screen sleep once until you touch it.\n5. Inspect Detailed logs: Enable Settings → Detailed logs and reproduce once to see the exact per-round reason (kept for 24 h, cleared immediately when turned off).',
      },
      {
        question: "Which APK from GitHub Releases should I download, and will upgrading keep my settings?",
        answer:
          "• arm64-v8a (Recommended): Most modern Android phones and tablets.\n• armeabi-v7a: Older 32-bit Android devices.\n• x86_64: Android emulators on PC.\n• universal: All-ABI package if you are unsure of your device architecture.\n\nAll official release APKs share the same signature and version code, so you can install over an existing release to upgrade without losing settings or app lists. If switching from a differently signed or Debug build, uninstall first. LSPosed users must reboot after updating the APK.",
      },
    ],
  },
  footer: {
    title: "More attention\nfor your world.",
    text: "An open-source Android utility. Yours to control, explore and improve.",
    docs: "User guide",
    license: "License",
    openSource: "On-device offline · MIT License",
    navTitle: "Explore",
    linksTitle: "Resources",
  },
  changelog: {
    nav: "Changelog",
    title: "Changelog - GazeKeep",
    description: "Explore GazeKeep releases, new features, improvements, bug fixes, and setup steps.",
    kicker: "Release history",
    heading: "Changelog",
    intro: "Track new capabilities, power optimizations, bug fixes, and setup notes across GazeKeep releases.",
    backHome: "Back to home",
    githubReleases: "GitHub Releases",
    releaseTagLabel: "View this release on GitHub",
    latestBadge: "Latest",
    setupTitle: "Setup steps & tuning tips",
    emptyNotice: "No release notes found yet.",
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
    scope_whitelist: "Whitelist only · Demo",
    scope_blacklist: "Blacklist · Demo",
  },
  hint: {
    touch: "Tap to use · hold, then swipe to scroll",
    mouse: "Click to use · drag to scroll",
  },
  tour: "Stays on while you look",
  ui: {
    skip: "Skip to content",
    productNote: "A little understanding. For Android.",
    kickers: {
      workflow: "Considered by design",
      backends: "Three ways to connect",
      whitelist: "Your focus, your rules",
      settings: "Tuned to you",
      privacy: "Privacy starts with restraint.",
    },
    tagline: "Stay in the moment.",
    switchLanguage: "切换至中文",
    languageShort: "中文",
    languageName: "中文",
  },
} satisfies SiteContent;
