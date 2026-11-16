#!/usr/bin/env python3
"""Rebrand the pinned Winlator Mali Ludashi flavor as a standalone Storm app."""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: patch_winlator_branding.py <path-to-app-build.gradle>")

gradle_file = Path(sys.argv[1])
if not gradle_file.is_file():
    raise SystemExit(f"Gradle file not found: {gradle_file}")

content = gradle_file.read_text(encoding="utf-8")
replacements = (
    ('applicationId "com.ludashi.benchmark"',
     'applicationId "com.stormrevolution.launcher"'),
    ('resValue "string", "app_name", "Winlator Mali Ludashi"',
     'resValue "string", "app_name", "Storm Revolution Launcher"'),
    ('versionName "1.2.1 Bionic"', 'versionName "1.0.0"'),
)

for old, new in replacements:
    count = content.count(old)
    if count != 1:
        raise SystemExit(
            f"Expected exactly one occurrence of {old!r}, found {count}; "
            "upstream source changed and the patch must be reviewed."
        )
    content = content.replace(old, new, 1)

gradle_file.write_text(content, encoding="utf-8")
print("Applied Storm Revolution package ID, app label and version branding.")
