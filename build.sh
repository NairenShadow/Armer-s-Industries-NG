#!/usr/bin/env bash
# Compile tous les plugins du pack dans dist/.
# Usage : ./build.sh /chemin/vers/server.jar   (le server.jar MCPC+ 1.6.4 de ton serveur NationsGlory)
# Les .jar de libs/ (WorldEdit...) sont ajoutes au classpath s'ils existent.
set -e
cd "$(dirname "$0")"
SERVEUR="${1:?Donne le chemin de server.jar (MCPC+ 1.6.4 NationsGlory)}"
CP="$SERVEUR"
for j in libs/*.jar; do [ -f "$j" ] && CP="$CP:$j"; done
rm -rf build dist && mkdir -p build dist

compiler() { # $1 = dossier source, $2 = dossier resources, $3 = nom du jar
  local cls="build/$3"; mkdir -p "$cls"
  javac --release 8 -nowarn -encoding UTF-8 -cp "$CP" -d "$cls" $(find "$1" -name '*.java')
  cp "$2"/*.yml "$cls"/ 2>/dev/null || true
  (cd "$cls" && jar cf "../../dist/$3.jar" .)
  CP="$CP:$cls"
  echo "  dist/$3.jar"
}

echo "Compilation du pack ShadowState-NG (Armer's Industries) :"
for p in SSNG-Core SSNG-Ponts SSNG-Ressources SSNG-Carte SSNG-Assaut SSNG-Iles SSNG-Machines; do
  d="plugins/$p"
  [ -d "$d" ] || continue
  compiler "$d/src" "$d/res" "$p"
  if [ -d "$d/ponts" ]; then
    for q in "$d"/ponts/*/; do
      n=$(basename "$q")
      compiler "$q/src" "$q" "SSNG-Pont-$n"
    done
  fi
done
echo "Termine : copie les .jar de dist/ dans le dossier plugins/ de ton serveur."
