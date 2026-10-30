#!/bin/sh

# Attempt to find gradle in PATH first
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

# Fallback to gradle-wrapper.jar if present
APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$WRAPPER_JAR" ]; then
    exec java -jar "$WRAPPER_JAR" "$@"
fi

echo "Error: Neither 'gradle' command nor 'gradle-wrapper.jar' was found." >&2
exit 1
