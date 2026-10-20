#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8
BUILD_DIR=/build
OUTPUT_DIR=/out
SIGNING_MODE="${SIGNING_MODE:-local}"
BUILD_VARIANT="${BUILD_VARIANT:-registered}"
case "$SIGNING_MODE" in local|unsigned|release) ;; *) echo "SIGNING_MODE must be local, unsigned or release" >&2; exit 2 ;; esac
case "$BUILD_VARIANT" in registered|legacy) ;; *) echo "BUILD_VARIANT must be registered or legacy" >&2; exit 2 ;; esac
if [[ "$SIGNING_MODE" == release ]]; then
    [[ -f /signing/release.jks ]] || { echo "Release signing key is missing" >&2; exit 2; }
    : "${SIGNING_STORE_PASSWORD:?Required for release signing}"
    : "${SIGNING_KEY_PASSWORD:?Required for release signing}"
    : "${SIGNING_KEY_ALIAS:?Required for release signing}"
    : "${SIGNING_CERT_SHA256:?Required for release signing}"
fi
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/java" "$OUTPUT_DIR"
PROJECT_DIR=/project
APK_BASE=AicyBrowserHook
EXPECTED_PACKAGE=io.github.m_ls.aicybrowserhook
if [[ "$BUILD_VARIANT" == legacy ]]; then
    PROJECT_DIR="$BUILD_DIR/legacy-project"
    rm -rf "$PROJECT_DIR"
    mkdir -p "$PROJECT_DIR"
    cp /project/AndroidManifest.xml "$PROJECT_DIR/"
    cp -a /project/src /project/res /project/assets "$PROJECT_DIR/"
    sed -i 's/io\.github\.m_ls\.aicybrowserhook/local.aicy.browserhook/g' \
        "$PROJECT_DIR/AndroidManifest.xml" \
        "$PROJECT_DIR/src/local/aicy/browserhook/ConfigProvider.java" \
        "$PROJECT_DIR/src/local/aicy/browserhook/SettingsActivity.java"
    APK_BASE=AicyBrowserHook-legacy
    EXPECTED_PACKAGE=local.aicy.browserhook
fi
find "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/java" -type f -delete
# Compile against Android SDK; D8 desugars the Java 8 callbacks for Android.
"$BUILD_TOOLS/aapt" package -f -M "$PROJECT_DIR/AndroidManifest.xml" -S "$PROJECT_DIR/res" -A "$PROJECT_DIR/assets" -I "$ANDROID_JAR" -J "$BUILD_DIR/java" -F "$BUILD_DIR/resources.apk"
find "$PROJECT_DIR/src" "$BUILD_DIR/java" -name '*.java' > "$BUILD_DIR/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID_JAR:/opt/xposed/api.jar" -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt"
jar cf "$BUILD_DIR/classes.jar" -C "$BUILD_DIR/classes" .
PATH=/opt/jre21/bin:$PATH "$BUILD_TOOLS/d8" --min-api 30 --lib "$ANDROID_JAR" --classpath /opt/xposed/api.jar --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar"
cp "$BUILD_DIR/resources.apk" "$BUILD_DIR/unsigned.apk"
(cd "$BUILD_DIR/dex" && "$BUILD_TOOLS/aapt" add "$BUILD_DIR/unsigned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"
if [[ "$SIGNING_MODE" == unsigned ]]; then
    APK_PATH="$OUTPUT_DIR/$APK_BASE-unsigned.apk"
    cp "$BUILD_DIR/aligned.apk" "$APK_PATH"
    "$BUILD_TOOLS/zipalign" -c 4 "$APK_PATH"
else
    APK_PATH="$OUTPUT_DIR/$APK_BASE.apk"
    if [[ "$SIGNING_MODE" == release ]]; then
        PATH=/opt/jre21/bin:$PATH "$BUILD_TOOLS/apksigner" sign --v4-signing-enabled false --ks /signing/release.jks --ks-pass env:SIGNING_STORE_PASSWORD --key-pass env:SIGNING_KEY_PASSWORD --ks-key-alias "$SIGNING_KEY_ALIAS" --out "$APK_PATH" "$BUILD_DIR/aligned.apk"
    else
        if [[ ! -f "$BUILD_DIR/signing.jks" ]]; then
            keytool -genkeypair -keystore "$BUILD_DIR/signing.jks" -storepass android -keypass android -alias aicy-local -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Aicy Browser Hook Local Development'
        fi
        PATH=/opt/jre21/bin:$PATH "$BUILD_TOOLS/apksigner" sign --v4-signing-enabled false --ks "$BUILD_DIR/signing.jks" --ks-pass pass:android --ks-key-alias aicy-local --out "$APK_PATH" "$BUILD_DIR/aligned.apk"
    fi
    PATH=/opt/jre21/bin:$PATH "$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$APK_PATH" | tee "$BUILD_DIR/signature.txt"
    if [[ "$SIGNING_MODE" == release ]]; then
        actual_cert=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$BUILD_DIR/signature.txt")
        [[ "$actual_cert" == "$SIGNING_CERT_SHA256" ]] || { echo "Release signing certificate does not match the existing APK" >&2; exit 3; }
    fi
fi
badging=$("$BUILD_TOOLS/aapt" dump badging "$APK_PATH")
[[ "$badging" == "package: name='$EXPECTED_PACKAGE' "* ]] || { echo "APK package does not match $BUILD_VARIANT variant" >&2; exit 4; }
printf '%s\n' "$badging"
sha256sum "$APK_PATH"
