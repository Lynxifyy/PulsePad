#!/usr/bin/env python3
"""Repository integrity gate for PulsePad.

Scans every tracked file for the classes of defect that a compiler and a linter
do not catch: unfinished work, work that was written but never wired up, resources
nothing points at, dependencies the project forbids, and files that are malformed
or empty. The CI build refuses to produce an APK while any error is outstanding.

Design notes
------------
* Every *tracked* file is enumerated, not just source: a stray binary, an empty
  file or a broken JSON asset is exactly the kind of thing that slips through.
* Rules are independent and each reports its own findings, so the output tells
  you which check failed rather than just "the gate is red".
* A finding can be waived in two ways, both of which require a reason:
    - an inline comment on the line, or the line directly above:  integrity:allow
    - a line in scripts/integrity-allowlist.txt:  <rule>:<path>[:<line>]  # reason
  Waivers are themselves checked: a waiver that no longer matches anything is an
  error, so the allowlist cannot rot into a permanent blanket.

Exit codes: 0 = clean (warnings allowed), 1 = at least one error.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
ALLOWLIST_PATH = REPO_ROOT / "scripts" / "integrity-allowlist.txt"
SELF_REL = "scripts/check_repo_integrity.py"

# Files that this checker must not scan with its own pattern rules: the checker
# itself (it contains the patterns as literals) and the one sanctioned Log wrapper.
PATTERN_RULE_SKIP = {SELF_REL, "app/src/main/java/com/pulsepad/controller/util/AppLog.kt"}

# Directories that are generated, vendored or not part of the shipped app.
SKIP_DIR_PARTS = {
    ".git", ".gradle", ".kotlin", "build", ".idea", ".openhands",
    "node_modules", "__pycache__", ".cxx",
}

TEXT_EXTENSIONS = {".kt", ".kts", ".xml", ".py", ".md", ".txt", ".json", ".toml",
                   ".pro", ".properties", ".yml", ".yaml", ".cfg", ".sh"}
BINARY_EXTENSIONS = {".glb", ".png", ".jpg", ".jpeg", ".webp", ".apk", ".jar",
                     ".keystore", ".jks", ".so"}


# --------------------------------------------------------------------------- #
# Finding model
# --------------------------------------------------------------------------- #

@dataclass
class Finding:
    severity: str          # "error" | "warn"
    rule: str
    path: str              # repo-relative, POSIX separators
    line: int              # 1-based, 0 when the finding is file-level
    message: str

    def location(self) -> str:
        return f"{self.path}:{self.line}" if self.line else self.path


@dataclass
class Waiver:
    rule: str
    path: str
    line: int | None
    reason: str
    used: bool = field(default=False, compare=False)

    def matches(self, finding: Finding) -> bool:
        if self.rule != finding.rule or self.path != finding.path:
            return False
        return self.line is None or self.line == finding.line


# --------------------------------------------------------------------------- #
# File enumeration
# --------------------------------------------------------------------------- #

def tracked_files() -> list[str]:
    """Every file git tracks, plus untracked-but-not-ignored files.

    Using git rather than a directory walk means generated output that happens to
    be on disk cannot mask a missing source file, and it respects .gitignore.
    """
    try:
        out = subprocess.run(
            ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
            cwd=REPO_ROOT, capture_output=True, text=True, check=True,
        ).stdout
    except (subprocess.CalledProcessError, FileNotFoundError):
        return sorted(
            str(p.relative_to(REPO_ROOT)).replace(os.sep, "/")
            for p in REPO_ROOT.rglob("*")
            if p.is_file() and not _is_skipped(p)
        )
    files = []
    for line in out.splitlines():
        rel = line.strip()
        if not rel:
            continue
        if _is_skipped(REPO_ROOT / rel):
            continue
        if (REPO_ROOT / rel).is_file():
            files.append(rel)
    return sorted(files)


def _is_skipped(path: Path) -> bool:
    return any(part in SKIP_DIR_PARTS for part in path.parts)


def read_text(path: Path) -> str | None:
    try:
        return path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return None


def lines_of(text: str) -> list[str]:
    return text.splitlines()


def is_waived_inline(lines: list[str], index: int) -> bool:
    """True when the line, or the line above it, carries an inline waiver."""
    if "integrity:allow" in lines[index]:
        return True
    return index > 0 and "integrity:allow" in lines[index - 1]


# --------------------------------------------------------------------------- #
# Rules
# --------------------------------------------------------------------------- #

# Matched case-sensitively so prose like "swipe" or "stub" in a doc comment is
# not flagged. Only the conventional uppercase markers are defects.
UNFINISHED_MARKER = re.compile(r"\b(TODO|FIXME|XXX|HACK|TBD|WIP)\b")
UNFINISHED_PHRASE = re.compile(
    r"\bnot (?:yet )?implemented\b|\bunimplemented\b|\bcoming soon\b|\bplaceholder\b",
    re.IGNORECASE,
)
CODE_EXTENSIONS = {".kt", ".kts", ".py"}


def rule_unfinished_work(files: list[str]) -> list[Finding]:
    findings = []
    for rel in files:
        if Path(rel).suffix not in CODE_EXTENSIONS or rel in PATTERN_RULE_SKIP:
            continue
        text = read_text(REPO_ROOT / rel)
        if text is None:
            continue
        lines = lines_of(text)
        for i, line in enumerate(lines):
            if is_waived_inline(lines, i):
                continue
            marker = UNFINISHED_MARKER.search(line)
            if marker:
                findings.append(Finding(
                    "error", "unfinished-work", rel, i + 1,
                    f"unfinished-work marker {marker.group(0)!r} left in source",
                ))
                continue
            phrase = UNFINISHED_PHRASE.search(line)
            # A doc comment may legitimately explain that something is *not* a
            # placeholder, so only a bare phrase in a comment-free context counts.
            if phrase and not line.lstrip().startswith(("*", "//", "#")):
                findings.append(Finding(
                    "error", "unfinished-work", rel, i + 1,
                    f"unfinished-work phrase {phrase.group(0)!r} in code",
                ))
    return findings


def rule_truncated_kotlin(files: list[str]) -> list[Finding]:
    """Catches files that were cut off mid-write: an unbalanced brace block.

    Brace counting ignores braces inside string literals and comments, which is
    where the false positives would otherwise come from.
    """
    findings = []
    for rel in files:
        if Path(rel).suffix != ".kt":
            continue
        text = read_text(REPO_ROOT / rel)
        if text is None:
            continue
        stripped = _strip_kt_strings_and_comments(text)
        balance = stripped.count("{") - stripped.count("}")
        if balance != 0:
            findings.append(Finding(
                "error", "truncated-source", rel, 0,
                f"unbalanced braces ({balance:+d}); file may be truncated or malformed",
            ))
    return findings


def _strip_kt_strings_and_comments(text: str) -> str:
    out = []
    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        if ch == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
        elif ch == "/" and i + 1 < n and text[i + 1] == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i += 2
        elif text.startswith('"""', i):
            i += 3
            while i + 2 < len(text) and text[i:i + 3] != '"""':
                i += 1
            i += 3
        elif ch == '"':
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == "\\" else 1
            i += 1
        elif ch == "'":
            i += 1
            while i < n and text[i] != "'":
                i += 2 if text[i] == "\\" else 1
            i += 1
        else:
            out.append(ch)
            i += 1
    return "".join(out)


# Config files that are legitimately empty until the first waiver is added.
INTENTIONALLY_EMPTY = {"scripts/integrity-allowlist.txt"}


def rule_empty_files(files: list[str]) -> list[Finding]:
    findings = []
    for rel in files:
        path = REPO_ROOT / rel
        if path.suffix not in TEXT_EXTENSIONS or rel in INTENTIONALLY_EMPTY:
            continue
        try:
            size = path.stat().st_size
        except OSError:
            continue
        if size == 0:
            findings.append(Finding(
                "error", "empty-file", rel, 0,
                "tracked text file is empty",
            ))
    return findings


def rule_xml_well_formed(files: list[str]) -> list[Finding]:
    findings = []
    for rel in files:
        if Path(rel).suffix != ".xml":
            continue
        text = read_text(REPO_ROOT / rel)
        if text is None:
            continue
        try:
            ET.fromstring(text)
        except ET.ParseError as error:
            findings.append(Finding(
                "error", "malformed-xml", rel, getattr(error, "position", (0, 0))[0],
                f"XML does not parse: {error}",
            ))
    return findings


def rule_python_compiles(files: list[str]) -> list[Finding]:
    findings = []
    for rel in files:
        if Path(rel).suffix != ".py":
            continue
        text = read_text(REPO_ROOT / rel)
        if text is None:
            continue
        try:
            compile(text, rel, "exec")
        except SyntaxError as error:
            findings.append(Finding(
                "error", "malformed-python", rel, error.lineno or 0,
                f"Python does not compile: {error.msg}",
            ))
    return findings


def rule_glb_assets(files: list[str]) -> list[Finding]:
    """A GLB is a 12-byte header: magic 'glTF', version 2, total length."""
    findings = []
    for rel in files:
        if Path(rel).suffix != ".glb":
            continue
        try:
            raw = (REPO_ROOT / rel).read_bytes()
        except OSError:
            continue
        if len(raw) < 12 or raw[0:4] != b"glTF":
            findings.append(Finding(
                "error", "malformed-asset", rel, 0,
                "not a binary glTF container (missing 'glTF' magic)",
            ))
            continue
        version = int.from_bytes(raw[4:8], "little")
        declared = int.from_bytes(raw[8:12], "little")
        if version != 2:
            findings.append(Finding(
                "error", "malformed-asset", rel, 0,
                f"unsupported glTF version {version}, expected 2",
            ))
        if declared != len(raw):
            findings.append(Finding(
                "error", "malformed-asset", rel, 0,
                f"header declares {declared} bytes but file is {len(raw)}",
            ))
    return findings


FORBIDDEN_DEPENDENCY = re.compile(
    r"shizuku|rikka\.|com\.topjohnwu|eu\.chainfire|magisk|\blibsu\b|"
    r"com\.google\.android\.gms|play-services",
    re.IGNORECASE,
)
# A mention is allowed where the code is explicitly *detecting* the component in
# order to report or avoid it, never where it is depended upon. The CI workflows
# name the same components in order to assert that no build file declares them.
FORBIDDEN_DEPENDENCY_ALLOWED_PATHS = {
    "app/src/main/java/com/pulsepad/controller/util/CapabilityChecker.kt",
    "Readme.md",
}
FORBIDDEN_DEPENDENCY_ALLOWED_PREFIXES = (".github/workflows/",)


def rule_forbidden_dependencies(files: list[str]) -> list[Finding]:
    findings = []
    build_files = {"app/build.gradle.kts", "build.gradle.kts",
                   "settings.gradle.kts", "gradle/libs.versions.toml"}
    for rel in files:
        if Path(rel).suffix not in TEXT_EXTENSIONS:
            continue
        if rel in FORBIDDEN_DEPENDENCY_ALLOWED_PATHS or rel in PATTERN_RULE_SKIP:
            continue
        if rel.startswith(FORBIDDEN_DEPENDENCY_ALLOWED_PREFIXES):
            continue
        text = read_text(REPO_ROOT / rel)
        if text is None:
            continue
        lines = lines_of(text)
        for i, line in enumerate(lines):
            if is_waived_inline(lines, i):
                continue
            match = FORBIDDEN_DEPENDENCY.search(line)
            if not match:
                continue
            severity = "error" if rel in build_files else "warn"
            findings.append(Finding(
                severity, "forbidden-dependency", rel, i + 1,
                f"reference to {match.group(0)!r}; PulsePad must stay root-free and "
                f"free of Google Play Services",
            ))
    return findings


SECRET_PATTERNS = [
    (re.compile(r"AKIA[0-9A-Z]{16}"), "AWS access key id"),
    (re.compile(r"gh[pousr]_[A-Za-z0-9]{20,}"), "GitHub token"),
    (re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"), "private key"),
    (re.compile(r"AIza[0-9A-Za-z\-_]{35}"), "Google API key"),
    (re.compile(r"xox[baprs]-[0-9A-Za-z-]{10,}"), "Slack token"),
    (re.compile(r"(?i)\b(?:api[_-]?key|secret|passwd|password)\s*[:=]\s*[\"'][^\"']{12,}[\"']"),
     "hardcoded credential"),
]


def rule_hardcoded_secrets(files: list[str]) -> list[Finding]:
    findings = []
    for rel in files:
        if Path(rel).suffix not in TEXT_EXTENSIONS:
            continue
        text = read_text(REPO_ROOT / rel)
        if text is None:
            continue
        lines = lines_of(text)
        for i, line in enumerate(lines):
            if is_waived_inline(lines, i):
                continue
            for pattern, label in SECRET_PATTERNS:
                if pattern.search(line):
                    findings.append(Finding(
                        "error", "hardcoded-secret", rel, i + 1,
                        f"looks like a {label} committed to the repository",
                    ))
                    break
    return findings


# Above the typographic range so an em dash or ellipsis is fine but a symbol or
# emoji is not: the UI must use vector icons, never glyphs.
GLYPH_CODEPOINT_FLOOR = 0x2100


def rule_emoji_in_resources(files: list[str]) -> list[Finding]:
    findings = []
    for rel in files:
        if not rel.startswith("app/src/main/res/values") or Path(rel).suffix != ".xml":
            continue
        text = read_text(REPO_ROOT / rel)
        if text is None:
            continue
        for i, line in enumerate(lines_of(text)):
            if is_waived_inline(lines_of(text), i):
                continue
            for ch in line:
                if ord(ch) >= GLYPH_CODEPOINT_FLOOR:
                    findings.append(Finding(
                        "error", "glyph-in-resources", rel, i + 1,
                        f"U+{ord(ch):04X} {ch!r} in user-facing text; use a vector icon",
                    ))
                    break
    return findings


def rule_aapt_unsafe_entities(files: list[str]) -> list[Finding]:
    """AAPT2 rejects &apos; with 'Invalid unicode escape sequence'."""
    findings = []
    for rel in files:
        if Path(rel).suffix != ".xml" or not rel.startswith("app/src/main/res"):
            continue
        text = read_text(REPO_ROOT / rel)
        if text is None:
            continue
        lines = lines_of(text)
        for i, line in enumerate(lines):
            if "&apos;" in line and not is_waived_inline(lines, i):
                findings.append(Finding(
                    "error", "aapt-unsafe-entity", rel, i + 1,
                    "&apos; is rejected by AAPT2; reword to avoid the apostrophe",
                ))
    return findings


DEBUG_LEFTOVERS = [
    (re.compile(r"(?<![\w.])println\("), "println()"),
    (re.compile(r"System\.out\.print"), "System.out.print"),
    (re.compile(r"printStackTrace\(\)"), "printStackTrace()"),
    (re.compile(r"\bLog\.[dviwe]\("), "direct android.util.Log call (use AppLog)"),
]


def rule_debug_leftovers(files: list[str]) -> list[Finding]:
    findings = []
    for rel in files:
        if Path(rel).suffix != ".kt" or rel in PATTERN_RULE_SKIP:
            continue
        if "/src/test/" in rel:
            continue
        text = read_text(REPO_ROOT / rel)
        if text is None:
            continue
        lines = lines_of(text)
        for i, line in enumerate(lines):
            if is_waived_inline(lines, i):
                continue
            stripped = line.lstrip()
            if stripped.startswith(("*", "//")):
                continue
            for pattern, label in DEBUG_LEFTOVERS:
                if pattern.search(line):
                    findings.append(Finding(
                        "warn", "debug-leftover", rel, i + 1,
                        f"debug output left in shipped code: {label}",
                    ))
                    break
    return findings


# --------------------------------------------------------------------------- #
# Resource wiring
# --------------------------------------------------------------------------- #

RESOURCE_DEF = re.compile(r'<(?P<type>string|color|dimen|bool|integer|style|plurals|'
                          r'string-array|integer-array|array|attr|font)\s+name="(?P<name>[^"]+)"')
LOCAL_RESOURCE_REF = re.compile(r'@(?P<type>[\w.]+)/(?P<name>[\w.]+)')
CODE_RESOURCE_REF = re.compile(r'\bR\.(?P<type>\w+)\.(?P<name>\w+)')

# Resource types that are referenced by the platform rather than by our code, so
# "defined but never referenced" is expected for them.
NEVER_REFERENCED_TYPES = {"style", "attr", "dimen", "bool", "integer", "font"}

# Entry points and manifest-declared components are wired by the system.
ENTRY_POINT_MARKERS = ("@HiltAndroidApp", "@AndroidEntryPoint")


def collect_defined_resources(files: list[str]) -> dict[str, set[str]]:
    defined: dict[str, set[str]] = {}
    for rel in files:
        if Path(rel).suffix != ".xml" or not rel.startswith("app/src/main/res/values"):
            continue
        text = read_text(REPO_ROOT / rel)
        if text is None:
            continue
        for match in RESOURCE_DEF.finditer(text):
            defined.setdefault(match.group("type"), set()).add(match.group("name"))
    return defined


def collect_file_resources(files: list[str]) -> dict[str, set[str]]:
    """Resources defined by the presence of a file, e.g. res/drawable/foo.xml."""
    defined: dict[str, set[str]] = {}
    for rel in files:
        if not rel.startswith("app/src/main/res/"):
            continue
        parts = Path(rel).parts
        if len(parts) < 5:
            continue
        folder = parts[4]
        stem = Path(rel).stem
        if folder.startswith("drawable"):
            defined.setdefault("drawable", set()).add(stem)
        elif folder.startswith("mipmap"):
            defined.setdefault("mipmap", set()).add(stem)
        elif folder.startswith("xml"):
            defined.setdefault("xml", set()).add(stem)
    return defined


def collect_referenced_resources(files: list[str]) -> dict[str, set[tuple[str, int]]]:
    refs: dict[str, set[tuple[str, int]]] = {}
    for rel in files:
        if Path(rel).suffix not in {".xml", ".kt", ".kts"}:
            continue
        text = read_text(REPO_ROOT / rel)
        if text is None:
            continue
        for i, line in enumerate(lines_of(text), start=1):
            for match in LOCAL_RESOURCE_REF.finditer(line):
                # @android:... and @*android:... are framework resources.
                start = match.start()
                if start > 0 and line[start - 1] in "*":
                    continue
                if line[max(0, start - 8):start].endswith("android:"):
                    continue
                refs.setdefault(match.group("type"), set()).add((rel, i))
            for match in CODE_RESOURCE_REF.finditer(line):
                refs.setdefault(match.group("type"), set()).add((rel, i))
    return refs


def rule_resource_wiring(files: list[str]) -> list[Finding]:
    findings = []
    defined = collect_defined_resources(files)
    defined.update(collect_file_resources(files))
    referenced = collect_referenced_resources(files)

    # Undefined references: a rename that missed a call site.
    for rtype, sites in referenced.items():
        known = defined.get(rtype)
        if known is None:
            # A resource type we do not model (e.g. an android: namespace type).
            continue
        for rel, line in sorted(sites):
            name = _name_at(REPO_ROOT / rel, line, rtype)
            if name is None or name in known:
                continue
            findings.append(Finding(
                "error", "undefined-resource", rel, line,
                f"@{rtype}/{name} is referenced but never defined",
            ))

    # Defined but unreferenced: work that was written and then orphaned.
    for rtype, names in defined.items():
        if rtype in NEVER_REFERENCED_TYPES:
            continue
        used_names = {name for rel, line in referenced.get(rtype, set())
                      if (name := _name_at(REPO_ROOT / rel, line, rtype))}
        for name in sorted(names - used_names):
            findings.append(Finding(
                "warn", "unused-resource", f"app/src/main/res ({rtype})", 0,
                f"{rtype} '{name}' is never referenced",
            ))
    return findings


def _name_at(path: Path, line: int, rtype: str) -> str | None:
    text = read_text(path)
    if text is None:
        return None
    lines = lines_of(text)
    if not (1 <= line <= len(lines)):
        return None
    content = lines[line - 1]
    for match in LOCAL_RESOURCE_REF.finditer(content):
        if match.group("type") == rtype:
            return match.group("name")
    for match in CODE_RESOURCE_REF.finditer(content):
        if match.group("type") == rtype:
            return match.group("name")
    return None


# --------------------------------------------------------------------------- #
# Component wiring
# --------------------------------------------------------------------------- #

MANIFEST_COMPONENT = re.compile(
    r'<(?P<kind>activity|service|receiver|provider)\b[^>]*android:name="(?P<name>\.?[\w.]+)"',
    re.DOTALL,
)


def rule_manifest_components(files: list[str]) -> list[Finding]:
    findings = []
    manifest = REPO_ROOT / "app/src/main/AndroidManifest.xml"
    text = read_text(manifest)
    if text is None:
        return [Finding("error", "manifest", "app/src/main/AndroidManifest.xml", 0,
                        "manifest is missing or unreadable")]
    namespace = "com.pulsepad.controller"
    for match in MANIFEST_COMPONENT.finditer(text):
        raw = match.group("name")
        # Only components in the app's own namespace are ours to provide; a
        # fully-qualified library class (FileProvider, say) lives in its own AAR.
        if not raw.startswith(".") and not raw.startswith(namespace):
            continue
        fqcn = f"{namespace}{raw}" if raw.startswith(".") else raw
        relative = fqcn.replace(".", "/") + ".kt"
        candidates = [
            REPO_ROOT / "app/src/main/java" / relative,
            REPO_ROOT / "app/src/main/kotlin" / relative,
        ]
        if not any(c.is_file() for c in candidates):
            findings.append(Finding(
                "error", "missing-component",
                "app/src/main/AndroidManifest.xml", 0,
                f"manifest declares {match.group('kind')} {fqcn} but no such class file exists",
            ))
    return findings


NAV_ENUM_ENTRY = re.compile(r'^\s{4}([A-Z][A-Z0-9_]*)\(')
NAV_COMPOSABLE = re.compile(r'composable\(\s*([^)\n]+?)\s*\)')


def rule_navigation_wiring(files: list[str]) -> list[Finding]:
    """Every top-level destination must have a composable() registered for it.

    The nav host registers destinations as `composable(TopLevelDestination.X.route)`,
    so the check is on the enum constant being passed to a `composable` call, not
    on the route string literal appearing anywhere.
    """
    findings = []
    destinations = REPO_ROOT / ("app/src/main/java/com/pulsepad/controller/"
                                "presentation/navigation/TopLevelDestination.kt")
    nav_host = REPO_ROOT / ("app/src/main/java/com/pulsepad/controller/"
                            "presentation/navigation/PulsePadNavHost.kt")
    dest_text = read_text(destinations)
    host_text = read_text(nav_host)
    if dest_text is None or host_text is None:
        return findings
    registered = " ".join(NAV_COMPOSABLE.findall(host_text))
    for line in dest_text.splitlines():
        match = NAV_ENUM_ENTRY.match(line)
        if not match:
            continue
        constant = match.group(1)
        if constant not in registered:
            findings.append(Finding(
                "error", "unwired-route", str(destinations.relative_to(REPO_ROOT)), 0,
                f"destination {constant} has no composable() registered in the nav host",
            ))
    return findings


# --------------------------------------------------------------------------- #
# Orphaned source (work that was written but never used)
# --------------------------------------------------------------------------- #

TOP_LEVEL_DECL = re.compile(
    r'^(?:@\w+(?:\([^)]*\))?\s*)*'
    r'(?:(?:public|internal|private|protected|abstract|open|sealed|data|value|'
    r'annotation|enum|fun|object|class|interface|typealias|const|lateinit|val|var)\s+)*'
    r'(?:class|object|interface|fun|val|var|typealias)\s+([A-Za-z_]\w*)',
    re.MULTILINE,
)


def rule_orphaned_sources(files: list[str]) -> list[Finding]:
    """A production Kotlin file whose declarations nothing else mentions.

    This is the check that catches "I wrote the helper but forgot to call it".
    Entry points, Hilt modules and manifest components are excluded because the
    framework, not our code, is what reaches them.
    """
    manifest_text = read_text(REPO_ROOT / "app/src/main/AndroidManifest.xml") or ""
    kotlin_main = [rel for rel in files
                   if rel.startswith("app/src/main/") and rel.endswith(".kt")]

    contents: dict[str, str] = {}
    for rel in kotlin_main:
        text = read_text(REPO_ROOT / rel)
        if text is not None:
            contents[rel] = text

    findings = []
    for rel, text in contents.items():
        if any(marker in text for marker in ENTRY_POINT_MARKERS):
            continue
        if "@Module" in text:
            continue
        simple_name = Path(rel).stem
        if f".{simple_name}" in manifest_text:
            continue

        names = set(TOP_LEVEL_DECL.findall(text))
        if not names:
            continue

        referenced = False
        for other_rel, other_text in contents.items():
            if other_rel == rel:
                continue
            if any(re.search(rf"\b{re.escape(name)}\b", other_text) for name in names):
                referenced = True
                break
        if referenced:
            continue
        # A file referenced only from the test tree is still wired.
        for rel_test in files:
            if "/src/test/" not in rel_test and "/src/androidTest/" not in rel_test:
                continue
            test_text = read_text(REPO_ROOT / rel_test) or ""
            if any(re.search(rf"\b{re.escape(name)}\b", test_text) for name in names):
                referenced = True
                break
        if referenced:
            continue
        findings.append(Finding(
            "error", "orphaned-source", rel, 0,
            f"nothing references {', '.join(sorted(names))}; the file may be unwired",
        ))
    return findings


# --------------------------------------------------------------------------- #
# Build configuration
# --------------------------------------------------------------------------- #

def rule_build_configuration(files: list[str]) -> list[Finding]:
    findings = []
    app_build = REPO_ROOT / "app/build.gradle.kts"
    text = read_text(app_build)
    if text is None:
        return [Finding("error", "build-config", "app/build.gradle.kts", 0,
                        "app build script is missing or unreadable")]

    for key in ("applicationId", "minSdk", "targetSdk", "compileSdk", "versionCode",
                "versionName"):
        if not re.search(rf"\b{key}\s*=", text):
            findings.append(Finding(
                "error", "build-config", "app/build.gradle.kts", 0,
                f"expected '{key}' to be declared explicitly",
            ))

    # Every version catalog alias used must be declared, or the build fails with a
    # less obvious message.
    catalog = REPO_ROOT / "gradle/libs.versions.toml"
    catalog_text = read_text(catalog) or ""
    declared_aliases = set(re.findall(r'^([\w-]+)\s*=\s*\{', catalog_text, re.MULTILINE))
    declared_aliases |= set(re.findall(r'^([\w-]+)\s*=\s*"', catalog_text, re.MULTILINE))
    for rel in ("app/build.gradle.kts", "build.gradle.kts"):
        script = read_text(REPO_ROOT / rel) or ""
        for alias in re.findall(r"libs\.(?:plugins\.)?([\w.]+)", script):
            flat = alias.replace(".", "-")
            if flat in declared_aliases:
                continue
            if any(a.replace("-", ".") == alias for a in declared_aliases):
                continue
            findings.append(Finding(
                "error", "build-config", rel, 0,
                f"catalog alias 'libs.{alias}' is used but not declared in libs.versions.toml",
            ))
    return findings


# --------------------------------------------------------------------------- #
# FileProvider path drift
# --------------------------------------------------------------------------- #

FILE_PROVIDER_PATH_TAG = re.compile(
    r'<(external-files-path|files-path|cache-path|external-cache-path|external-path)\b'
    r'[^>]*?\bpath="([^"]*)"',
    re.S,
)
CONST_STRING = re.compile(r'const val ([A-Za-z_]\w*)\s*=\s*"([^"]*)"')
SET_DEST_EXTERNAL_FILES = re.compile(
    r'setDestinationInExternalFilesDir\s*\((.*?)\)\s*$', re.S | re.MULTILINE
)
# The public constants the destination call takes, and the directory each names.
ENVIRONMENT_DIRS = {
    "DIRECTORY_DOWNLOADS": "Download",
    "DIRECTORY_DOCUMENTS": "Documents",
    "DIRECTORY_PICTURES": "Pictures",
    "DIRECTORY_MOVIES": "Movies",
    "DIRECTORY_MUSIC": "Music",
}


def _static_dir_prefix(expression: str, constants: dict[str, str]) -> str | None:
    """The leading all-static directory components of a destination expression.

    `"$UPDATE_DIR/${apkFileName(update)}"` yields `"updates"`: the first segment
    resolves through a constant and scanning stops at the segment holding the call.
    A bare call with no separator (`apkFileName(update)`) yields `""`, because the
    file then lands directly in the environment directory. Returns None only when
    the leading segment is unresolvable *and* more segments follow, so the true
    path cannot be known.
    """
    segments = expression.strip().strip('"').split("/")
    resolved = []
    for index, raw in enumerate(segments):
        token = raw.replace("${", "").replace("}", "").strip()
        if token in constants:
            resolved.append(constants[token])
        elif re.fullmatch(r"[A-Za-z0-9_.-]+", token):
            resolved.append(token)
        else:
            return "" if index == 0 and len(segments) == 1 else None
    return "/".join(resolved)


def rule_fileprovider_paths(files: list[str]) -> list[Finding]:
    """A shared file must live under a path the file provider actually exposes.

    A destination outside the declared provider path fails only at runtime, when
    the user presses the button, as an IllegalArgumentException that is easy to
    swallow. Cross-checking the two here turns that into a build failure.
    """
    findings = []
    manifest = read_text(REPO_ROOT / "app/src/main/AndroidManifest.xml") or ""
    if "androidx.core.content.FileProvider" not in manifest:
        return findings

    declared: list[tuple[str, str]] = []
    for xml_file in sorted((REPO_ROOT / "app/src/main/res/xml").glob("*.xml")):
        text = read_text(xml_file) or ""
        for match in FILE_PROVIDER_PATH_TAG.finditer(text):
            declared.append((str(xml_file.relative_to(REPO_ROOT)),
                             match.group(2).strip("/")))
    if not declared:
        return findings

    for rel in files:
        if not rel.endswith(".kt"):
            continue
        text = read_text(REPO_ROOT / rel)
        if text is None or "setDestinationInExternalFilesDir" not in text:
            continue
        constants = dict(CONST_STRING.findall(text))
        for call in SET_DEST_EXTERNAL_FILES.finditer(text):
            args = call.group(1)
            parts = [p.strip() for p in args.split(",")]
            if len(parts) < 3:
                continue
            dir_name = parts[-2].split(".")[-1]
            directory = ENVIRONMENT_DIRS.get(dir_name)
            if directory is None:
                continue
            prefix = _static_dir_prefix(parts[-1], constants)
            if prefix is None:
                continue
            expected = f"{directory}/{prefix}".rstrip("/")
            line = text[:call.start()].count("\n") + 1
            if not any(declared_path == expected
                       or expected.startswith(declared_path + "/")
                       for _, declared_path in declared):
                findings.append(Finding(
                    "error", "fileprovider-path", rel, line,
                    f"writes to '{expected}' but no file_paths.xml entry exposes it "
                    f"(declared: {', '.join(sorted(p for _, p in declared))}); "
                    "the file provider will reject the share at runtime",
                ))
    return findings


# --------------------------------------------------------------------------- #
# Allowlist
# --------------------------------------------------------------------------- #

def load_allowlist() -> list[Waiver]:
    if not ALLOWLIST_PATH.is_file():
        return []
    waivers = []
    for i, raw in enumerate(ALLOWLIST_PATH.read_text().splitlines(), start=1):
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        reason = raw.split("#", 1)[1].strip() if "#" in raw else ""
        parts = line.split(":")
        if len(parts) < 2:
            print(f"warning: {ALLOWLIST_PATH.name}:{i} is not '<rule>:<path>[:line]'",
                  file=sys.stderr)
            continue
        rule, path = parts[0], parts[1]
        line_no = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else None
        waivers.append(Waiver(rule, path, line_no, reason))
    return waivers


def apply_waivers(findings: list[Finding], waivers: list[Waiver]) -> list[Finding]:
    kept = []
    for finding in findings:
        for waiver in waivers:
            if waiver.matches(finding):
                waiver.used = True
                break
        else:
            kept.append(finding)
    return kept


# --------------------------------------------------------------------------- #
# Driver
# --------------------------------------------------------------------------- #

RULES = [
    rule_unfinished_work,
    rule_truncated_kotlin,
    rule_empty_files,
    rule_xml_well_formed,
    rule_python_compiles,
    rule_glb_assets,
    rule_forbidden_dependencies,
    rule_hardcoded_secrets,
    rule_emoji_in_resources,
    rule_aapt_unsafe_entities,
    rule_debug_leftovers,
    rule_resource_wiring,
    rule_manifest_components,
    rule_navigation_wiring,
    rule_fileprovider_paths,
    rule_orphaned_sources,
    rule_build_configuration,
]


def discover_rule_ids() -> list[str]:
    """The rule ids this checker can emit, read from its own source.

    Waivers are keyed by rule id, so `--list-rules` has to print the ids that
    actually appear in findings. Deriving them from the source keeps the list from
    drifting away from the code that produces it.
    """
    text = read_text(REPO_ROOT / SELF_REL) or ""
    ids = set()
    for match in re.finditer(r"""Finding\(\s*[^,]+,\s*"([a-z][a-z0-9-]+)""", text):
        ids.add(match.group(1))
    return sorted(ids)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--json", action="store_true",
                        help="emit findings as JSON instead of text")
    parser.add_argument("--list-rules", action="store_true",
                        help="print the rule names and exit")
    args = parser.parse_args()

    if args.list_rules:
        for rule_id in discover_rule_ids():
            print(rule_id)
        return 0

    files = tracked_files()
    findings: list[Finding] = []
    for rule in RULES:
        findings.extend(rule(files))

    waivers = load_allowlist()
    findings = apply_waivers(findings, waivers)
    for waiver in waivers:
        if not waiver.used:
            findings.append(Finding(
                "error", "stale-waiver", waiver.path, waiver.line or 0,
                f"allowlist entry for rule '{waiver.rule}' matches nothing; remove it",
            ))

    findings.sort(key=lambda f: (f.severity != "error", f.rule, f.path, f.line))

    if args.json:
        print(json.dumps([f.__dict__ for f in findings], indent=2))
    else:
        _print_report(files, findings)

    return 1 if any(f.severity == "error" for f in findings) else 0


def _print_report(files: list[str], findings: list[Finding]) -> None:
    extensions: dict[str, int] = {}
    for rel in files:
        ext = Path(rel).suffix or "(none)"
        extensions[ext] = extensions.get(ext, 0) + 1

    print("PulsePad repository integrity check")
    print(f"scanned {len(files)} tracked files: "
          + ", ".join(f"{count} {ext}" for ext, count in sorted(extensions.items())))
    print()

    if not findings:
        print("no findings.")
    else:
        for finding in findings:
            label = "ERROR" if finding.severity == "error" else "WARN "
            print(f"{label} {finding.rule:<22} {finding.location()}")
            print(f"      {finding.message}")
        print()

    errors = sum(1 for f in findings if f.severity == "error")
    warnings = len(findings) - errors
    print(f"{len(findings)} finding(s): {errors} error(s), {warnings} warning(s)")
    if errors:
        print("FAILED - resolve the errors above; the APK build is blocked.")
    else:
        print("PASSED - no blocking findings.")


if __name__ == "__main__":
    sys.exit(main())
