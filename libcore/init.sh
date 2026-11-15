#!/bin/bash

chmod -R 777 .build 2>/dev/null
rm -rf .build 2>/dev/null

if [ -z "$GOPATH" ]; then
    GOPATH=$(go env GOPATH)
fi

# Install gomobile
if [ ! -f "$GOPATH/bin/gomobile-matsuri" ]; then
    git clone https://github.com/MatsuriDayo/gomobile.git
    pushd gomobile
	git checkout origin/master2
    python3 -c '
path = "cmd/gomobile/init.go"
with open(path, "r") as f:
    s = f.read()
s = s.replace("cmd.Stdout = os.Stdout\n\t\tcmd.Stderr = os.Stderr", "cmd.Stdout = io.MultiWriter(os.Stdout, buf)\n\t\tcmd.Stderr = io.MultiWriter(os.Stderr, buf)")
s = s.replace("return fmt.Errorf(\"%s failed: %v%s\", strings.Join(cmd.Args, \" \"), err, buf)", "return fmt.Errorf(\"%s failed: %v\\n--- COMMAND OUTPUT ---\\n%s\\n--- END OUTPUT ---\", strings.Join(cmd.Args, \" \"), err, buf)")
with open(path, "w") as f:
    f.write(s)
'
    pushd cmd
    pushd gomobile
    go install -v
    popd
    pushd gobind
    go install -v
    popd
    popd
    rm -rf gomobile
    mv "$GOPATH/bin/gomobile" "$GOPATH/bin/gomobile-matsuri"
    mv "$GOPATH/bin/gobind" "$GOPATH/bin/gobind-matsuri"
fi

GOBIND=gobind-matsuri gomobile-matsuri init
