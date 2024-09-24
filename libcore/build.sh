#!/bin/bash

source ../buildScript/init/env_ndk.sh

VERSION=$(curl --silent "https://api.github.com/repos/sagernet/sing-box/releases" | jq -r 'sort_by(.published_at) | .[-1].tag_name')
gomobile bind -v -androidapi 21 -trimpath -buildvcs=false -ldflags="-X github.com/sagernet/sing-box/constant.Version=${VERSION} -s -w -buildid=" -tags="with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,with_ech,with_shadowsocksr" . || exit 1
rm -r libcore-sources.jar

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
