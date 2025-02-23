#!/usr/bin/env bash

source buildScript/init/env.sh
export CGO_ENABLED=1
export GO386=softfloat

source buildScript/init/env_ndk.sh

cd sing-box
gomobile bind -v -androidapi 21 -trimpath -buildvcs=false -ldflags="-X github.com/sagernet/sing-box/constant.Version=$(git rev-parse --short HEAD) -s -w -buildid=" -tags="with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,with_shadowsocksr" ./experimental/libbox || exit 1
rm -r libbox-sources.jar

proj=../app/libs
mkdir -p $proj
cp -f libbox.aar $proj
echo ">> install $(realpath $proj)/libbox.aar"
