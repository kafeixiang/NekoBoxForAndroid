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

sb_dir=""
for candidate in "../sing-box" "../../sing-box"; do
  if [ -d "$candidate" ]; then
    sb_dir="$candidate"
    break
  fi
done

if [ -n "$sb_dir" ]; then
  changelog_version=$(grep -E '^#+ +[0-9]+\.[0-9]+' "$sb_dir/docs/changelog.md" 2>/dev/null | head -n 1 | sed -E 's/^#+ +//; s/ .*//')
  commit_hash=$(git -C "$sb_dir" rev-parse --short HEAD 2>/dev/null || true)
fi

if [ -n "$changelog_version" ] && [ -n "$commit_hash" ]; then
  box_version="${changelog_version} (${commit_hash})"
elif [ -n "$changelog_version" ]; then
  box_version="${changelog_version}"
elif [ -n "$commit_hash" ]; then
  box_version="${commit_hash}"
else
  box_version="unknown"
fi

export GOBIND=gobind-matsuri

# Build each ABI sequentially to avoid runner memory exhaustion and linker concurrency failures on CI
TARGETS=("android/arm64" "android/arm" "android/amd64" "android/386")
AARS=()

for target in "${TARGETS[@]}"; do
  arch="${target#android/}"
  out_aar="$BUILD/libcore-${arch}.aar"
  echo ">> [$(date +'%T')] Building $target -> $out_aar..."
  free -h 2>/dev/null || true
  df -h / /mnt 2>/dev/null || true
  "$GOPATH"/bin/gomobile-matsuri bind -x -v -target "$target" -androidapi 24 -cache "$(realpath $BUILD)" -trimpath -ldflags="-s -w -X 'github.com/sagernet/sing-box/constant.Version=${box_version}'" -tags='with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,with_openvpn,with_openconnect,with_tailscale,with_acme,with_naive_outbound' -o "$out_aar" . || exit 1
  rm -f "$BUILD/libcore-${arch}-sources.jar" libcore-sources.jar 2>/dev/null || true
  AARS+=("$out_aar")
done

echo ">> Merging AARs into libcore.aar..."
python3 - "${AARS[@]}" << 'EOF'
import sys, zipfile

if len(sys.argv) < 2:
    sys.exit(1)

primary_aar = sys.argv[1]
other_aars = sys.argv[2:]

with zipfile.ZipFile("libcore.aar", "w", zipfile.ZIP_DEFLATED) as out_zip:
    with zipfile.ZipFile(primary_aar, "r") as z1:
        for item in z1.infolist():
            out_zip.writestr(item, z1.read(item.filename))
    for aar in other_aars:
        with zipfile.ZipFile(aar, "r") as z:
            for item in z.infolist():
                if item.filename.startswith("jni/"):
                    out_zip.writestr(item, z.read(item.filename))

print("Successfully merged into libcore.aar")
EOF

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
