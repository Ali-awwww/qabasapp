#!/usr/bin/env python3
"""فحص صحة مشروع قبس: التخزين العام، العلامات القديمة، والتكرار عالي الخطورة."""

from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path

KOTLIN_SUFFIXES = {".kt", ".kts"}
IGNORED_DIRS = {"build", ".gradle", ".git", "node_modules"}
LEGACY_MARKERS = re.compile(r"\b(?:TODO|FIXME|simulateSmartDeveloperReply)\b|قريبًا|محاكاة", re.IGNORECASE)
PREF_KEY = re.compile(r"(?:get|put|remove|contains)\w*\(\s*[\"']([^\"']+)[\"']")
CLASS_DECL = re.compile(r"\b(?:class|object|interface)\s+(\w+)")


def source_files(root: Path):
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix not in KOTLIN_SUFFIXES:
            continue
        if any(part in IGNORED_DIRS for part in path.parts):
            continue
        yield path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    args = parser.parse_args()
    root = args.root.resolve()
    files = list(source_files(root))
    key_locations: dict[str, list[str]] = {}
    marker_locations: list[tuple[str, str]] = []
    class_locations: dict[str, list[str]] = {}
    global_avatar_hits: list[str] = []

    for path in files:
        text = path.read_text(encoding="utf-8", errors="replace")
        relative = str(path.relative_to(root))
        for key in PREF_KEY.findall(text):
            key_locations.setdefault(key, []).append(relative)
        for match in LEGACY_MARKERS.finditer(text):
            line = text.count("\n", 0, match.start()) + 1
            marker_locations.append((relative, f"{line}: {match.group(0)}"))
        for name in CLASS_DECL.findall(text):
            class_locations.setdefault(name, []).append(relative)
        # The unsafe form uses the generic `prefs` handle. AccountScopedPreferences
        # and accountProfilePreferences are intentionally excluded.
        unsafe_avatar = re.search(
            r"\bprefs\.(?:getString|edit)\([^\n]*user_avatar_uri",
            text,
        )
        if unsafe_avatar:
            global_avatar_hits.append(relative)

    duplicate_keys = {
        key: sorted(set(locations))
        for key, locations in key_locations.items()
        if len(set(locations)) > 1
    }
    duplicate_types = {
        name: sorted(set(locations))
        for name, locations in class_locations.items()
        if len(set(locations)) > 1
    }

    report_dir = root / "build" / "reports" / "project-health"
    report_dir.mkdir(parents=True, exist_ok=True)
    report = report_dir / "PROJECT_HEALTH_REPORT_AR.md"
    lines = [
        "# تقرير صحة مشروع قبس",
        "",
        "## الملخص",
        "",
        f"- ملفات Kotlin المفحوصة: **{len(files)}**",
        f"- مفاتيح التخزين المتكررة بين الملفات: **{len(duplicate_keys)}**",
        f"- أنواع Kotlin المكررة بين الملفات: **{len(duplicate_types)}**",
        f"- علامات قديمة أو مؤجلة: **{len(marker_locations)}**",
        f"- استخدامات محتملة لمفتاح صورة عام: **{len(global_avatar_hits)}**",
        "",
        "## مفاتيح التخزين المشتركة بين أكثر من ملف",
        "",
    ]
    if duplicate_keys:
        for key, locations in sorted(duplicate_keys.items()):
            lines.append(f"- `{key}`: " + ", ".join(f"`{location}`" for location in locations))
    else:
        lines.append("- لا توجد مفاتيح مشتركة تحتاج مراجعة.")

    lines += ["", "## علامات قديمة أو مؤجلة", ""]
    if marker_locations:
        lines.extend(f"- `{location}`: `{marker}`" for location, marker in marker_locations[:200])
    else:
        lines.append("- لم يتم العثور على علامات قديمة ضمن المصدر.")

    lines += ["", "## استخدامات صورة البروفايل العامة", ""]
    if global_avatar_hits:
        lines.extend(f"- `{location}`" for location in global_avatar_hits)
    else:
        lines.append("- لا توجد قراءة مباشرة لصورة البروفايل من qabas_prefs العام.")

    lines += [
        "",
        "## سياسة الفشل",
        "",
        "- التكرار في مفاتيح التخزين تقرير مراجعة وليس فشلاً تلقائياً؛ قد تكون القراءة والكتابة موزعة عمداً.",
        "- استخدام صورة البروفايل من `qabas_prefs` العام فشل عالي الخطورة لأنه قد يخلط الحسابات.",
        "- العلامات القديمة تظهر للتنظيف التدريجي ولا تمنع البناء وحدها.",
        "",
    ]
    report.write_text("\n".join(lines), encoding="utf-8")

    print(report)
    if global_avatar_hits:
        print("ERROR: profile avatar is still read from global qabas_prefs", flush=True)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
