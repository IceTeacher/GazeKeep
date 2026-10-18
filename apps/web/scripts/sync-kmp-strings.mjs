import { readFile, writeFile } from "node:fs/promises";
import { assertPrivacyInPolicy, extractPrivacy } from "./privacy.mjs";
const root = new URL("../../app/shared/src/commonMain/composeResources/", import.meta.url);
const zh = extractPrivacy(await readFile(new URL("values/strings.xml", root), "utf8"));
const en = extractPrivacy(await readFile(new URL("values-en/strings.xml", root), "utf8"));
const policy = await readFile(new URL("../../../docs/product/privacy-security.md", import.meta.url), "utf8");
assertPrivacyInPolicy(policy, zh, en);
await writeFile(
  new URL("../src/data/privacy.generated.json", import.meta.url),
  JSON.stringify({ zh, en }, null, 2) + "\n",
);
