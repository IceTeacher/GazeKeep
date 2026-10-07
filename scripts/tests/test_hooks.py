import json
import os
import shlex
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LEFTHOOK = ROOT / "node_modules/.bin/lefthook"


class HooksTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="gazekeep-hooks-")
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.environment = dict(os.environ)
        self.environment.pop("LEFTHOOK_BIN", None)
        self.environment["PATH"] = (
            str(ROOT / "node_modules/.bin") + os.pathsep + self.environment["PATH"]
        )
        self.environment["LEFTHOOK_OUTPUT"] = "failure,summary"
        for name in (
            "lefthook.yml",
            "commitlint.config.cjs",
            "settings.gradle.kts",
            ".prettierrc.json",
            ".prettierignore",
            ".editorconfig",
        ):
            shutil.copyfile(ROOT / name, self.directory / name)
        (self.directory / "scripts").mkdir()
        (self.directory / "scripts/hook_files.py").symlink_to(ROOT / "scripts/hook_files.py")
        (self.directory / "node_modules").symlink_to(
            ROOT / "node_modules", target_is_directory=True
        )
        (self.directory / "package.json").write_text('{"private": true}\n')
        self.git("init", "--quiet")
        self.git("config", "user.name", "Hook Test")
        self.git("config", "user.email", "hook-test@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        self.git(
            "add",
            "--",
            "lefthook.yml",
            "commitlint.config.cjs",
            "settings.gradle.kts",
            "package.json",
        )
        self.git("-c", "core.hooksPath=/dev/null", "commit", "--quiet", "-m", "测试基线")
        self.command([str(LEFTHOOK), "install", "pre-commit", "commit-msg"])

    def command(self, arguments, check=True, input=None):
        return subprocess.run(
            arguments,
            cwd=self.directory,
            env=self.environment,
            input=input,
            capture_output=True,
            text=True,
            check=check,
        )

    def git(self, *arguments, check=True):
        return self.command(["git", *arguments], check=check)

    def write(self, name, contents):
        path = self.directory / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents, encoding="utf-8")
        return path

    def commit(self, title="docs(docs): 📝 更新测试文档"):
        return self.git("commit", "--quiet", "-m", title, check=False)

    def test_valid_commit_formats_only_staged_files_with_chinese_space_path(self):
        page = self.write("文档 空格.md", "#标题\n\n  说明\n")
        unstaged = self.write("untracked.json", '{"keep":1}')
        self.git("add", "--", str(page.relative_to(self.directory)))
        result = self.commit()
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        self.assertEqual(self.git("show", "HEAD:文档 空格.md").stdout, page.read_text())
        self.assertEqual(unstaged.read_text(), '{"keep":1}')

    def test_invalid_commit_titles_are_rejected(self):
        for title in (
            "fix(core): no emoji",
            "feat(no-scope): ✨ 新功能",
            "unknown(core): 🐛 修复",
            "docs(core): 📝 English only",
        ):
            with self.subTest(title=title):
                self.write("sample.md", "# 测试\n")
                self.git("add", "--", "sample.md")
                before = self.git("rev-parse", "HEAD").stdout
                self.assertNotEqual(self.commit(title).returncode, 0)
                self.assertEqual(self.git("rev-parse", "HEAD").stdout, before)

    def test_pinned_runner_ignores_global_binary_and_fails_if_local_runner_missing(self):
        global_bin = self.directory / "global-bin"
        global_bin.mkdir()
        executable = global_bin / "lefthook"
        executable.write_text(
            '#!/bin/sh\nif [ "$1" = "-h" ]; then exit 0; fi\n'
            "printf 'unexpected global lefthook\\n' >&2\nexit 91\n"
        )
        executable.chmod(0o755)
        self.environment["PATH"] = str(global_bin) + os.pathsep + self.environment["PATH"]
        self.write("pinned.md", "# 固定版本\n")
        self.git("add", "--", "pinned.md")
        result = self.commit()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        (self.directory / "node_modules").unlink()
        self.write("pinned.md", "# 缺少本地工具\n")
        self.git("add", "--", "pinned.md")
        result = self.commit()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("node_modules/.bin/lefthook", result.stdout + result.stderr)
        self.assertNotIn("unexpected global lefthook", result.stdout + result.stderr)

    def test_partial_staging_preserves_unstaged_edit_and_excludes_it_from_commit(self):
        page = self.write("sample.json", '{"one":1,"two":2,"three":3}\n')
        self.git("add", "--", "sample.json")
        self.assertEqual(self.commit().returncode, 0)
        page.write_text('{\n  "one": 10,\n  "two":2,\n  "three": 3\n}\n')
        self.git("add", "--", "sample.json")
        page.write_text('{\n  "one": 10,\n  "two":2,\n  "three": 99\n}\n')
        result = self.commit()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(json.loads(self.git("show", "HEAD:sample.json").stdout)["three"], 3)
        self.assertEqual(json.loads(page.read_text())["three"], 99)
        self.assertEqual(json.loads(self.git("show", "HEAD:sample.json").stdout)["one"], 10)

    def test_conflicting_partial_format_blocks_commit_without_losing_work(self):
        page = self.write("sample.py", "values = [\n    1,\n    2,\n    3,\n]\n")
        self.git("add", "--", "sample.py")
        self.git("-c", "core.hooksPath=/dev/null", "commit", "--quiet", "-m", "测试基线")
        staged = "values = [\n    1,\n    2,\n    3\n]\n"
        working = "values = [\n    99,\n    2,\n    3\n]\n"
        page.write_text(staged)
        self.git("add", "--", "sample.py")
        page.write_text(working)
        before = self.git("rev-parse", "HEAD").stdout
        result = self.commit("test(dev-workflow): ✅ 验证恢复冲突")
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.git("rev-parse", "HEAD").stdout, before)
        self.assertEqual(page.read_text(), working)
        self.assertEqual(self.git("show", ":sample.py").stdout, staged)

    def test_bad_syntax_and_missing_formatter_block_commit(self):
        self.write("bad.json", '{"not valid":')
        self.git("add", "--", "bad.json")
        self.assertNotEqual(self.commit().returncode, 0)
        self.git("reset", "--", "bad.json")
        self.write("bad.cjs", "module.exports = (\n")
        self.git("add", "--", "bad.cjs")
        self.assertNotEqual(self.commit().returncode, 0)
        self.git("reset", "--", "bad.cjs")
        self.write("missing.json", "{}\n")
        self.git("add", "--", "missing.json")
        (self.directory / "scripts/hook_files.py").unlink()
        shutil.copyfile(ROOT / "scripts/hook_files.py", self.directory / "scripts/hook_files.py")
        (self.directory / "node_modules").unlink()
        (self.directory / "node_modules/.bin").mkdir(parents=True)
        (self.directory / "node_modules/.bin/commitlint").symlink_to(
            ROOT / "node_modules/.bin/commitlint"
        )
        runner = self.directory / "node_modules/.bin/lefthook"
        runner.write_text(f'#!/bin/sh\nexec {shlex.quote(str(LEFTHOOK))} "$@"\n')
        runner.chmod(0o755)
        missing = self.commit()
        self.assertNotEqual(missing.returncode, 0)
        self.assertIn("prettier", missing.stdout + missing.stderr)

    def test_hook_installer_preserves_existing_hook_and_custom_path(self):
        installer = self.directory / "scripts/install_hooks.py"
        shutil.copyfile(ROOT / "scripts/install_hooks.py", installer)
        self.git("config", "core.hooksPath", ".custom-hooks")
        result = self.command(["python3", str(installer)], check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("core.hooksPath", result.stderr)
        self.assertEqual(
            self.git("config", "--get", "core.hooksPath").stdout.strip(), ".custom-hooks"
        )
        self.git("config", "--unset", "core.hooksPath")
        hook = self.directory / ".git/hooks/pre-commit"
        original = "#!/bin/sh\nprintf 'custom hook\\n'\n"
        hook.write_text(original)
        result = self.command(["python3", str(installer)], check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(hook.read_text(), original)

    def test_rename_delete_and_archive_are_handled_without_formatting_history(self):
        self.write("old.md", "# 原文\n")
        self.write("deleted.md", "# 删除\n")
        self.git("add", "--", "old.md", "deleted.md")
        self.assertEqual(self.commit().returncode, 0)
        self.git("mv", "--", "old.md", "新 名称.md")
        self.git("rm", "--", "deleted.md")
        archive = self.write("docs/archive/history.json", "invalid historical content")
        self.git("add", "--", "docs/archive/history.json")
        result = self.commit()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(archive.read_text(), "invalid historical content")
        self.assertIn("新 名称.md", self.git("ls-tree", "--name-only", "-z", "HEAD").stdout)

    def test_xml_text_and_privacy_sentences_remain_identical(self):
        resource = ROOT / "shared/src/commonMain/composeResources/values/strings.xml"
        sentences = [
            node.text
            for node in ET.parse(resource).getroot()
            if node.attrib.get("name", "").startswith("privacy_line_")
        ]
        original = (
            '<resources>\n<string name="spaces">  保留 空白  </string>\n'
            + "\n".join(
                f'<string name="privacy_line_{index}">{sentence}</string>'
                for index, sentence in enumerate(sentences, 1)
            )
            + "\n</resources>\n"
        )
        page = self.write("values/strings.xml", original)
        self.git("add", "--", "values/strings.xml")
        result = self.commit()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        before = [(node.attrib, node.text) for node in ET.fromstring(original)]
        after = [(node.attrib, node.text) for node in ET.parse(page).getroot()]
        self.assertEqual(before, after)
        docs = (ROOT / "docs/privacy-security.md").read_text()
        self.assertTrue(all(sentence in docs for sentence in sentences))

    def test_kotlin_python_and_toml_use_their_actual_tools(self):
        kotlin = self.write("Example.kt", 'fun example( ) {println("ok")}\n')
        python = self.write("example.py", "value= [1,2,3]\n")
        self.write("valid.toml", '[tool]\nvalue = "中文"\n')
        self.git("add", "--", "Example.kt", "example.py", "valid.toml")
        result = self.commit("test(dev-workflow): ✅ 验证多语言门禁")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn("example( )", kotlin.read_text())
        self.assertIn("value = [1, 2, 3]", python.read_text())
        self.write("invalid.toml", "value = [\n")
        self.git("add", "--", "invalid.toml")
        self.assertNotEqual(self.commit().returncode, 0)


if __name__ == "__main__":
    unittest.main()
