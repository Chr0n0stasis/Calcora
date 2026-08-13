#!/usr/bin/env bash
set -euo pipefail

target_name="$1"
output_dir="$2"
script_dir="$(cd "$(dirname "$0")" && pwd)"

case "$target_name" in
  iosArm64) sdk="iphoneos"; arch="arm64" ;;
  iosSimulatorArm64) sdk="iphonesimulator"; arch="arm64" ;;
  iosX64) sdk="iphonesimulator"; arch="x86_64" ;;
  *) echo "Unknown Kotlin/Native target: $target_name" >&2; exit 2 ;;
esac

build_dir="$output_dir/cmake"
mkdir -p "$build_dir"
cmake -S "$script_dir" -B "$build_dir" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_SYSTEM_NAME=iOS \
  -DCMAKE_OSX_SYSROOT="$(xcrun --sdk "$sdk" --show-sdk-path)" \
  -DCMAKE_OSX_ARCHITECTURES="$arch" \
  -DCMAKE_OSX_DEPLOYMENT_TARGET=16.0 \
  -DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY
cmake --build "$build_dir" --config Release --parallel

calcora_lib="$(find "$build_dir" -name 'libcalcora_ios.a' -print -quit)"
giac_lib="$(find "$build_dir" -name 'libgiac_core.a' -print -quit)"
tommath_lib="$(find "$build_dir" -name 'libtommath.a' -print -quit)"
xcrun libtool -static -o "$output_dir/libcalcora.a" "$calcora_lib" "$giac_lib" "$tommath_lib"
