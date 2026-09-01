#!/bin/sh
set -eu

APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.9
GRADLE_CACHE_ROOT="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-${GRADLE_VERSION}-bin/expense-notebook"
GRADLE_EXE="$GRADLE_CACHE_ROOT/gradle-${GRADLE_VERSION}/bin/gradle"
ARCHIVE="$GRADLE_CACHE_ROOT/gradle-${GRADLE_VERSION}-bin.zip"

if [ ! -x "$GRADLE_EXE" ]; then
    mkdir -p "$GRADLE_CACHE_ROOT"
    if [ ! -f "$ARCHIVE" ]; then
        URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
        if command -v curl >/dev/null 2>&1; then
            curl -fL "$URL" -o "$ARCHIVE"
        elif command -v wget >/dev/null 2>&1; then
            wget -O "$ARCHIVE" "$URL"
        else
            echo "Install curl or wget so the Gradle distribution can be downloaded." >&2
            exit 1
        fi
    fi
    command -v unzip >/dev/null 2>&1 || {
        echo "Install unzip, then run this command again." >&2
        exit 1
    }
    unzip -q -o "$ARCHIVE" -d "$GRADLE_CACHE_ROOT"
fi

exec "$GRADLE_EXE" -p "$APP_DIR" "$@"
