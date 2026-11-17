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
MARKER="$GOPATH/bin/.gomobile-matsuri-v3"
if [ ! -f "$GOPATH/bin/gomobile-matsuri" ] || [ ! -f "$MARKER" ]; then
    rm -rf gomobile "$GOPATH/bin/gomobile-matsuri" "$GOPATH/bin/gobind-matsuri" "$MARKER" 2>/dev/null || true
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

path_bind = "cmd/gomobile/bind.go"
with open(path_bind, "r") as f:
    s_bind = f.read()
old_bind = """\treturn writeFile(filepath.Join(dir, "go.mod"), func(w io.Writer) error {
\t\tf, err := getModuleVersions(targetPlatform, targetArch, ".")
\t\tif err != nil {
\t\t\treturn err
\t\t}
\t\tif f == nil {
\t\t\treturn nil
\t\t}
\t\tbs, err := f.Format()
\t\tif err != nil {
\t\t\treturn err
\t\t}
\t\tif _, err := w.Write(bs); err != nil {
\t\t\treturn err
\t\t}
\t\treturn nil
\t})"""
new_bind = """\tif err := writeFile(filepath.Join(dir, "go.mod"), func(w io.Writer) error {
\t\tf, err := getModuleVersions(targetPlatform, targetArch, ".")
\t\tif err != nil {
\t\t\treturn err
\t\t}
\t\tif f == nil {
\t\t\treturn nil
\t\t}
\t\tbs, err := f.Format()
\t\tif err != nil {
\t\t\treturn err
\t\t}
\t\tif _, err := w.Write(bs); err != nil {
\t\t\treturn err
\t\t}
\t\treturn nil
\t}); err != nil {
\t\treturn err
\t}
\tif data, err := os.ReadFile("go.sum"); err == nil {
\t\t_ = os.WriteFile(filepath.Join(dir, "go.sum"), data, 0644)
\t} else if out, err := exec.Command("go", "env", "GOMOD").Output(); err == nil {
\t\tgoModPath := strings.TrimSpace(string(out))
\t\tif goModPath != "" && goModPath != os.DevNull {
\t\t\tif sumData, err := os.ReadFile(filepath.Join(filepath.Dir(goModPath), "go.sum")); err == nil {
\t\t\t\t_ = os.WriteFile(filepath.Join(dir, "go.sum"), sumData, 0644)
\t\t\t}
\t\t}
\t}
\treturn nil"""
assert old_bind in s_bind, "bind.go target not found"
s_bind = s_bind.replace(old_bind, new_bind)
with open(path_bind, "w") as f:
    f.write(s_bind)

path_build = "cmd/gomobile/build.go"
with open(path_build, "r") as f:
    s_build = f.read()
old_build = """\tif gmc, err := goModCachePath(); err == nil {
\t\tenv = append([]string{"GOMODCACHE=" + gmc}, env...)
\t} else {
\t\tenv = append([]string{}, env...)
\t}
\tcmd.Env = env"""
new_build = """\tif gmc, err := goModCachePath(); err == nil {
\t\tenv = append([]string{"GOMODCACHE=" + gmc}, env...)
\t} else {
\t\tenv = append([]string{}, env...)
\t}
\tfor _, envKey := range []string{"GOPROXY", "GOSUMDB", "GONOPROXY", "GONOSUMDB", "GOPRIVATE", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY"} {
\t\tif val, ok := os.LookupEnv(envKey); ok {
\t\t\tenv = append(env, envKey+"="+val)
\t\t}
\t}
\tcmd.Env = env"""
assert old_build in s_build, "build.go target not found"
s_build = s_build.replace(old_build, new_build)
with open(path_build, "w") as f:
    f.write(s_build)
'
    pushd cmd/gomobile
    go install -v
    popd
    pushd cmd/gobind
    go install -v
    popd
    popd
    rm -rf gomobile
    mv "$GOPATH/bin/gomobile" "$GOPATH/bin/gomobile-matsuri"
    mv "$GOPATH/bin/gobind" "$GOPATH/bin/gobind-matsuri"
    touch "$MARKER"
fi

GOBIND=gobind-matsuri gomobile-matsuri init
