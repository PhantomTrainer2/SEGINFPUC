#!/bin/sh
# INF1416 - Trabalho 3 - Cofre Digital
# Testes automatizados. A partir da pasta T3 (Git Bash, Linux ou macOS):
#   ./testesUni/rodar.sh            todos (unitario, gui e logview)
#   ./testesUni/rodar.sh unitario   so os testes sem interface (~10 s)
#   ./testesUni/rodar.sh gui        a interface (abre janelas, ~5 min) e depois o logView
#   ./testesUni/rodar.sh logview    so o logView (usa o banco gerado pelo gui)
# Tudo roda em build/testes: o cofre.db e o Pacote-T3 da pasta T3 nao sao alterados.
cd "$(dirname "$0")/.."

QUAIS="${1:-todos}"
case "$QUAIS" in
    todos|unitario|gui|logview) ;;
    *) echo "Uso: ./testesUni/rodar.sh [todos|unitario|gui|logview]"; exit 1 ;;
esac

# O build.sh recria a pasta build: o gui e o logview precisam rodar juntos
if [ "$QUAIS" != logview ] || [ ! -f build/testes/e2e/cofre.db ]; then
    if [ "$QUAIS" = logview ]; then
        echo "Banco do teste da interface nao encontrado: rode antes ./testesUni/rodar.sh gui"
        exit 1
    fi
    ./build.sh || exit 1
fi

# Mesmo ajuste do build.sh para o javac fora do PATH
if ! command -v javac >/dev/null 2>&1 && [ -n "$JAVA_HOME" ]; then
    PATH="$(cygpath -u "$JAVA_HOME" 2>/dev/null || echo "$JAVA_HOME")/bin:$PATH"
fi

# No Windows (Git Bash/MSYS/Cygwin) o Java espera caminhos C:/... e ';' no classpath
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
    *) SEP=':' ;;
esac
RAIZ="$(cygpath -m "$(pwd)" 2>/dev/null || pwd)"
TESTES="$RAIZ/build/testes"
CP="$TESTES/classes$SEP$RAIZ/build/classes$SEP$RAIZ/lib/bcprov-jdk18on-1.86.jar$SEP$RAIZ/lib/sqlite-jdbc-3.53.4.0.jar"
LOG="$TESTES/resultado.txt"

mkdir -p "$TESTES/classes"
javac -encoding UTF-8 -cp "$CP" -d "$TESTES/classes" testesUni/*.java || exit 1
: > "$LOG"

# Java 22+ avisa quando o driver do SQLite carrega a biblioteca nativa
NATIVO=""
if java --enable-native-access=ALL-UNNAMED -version >/dev/null 2>&1; then
    NATIVO="--enable-native-access=ALL-UNNAMED"
fi
# Mostra a saida ao vivo e acumula no log; um teste que nao chega ao RESULTADO conta como falha
rodar_java() {
    java $NATIVO -Dstdout.encoding=UTF-8 "-Dt3=$RAIZ" -cp "$CP" "$@" 2>&1 | tee "$TESTES/ultima.txt"
    cat "$TESTES/ultima.txt" >> "$LOG"
    if ! grep -q '^RESULTADO' "$TESTES/ultima.txt"; then
        echo "  [FALHA] $* terminou sem RESULTADO" | tee -a "$LOG"
    fi
}

if [ "$QUAIS" = todos ] || [ "$QUAIS" = unitario ]; then
    echo "##### Testes unitarios" | tee -a "$LOG"
    rodar_java TesteUnitario
fi

if [ "$QUAIS" = todos ] || [ "$QUAIS" = gui ]; then
    echo | tee -a "$LOG"
    echo "##### Testes da interface: janelas vao abrir e se mexer sozinhas, NAO clique nelas." | tee -a "$LOG"
    echo "##### Leva ~5 min: os testes esperam o fim dos bloqueios de 2 minutos." | tee -a "$LOG"
    rm -rf "$TESTES/e2e"
    mkdir -p "$TESTES/e2e"
    # Cada execucao e um novo processo do cofre no mesmo diretorio (mesmo cofre.db)
    for execucao in run1 run2 run3 run4 run5; do
        (cd "$TESTES/e2e" && rodar_java TesteGui $execucao)
    done
fi

if [ "$QUAIS" = todos ] || [ "$QUAIS" = gui ] || [ "$QUAIS" = logview ]; then
    echo | tee -a "$LOG"
    echo "##### Testes do logView" | tee -a "$LOG"
    (cd "$TESTES/e2e" && rodar_java TesteLogView)
fi

TOTAL_OK=$(grep -c '\[OK\]' "$LOG")
TOTAL_FALHAS=$(grep -c '\[FALHA\]' "$LOG")
echo
echo "================================================================"
echo "TOTAL: $TOTAL_OK OK, $TOTAL_FALHAS FALHA(S)   (log: build/testes/resultado.txt)"
if [ "$TOTAL_FALHAS" -gt 0 ]; then
    grep '\[FALHA\]' "$LOG"
    exit 1
fi
