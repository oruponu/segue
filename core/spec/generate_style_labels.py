#!/usr/bin/env python3
"""Generates core/spec/style-labels.json from flutter/lib/src/native/discogs_labels.dart."""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "flutter", "lib", "src", "native", "discogs_labels.dart")
DST = os.path.join(ROOT, "core", "spec", "style-labels.json")
LINE = re.compile(r"""^\s*(['"])(.*)\1,\s*$""")


def main():
    labels = []
    with open(SRC, encoding="utf-8") as f:
        for line in f:
            m = LINE.match(line)
            if m:
                labels.append(m.group(2).replace("\\'", "'").replace('\\"', '"').replace("\\$", "$"))
    if len(labels) != 400:
        raise SystemExit(f"expected 400 labels, got {len(labels)}")
    with open(DST, "w", encoding="utf-8", newline="\n") as f:
        json.dump(labels, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
