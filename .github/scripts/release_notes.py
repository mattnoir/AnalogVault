#!/usr/bin/env python3
"""Print the GitHub release notes for a tag, taken from CHANGELOG.md.

    release_notes.py TAG CHANGELOG [RELEASED_CHANGELOGS]

Uses the section headed [X.Y.Z] (or [vX.Y.Z]) for the tag. A tag cut without
dating its section gets the [Unreleased] entries that no earlier release had,
where RELEASED_CHANGELOGS is the CHANGELOG.md of every lower tag, concatenated.
Earlier tags rather than the previous one: releases have been cut from more
than one branch, so the previous tag by version isn't always an ancestor.
"""
import re
import sys

# A version heading, with or without leading #s. The lookahead keeps markdown
# links such as "[Keep a Changelog](https://...)" from counting as one.
HEADING = re.compile(r"^#{0,6}\s*\[(?P<name>Unreleased|v?\d[^\]]*)\](?!\()")


def read(path):
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except OSError:
        return ""


def sections(text):
    """Section name -> its lines, heading excluded."""
    out, name = {}, None
    for line in text.splitlines():
        m = HEADING.match(line)
        if m:
            name = m.group("name")
            out.setdefault(name, [])
        elif name is not None:
            out[name].append(line)
    return out


def entries(lines):
    """Group lines into blocks: a "- " entry with its indented continuation, or a single other line."""
    blocks = []
    for line in lines:
        if blocks and blocks[-1][0].startswith("- ") and line[:1] in (" ", "\t") and line.strip():
            blocks[-1].append(line)
        else:
            blocks.append([line])
    return blocks


def drop_released(lines, previous):
    released = {tuple(b) for b in entries(previous.splitlines()) if b[0].startswith("- ")}
    kept = [b for b in entries(lines) if not (b[0].startswith("- ") and tuple(b) in released)]
    out = []
    for i, block in enumerate(kept):
        if block[0].startswith("### "):
            nxt = next((b[0] for b in kept[i + 1:] if b[0].strip()), "")
            if not nxt.startswith("- "):
                continue  # subsection left with no entries
        out.extend(block)
    return out


def tidy(lines):
    """Drop leading, trailing and repeated blank lines."""
    out = []
    for line in lines:
        if line.strip() or (out and out[-1].strip()):
            out.append(line)
    while out and not out[-1].strip():
        out.pop()
    return out


def main(argv):
    tag = argv[1]
    version = tag.lstrip("vV")
    secs = sections(read(argv[2]))
    body = next((lines for name, lines in secs.items() if name.lstrip("vV") == version), None)
    if body is None:
        body = drop_released(secs.get("Unreleased", []), read(argv[3]) if len(argv) > 3 else "")
    body = tidy(body)
    if not any(line.startswith("- ") for line in body):
        body = ["No changes are listed in CHANGELOG.md for this release."]
    print(f"## Analog Vault {tag}")
    print()
    print("\n".join(body))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main(sys.argv)
