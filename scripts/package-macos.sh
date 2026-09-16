#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"

usage() {
  cat <<'HELP'
用法：bash scripts/package-macos.sh [arm64|x64|all|menu] [--check]
  不传架构：打包当前 Mac 的原生版本
  arm64：Apple Silicon 版；x64（或 x86_64）：Intel 64 位版
  all：依次打包两个版本；menu：交互选择版本
  --check：只检查环境，不构建或生成安装包
也可以直接双击项目根目录的「打包Mac.command」。
HELP
}

requested=""
check_only=false
for argument in "$@"; do
  case "$argument" in
    -h|--help) usage; exit 0 ;;
    --check) check_only=true ;;
    arm64|aarch64|x64|x86|x86_64|all|menu)
      if [[ -n "$requested" ]]; then echo '请只指定一个目标架构，或使用 all。' >&2; exit 1; fi
      requested="$argument" ;;
    *) echo "未知参数：$argument" >&2; usage >&2; exit 1 ;;
  esac
done
if [[ "$(uname -s)" != Darwin ]]; then
  echo '请在 macOS 上运行此脚本。' >&2; exit 1
fi
if [[ "$(/usr/sbin/sysctl -n hw.optional.arm64 2>/dev/null || true)" == 1 ]]; then
  host_arch=arm64
else
  host_arch=x86_64
fi
requested="${requested:-$host_arch}"
if [[ "$requested" == menu ]]; then
  printf '\n欢欢 · macOS 打包\n  1) ARM64（Apple Silicon）\n  2) x64（Intel）\n  3) 两个版本\n  0) 退出\n'
  read -r -p '请选择 [1/2/3/0]：' choice || exit 1
  case "$choice" in
    1) requested=arm64 ;;
    2) requested=x86_64 ;;
    3) requested=all ;;
    0) exit 0 ;;
    *) echo '选择无效，请重新运行。' >&2; exit 1 ;;
  esac
fi
case "$requested" in
  arm64|aarch64) targets=(arm64) ;;
  x64|x86|x86_64) targets=(x86_64) ;;
  all) targets=(arm64 x86_64) ;;
esac

# 同时支持解压型 JDK 根目录与 macOS 的 .jdk/Contents/Home 目录。
matching_jdk() {
  local candidate="$1" target="$2" tool
  [[ -n "$candidate" ]] || return 1
  if [[ -d "$candidate/Contents/Home" ]]; then candidate="$candidate/Contents/Home"; fi
  [[ -f "$candidate/release" ]] || return 1
  /usr/bin/grep -Eq '^JAVA_VERSION="17\.' "$candidate/release" || return 1
  /usr/bin/grep -Eq '^OS_NAME="Darwin"' "$candidate/release" || return 1
  for tool in java javac jlink jpackage; do
    [[ -x "$candidate/bin/$tool" ]] || return 1
    /usr/bin/lipo "$candidate/bin/$tool" -verify_arch "$target" >/dev/null 2>&1 || return 1
  done
  (cd "$candidate" && pwd)
}

find_jdk() {
  local target="$1" explicit candidate found
  if [[ "$target" == arm64 ]]; then
    explicit="${HUANHUAN_JDK17_ARM64:-}"
  else
    explicit="${HUANHUAN_JDK17_X64:-}"
  fi
  if [[ -n "$explicit" ]]; then
    matching_jdk "$explicit" "$target" || {
      echo "指定的 JDK 无效：${explicit}；需要 macOS $target JDK 17（含 jlink、jpackage）。" >&2
      return 1
    }
    return
  fi
  for candidate in "${JAVA_HOME:-}" "$project_dir/.tools/jdk17-$target"; do
    if found="$(matching_jdk "$candidate" "$target")"; then printf '%s\n' "$found"; return; fi
  done
  candidate="$(/usr/libexec/java_home -v 17 -a "$target" 2>/dev/null || true)"
  if found="$(matching_jdk "$candidate" "$target")"; then printf '%s\n' "$found"; return; fi
  for candidate in "$HOME"/Library/Java/JavaVirtualMachines/* /Library/Java/JavaVirtualMachines/*; do
    if found="$(matching_jdk "$candidate" "$target")"; then printf '%s\n' "$found"; return; fi
  done
  echo "找不到 macOS $target 版 JDK 17。" >&2
  echo "请将对应 JDK 根目录放到：$project_dir/.tools/jdk17-$target" >&2
  echo '该目录应包含 bin/java，也支持 .jdk 包内的 Contents/Home 结构。' >&2
  return 1
}

# all 先检查两套环境；缺少任意一套时，不开始构建。
jdk_homes=()
for architecture in "${targets[@]}"; do
  if [[ "$architecture" == arm64 && "$host_arch" != arm64 ]]; then
    echo 'Intel Mac 无法运行 ARM64 工具链；请在 Apple Silicon Mac 上打包 ARM64 版。' >&2; exit 1
  fi
  if [[ "$architecture" == x86_64 && "$host_arch" == arm64 ]]; then
    if ! /usr/bin/arch -x86_64 /usr/bin/true 2>/dev/null; then
      echo '打包 Intel 版需要 Rosetta。请先安装 Rosetta 后重试；脚本不会自动修改系统。' >&2; exit 1
    fi
  fi
  jdk_home="$(find_jdk "$architecture")" || exit 1
  /usr/bin/arch "-$architecture" "$jdk_home/bin/java" -version
  jdk_homes+=("$jdk_home")
  printf '环境就绪：%s\nJDK：%s\n' "$architecture" "$jdk_home"
done
if "$check_only"; then echo '环境检查通过，未执行构建或打包。'; exit 0; fi

build_target() (
  # 子 shell 的环境变量不会影响另一架构或系统默认 Java。
  architecture="$1"
  export JAVA_HOME="$2"
  export PATH="$JAVA_HOME/bin:$PATH"
  app_name="欢欢"
  app_version="1.0.1"
  icon_file="$project_dir/packaging/icons/huanhuan.icns"
  if [[ ! -f "$icon_file" ]]; then echo "缺少应用图标：$icon_file" >&2; exit 1; fi
  case "$architecture" in
    arm64) classifier=mac-aarch64; minimum_macos=11.0 ;;
    x86_64) classifier=mac; minimum_macos=10.15.7 ;;
  esac
  echo "开始打包：$architecture"
  /usr/bin/arch "-$architecture" /bin/bash ./mvnw -B clean package

  input_dir="$project_dir/target/package-input"
  runtime_dir="$project_dir/target/package-runtime"
  image_dir="$project_dir/target/package-image"
  mkdir -p "$input_dir/lib" "$input_dir/javafx" "$image_dir" "$project_dir/dist"
  cp target/record-timeliness.jar "$input_dir/"
  cp target/lib/*.jar "$input_dir/lib/"
  cp target/lib/javafx-*"-$classifier.jar" "$input_dir/javafx/"
  find "$input_dir/lib" -name 'javafx-*.jar' -delete

  "$JAVA_HOME/bin/jlink" --add-modules java.se,jdk.unsupported,jdk.crypto.ec \
    --strip-debug --no-header-files --no-man-pages --output "$runtime_dir"
  "$JAVA_HOME/bin/jpackage" --type app-image --name "$app_name" --app-version "$app_version" \
    --input "$input_dir" --dest "$image_dir" --main-jar record-timeliness.jar \
    --main-class cn.huanhuan.app.Launcher --runtime-image "$runtime_dir" \
    --mac-package-identifier cn.huanhuan.timeliness --mac-package-name "$app_name" \
    --description '欢欢 · 入出院记录时效统计' --icon "$icon_file" \
    --java-options '--module-path=$APPDIR/javafx' \
    --java-options '--add-modules=javafx.controls' \
    --java-options '-Dfile.encoding=UTF-8'
  # 两种架构的系统基线不同；升级 JDK/JavaFX 时也必须核对原生库的最低系统版本。
  /usr/libexec/PlistBuddy -c "Set :LSMinimumSystemVersion $minimum_macos" "$image_dir/$app_name.app/Contents/Info.plist"
  /usr/bin/codesign --force --deep --sign - "$image_dir/$app_name.app"
  "$JAVA_HOME/bin/jpackage" --type dmg --name "$app_name" --app-version "$app_version" \
    --app-image "$image_dir/$app_name.app" --dest "$project_dir/target/package-dmg"
  cp "$project_dir/target/package-dmg/$app_name-$app_version.dmg" \
    "$project_dir/dist/$app_name-$app_version-macos-$architecture.dmg"
  echo "已生成：$project_dir/dist/$app_name-$app_version-macos-$architecture.dmg"
)

for index in "${!targets[@]}"; do
  build_target "${targets[$index]}" "${jdk_homes[$index]}"
done
