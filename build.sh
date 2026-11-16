#!/usr/bin/env bash
# Spool Factory — build no Linux/macOS (equivalente a setup.ps1 + build.ps1 [+ test.ps1]).
# Uso:  ./build.sh            baixa as ferramentas (se faltarem), gera assets e o APK assinado
#       ./build.sh --test     também roda os testes Java e de UI
# Variáveis: SPOOL_TOOLS_DIR (padrão ./tools), SPOOL_SIGNING_KEY/ALIAS/PASSWORD (chave própria; sem elas,
# usa uma chave de desenvolvimento em signing/ — um APK assim NÃO instala por cima do APK oficial).
set -euo pipefail
cd "$(dirname "$0")"
TOOLS="${SPOOL_TOOLS_DIR:-$PWD/tools}"
mkdir -p "$TOOLS"

fetch(){ # url arquivo algoritmo hash destino
  local url=$1 file=$2 alg=$3 hash=$4 dest=$5
  [ -d "$dest" ] && return 0
  echo "Baixando $url"
  curl -fL --retry 3 -o "$file" "$url"
  echo "$hash  $file" | "${alg}sum" -c - >/dev/null || { echo "Checksum inválido: $file" >&2; exit 1; }
  mkdir -p "$dest"
  case "$file" in *.zip) unzip -q -o "$file" -d "$dest";; *) tar -xzf "$file" -C "$dest";; esac
}
case "$(uname -s)" in Darwin) OS=mac; JOS=mac;; *) OS=linux; JOS=linux;; esac
ARCH=$(uname -m); [ "$ARCH" = "arm64" ] || [ "$ARCH" = "aarch64" ] && JARCH=aarch64 || JARCH=x64
if [ ! -d "$TOOLS/jdk" ]; then
  meta=$(curl -fsSL "https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=$JARCH&image_type=jdk&os=$JOS&vendor=eclipse")
  link=$(printf '%s' "$meta" | python3 -c 'import json,sys;print(json.load(sys.stdin)[0]["binary"]["package"]["link"])')
  sum=$(printf '%s' "$meta" | python3 -c 'import json,sys;print(json.load(sys.stdin)[0]["binary"]["package"]["checksum"])')
  fetch "$link" "$TOOLS/jdk.tar.gz" sha256 "$sum" "$TOOLS/jdk"
fi
fetch https://dl.google.com/android/repository/platform-35_r02.zip "$TOOLS/platform.zip" sha1 0bb560a90a7a2cbd0dd8348224d518b638fe7949 "$TOOLS/platform"
if [ "$OS" = linux ]; then
  fetch https://dl.google.com/android/repository/build-tools_r35_linux.zip "$TOOLS/build.zip" sha1 2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc "$TOOLS/build"
else
  [ -d "$TOOLS/build" ] || { echo "macOS: instale build-tools 35 em $TOOLS/build/<pasta> (Android SDK)." >&2; exit 1; }
fi
if [ ! -f "$TOOLS/json.jar" ]; then
  curl -fL --retry 3 -o "$TOOLS/json.jar" https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar
  echo "3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed  $TOOLS/json.jar" | sha256sum -c - >/dev/null
fi

first(){ find "$1" -mindepth 1 -maxdepth 1 -type d | sort | head -1; }
JDK=$(first "$TOOLS/jdk"); [ -d "$JDK/Contents/Home" ] && JDK="$JDK/Contents/Home"
BT=$(first "$TOOLS/build"); PLATFORM=$(first "$TOOLS/platform"); ANDROID="$PLATFORM/android.jar"
JAVA="$JDK/bin/java"; JAVAC="$JDK/bin/javac"

# Textos compartilhados (U1 Spool Spec v1): spec/strings.json -> assets/strings.json + assets/i18n-data.js
cp spec/strings.json assets/strings.json
{ printf "'use strict';\nwindow.STRINGS="; cat spec/strings.json; printf ";\n"; } > assets/i18n-data.js

if [ "${1:-}" = "--test" ]; then
  rm -rf build/tests; mkdir -p build/tests
  "$JAVAC" -encoding UTF-8 -cp "$TOOLS/json.jar" -d build/tests src/br/com/caiot/u1spool/{EdgeDetector,Api,AssociationLedger,InventoryManager,ConnectionConfig,L10n}.java tests/{EdgeDetectorTest,ApiTest,AssociationLedgerTest,InventoryManagerTest,L10nTest,SpecFixturesTest}.java
  for t in EdgeDetectorTest ApiTest AssociationLedgerTest InventoryManagerTest L10nTest SpecFixturesTest; do "$JAVA" -cp "build/tests:$TOOLS/json.jar" "br.com.caiot.u1spool.$t"; done
  node tests/ui-test.cjs
  if [ -d "$TOOLS/ui-test/node_modules/playwright-core" ]; then SPOOL_TOOLS_DIR="$TOOLS" node tests/ui-browser-test.cjs; else echo "(UI no navegador: npm install --prefix \"$TOOLS/ui-test\" playwright-core)"; fi
fi

VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)
OUT="release/Spool-Factory-$VERSION.apk"
rm -rf build/generated build/classes build/dex; mkdir -p build/generated build/classes build/dex release signing
"$BT/aapt2" compile --dir res -o build/resources.zip
"$BT/aapt2" link -o build/unsigned.apk --manifest AndroidManifest.xml -I "$ANDROID" -A assets --java build/generated build/resources.zip
find src build/generated -name '*.java' > build/sources.txt
"$JAVAC" -encoding UTF-8 -source 8 -target 8 -nowarn -classpath "$ANDROID" -d build/classes @build/sources.txt
"$JDK/bin/jar" cf build/classes.jar -C build/classes .
"$JAVA" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --lib "$ANDROID" --min-api 26 --output build/dex build/classes.jar
if command -v zip >/dev/null; then (cd build/dex && zip -q -j ../unsigned.apk classes.dex)
else python3 -c 'import zipfile,sys;z=zipfile.ZipFile(sys.argv[1],"a",zipfile.ZIP_DEFLATED);z.write(sys.argv[2],"classes.dex");z.close()' build/unsigned.apk build/dex/classes.dex; fi
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
KEY="${SPOOL_SIGNING_KEY:-$PWD/signing/development.p12}"; ALIAS="${SPOOL_SIGNING_ALIAS:-spool-factory}"
if [ -z "${SPOOL_SIGNING_KEY:-}" ]; then
  [ -f signing/development-password.txt ] || head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n' > signing/development-password.txt
  export SPOOL_SIGNING_PASSWORD=$(tr -d '\r\n' < signing/development-password.txt)
  [ -f "$KEY" ] || "$JDK/bin/keytool" -genkeypair -keystore "$KEY" -storetype PKCS12 -storepass:env SPOOL_SIGNING_PASSWORD -keypass:env SPOOL_SIGNING_PASSWORD -alias "$ALIAS" -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Spool Factory Development"
fi
: "${SPOOL_SIGNING_PASSWORD:?Defina SPOOL_SIGNING_PASSWORD para a chave escolhida.}"
"$JAVA" -jar "$BT/lib/apksigner.jar" sign --ks "$KEY" --ks-pass env:SPOOL_SIGNING_PASSWORD --key-pass env:SPOOL_SIGNING_PASSWORD --ks-key-alias "$ALIAS" --out "$OUT" build/aligned.apk
"$JAVA" -jar "$BT/lib/apksigner.jar" verify --verbose "$OUT" | head -5
"$BT/aapt2" dump badging "$OUT" | head -3
cp "$OUT" release/Spool-Factory.apk
(cd release && sha256sum Spool-Factory.apk > SHA256SUMS.txt)
echo "APK: $OUT ($(wc -c < "$OUT") bytes)"
