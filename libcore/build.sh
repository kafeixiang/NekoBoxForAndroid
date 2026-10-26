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

box_version="$(cd ../sing-box 2>/dev/null && git describe --tags --always 2>/dev/null || echo "1.15.0-alpha.13-neko")"
if [ -z "$box_version" ]; then
  box_version="1.15.0-alpha.13-neko"
fi

export GOBIND=gobind-matsuri
"$GOPATH"/bin/gomobile-matsuri bind -v -androidapi 21 -cache "$(realpath $BUILD)" -trimpath -ldflags="-s -w -X 'github.com/sagernet/sing-box/constant.Version=${box_version}'" -tags='with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,with_openvpn,with_openconnect,with_tailscale,with_acme' . || exit 1
rm -r libcore-sources.jar

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
