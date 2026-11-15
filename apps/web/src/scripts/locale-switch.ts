import type { Locale, SiteContent } from "../data/schema.ts";
import { sections } from "../data/sections.ts";
import { site } from "../data/site.config.ts";
import { clickOrigin, runCircularReveal } from "./reveal-transition.ts";

export type SitePage = "home" | "changelog";

export interface LocaleBundle {
  content: SiteContent;
  privacy: readonly string[];
}

export function alternateLocale(locale: Locale): Locale {
  return locale === "zh" ? "en" : "zh";
}

export function resolveLocalePath(locale: Locale, page: SitePage, hash = "") {
  const base = page === "changelog" ? `/${locale}/changelog/` : `/${locale}/`;
  return hash ? `${base}${hash}` : base;
}

export function localeFromPath(pathname: string): Locale {
  return pathname.startsWith("/en") ? "en" : "zh";
}

const bundleCache = new Map<Locale, Promise<LocaleBundle>>();
const changelogCache = new Map<Locale, Promise<{ title: string; description: string; mainHtml: string }>>();

export function loadLocaleBundle(locale: Locale): Promise<LocaleBundle> {
  let cached = bundleCache.get(locale);
  if (!cached) {
    cached = Promise.all([
      locale === "en" ? import("../data/en.ts").then((m) => m.en) : import("../data/zh.ts").then((m) => m.zh),
      import("../data/privacy.generated.json").then((m) => m.default[locale] as readonly string[]),
    ]).then(([content, privacy]) => ({ content, privacy }));
    bundleCache.set(locale, cached);
  }
  return cached;
}

async function fetchChangelogPage(locale: Locale) {
  let cached = changelogCache.get(locale);
  if (!cached) {
    cached = fetch(`/${locale}/changelog/`)
      .then((res) => {
        if (!res.ok) throw new Error(`Failed to load ${locale} changelog: ${res.status}`);
        return res.text();
      })
      .then((html) => {
        const doc = new DOMParser().parseFromString(html, "text/html");
        const main = doc.querySelector("main#main");
        if (!main) throw new Error("Missing #main in changelog document");
        return {
          title: doc.title,
          description: doc.querySelector<HTMLMetaElement>('meta[name="description"]')?.content ?? "",
          mainHtml: main.innerHTML,
        };
      });
    changelogCache.set(locale, cached);
  }
  return cached;
}

function setText(root: ParentNode, selector: string, value: string) {
  const el = root.querySelector(selector);
  if (el) el.textContent = value;
}

function setAttr(root: ParentNode, selector: string, name: string, value: string) {
  const el = root.querySelector(selector);
  if (el) el.setAttribute(name, value);
}

function setMeta(selector: string, content: string) {
  const meta = document.querySelector<HTMLMetaElement>(selector);
  if (meta) meta.content = content;
}

export function applyHeadAndChrome(
  c: SiteContent,
  page: SitePage,
  customMeta?: { title?: string; description?: string },
) {
  const other = alternateLocale(c.locale);
  const subPath = page === "changelog" ? "changelog/" : "";
  const pageUrl = new URL(`/${c.locale}/${subPath}`, site.url).href;
  const image = new URL(`/og/og-${c.locale}.png`, site.url).href;
  const ogLocale = c.locale === "zh" ? "zh_CN" : "en_US";
  const altOgLocale = other === "zh" ? "zh_CN" : "en_US";
  const pageTitle = customMeta?.title || (page === "changelog" ? c.changelog.title : c.title);
  const pageDescription = customMeta?.description || (page === "changelog" ? c.changelog.description : c.description);

  document.documentElement.lang = c.locale;
  document.title = pageTitle;
  setMeta('meta[name="description"]', pageDescription);
  setAttr(document, 'link[rel="canonical"]', "href", pageUrl);
  setMeta('meta[property="og:url"]', pageUrl);
  setMeta('meta[property="og:title"]', pageTitle);
  setMeta('meta[property="og:description"]', pageDescription);
  setMeta('meta[property="og:image"]', image);
  setMeta('meta[property="og:image:alt"]', c.ogImageAlt);
  setMeta('meta[property="og:locale"]', ogLocale);
  setMeta('meta[property="og:locale:alternate"]', altOgLocale);
  setMeta('meta[name="twitter:title"]', pageTitle);
  setMeta('meta[name="twitter:description"]', pageDescription);
  setMeta('meta[name="twitter:image"]', image);
  setMeta('meta[name="twitter:image:alt"]', c.ogImageAlt);

  setText(document, ".skip-link", c.ui.skip);
  document.querySelectorAll<HTMLAnchorElement>("[data-home-link]").forEach((a) => {
    a.setAttribute("href", `/${c.locale}/`);
  });
  setAttr(document, "nav.nav", "aria-label", c.menu);
  setAttr(document, "#menu-toggle", "aria-label", c.menu);

  sections.forEach((id, i) => {
    const href = page === "home" ? `#${id}` : `/${c.locale}/#${id}`;
    document.querySelectorAll<HTMLAnchorElement>(`[data-nav-section="${id}"]`).forEach((a) => {
      a.setAttribute("href", href);
      const span = a.querySelector("span");
      if (span) span.textContent = c.nav[i];
      else a.textContent = c.nav[i];
    });
  });

  document.querySelectorAll<HTMLAnchorElement>("[data-nav-changelog]").forEach((a) => {
    a.setAttribute("href", `/${c.locale}/changelog/`);
    const span = a.querySelector("span");
    if (span) span.textContent = c.changelog.nav;
    else a.textContent = c.changelog.nav;
  });

  document.querySelectorAll("[data-nav-download] span").forEach((span) => {
    span.textContent = c.download;
  });

  const langHref = resolveLocalePath(other, page);
  document.querySelectorAll<HTMLAnchorElement>("[data-locale-switch]").forEach((a) => {
    a.setAttribute("href", langHref);
    a.setAttribute("lang", other);
    if (a.dataset.localeSwitch === "short") {
      a.setAttribute("aria-label", c.ui.switchLanguage);
      a.textContent = c.ui.languageShort;
    } else {
      a.textContent = c.ui.languageName;
    }
  });

  setText(document, "[data-footer-tagline]", c.ui.tagline);
  setText(document, "[data-footer-meta]", c.footer.openSource);
  setText(document, "[data-footer-nav-title]", c.footer.navTitle);
  setText(document, "[data-footer-links-title]", c.footer.linksTitle);
  setText(document, "[data-footer-docs]", c.footer.docs);
  setText(document, "[data-footer-license]", c.footer.license);
}

export function applyHomeLocale(c: SiteContent, privacyLines: readonly string[]) {
  setAttr(document, "#retry-hit", "aria-label", c.retry);
  setAttr(document, "#kmp-frame", "title", c.demo);
  setText(document, "#retry-showcase", c.retry);

  const hero = document.querySelector("#hero");
  if (hero) {
    setText(hero, "[data-hero-note]", c.ui.productNote);
    hero.querySelectorAll("h1 span").forEach((span, i) => {
      if (c.hero.title[i] !== undefined) span.textContent = c.hero.title[i];
    });
    setText(hero, ".hero-description", c.hero.subtitle);
    setText(hero, "[data-dl-link] span", c.download);
    setText(hero, "[data-source-link] span", c.source);
    hero.querySelectorAll(".trust-pills .pill-label").forEach((el, i) => {
      if (c.hero.pills[i] !== undefined) el.textContent = c.hero.pills[i];
    });
    setText(hero, ".explore-label", c.hero.explore);
  }

  const workflow = document.querySelector("#workflow");
  if (workflow) {
    setText(workflow, ".story-heading .section-kicker", c.ui.kickers.workflow);
    setText(workflow, ".story-heading h2", c.story.title);
    setText(workflow, ".story-heading p", c.story.intro);
    workflow.querySelectorAll(".story-step").forEach((step, i) => {
      const data = c.story.steps[i];
      if (!data) return;
      setText(step, "h3", data.title);
      setText(step, "p", data.text);
    });
  }

  const backends = document.querySelector("#backends");
  if (backends) {
    setText(backends, ".section-kicker", c.ui.kickers.backends);
    setText(backends, "h2", c.backends.title);
    setText(backends, ".section-heading p", c.backends.intro);
    backends.querySelectorAll<HTMLElement>("[data-backend]").forEach((card) => {
      const item = c.backends.items.find((x) => x.id === card.dataset.backend);
      if (!item) return;
      setText(card, ".backend-tag", item.tag);
      setText(card, "h3", item.name);
      setText(card, "p", item.text);
      setText(card, ".requirement", item.requirement);
    });
  }

  const whitelist = document.querySelector("#whitelist");
  if (whitelist) {
    setText(whitelist, ".section-kicker", c.ui.kickers.whitelist);
    setText(whitelist, ".section-heading h2", c.whitelist.title);
    setText(whitelist, ".section-heading p", c.whitelist.text);
    setAttr(whitelist, ".whitelist-modes", "aria-label", c.whitelist.scopeLabel);
    whitelist.querySelectorAll<HTMLElement>("[data-scope]").forEach((card) => {
      const mode = c.whitelist.modes.find((x) => x.id === card.dataset.scope);
      if (!mode) return;
      setText(card, "strong", mode.label);
      setText(card, ".scope-copy span", mode.summary);
    });
  }

  const settings = document.querySelector("#settings");
  if (settings) {
    setText(settings, ".section-kicker", c.ui.kickers.settings);
    setText(settings, ".section-heading h2", c.settings.title);
    setText(settings, ".section-heading p", c.settings.text);
    settings.querySelectorAll<HTMLElement>("[data-setting]").forEach((card) => {
      const item = c.settings.items.find((x) => x.id === card.dataset.setting);
      if (!item) return;
      setText(card, ".setting-label", item.label);
      setText(card, ".setting-detail", item.detail);
    });
  }

  const privacy = document.querySelector("#privacy");
  if (privacy) {
    setText(privacy, ".section-kicker", c.ui.kickers.privacy);
    setText(privacy, ".section-heading h2", c.privacy.title);
    setText(privacy, ".section-heading p", c.privacy.text);
    privacy.querySelectorAll(".privacy-lines article").forEach((article, i) => {
      if (c.privacy.labels[i] !== undefined) setText(article, "h3", c.privacy.labels[i]);
      if (privacyLines[i] !== undefined) setText(article, "p", privacyLines[i]);
    });
    setText(privacy, ".manifest-card summary", c.privacy.permission);
    setText(privacy, ".manifest-card .text-link span", c.source);
  }

  const start = document.querySelector("#start");
  if (start) {
    setText(start, "h2", c.start.title);
    start.querySelectorAll(".start-grid article").forEach((article, i) => {
      const step = c.start.steps[i];
      if (!step) return;
      setText(article, "h3", step.title);
      setText(article, "p", step.text);
    });
  }

  const faq = document.querySelector("#faq");
  if (faq) {
    setText(faq, "h2", c.faq.title);
    faq.querySelectorAll(".faq-item").forEach((itemEl, i) => {
      const item = c.faq.items[i];
      if (!item) return;
      setText(itemEl, ".faq-question", item.question);
      setText(itemEl, ".faq-answer p", item.answer);
    });
  }

  const closing = document.querySelector(".closing");
  if (closing) {
    setText(closing, "h2", c.footer.title);
    setText(closing, "p", c.footer.text);
    setText(closing, ".button span", c.download);
  }
}

function captureScrollAnchor() {
  const y = scrollY;
  if (y <= 4) return { restore: () => window.scrollTo({ top: 0, behavior: "instant" }) };
  const candidates = [...document.querySelectorAll<HTMLElement>("main section, .changelog-card")];
  let anchor: HTMLElement | undefined;
  for (let i = candidates.length - 1; i >= 0; i--) {
    const box = candidates[i].getBoundingClientRect();
    if (box.top <= 140 && box.bottom > 40) {
      anchor = candidates[i];
      break;
    }
  }
  anchor ??= candidates.find((el) => el.getBoundingClientRect().bottom > 0);
  if (!anchor) return { restore: () => window.scrollTo({ top: y, behavior: "instant" }) };
  const beforeTop = anchor.getBoundingClientRect().top;
  return {
    restore() {
      const delta = anchor.getBoundingClientRect().top - beforeTop;
      if (Math.abs(delta) > 1) window.scrollBy({ top: delta, behavior: "instant" });
    },
  };
}

export function initLocaleSwitch(options: {
  page: SitePage;
  frame?: HTMLIFrameElement | null;
  onApplied?: (content: SiteContent) => void;
}) {
  const reduce = matchMedia("(prefers-reduced-motion: reduce)");
  let switching = false;

  const preload = (locale: Locale) => {
    void loadLocaleBundle(locale).catch(() => {});
    if (options.page === "changelog") void fetchChangelogPage(locale).catch(() => {});
  };

  const applyLocale = async (next: Locale, origin: { x: number; y: number }, pushHistory: boolean) => {
    if (switching || document.documentElement.lang === next) return;
    switching = true;
    try {
      const bundle = await loadLocaleBundle(next);
      const changelogData = options.page === "changelog" ? await fetchChangelogPage(next) : undefined;
      await runCircularReveal({
        origin,
        animate: !reduce.matches,
        frame: options.frame,
        flag: "localeTransition",
        apply: () => {
          const anchor = captureScrollAnchor();
          if (options.page === "changelog" && changelogData) {
            const main = document.querySelector("main#main");
            if (main) main.innerHTML = changelogData.mainHtml;
            applyHeadAndChrome(bundle.content, options.page, {
              title: changelogData.title,
              description: changelogData.description,
            });
          } else {
            applyHomeLocale(bundle.content, bundle.privacy);
            applyHeadAndChrome(bundle.content, options.page);
          }
          options.onApplied?.(bundle.content);
          window.dispatchEvent(new Event("resize"));
          anchor.restore();
          if (pushHistory) {
            const nextUrl = resolveLocalePath(next, options.page, options.page === "home" ? location.hash : "");
            if (`${location.pathname}${location.hash}` !== nextUrl) {
              history.pushState({ locale: next }, "", nextUrl);
            }
          }
        },
      });
    } catch {
      location.href = resolveLocalePath(next, options.page);
    } finally {
      switching = false;
    }
  };

  const onClick = (event: MouseEvent) => {
    if (!(event.target instanceof Element)) return;
    const link = event.target.closest<HTMLAnchorElement>("[data-locale-switch]");
    if (!link) return;
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    const next = link.getAttribute("lang") === "en" ? "en" : "zh";
    void applyLocale(next, clickOrigin(link, event), true);
  };

  const onPointerIntent = (event: Event) => {
    if (!(event.target instanceof Element)) return;
    const link = event.target.closest<HTMLAnchorElement>("[data-locale-switch]");
    if (!link) return;
    preload(link.getAttribute("lang") === "en" ? "en" : "zh");
  };

  const onPopState = () => {
    const next = localeFromPath(location.pathname);
    if (next === document.documentElement.lang) return;
    void applyLocale(next, { x: innerWidth / 2, y: 36 }, false);
  };

  document.addEventListener("click", onClick);
  document.addEventListener("pointerenter", onPointerIntent, true);
  document.addEventListener("focusin", onPointerIntent);
  window.addEventListener("popstate", onPopState);
  return () => {
    document.removeEventListener("click", onClick);
    document.removeEventListener("pointerenter", onPointerIntent, true);
    document.removeEventListener("focusin", onPointerIntent);
    window.removeEventListener("popstate", onPopState);
  };
}
