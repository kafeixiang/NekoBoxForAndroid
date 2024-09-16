#!/bin/bash

source ../buildScript/init/env_ndk.sh

gomobile bind -v -androidapi 21 -trimpath -buildvcs=false -ldflags='-s -w -buildid=' -tags='with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,with_ech' . || exit 1
rm -r libcore-sources.jar

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
