#!/usr/bin/env python3
"""Collapse duplicate keys in the .lang files.

Language.readLocal does `entries[key] = value` while streaming the file, so a repeated key silently
wins with its LAST value and every earlier line is dead. This keeps that runtime-effective value but
moves it to the key's first position, so the file finally says what the client actually shows.

  --check  report duplicates and value conflicts, exit 1 if any
  --fix    rewrite the files in place
"""
import sys
import collections
from pathlib import Path

LANG_DIR = Path(__file__).resolve().parent.parent / "src/main/resources/assets/fpsmaster/lang"


def parse(path):
    """Return (lines, occurrences) using Language.readLocal's exact rules."""
    lines = path.read_text(encoding="utf-8").splitlines()
    occ = collections.OrderedDict()
    for idx, line in enumerate(lines):
        trimmed = line.strip()
        if not trimmed or trimmed.startswith("#"):
            continue
        sep = trimmed.find("=")
        if sep <= 0:
            continue
        occ.setdefault(trimmed[:sep], []).append((idx, trimmed[sep + 1:]))
    return lines, occ


def main():
    fix = "--fix" in sys.argv
    failed = False
    for path in sorted(LANG_DIR.glob("*.lang")):
        lines, occ = parse(path)
        dups = {k: v for k, v in occ.items() if len(v) > 1}
        conflicts = {k: v for k, v in dups.items() if len({val for _, val in v}) > 1}
        print(f"{path.name}: keys={len(occ)} duplicate={len(dups)} value-conflict={len(conflicts)}")
        if not dups:
            continue
        failed = True
        if not fix:
            for key, entries in conflicts.items():
                shown = entries[-1][1]
                dead = ", ".join(f"L{ln + 1}={val!r}" for ln, val in entries[:-1])
                print(f"  {key}: shows {shown!r}; dead {dead}")
            continue

        drop = set()
        for key, entries in dups.items():
            first_idx = entries[0][0]
            lines[first_idx] = f"{key}={entries[-1][1]}"
            drop.update(idx for idx, _ in entries[1:])
        kept = [line for idx, line in enumerate(lines) if idx not in drop]

        # A removed block can leave a run of blank lines behind; collapse them.
        squeezed, blank = [], False
        for line in kept:
            if not line.strip():
                if blank:
                    continue
                blank = True
            else:
                blank = False
            squeezed.append(line)
        path.write_text("\n".join(squeezed) + "\n", encoding="utf-8")
        print(f"  removed {len(drop)} dead line(s)")
    return 0 if (fix or not failed) else 1


if __name__ == "__main__":
    sys.exit(main())
