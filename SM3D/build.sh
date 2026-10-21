#!/bin/sh
# Сборка игрового JAR из исходников.
#
# Требуется:
#   JDK 8            (javac/jar)                  -> JAVA_HOME
#   Библиотеки WTK   cldcapi11, midpapi21, jsr184, mmapi -> WTK_LIB
#   ProGuard 5.x     (преверификация, -microedition)      -> PROGUARD_JAR
#
# ВАЖНО: исходники лежат в UTF-8, поэтому javac обязательно запускается
# с -encoding UTF-8. Без этого кириллица в строковых константах
# (алфавиты ModChanges/TextCreator) превращается в символы замены,
# и весь текст в игре отображается мусором.
#
# Результат кладётся в dist/SM3D_Build.jar и dist/SM3D_Build_Test_All_Weapons.jar.
# Продовые dist/WTK_PreciseRender/SM3D.jar и SM3D_Test_All_Weapons.jar не трогаются.

set -e

SRC_DIR=$(cd "$(dirname "$0")" && pwd)
JAVA_HOME=${JAVA_HOME:?укажите JAVA_HOME (JDK 8)}
WTK_LIB=${WTK_LIB:?укажите WTK_LIB (каталог lib из WTK 2.5.2)}
PROGUARD_JAR=${PROGUARD_JAR:?укажите PROGUARD_JAR (proguard.jar 5.x)}

JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"
BOOTCP="$WTK_LIB/cldcapi11.jar:$WTK_LIB/midpapi21.jar:$WTK_LIB/jsr184.jar:$WTK_LIB/mmapi.jar"

BUILD_DIR="$SRC_DIR/build/jar"
CLASSES="$BUILD_DIR/classes"
PREVERIFIED="$BUILD_DIR/preverified"
DIST="$SRC_DIR/dist"

rm -rf "$BUILD_DIR"
mkdir -p "$CLASSES" "$PREVERIFIED" "$DIST"

find "$SRC_DIR/src/code" -name '*.java' > "$BUILD_DIR/sources.txt"

echo "==> javac (-encoding UTF-8, -source/-target 1.3)"
"$JAVAC" -encoding UTF-8 -source 1.3 -target 1.3 -nowarn \
    -bootclasspath "$BOOTCP" -d "$CLASSES" @"$BUILD_DIR/sources.txt"

echo "==> преверификация (ProGuard -microedition)"
cat > "$BUILD_DIR/proguard.cfg" <<EOF
-injars $CLASSES
-outjars $PREVERIFIED/classes.jar
-libraryjars $BOOTCP
-dontshrink
-dontoptimize
-dontobfuscate
-microedition
-keepattributes *
-dontnote
-dontwarn
EOF
"$JAVA" -jar "$PROGUARD_JAR" @"$BUILD_DIR/proguard.cfg" > /dev/null
(cd "$PREVERIFIED" && "$JAVA_HOME/bin/jar" xf classes.jar)

echo "==> упаковка JAR"
python3 "$SRC_DIR/build_jar.py" \
    --classes "$PREVERIFIED/code" \
    --res "$SRC_DIR/res" \
    --manifest "$SRC_DIR/dist/WTK_PreciseRender/SM3D.jar" \
    --out "$DIST/SM3D_Build.jar"

python3 "$SRC_DIR/build_jar.py" \
    --classes "$PREVERIFIED/code" \
    --res "$SRC_DIR/res" \
    --manifest "$SRC_DIR/dist/WTK_PreciseRender/SM3D_Test_All_Weapons.jar" \
    --out "$DIST/SM3D_Build_Test_All_Weapons.jar"

echo "Готово:"
ls -l "$DIST"/SM3D_Build*.jar
