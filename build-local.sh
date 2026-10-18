#!/usr/bin/env bash
# Local build without Gradle: compiles against the Minecraft/NeoForge libraries already
# downloaded by Prism Launcher and the mod jars lying in the parent folder.
# Usage (Git Bash):  ./build-local.sh
set -euo pipefail

cd "$(dirname "$0")"
ROOT="$(pwd)"

MOD_VERSION="$(grep -E '^mod_version=' gradle.properties | cut -d= -f2)"
MOD_ID="$(grep -E '^mod_id=' gradle.properties | cut -d= -f2)"

PRISM="${PRISM_HOME:-$APPDATA/PrismLauncher}"
PRISM="$(cygpath -u "$PRISM")"
LIBS="$PRISM/libraries"
META="$PRISM/meta"
JDK="${JDK25_HOME:-$PRISM/java/java-runtime-epsilon}"
NEO_VERSION="${NEO_VERSION:-26.1.2.109}"
MC_VERSION="${MC_VERSION:-26.1.2}"
# Folder with the dependency mod jars (AE2, Apotheosis, Placebo, ...)
DEPS="${DEPS_DIR:-$ROOT/..}"
PLACEBO_JAR="${PLACEBO_JAR:-$PRISM/instances/All the Mods 11 - ATM11/minecraft/mods/Placebo-26.1.2-10.0.2.jar}"
GUIDEME_JAR="${GUIDEME_JAR:-$PRISM/instances/All the Mods 11 - ATM11/minecraft/mods/guideme-26.1.15-beta.jar}"

BUILD="$ROOT/build"
CLASSES="$BUILD/classes"
rm -rf "$CLASSES"
mkdir -p "$CLASSES" "$BUILD/libs"

coord_to_path() {
  # group:artifact:version[:classifier] -> relative maven path
  local IFS=':'
  read -r g a v c <<< "$1"
  local base="${g//.//}/$a/$v/$a-$v"
  if [[ -n "${c:-}" ]]; then echo "$base-$c.jar"; else echo "$base.jar"; fi
}

CP_ENTRIES=()
for meta in "$META/net.minecraft/$MC_VERSION.json" "$META/net.neoforged/$NEO_VERSION.json"; do
  while read -r coord; do
    [[ "$coord" == *:* ]] || continue
    [[ "$coord" == *natives* || "$coord" == *:installer || "$coord" == com.mojang:minecraft:* ]] && continue
    [[ "$coord" == io.github.zekerzhayard:ForgeWrapper:* ]] && continue
    p="$LIBS/$(coord_to_path "$coord")"
    [[ -f "$p" ]] && CP_ENTRIES+=("$p")
  done < <(grep -o '"name": *"[^"]*"' "$meta" | sed 's/"name": *"//; s/"$//' | sort -u)
done
while IFS= read -r -d '' j; do CP_ENTRIES+=("$j"); done < <(find "$LIBS/org/lwjgl" -path "*3.4.1*" -name "*.jar" ! -name "*natives*" -print0)
CP_ENTRIES+=("$LIBS/net/neoforged/minecraft-client-patched/$NEO_VERSION/minecraft-client-patched-$NEO_VERSION.jar")
for j in "$DEPS"/appliedenergistics2-*.jar "$DEPS"/Apotheosis-*.jar "$DEPS"/ApothicAttributes-*.jar "$DEPS"/ApothicEnchanting-*.jar; do
  CP_ENTRIES+=("$j")
done
CP_ENTRIES+=("$PLACEBO_JAR" "$GUIDEME_JAR")

ARGFILE="$BUILD/javac.args"
{
  printf -- '-encoding\nUTF-8\n--release\n25\n-proc:none\n-Xlint:-options\n-nowarn\n-d\n"%s"\n' "$(cygpath -w "$CLASSES" | sed 's/\\/\\\\/g')"
  printf -- '-classpath\n"'
  first=1
  for e in "${CP_ENTRIES[@]}"; do
    w="$(cygpath -w "$e" | sed 's/\\/\\\\/g')"
    if [[ $first -eq 1 ]]; then printf '%s' "$w"; first=0; else printf ';%s' "$w"; fi
  done
  printf '"\n'
  find "$ROOT/src/main/java" -name '*.java' -print0 | while IFS= read -r -d '' f; do
    printf '"%s"\n' "$(cygpath -w "$f" | sed 's/\\/\\\\/g')"
  done
} > "$ARGFILE"

echo "Compiling with $JDK ..."
"$JDK/bin/javac.exe" "@$(cygpath -w "$ARGFILE")"

cp -r "$ROOT/src/main/resources/." "$CLASSES/"
sed -i "s/\${mod_version}/$MOD_VERSION/g" "$CLASSES/META-INF/neoforge.mods.toml"

JAR="$BUILD/libs/$MOD_ID-$MOD_VERSION.jar"
rm -f "$JAR"
"$JDK/bin/jar.exe" --create --file "$(cygpath -w "$JAR")" -C "$(cygpath -w "$CLASSES")" .
echo "Built: $JAR"

TEST_CLIENT="/d/ai2/test-client"
if [[ -d "$TEST_CLIENT/mods" ]]; then
  rm -f "$TEST_CLIENT/mods/$MOD_ID"-*.jar
  cp "$JAR" "$TEST_CLIENT/mods/$MOD_ID-$MOD_VERSION.jar"
  echo "Copied to: $TEST_CLIENT/mods/$MOD_ID-$MOD_VERSION.jar"
fi

