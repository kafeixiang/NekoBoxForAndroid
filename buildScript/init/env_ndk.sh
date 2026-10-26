#!/bin/bash

if [ -z "$ANDROID_HOME" ]; then
  if [ -d "$HOME/Android/Sdk" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
  elif [ -d "$HOME/.local/lib/android/sdk" ]; then
    export ANDROID_HOME="$HOME/.local/lib/android/sdk"
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
  fi
fi

if [ -n "$ANDROID_NDK_HOME" ] && [ -f "$ANDROID_NDK_HOME/source.properties" ]; then
  _NDK="$ANDROID_NDK_HOME"
elif [ -n "$NDK" ] && [ -f "$NDK/source.properties" ]; then
  _NDK="$NDK"
elif [ -d "$ANDROID_HOME/ndk/28.2.13676358" ]; then
  _NDK="$ANDROID_HOME/ndk/28.2.13676358"
elif [ -d "$ANDROID_HOME/ndk/25.0.8775105" ]; then
  _NDK="$ANDROID_HOME/ndk/25.0.8775105"
else
  _NDK="$(ls -d "$ANDROID_HOME/ndk/"* 2>/dev/null | sort -V | tail -n 1)"
fi
[ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_HOME/ndk-bundle"

if [ ! -f "$_NDK/source.properties" ]; then
  echo "Error: NDK not found."
  exit 1
fi

export ANDROID_NDK_HOME=$_NDK
export NDK=$_NDK
