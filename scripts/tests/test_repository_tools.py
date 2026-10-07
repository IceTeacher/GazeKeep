import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class RepositoryChecksTest(unittest.TestCase):
    def check(self, script, directory):
        return subprocess.run(
            ["python3", str(ROOT / "scripts" / script), "--root", str(directory)],
            capture_output=True,
            text=True,
        )

    def fixture(self, directory):
        (directory / "docs").mkdir()
        (directory / "docs/index.md").write_text(
            "# 索引\n\n| 文档 | 类型 | 加载时机 | 维护状态 | 最后复核 |\n"
            "| --- | --- | --- | --- | --- |\n"
            "| [development.md](development.md) | 规范 | 开发时 | 持续维护 | 2026-10-08 |\n",
            encoding="utf-8",
        )
        (directory / "docs/development.md").write_text("# 开发\n", encoding="utf-8")

    def test_document_registration_and_links(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.fixture(directory)
            self.assertEqual(self.check("check_docs.py", directory).returncode, 0)
            page = directory / "docs/development.md"
            page.write_text("# 开发\n[缺失](missing.md)\n", encoding="utf-8")
            result = self.check("check_docs.py", directory)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("missing.md", result.stderr)
            page.write_text("# 开发\n", encoding="utf-8")
            (directory / "docs/unregistered.md").write_text("# 未登记\n", encoding="utf-8")
            self.assertIn("未登记", self.check("check_docs.py", directory).stderr)

    def test_plan_dates_and_archive_exemption(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.fixture(directory)
            (directory / "docs/archive").mkdir()
            (directory / "docs/archive/history.md").write_text(
                "[旧路径](gone.md)\n", encoding="utf-8"
            )
            self.assertEqual(self.check("check_docs.py", directory).returncode, 0)
            (directory / "docs/plans").mkdir()
            plan = directory / "docs/plans/20261008-sample.md"
            plan.write_text("# 计划\n\n最后维护：2026-10-07\n", encoding="utf-8")
            index = directory / "docs/index.md"
            index.write_text(
                index.read_text()
                + "| [计划](plans/20261008-sample.md) | 计划 | 修改时 | 计划文档 | 2026-10-08 |\n"
            )
            self.assertIn("日期", self.check("check_docs.py", directory).stderr)
            plan.write_text("# 计划\n\n最后维护：2026-10-08\n", encoding="utf-8")
            self.assertEqual(self.check("check_docs.py", directory).returncode, 0)

    def test_code_examples_are_not_links_and_anchors_are_checked(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.fixture(directory)
            page = directory / "docs/development.md"
            page.write_text(
                "# 开发\n\n```md\n[示例](absent.md)\n```\n\n[本页](#开发)\n", encoding="utf-8"
            )
            self.assertEqual(self.check("check_docs.py", directory).returncode, 0)
            page.write_text(page.read_text() + "\n[不存在](#不存在)\n")
            self.assertIn("锚点", self.check("check_docs.py", directory).stderr)

    def test_missing_module_rules_and_duplicate_skill_names_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "settings.gradle.kts").write_text('include(":core")\n')
            (directory / "AGENTS.md").write_text("# 根规则\n")
            result = self.check("check_agent_assets.py", directory)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("core/AGENTS.md", result.stderr)
            (directory / "core").mkdir()
            (directory / "core/AGENTS.md").write_text("# 模块规则\n")
            for name in ("first", "second"):
                skill = directory / ".agents/skills" / name
                skill.mkdir(parents=True)
                (skill / "SKILL.md").write_text(
                    "---\nname: first\ndescription: 有效的技能描述\n---\n\n# 技能\n"
                )
            result = self.check("check_agent_assets.py", directory)
            self.assertIn("重复", result.stderr)


if __name__ == "__main__":
    unittest.main()
