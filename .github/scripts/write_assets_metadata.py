#!/usr/bin/env python3
"""Write update metadata JSON consumed by the app's update checker."""

import argparse
import json


def build_app_metadata(args):
    app = {
        "version": args.version,
        "versionCode": args.version_code,
        "extra": {
            "target": args.target,
            "min": args.min_sdk,
            "compile": args.compile_sdk,
            "packageSize": args.package_size,
        },
        "link": args.link,
    }
    if args.note_file:
        with open(args.note_file, "r", encoding="utf-8") as f:
            note = f.read().strip().lstrip("\ufeff")
        if note:
            app["note"] = note
    return app


def main():
    parser = argparse.ArgumentParser(description="Write update metadata JSON")
    parser.add_argument("--output", required=True, help="Output JSON path")
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--target", required=True, type=int)
    parser.add_argument("--min", required=True, type=int, dest="min_sdk")
    parser.add_argument("--compile", required=True, type=int, dest="compile_sdk")
    parser.add_argument("--package-size", required=True, type=int)
    parser.add_argument("--link", required=True)
    parser.add_argument("--note-file", help="File containing update notes text")
    args = parser.parse_args()

    metadata = {"app": build_app_metadata(args)}
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
