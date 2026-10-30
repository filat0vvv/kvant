#!/usr/bin/env bash
set -euo pipefail
cd "${1:?Pass the source checkout}/TMessagesProj/jni"
: "${NDK:?Set NDK}" "${NINJA_PATH:?Set NINJA_PATH}"

test -x "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"
test -x "$NINJA_PATH"
for dependency in libvpx ffmpeg boringssl libwebp; do
  test -n "$(ls -A "$dependency")"
done

# Follow Telegram-FOSS's documented native build order, restricted to arm64.
bash build_libvpx_clang.sh arm64
bash build_ffmpeg_clang.sh arm64
bash patch_ffmpeg.sh
bash patch_boringssl.sh
bash build_boringssl.sh arm64

for archive in \
  libvpx/build/arm64-v8a/lib/libvpx.a \
  ffmpeg/build/arm64-v8a/lib/libswscale.a \
  ffmpeg/build/arm64-v8a/lib/libavformat.a \
  ffmpeg/build/arm64-v8a/lib/libavcodec.a \
  ffmpeg/build/arm64-v8a/lib/libavresample.a \
  ffmpeg/build/arm64-v8a/lib/libavutil.a \
  ffmpeg/build/arm64-v8a/lib/libswresample.a \
  boringssl/build/arm64-v8a/ssl/libssl.a \
  boringssl/build/arm64-v8a/crypto/libcrypto.a; do
  test -s "$archive" || { echo "Missing native archive: $archive" >&2; exit 1; }
done
