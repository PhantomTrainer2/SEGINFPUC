/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

public class ChavePrivadaInvalidaException extends Exception {

    private static final long serialVersionUID = 1L;

    enum Motivo {
        FRASE_SECRETA_INVALIDA,
        ASSINATURA_INVALIDA
    }

    private final Motivo motivo;

    ChavePrivadaInvalidaException(Motivo motivo, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.motivo = motivo;
    }

    Motivo getMotivo() {
        return motivo;
    }
}
