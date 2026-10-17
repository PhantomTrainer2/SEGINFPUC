#!/bin/sh
# INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
# Rodrigo - 2210814
# Breno Gallo - 2110183
set -e
cd "$(dirname "$0")"

BCPROV=lib/bcprov-jdk18on-1.86.jar
SQLITE=lib/sqlite-jdbc-3.53.4.0.jar

for jar in "$BCPROV" "$SQLITE"; do
    if [ ! -f "$jar" ]; then
        echo "Biblioteca nao encontrada: $jar (veja o README.md)"
        exit 1
    fi
done

if ! command -v jar >/dev/null 2>&1 && [ -n "$JAVA_HOME" ]; then
    JDK_BIN="$(cygpath -u "$JAVA_HOME" 2>/dev/null || echo "$JAVA_HOME")/bin"
    PATH="$JDK_BIN:$PATH"
fi
for ferramenta in javac jar; do
    if ! command -v "$ferramenta" >/dev/null 2>&1; then
        echo "Ferramenta '$ferramenta' do JDK nao encontrada: defina JAVA_HOME ou coloque <JDK>/bin no PATH."
        exit 1
    fi
done

if javac -version 2>&1 | grep -q ' 1\.8\.'; then
    COMPAT="-source 1.8 -target 1.8"
else
    COMPAT="--release 8 -Xlint:-options"
fi

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
    *) SEP=':' ;;
esac

rm -rf build
mkdir -p build/classes
javac $COMPAT -encoding UTF-8 -cp "$BCPROV$SEP$SQLITE" -d build/classes src/*.java

printf 'Main-Class: CofreDigital\nClass-Path: %s %s\n' "$BCPROV" "$SQLITE" > build/manifest-cofre.txt
printf 'Main-Class: LogView\nClass-Path: %s\n' "$SQLITE" > build/manifest-logview.txt

cd build/classes
jar cfm ../../cofre.jar ../manifest-cofre.txt $(ls *.class | grep -v '^LogView')
jar cfm ../../logview.jar ../manifest-logview.txt LogView*.class BancoDeDados*.class Usuario*.class \
    Criptografia*.class ChavePrivadaInvalidaException*.class
cd ../..

echo "Gerados: cofre.jar e logview.jar"
echo "  java -jar cofre.jar"
echo "  java -jar logview.jar <Caminho_Chave_Privada_Admin>"
