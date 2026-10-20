#!/usr/bin/env sh
# Install Cybermeta for Android from the latest signed GitHub release.
#
#   curl -fsSL https://raw.githubusercontent.com/cybercoretech/cybermeta-android-app/main/install.sh | sh
#
# Downloads the APK, verifies its SHA-256 (and, when apksigner is around,
# the release signing certificate), then:
#   - with a phone on adb:  installs it there (asks first)
#   - on Termux:            opens Android's installer
#   - otherwise:            leaves the verified APK in the current folder
#
# Environment: CYBERMETA_YES=1 skips the prompt, CYBERMETA_DIR sets where
# the APK is saved (default: current directory).
set -eu

REPO="cybercoretech/cybermeta-android-app"
BASE="https://github.com/$REPO/releases/latest/download"
APK="cybermeta.apk"
# SHA-256 of the release signing certificate (also on the site and in README).
CERT_SHA256="88390595f5fbf171dc03d040abc8edc2a7fb9e973320b3c4bbe725d4139b283c"
DIR="${CYBERMETA_DIR:-.}"

teal() { printf '\033[38;2;45;212;191m%s\033[0m\n' "$*"; }
coral() { printf '\033[38;2;255;107;92m%s\033[0m\n' "$*" >&2; }
die() { coral "error: $*"; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "'$1' is required but not found on PATH"; }

need curl
if command -v sha256sum >/dev/null 2>&1; then
  sha() { sha256sum "$1" | cut -d' ' -f1; }
elif command -v shasum >/dev/null 2>&1; then
  sha() { shasum -a 256 "$1" | cut -d' ' -f1; }
else
  die "need sha256sum or shasum"
fi

teal "▚ cybermeta for android"
mkdir -p "$DIR"
cd "$DIR"
tmp="$APK.part"
trap 'rm -f "$tmp" "$tmp.sha256"' EXIT INT TERM

echo "downloading the latest release…"
curl -fL --progress-bar -o "$tmp" "$BASE/$APK" || die "download failed (is there a release yet?)"
curl -fsSL -o "$tmp.sha256" "$BASE/$APK.sha256" || die "checksum download failed"

want=$(cut -d' ' -f1 "$tmp.sha256" | tr 'A-F' 'a-f')
got=$(sha "$tmp")
[ "$want" = "$got" ] || die "SHA-256 mismatch: expected $want, got $got. Nothing was installed."
teal "✔ SHA-256 verified ($got)"

signer=""
for c in apksigner "${ANDROID_HOME:-$HOME/android}"/build-tools/*/apksigner; do
  if command -v "$c" >/dev/null 2>&1 || [ -x "$c" ]; then signer="$c"; fi
done
if [ -n "$signer" ]; then
  cert=$("$signer" verify --print-certs "$tmp" 2>/dev/null | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)
  [ "$cert" = "$CERT_SHA256" ] || die "signing certificate mismatch ($cert). Nothing was installed."
  teal "✔ signed by the Cybermeta release key"
else
  echo "  (apksigner not found; skipped the certificate check — expected $CERT_SHA256)"
fi
mv "$tmp" "$APK"

ask() {
  [ "${CYBERMETA_YES:-}" = "1" ] && return 0
  [ -r /dev/tty ] || return 1
  printf '%s [y/N] ' "$1" > /dev/tty
  read -r answer < /dev/tty || return 1
  case "$answer" in y|Y|yes|YES) return 0 ;; *) return 1 ;; esac
}

if [ -n "${PREFIX:-}" ] && echo "$PREFIX" | grep -q com.termux; then
  if command -v termux-open >/dev/null 2>&1; then
    teal "opening Android's installer…"
    termux-open "$APK"
    exit 0
  fi
  echo "install termux-tools for termux-open, or open $(pwd)/$APK from a file manager"
  exit 0
fi

if command -v adb >/dev/null 2>&1 && [ "$(adb devices | grep -c 'device$')" -gt 0 ]; then
  devices=$(adb devices | awk '/device$/{print $1}' | tr '\n' ' ')
  if ask "install on $devices over adb?"; then
    adb install -r "$APK"
    teal "✔ installed. Open Cybermeta, or share a photo to it from your gallery."
    exit 0
  fi
fi

teal "✔ $(pwd)/$APK is ready"
echo "  Copy it to your phone and open it, or connect the phone and run: adb install -r $APK"
echo "  For update notifications, add https://github.com/$REPO to Obtainium."
