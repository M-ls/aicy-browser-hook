#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8
BUILD_DIR=/build
OUTPUT_DIR=/out
SIGNING_MODE="${SIGNING_MODE:-local}"
case "$SIGNING_MODE" in local|unsigned) ;; *) echo "SIGNING_MODE must be local or unsigned" >&2; exit 2 ;; esac
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
if [[ ! -f "$BUILD_DIR/signing.jks" ]]; then
    keytool -genkeypair -keystore "$BUILD_DIR/signing.jks" -storepass android -keypass android -alias aicy-local -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Aicy Browser Hook Local Development'
fi
PATH=/opt/jre21/bin:$PATH "$BUILD_TOOLS/apksigner" sign --v4-signing-enabled false --ks "$BUILD_DIR/signing.jks" --ks-pass pass:android --ks-key-alias aicy-local --out "$APK_PATH" "$BUILD_DIR/aligned.apk"
PATH=/opt/jre21/bin:$PATH "$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$APK_PATH"
fi
"$BUILD_TOOLS/aapt" dump badging "$APK_PATH"
# Pure JVM filter checks and actual Android Intent preservation checks are separate.
javac -encoding UTF-8 -d "$BUILD_DIR/tests" /project/src/local/aicy/browserhook/RoutingPolicy.java /project/src/local/aicy/browserhook/BrowserEntryScope.java /project/tests/PolicyTest.java
java -cp "$BUILD_DIR/tests" PolicyTest
javac -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID_JAR:$BUILD_DIR/classes" -d "$BUILD_DIR/tests" /project/tests/AndroidIntentTest.java
jar cf "$BUILD_DIR/intent-tests-classes.jar" -C "$BUILD_DIR/classes" local/aicy/browserhook/IntentAdapter.class -C "$BUILD_DIR/classes" local/aicy/browserhook/BrowserResolver.class -C "$BUILD_DIR/classes" local/aicy/browserhook/RoutingPolicy.class -C "$BUILD_DIR/tests" local/aicy/browserhook/AndroidIntentTest.class
PATH=/opt/jre21/bin:$PATH "$BUILD_TOOLS/d8" --min-api 30 --lib "$ANDROID_JAR" --output "$BUILD_DIR/testdex" "$BUILD_DIR/intent-tests-classes.jar"
(cd "$BUILD_DIR/testdex" && jar cf "$BUILD_DIR/intent-tests.jar" classes.dex)
sha256sum "$APK_PATH"
