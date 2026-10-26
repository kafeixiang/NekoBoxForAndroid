#!/bin/bash

source ./env_java.sh || true
source ../buildScript/init/env_ndk.sh

BUILD=".build"

mkdir -p "$BUILD"
rm -rf $BUILD/android \
  $BUILD/java \
  $BUILD/javac-output \
  $BUILD/src

if [ -z "$GOPATH" ]; then
  GOPATH=$(go env GOPATH)
fi

changelog_version=$(grep -E '^#+ +[0-9]+\.[0-9]+' ../sing-box/docs/changelog.md 2>/dev/null | head -n 1 | sed -E 's/^#+ +//; s/ .*//')
commit_hash=$(git -C ../sing-box rev-parse --short HEAD 2>/dev/null || true)

if [ -n "$changelog_version" ] && [ -n "$commit_hash" ]; then
  box_version="${changelog_version} (${commit_hash})"
elif [ -n "$changelog_version" ]; then
  box_version="${changelog_version}"
elif [ -n "$commit_hash" ]; then
  box_version="${commit_hash}"
else
  box_version="$(cd ../sing-box 2>/dev/null && git describe --tags --always 2>/dev/null || echo "unknown")"
fi

export GOBIND=gobind-matsuri
"$GOPATH"/bin/gomobile-matsuri bind -v -androidapi 21 -cache "$(realpath $BUILD)" -trimpath -ldflags="-s -w -X 'github.com/sagernet/sing-box/constant.Version=${box_version}'" -tags='with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,with_openvpn,with_openconnect,with_tailscale,with_acme' . || exit 1
rm -r libcore-sources.jar

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
