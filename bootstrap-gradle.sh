#!/usr/bin/env bash
# One-time setup; creates the official Gradle wrapper.
set -euo pipefail
cd "$(dirname "$0")"
if command -v gradle >/dev/null 2>&1; then
  gradle wrapper --gradle-version 8.9 --distribution-type bin
else
  printf '%s\n' 'Install Gradle 8.9 or run the included GitHub Actions workflow.'
  exit 1
fi
