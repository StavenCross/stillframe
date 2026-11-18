#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17 or later}"
export PATH="$JAVA_HOME/bin:$PATH"
sdk="$ANDROID_SDK_ROOT"
tools="$sdk/build-tools/35.0.0"
platform="$sdk/platforms/android-35/android.jar"
mkdir -p build
scratch=$(mktemp -d "$PWD/build/stage.XXXXXX")
mkdir "$scratch/classes" "$scratch/dex" "$scratch/compiled" "$scratch/generated"
trap 'find "$scratch" -depth -delete' EXIT
mapfile -t sources < <(find src/main/java -name '*.java' -print | sort)
"$tools/aapt2" compile --dir src/main/res -o "$scratch/compiled"
link_flags=()
if [[ ${STILLFRAME_DEBUG:-1} == 1 ]]; then link_flags+=(--debug-mode); fi
"$tools/aapt2" link -I "$platform" --manifest src/main/AndroidManifest.xml \
    "${link_flags[@]}" --java "$scratch/generated" -A src/main/assets -o "$scratch/resources.apk" "$scratch"/compiled/*.flat
javac --release 11 -cp "$platform" -d "$scratch/classes" "${sources[@]}" "$scratch/generated/dev/stillframe/R.java"
jar cf "$scratch/classes.jar" -C "$scratch/classes" .
"$tools/d8" --lib "$platform" --min-api 28 --output "$scratch/dex" "$scratch/classes.jar"
(cd "$scratch/dex" && zip -q ../resources.apk ./*.dex)
"$tools/zipalign" -f 4 "$scratch/resources.apk" "$scratch/aligned.apk"
if [[ ! -f build/dev.keystore ]]; then
    keytool -genkeypair -keystore build/dev.keystore -storepass android -keypass android \
        -alias stillframe-dev -dname 'CN=Stillframe Development' -keyalg RSA -validity 3650
fi
"$tools/apksigner" sign --ks build/dev.keystore --ks-pass pass:android \
    --out build/stillframe-debug.apk "$scratch/aligned.apk"
"$tools/apksigner" verify build/stillframe-debug.apk
