#!/bin/bash
# Pre-delivery UI verification on the JVM (docs/UI_VERIFICATION.md).
#
# Same VM conventions as build.sh (local mirror, IPv4, in-process
# Gradle 8.9), except GRADLE_USER_HOME is project-local: the shared
# gradle home's journal cache serialises every app chat's builds in
# this VM, and test runs timed out on that lock repeatedly during the
# 2026-11-23 cross-project rollout. Dependencies still resolve
# through the same local mirror (disk-cached), so a fresh home is
# cheap. The robolectric android-all jar lives in
# .local/robolectric-deps/ (see docs/UI_VERIFICATION.md).
#
# The gradle invocation itself goes through the VM-wide build lock
# (AGENTS.md "Build serialization rule", 2026-11-23): only one build
# runs at a time on this VM.
#
# Usage:
#   ./verify-ui.sh          verify screens against the goldens
#   ./verify-ui.sh record   re-record the goldens
#   ./verify-ui.sh all      run the whole unit-test suite (no recording)
set -e
cd "$(dirname "$0")"
export MOCHIMORI_LOCAL_MIRROR=1
export JAVA_HOME=~/workspace/tools/jdk21
export ANDROID_HOME=~/workspace/tools/android-sdk
export GRADLE_USER_HOME="$PWD/.local/gradle-home"
export GRADLE_OPTS="-Xmx640m -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true"
if ! curl -s -o /dev/null --max-time 3 http://127.0.0.1:18080/central/junit/junit/4.13.2/junit-4.13.2.pom; then
  (cd ~/workspace/tools && nohup python3 mirror.py > /tmp/mirror.log 2>&1 &)
  sleep 2
fi
GRADLE=(~/workspace/tools/gradle-8.9/bin/gradle :app:testDebugUnitTest
  -Dorg.gradle.daemon=false -Dkotlin.daemon.enabled=false --max-workers=1)
MODE="${1:-verify}"
case "$MODE" in
  record)
    ROBORAZZI_RECORD=1 ~/workspace/tools/with-build-lock.sh "${GRADLE[@]}" \
      --tests "com.taigashinohara.mochimori.MainActivityScreenTest"
    ;;
  all)
    ~/workspace/tools/with-build-lock.sh "${GRADLE[@]}"
    ;;
  *)
    ~/workspace/tools/with-build-lock.sh "${GRADLE[@]}" \
      --tests "com.taigashinohara.mochimori.MainActivityScreenTest"
    ;;
esac
