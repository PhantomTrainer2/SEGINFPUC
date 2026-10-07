#!/bin/sh
# INF1416 - Trabalho 3 - Cofre Digital
# Compila os fontes (bytecode Java 8) e gera cofre.jar e logview.jar.
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

# No JDK 8 usa -source/-target; nos JDKs 9+ usa --release 8, que tambem
# impede o uso de APIs posteriores ao Java 8
if javac -version 2>&1 | grep -q ' 1\.8\.'; then
    COMPAT="-source 1.8 -target 1.8"
else
    COMPAT="--release 8 -Xlint:-options"
fi

rm -rf build
mkdir -p build/classes
javac $COMPAT -encoding UTF-8 -cp "$BCPROV:$SQLITE" -d build/classes src/*.java

printf 'Main-Class: CofreDigital\nClass-Path: %s %s\n' "$BCPROV" "$SQLITE" > build/manifest-cofre.txt
printf 'Main-Class: LogView\nClass-Path: %s\n' "$SQLITE" > build/manifest-logview.txt

# O logView e um programa separado: suas classes nao entram no cofre.jar
cd build/classes
jar cfm ../../cofre.jar ../manifest-cofre.txt $(ls *.class | grep -v '^LogView')
jar cfm ../../logview.jar ../manifest-logview.txt LogView*.class BancoDeDados*.class Usuario*.class \
    Criptografia*.class ChavePrivadaInvalidaException*.class
cd ../..

echo "Gerados: cofre.jar e logview.jar"
echo "  java -jar cofre.jar"
echo "  java -jar logview.jar <Caminho_Chave_Privada_Admin>"
