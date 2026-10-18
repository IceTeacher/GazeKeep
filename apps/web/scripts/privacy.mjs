export function extractPrivacy(xml) {
  return [1, 2, 3].map((n) => {
    const name = `privacy_line_${n}`;
    const matches = [...xml.matchAll(new RegExp(`<string\\s+name="${name}"\\s*>([\\s\\S]*?)</string>`, "g"))];
    if (matches.length !== 1 || !matches[0][1].trim()) throw new Error(`隐私资源缺失或重复：${name}`);
    return matches[0][1]
      .trim()
      .replace(/&lt;/g, "<")
      .replace(/&gt;/g, ">")
      .replace(/&quot;/g, '"')
      .replace(/&apos;/g, "'")
      .replace(/&amp;/g, "&");
  });
}
export function assertPrivacyInPolicy(policy, zh, en) {
  for (const line of [...zh, ...en]) {
    if (!policy.includes(line)) throw new Error(`隐私文案与真源不一致：${line}`);
  }
}
