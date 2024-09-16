#!/bin/bash
set -e

source "buildScript/init/env.sh"
ENV_NB4A=1
source "buildScript/lib/core/get_source_env.sh"
pushd ..

######
## From nekoray/libs/get_source.sh
######

####

if [ ! -d "sing-box" ]; then
  git clone https://github.com/Restia-Ashbell/sing-box
fi

####

popd
