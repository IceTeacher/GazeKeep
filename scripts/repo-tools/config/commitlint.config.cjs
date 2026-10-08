const { readFileSync } = require("node:fs");
const { join } = require("node:path");

const modules = [
  ...readFileSync(join(__dirname, "../../../apps/app/settings.gradle.kts"), "utf8").matchAll(/include\(":([^"]+)"\)/g),
].map((match) => match[1].replaceAll(":", "-"));
const types = [
  ["feat", "✨", "新增功能"],
  ["fix", "🐛", "修复缺陷"],
  ["docs", "📝", "文档更新"],
  ["style", "🎨", "格式调整"],
  ["refactor", "♻️", "代码重构"],
  ["perf", "⚡️", "性能提升"],
  ["test", "✅", "测试相关"],
  ["chore", "🔨", "开发工具与构建"],
];
const scopes = [...modules, "docs", "dev-workflow", "ci-cd", "release", "deps", "web"];

module.exports = {
  extends: ["@commitlint/config-conventional"],
  plugins: [
    {
      rules: {
        "subject-chinese-emoji": ({ subject }) => [
          typeof subject === "string" &&
            /^\p{Extended_Pictographic}(?:\uFE0F)? .+[\u3400-\u9FFF\uF900-\uFAFF]/u.test(subject),
          "提交 subject 必须以 emoji 开头、后接空格，并包含中文描述",
        ],
      },
    },
  ],
  rules: {
    "type-enum": [2, "always", types.map(([type]) => type)],
    "scope-enum": [2, "always", scopes],
    "scope-empty": [2, "never"],
    "subject-chinese-emoji": [2, "always"],
  },
  prompt: {
    types: types.map(([value, emoji, description]) => ({
      value,
      emoji,
      name: `${value}: ${emoji} ${description}`,
    })),
    scopes,
    useEmoji: true,
  },
};
