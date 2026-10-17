/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import javax.crypto.SecretKey;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

public class Autenticador {

    static final int MAX_ERROS = 3;
    static final long TEMPO_BLOQUEIO_MS = 2 * 60 * 1000L;
    static final int MIN_DIGITOS_SENHA = 8;
    static final int MAX_DIGITOS_SENHA = 10;
    static final long INTERVALO_TOTP_SEGUNDOS = 30;

    private static final Pattern FORMATO_EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final BancoDeDados banco;

    Autenticador(BancoDeDados banco) {
        this.banco = banco;
    }

    static boolean loginValido(String login) {
        return login != null && FORMATO_EMAIL.matcher(login).matches();
    }

    Usuario identificarUsuario(String login) {
        Usuario usuario = loginValido(login) ? banco.buscarUsuarioPorLogin(login) : null;
        if (usuario == null) {
            banco.registrar(2005, login, null);
        } else if (usuario.estaBloqueado()) {
            banco.registrar(2004, usuario.login, null);
        } else {
            banco.registrar(2003, usuario.login, null);
        }
        return usuario;
    }

    char[] verificarSenha(Usuario usuario, List<int[]> paresPressionados) {
        char[] senha = null;
        int quantidade = paresPressionados.size();
        if (quantidade >= MIN_DIGITOS_SENHA && quantidade <= MAX_DIGITOS_SENHA) {
            senha = buscarSenha(usuario.hashSenha, paresPressionados);
        }

        if (senha != null) {
            usuario.errosSenha = 0;
            banco.atualizarErrosSenha(usuario.uid, 0);
            banco.registrar(3003, usuario.login, null);
            return senha;
        }

        usuario.errosSenha++;
        banco.registrar(3003 + Math.min(usuario.errosSenha, MAX_ERROS), usuario.login, null);
        if (usuario.errosSenha >= MAX_ERROS) {
            bloquear(usuario);
            banco.registrar(3007, usuario.login, null);
        } else {
            banco.atualizarErrosSenha(usuario.uid, usuario.errosSenha);
        }
        return null;
    }

    boolean verificarToken(Usuario usuario, char[] senha, String codigo) {
        if (tokenConfere(usuario, senha, codigo)) {
            usuario.errosToken = 0;
            banco.atualizarErrosToken(usuario.uid, 0);
            banco.registrar(4003, usuario.login, null);
            return true;
        }

        usuario.errosToken++;
        banco.registrar(4003 + Math.min(usuario.errosToken, MAX_ERROS), usuario.login, null);
        if (usuario.errosToken >= MAX_ERROS) {
            bloquear(usuario);
            banco.registrar(4007, usuario.login, null);
        } else {
            banco.atualizarErrosToken(usuario.uid, usuario.errosToken);
        }
        return false;
    }

    static char[] buscarSenha(final String hash, final List<int[]> pares) {
        OptionalInt combinacao = IntStream.range(0, 1 << pares.size())
                .parallel()
                .filter(mascara -> confere(hash, pares, mascara))
                .findAny();
        return combinacao.isPresent() ? montarCandidata(pares, combinacao.getAsInt()) : null;
    }

    static char[] montarCandidata(List<int[]> pares, int mascara) {
        char[] candidata = new char[pares.size()];
        for (int i = 0; i < candidata.length; i++) {
            candidata[i] = (char) ('0' + pares.get(i)[(mascara >> i) & 1]);
        }
        return candidata;
    }

    private static boolean confere(String hash, List<int[]> pares, int mascara) {
        char[] candidata = montarCandidata(pares, mascara);
        try {
            return OpenBSDBCrypt.checkPassword(hash, candidata);
        } finally {
            Arrays.fill(candidata, '\0');
        }
    }

    private static boolean tokenConfere(Usuario usuario, char[] senha, String codigo) {
        if (codigo == null || !codigo.matches("\\d{6}")) {
            return false;
        }
        byte[] semente = Criptografia.paraBytes(senha);
        byte[] chaveBase32 = null;
        try {
            SecretKey chave = Criptografia.gerarChaveAES(semente);
            chaveBase32 = Criptografia.decifrarAES(chave, usuario.tokenCifrado);
            TOTP totp = new TOTP(new String(chaveBase32, StandardCharsets.US_ASCII), INTERVALO_TOTP_SEGUNDOS);
            return totp.validateCode(codigo);
        } catch (Exception e) {
            return false;
        } finally {
            Arrays.fill(semente, (byte) 0);
            if (chaveBase32 != null) {
                Arrays.fill(chaveBase32, (byte) 0);
            }
        }
    }

    private void bloquear(Usuario usuario) {
        usuario.bloqueadoAte = System.currentTimeMillis() + TEMPO_BLOQUEIO_MS;
        usuario.errosSenha = 0;
        usuario.errosToken = 0;
        banco.bloquearUsuario(usuario.uid, usuario.bloqueadoAte);
    }
}
