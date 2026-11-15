#!/bin/bash

chmod -R 777 .build 2>/dev/null
rm -rf .build 2>/dev/null

export CGO_CFLAGS_ALLOW=".*"
export CGO_CPPFLAGS_ALLOW=".*"
export CGO_CXXFLAGS_ALLOW=".*"
export CGO_LDFLAGS_ALLOW=".*"

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

path_env = "cmd/gomobile/env.go"
with open(path_env, "r") as f:
    s_env = f.read()
s_env = s_env.replace("\"CGO_ENABLED=1\",", "\"CGO_ENABLED=1\",\n\t\t\t\t\"CGO_CFLAGS_ALLOW=.*\",\n\t\t\t\t\"CGO_CPPFLAGS_ALLOW=.*\",\n\t\t\t\t\"CGO_CXXFLAGS_ALLOW=.*\",\n\t\t\t\t\"CGO_LDFLAGS_ALLOW=.*\",")
with open(path_env, "w") as f:
    f.write(s_env)
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
