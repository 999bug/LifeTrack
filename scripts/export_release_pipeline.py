#!/usr/bin/env python3
"""把发版流水线的真实配置导出成可归纳的事实与文档快照。

用途：给 ai-dev-standards 之类按项目类型归纳规范的工具提供**确定性的事实底稿**，
避免文档与实现漂移。只依赖 Python 标准库，不联网、不读 git 历史、不解析 YAML。

    python scripts/export_release_pipeline.py --facts    # key=value 事实流（默认）
    python scripts/export_release_pipeline.py --write    # 重写 docs 里的快照区块
    python scripts/export_release_pipeline.py --check    # 漂移校验，CI 用；不等返回 1

输入只有两个文件：.github/workflows/release.yml 与 app/build.gradle.kts。
输出不含 secret 值、邮箱、内网地址或任何远程 URL，可安全贴进公开仓库与对话。
"""

from __future__ import annotations

import argparse
import difflib
import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/release.yml"
BUILD_FILE = ROOT / "app/build.gradle.kts"
RECIPE = ROOT / "docs/release-pipeline-recipe.md"

BEGIN = "<!-- BEGIN AUTO-SNAPSHOT -->"
END = "<!-- END AUTO-SNAPSHOT -->"
FACTS_HEADER = "# 只含公开字段；不含 secret 值、邮箱、内网地址"


def read(path: Path) -> str:
    if not path.is_file():
        raise SystemExit(f"缺少输入文件: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def first(pattern: str, text: str, default: str = "") -> str:
    match = re.search(pattern, text, re.M)
    return match.group(1).strip() if match else default


def yn(condition: bool) -> str:
    return "yes" if condition else "no"


def detect_project_type() -> tuple[str, str]:
    """判据顺序与 ai-dev-standards/scripts/detect-profile.sh 一致：Android 优先。

    Android 工程根目录也有 gradle 文件，若按「有 gradle 就是 Java」会误判；
    真正的判据是 app/src/main/AndroidManifest.xml。
    """
    candidates = [
        ("android", "app/src/main/AndroidManifest.xml"),
        ("node", "package.json"),
        ("python", "pyproject.toml"),
        ("java-maven", "pom.xml"),
        ("go", "go.mod"),
        ("docker", "Dockerfile"),
    ]
    for kind, evidence in candidates:
        if (ROOT / evidence).is_file():
            return kind, evidence
    return "unknown", "none"


def collect_facts(workflow: str, build_file: str) -> list[tuple[str, str]]:
    project_type, evidence = detect_project_type()
    steps = re.findall(r"^\s*- name:\s*(.+?)\s*$", workflow, re.M)
    step_names = " ".join(steps)

    resolve_version = "Resolve version" in step_names
    bump_version = "Bump version in build file" in step_names
    commit_version = "Commit version bump to main" in step_names
    write_back = yn(bump_version and "sed -i" in workflow)
    input_match = re.search(
        r"inputs:\s*\n\s*version:[\s\S]*?required:\s*(\S+)", workflow
    )
    if not input_match:
        version_input = "none"
    elif input_match.group(1).strip() == "false":
        version_input = "optional_override"
    else:
        version_input = "required"

    secrets = sorted(set(re.findall(r"secrets\.([A-Z_][A-Z0-9_]*)", workflow)))
    artifact = first(r"-name\s+'([^']*\.apk)'", workflow, "unknown")
    build_command = first(r"^\s*(\./gradlew[^\n]+)$", workflow, "unknown")
    concurrency_group = first(r"concurrency:\s*\n\s*group:\s*(\S+)", workflow, "none")
    cancel_in_progress = first(r"cancel-in-progress:\s*(\S+)", workflow, "unknown")
    contents_permission = first(r"permissions:\s*\n\s*contents:\s*(\S+)", workflow, "unknown")
    fetch_depth = first(r"fetch-depth:\s*(\d+)", workflow, "1")

    if "needs.release.outputs.tag" in workflow:
        mirror_tag_source = "needs.release.outputs.tag"
    elif "inputs.version" in workflow:
        mirror_tag_source = "inputs.version"
    else:
        mirror_tag_source = "unknown"

    return [
        ("project_type", project_type),
        ("project_type_evidence", evidence),
        ("package_manager", "gradle" if (ROOT / "gradlew").is_file() else "unknown"),
        ("build_file", BUILD_FILE.relative_to(ROOT).as_posix()),
        ("application_id", first(r"^\s*applicationId\s*=\s*\"([^\"]+)\"", build_file, "unknown")),
        ("min_sdk", first(r"^\s*minSdk\s*=\s*(\d+)", build_file, "unknown")),
        ("version_name", first(r"^\s*versionName\s*=\s*\"([^\"]+)\"", build_file, "unknown")),
        ("version_code", first(r"^\s*versionCode\s*=\s*(\d+)", build_file, "unknown")),
        ("version_scheme", "semver_auto_patch" if resolve_version and "AUTO_TAG" in workflow else "semver_manual"),
        ("version_source", "highest_vX.Y.Z_tag" if "sort -V" in workflow else "unknown"),
        ("version_code_source", "gradle_plus_one" if "NEXT_CODE" in workflow else "manual"),
        ("version_input", version_input),
        ("version_write_back", write_back),
        ("version_write_back_target", BUILD_FILE.relative_to(ROOT).as_posix() if write_back == "yes" else "none"),
        ("release_trigger", "workflow_dispatch" if "workflow_dispatch:" in workflow else "unknown"),
        ("release_branch_guard", "main_only" if 'refs/heads/main' in workflow else "none"),
        ("checkout_fetch_depth", fetch_depth),
        ("ci_version_commit", yn(commit_version)),
        ("ci_version_commit_timing", "after_build" if commit_version else "none"),
        ("ci_version_commit_skip_ci", yn("[skip ci]" in workflow)),
        ("concurrency_group", concurrency_group),
        ("concurrency_cancel_in_progress", cancel_in_progress),
        ("tag_conflict_guard", yn("git/ref/tags/$TAG" in workflow or 'gh release view "$TAG"' in workflow)),
        ("release_target", "version_commit_sha" if "steps.bump.outputs.sha" in workflow else "trigger_sha"),
        ("artifact_pattern", artifact),
        ("checksum", "sha256" if "sha256sum" in workflow else "none"),
        # 流水线里是 "$APKSIGNER" verify，不会出现字面量 "apksigner verify"
        ("signature_verify", "apksigner" if re.search(r"APKSIGNER|apksigner", workflow) else "none"),
        ("build_command", build_command),
        ("mirror", "gitee_openapi" if "gitee.com/api/v5" in workflow else "github_only"),
        ("mirror_tag_source", mirror_tag_source),
        ("contents_permission", contents_permission),
        ("secrets", ",".join(secrets) if secrets else "none"),
        ("workflow_sha256_12", hashlib.sha256(workflow.encode()).hexdigest()[:12]),
        ("build_file_sha256_12", hashlib.sha256(build_file.encode()).hexdigest()[:12]),
        ("step_count", str(len(steps))),
    ]


def render_facts(facts: list[tuple[str, str]]) -> str:
    return "\n".join([FACTS_HEADER] + [f"{key}={value}" for key, value in facts])


def render_snapshot(facts: list[tuple[str, str]], workflow: str) -> str:
    """生成整段快照。内容必须对同样的输入完全确定，否则 --check 永远失败。"""
    by_key = dict(facts)
    steps = re.findall(r"^\s*- name:\s*(.+?)\s*$", workflow, re.M)
    step_lines = "\n".join(f"{i}. `{name}`" for i, name in enumerate(steps, 1))
    body = "\n".join(
        [
            BEGIN,
            "> 由 `scripts/export_release_pipeline.py --write` 从 `.github/workflows/release.yml` "
            "与 `app/build.gradle.kts` 生成，**不要手改**。",
            f"> 输入指纹：`release.yml` = `{by_key['workflow_sha256_12']}` · "
            f"`build.gradle.kts` = `{by_key['build_file_sha256_12']}`。",
            "",
            "### 机器可读事实",
            "",
            "```text",
            render_facts(facts),
            "```",
            "",
            "### 流水线步骤（按 workflow 顺序）",
            "",
            step_lines or "（未解析到步骤）",
            END,
        ]
    )
    return body


def snapshot_from_doc(doc: str) -> str | None:
    match = re.search(re.escape(BEGIN) + r".*?" + re.escape(END), doc, re.S)
    return match.group(0) if match else None


def cmd_write(facts: list[tuple[str, str]], workflow: str) -> int:
    doc = read(RECIPE)
    if snapshot_from_doc(doc) is None:
        raise SystemExit(f"文档里找不到 {BEGIN} / {END} 标记: {RECIPE.name}")
    snapshot = render_snapshot(facts, workflow)
    updated = re.sub(
        re.escape(BEGIN) + r".*?" + re.escape(END),
        lambda _: snapshot,
        doc,
        count=1,
        flags=re.S,
    )
    if updated == doc:
        print("快照已是最新，无需改动")
        return 0
    with open(RECIPE, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(updated)
    print(f"已更新 {RECIPE.relative_to(ROOT).as_posix()} 的事实快照")
    return 0


def cmd_check(facts: list[tuple[str, str]], workflow: str) -> int:
    doc = read(RECIPE)
    current = snapshot_from_doc(doc)
    if current is None:
        print(f"::error::{RECIPE.name} 缺少快照标记，请跑 --write")
        return 1
    expected = render_snapshot(facts, workflow)
    if current == expected:
        print("release-pipeline-recipe 快照与实现一致")
        return 0
    print("::error::发版流水线已改动，但 docs/release-pipeline-recipe.md 的快照没同步。")
    print("请运行: python scripts/export_release_pipeline.py --write")
    diff = difflib.unified_diff(
        current.splitlines(), expected.splitlines(),
        fromfile="docs(当前)", tofile="实现(期望)", lineterm="",
    )
    for line in list(diff)[:24]:
        print(line)
    return 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--facts", action="store_true", help="输出 key=value 事实流（默认）")
    group.add_argument("--write", action="store_true", help="重写文档里的自动快照区块")
    group.add_argument("--check", action="store_true", help="校验快照是否与实现一致")
    args = parser.parse_args()

    workflow = read(WORKFLOW)
    build_file = read(BUILD_FILE)
    facts = collect_facts(workflow, build_file)

    if args.write:
        return cmd_write(facts, workflow)
    if args.check:
        return cmd_check(facts, workflow)
    print(render_facts(facts))
    return 0


if __name__ == "__main__":
    sys.exit(main())
