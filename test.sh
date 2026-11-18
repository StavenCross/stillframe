#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17 or later}"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
output=$(mktemp -d)
trap 'find "$output" -depth -delete' EXIT
platform="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
tools="$ANDROID_SDK_ROOT/build-tools/35.0.0"
mkdir -p "$output/compiled" "$output/generated"
"$tools/aapt2" compile --dir src/main/res -o "$output/compiled"
"$tools/aapt2" link -I "$platform" --manifest src/main/AndroidManifest.xml     --java "$output/generated" -o "$output/resources.apk" "$output"/compiled/*.flat
mapfile -t sources < <(find src/main/java tests -name '*.java' -print | sort)
"$JAVA_HOME/bin/javac" --release 11 -cp "$platform" -d "$output" "${sources[@]}" "$output/generated/dev/stillframe/R.java"
for suite in LauncherPolicyTest PackMappingTest GlassPixelsTest WatchFeedTest HidivePicksTest WatchPrefetchTest DrawerFocusTest DrawerActivationTest DialogLayoutTest AmbienceWeatherTest TmdbMatchingTest; do
    "$JAVA_HOME/bin/java" -cp "$output:$platform" "dev.stillframe.$suite"
done
