#!/usr/bin/env python3
"""Fetch the latest GitHub release, download its APK asset, and save release info."""

import argparse
import json
import os
import sys
import urllib.request


API_URL = "https://api.github.com/repos/{repo}/releases/latest"
USER_AGENT = "ElectricityQuery-CI"


def auth_headers():
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": USER_AGENT,
    }
    token = os.environ.get("GITHUB_TOKEN", "")
    if token:
        headers["Authorization"] = "Bearer " + token
    return headers


def main():
    parser = argparse.ArgumentParser(description="Sync latest stable release metadata")
    parser.add_argument("--repo", required=True)
    parser.add_argument("--output-apk", required=True)
    parser.add_argument("--output-info", required=True)
    parser.add_argument("--output-body", required=True)
    args = parser.parse_args()

    url = API_URL.format(repo=args.repo)
    request = urllib.request.Request(url, headers=auth_headers())
    with urllib.request.urlopen(request, timeout=30) as response:
        release = json.load(response)

    apk_assets = [
        asset
        for asset in release.get("assets", [])
        if asset.get("name", "").lower().endswith(".apk")
    ]
    if not apk_assets:
        print("No APK asset found in latest release", file=sys.stderr)
        sys.exit(1)
    asset = apk_assets[0]

    download_headers = {
        "Accept": "application/octet-stream",
        "User-Agent": USER_AGENT,
    }
    token = os.environ.get("GITHUB_TOKEN", "")
    if token:
        download_headers["Authorization"] = "Bearer " + token
    download_request = urllib.request.Request(
        asset["browser_download_url"],
        headers=download_headers,
    )
    with urllib.request.urlopen(download_request, timeout=120) as response:
        with open(args.output_apk, "wb") as f:
            f.write(response.read())

    info = {
        "tag": release.get("tag_name", ""),
        "body": release.get("body", "") or "",
        "assetName": asset["name"],
        "size": asset.get("size", 0),
    }
    with open(args.output_info, "w", encoding="utf-8") as f:
        json.dump(info, f, ensure_ascii=False, indent=2)
        f.write("\n")
    with open(args.output_body, "w", encoding="utf-8") as f:
        f.write(info["body"])


if __name__ == "__main__":
    main()
