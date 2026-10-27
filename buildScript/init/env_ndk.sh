#!/bin/bash

if [ -z "$ANDROID_HOME" ]; then
  if [ -d "$HOME/Android/Sdk" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
  elif [ -d "$HOME/.local/lib/android/sdk" ]; then
    export ANDROID_HOME="$HOME/.local/lib/android/sdk"
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
  elif [ -d "/usr/local/lib/android/sdk" ]; then
    export ANDROID_HOME="/usr/local/lib/android/sdk"
  fi
fi

# Prefer NDK >= 28 (e.g. 29.x, 28.x) because newer prebuilts (like libcronet.a) require LLVM 19+ linker
# to support ARM64 relocation 315 (R_AARCH64_AUTH_ABS64). NDK 27 and older fail with "unknown relocation (315)".
_NDK=""
for candidate in \
  $(ls -d "$ANDROID_HOME/ndk/29"* 2>/dev/null | sort -V | tail -n 1) \
  $(ls -d "$ANDROID_HOME/ndk/28"* 2>/dev/null | sort -V | tail -n 1) \
  $(ls -d "$ANDROID_HOME/ndk/30"* 2>/dev/null | sort -V | tail -n 1) \
  "$ANDROID_NDK_LATEST_HOME"; do
  if [ -n "$candidate" ] && [ -f "$candidate/source.properties" ]; then
    _NDK="$candidate"
    break
  fi
done

if [ -z "$_NDK" ]; then
  if [ -n "$ANDROID_NDK_HOME" ] && [ -f "$ANDROID_NDK_HOME/source.properties" ]; then
    _NDK="$ANDROID_NDK_HOME"
  elif [ -n "$NDK" ] && [ -f "$NDK/source.properties" ]; then
    _NDK="$NDK"
  elif [ -d "$ANDROID_HOME/ndk/25.0.8775105" ]; then
    _NDK="$ANDROID_HOME/ndk/25.0.8775105"
  else
    _NDK="$(ls -d "$ANDROID_HOME/ndk/"* 2>/dev/null | sort -V | tail -n 1)"
  fi
fi
[ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_HOME/ndk-bundle"

if [ ! -f "$_NDK/source.properties" ]; then
  echo "Error: NDK not found."
  exit 1
fi

echo ">> using NDK: $_NDK"
export ANDROID_NDK_HOME=$_NDK
export NDK=$_NDK
