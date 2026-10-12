FROM eclipse-temurin:21-jre@sha256:cff19e6215689161eb6162c11b86b0c60ddf802164f2eaf48d570f8fb79a36c5 AS runtime
FROM maven:3.9-eclipse-temurin-8@sha256:b595d84453cef0567168b65c0566743114a6a47d2ad7e7a712a20cf4f4555fd4
COPY --from=runtime /opt/java/openjdk /opt/jre21
RUN mkdir -p /opt/android /opt/xposed
COPY --from=sdkcache /platform-35_r02.zip /tmp/platform.zip
COPY --from=sdkcache /build-tools_r35_linux.zip /tmp/tools.zip
COPY --from=sdkcache /xposed-api-82.jar /opt/xposed/api.jar
RUN echo '0bb560a90a7a2cbd0dd8348224d518b638fe7949  /tmp/platform.zip' | sha1sum -c - \
    && echo '2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc  /tmp/tools.zip' | sha1sum -c - \
    && cd /opt/android && jar xf /tmp/platform.zip && jar xf /tmp/tools.zip \
    && chmod +x /opt/android/android-15/aapt /opt/android/android-15/d8 /opt/android/android-15/apksigner /opt/android/android-15/zipalign \
    && rm /tmp/platform.zip /tmp/tools.zip
RUN echo 'f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25  /opt/xposed/api.jar' | sha256sum -c -
ENV ANDROID_JAR=/opt/android/android-35/android.jar
ENV BUILD_TOOLS=/opt/android/android-15
WORKDIR /project
ENTRYPOINT ["bash", "/project/build.sh"]
