#!/bin/bash

# Install gomobile
if [ ! -f "$GOPATH/bin/gomobile" ]; then
    go install -v github.com/sagernet/gomobile/cmd/gomobile
    go install -v github.com/sagernet/gomobile/cmd/gobind
fi

gomobile init
