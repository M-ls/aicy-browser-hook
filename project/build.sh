#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8
BUILD_DIR=/build
OUTPUT_DIR=/out
SIGNING_MODE="${SIGNING_MODE:-local}"
case "$SIGNING_MODE" in local|unsigned|release) ;; *) echo "SIGNING_MODE must be local, unsigned or release" >&2; exit 2 ;; esac
if [[ "$SIGNING_MODE" == release ]]; then
    [[ -f /signing/release.jks ]] || { echo "Release signing key is missing" >&2; exit 2; }
    : "${SIGNING_STORE_PASSWORD:?Required for release signing}"
    : "${SIGNING_KEY_PASSWORD:?Required for release signing}"
    : "${SIGNING_KEY_ALIAS:?Required for release signing}"
    : "${SIGNING_CERT_SHA256:?Required for release signing}"
fi
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/tests" "$BUILD_DIR/testdex" "$BUILD_DIR/java" "$OUTPUT_DIR"
find "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/tests" "$BUILD_DIR/testdex" "$BUILD_DIR/java" -type f -delete
# Compile against Android SDK; D8 desugars the Java 8 callbacks for Android.
"$BUILD_TOOLS/aapt" package -f -M /project/AndroidManifest.xml -S /project/res -A /project/assets -I "$ANDROID_JAR" -J "$BUILD_DIR/java" -F "$BUILD_DIR/resources.apk"
find /project/src "$BUILD_DIR/java" -name '*.java' > "$BUILD_DIR/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID_JAR:/opt/xposed/api.jar" -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt"
jar cf "$BUILD_DIR/classes.jar" -C "$BUILD_DIR/classes" .
PATH=/opt/jre21/bin:$PATH "$BUILD_TOOLS/d8" --min-api 30 --lib "$ANDROID_JAR" --classpath /opt/xposed/api.jar --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar"
cp "$BUILD_DIR/resources.apk" "$BUILD_DIR/unsigned.apk"
(cd "$BUILD_DIR/dex" && "$BUILD_TOOLS/aapt" add "$BUILD_DIR/unsigned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"
if [[ "$SIGNING_MODE" == unsigned ]]; then
    APK_PATH="$OUTPUT_DIR/AicyBrowserHook-unsigned.apk"
    cp "$BUILD_DIR/aligned.apk" "$APK_PATH"
    "$BUILD_TOOLS/zipalign" -c 4 "$APK_PATH"
else
    APK_PATH="$OUTPUT_DIR/AicyBrowserHook.apk"
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
"$BUILD_TOOLS/aapt" dump badging "$APK_PATH"
# Pure JVM filter checks and actual Android Intent preservation checks are separate.
javac -encoding UTF-8 -d "$BUILD_DIR/tests" /project/src/local/aicy/browserhook/BrowserChoices.java /project/src/local/aicy/browserhook/RoutingPolicy.java /project/src/local/aicy/browserhook/BrowserEntryScope.java /project/src/local/aicy/browserhook/SearchEngines.java /project/tests/PolicyTest.java /project/tests/SearchEnginesTest.java
java -cp "$BUILD_DIR/tests" PolicyTest
java -cp "$BUILD_DIR/tests" SearchEnginesTest
javac -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID_JAR:$BUILD_DIR/classes" -d "$BUILD_DIR/tests" /project/tests/AndroidIntentTest.java
jar cf "$BUILD_DIR/intent-tests-classes.jar" -C "$BUILD_DIR/classes" local/aicy/browserhook/IntentAdapter.class -C "$BUILD_DIR/classes" local/aicy/browserhook/BrowserResolver.class -C "$BUILD_DIR/classes" local/aicy/browserhook/RoutingPolicy.class -C "$BUILD_DIR/classes" local/aicy/browserhook/BrowserChoices.class -C "$BUILD_DIR/classes" local/aicy/browserhook/BrowserValidator.class -C "$BUILD_DIR/tests" local/aicy/browserhook/AndroidIntentTest.class
PATH=/opt/jre21/bin:$PATH "$BUILD_TOOLS/d8" --min-api 30 --lib "$ANDROID_JAR" --output "$BUILD_DIR/testdex" "$BUILD_DIR/intent-tests-classes.jar"
(cd "$BUILD_DIR/testdex" && jar cf "$BUILD_DIR/intent-tests.jar" classes.dex)
sha256sum "$APK_PATH"
