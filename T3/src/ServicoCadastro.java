/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.crypto.SecretKey;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

public class ServicoCadastro {

    static final int TAMANHO_TESTE_ASSINATURA = 10240;
    static final int CUSTO_BCRYPT = 8;
    static final int TAMANHO_SALT_BCRYPT = 16;
    static final int TAMANHO_CHAVE_TOTP = 20;

    static class CadastroException extends Exception {
        private static final long serialVersionUID = 1L;

        CadastroException(String mensagem) {
            super(mensagem);
        }
    }

    static class DadosCadastro {
        X509Certificate certificado;
        String certificadoPem;
        byte[] chavePrivadaCifrada;
        PrivateKey chavePrivada;
        String login;
        String nome;
        int gid;
        char[] senha;

        void limpar() {
            if (senha != null) {
                Arrays.fill(senha, '\0');
            }
            chavePrivada = null;
        }
    }

    private final BancoDeDados banco;
    private final SecureRandom aleatorio = new SecureRandom();

    ServicoCadastro(BancoDeDados banco) {
        this.banco = banco;
    }

    DadosCadastro validar(String operador, String caminhoCertificado, String caminhoChave, char[] frase,
                          int gid, char[] senha, char[] confirmacao) throws CadastroException {
        String erroSenha = criticarSenha(senha, confirmacao);
        if (erroSenha != null) {
            banco.registrar(6003, operador, null);
            throw new CadastroException(erroSenha);
        }

        DadosCadastro dados = new DadosCadastro();
        dados.gid = gid;
        try {
            byte[] conteudo = Files.readAllBytes(Paths.get(caminhoCertificado.trim()));
            dados.certificadoPem = Criptografia.extrairPEMCertificado(conteudo);
            dados.certificado = Criptografia.carregarCertificado(dados.certificadoPem);
        } catch (Exception e) {
            banco.registrar(6004, operador, null);
            throw new CadastroException("Caminho do certificado digital invalido: o arquivo nao existe "
                    + "ou nao contem um certificado X.509 no formato PEM.");
        }

        dados.login = Criptografia.extrairEmail(dados.certificado);
        dados.nome = Criptografia.extrairNome(dados.certificado);
        if (!Autenticador.loginValido(dados.login) || dados.nome == null || dados.nome.isEmpty()) {
            banco.registrar(6004, operador, null);
            throw new CadastroException("O certificado digital nao possui um e-mail valido e um nome (CN) "
                    + "no campo Sujeito.");
        }

        try {
            dados.chavePrivadaCifrada = Files.readAllBytes(Paths.get(caminhoChave.trim()));
        } catch (Exception e) {
            banco.registrar(6005, operador, null);
            throw new CadastroException("Caminho do arquivo da chave privada invalido.");
        }

        try {
            dados.chavePrivada = Criptografia.validarChavePrivada(dados.chavePrivadaCifrada, frase,
                    dados.certificado.getPublicKey(), TAMANHO_TESTE_ASSINATURA);
        } catch (ChavePrivadaInvalidaException e) {
            if (e.getMotivo() == ChavePrivadaInvalidaException.Motivo.FRASE_SECRETA_INVALIDA) {
                banco.registrar(6006, operador, null);
                throw new CadastroException("Frase secreta invalida para a chave privada.");
            }
            banco.registrar(6007, operador, null);
            throw new CadastroException("A chave privada nao corresponde ao certificado digital "
                    + "(assinatura digital invalida).");
        }

        dados.senha = senha.clone();
        return dados;
    }

    String efetivar(DadosCadastro dados) throws CadastroException {
        if (banco.buscarUsuarioPorLogin(dados.login) != null) {
            throw new CadastroException("O login name " + dados.login + " ja esta cadastrado no sistema.");
        }

        byte[] salt = new byte[TAMANHO_SALT_BCRYPT];
        aleatorio.nextBytes(salt);
        String hashSenha = OpenBSDBCrypt.generate("2y", dados.senha, salt, CUSTO_BCRYPT);

        byte[] chaveTotp = new byte[TAMANHO_CHAVE_TOTP];
        aleatorio.nextBytes(chaveTotp);
        String chaveBase32 = new Base32(Base32.Alphabet.BASE32, false, false).toString(chaveTotp);
        Arrays.fill(chaveTotp, (byte) 0);

        byte[] tokenCifrado;
        byte[] semente = Criptografia.paraBytes(dados.senha);
        try {
            SecretKey chaveAES = Criptografia.gerarChaveAES(semente);
            tokenCifrado = Criptografia.cifrarAES(chaveAES, chaveBase32.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new CadastroException("Falha ao cifrar a chave secreta TOTP: " + e.getMessage());
        } finally {
            Arrays.fill(semente, (byte) 0);
        }

        banco.inserirUsuario(dados.login, dados.nome, dados.gid, hashSenha, tokenCifrado,
                dados.certificadoPem, dados.chavePrivadaCifrada);
        return chaveBase32;
    }

    static String criticarSenha(char[] senha, char[] confirmacao) {
        if (senha.length < Autenticador.MIN_DIGITOS_SENHA || senha.length > Autenticador.MAX_DIGITOS_SENHA) {
            return "A senha pessoal deve ter 8, 9 ou 10 digitos.";
        }
        for (int i = 0; i < senha.length; i++) {
            if (senha[i] < '0' || senha[i] > '9') {
                return "A senha pessoal deve conter apenas digitos de 0 a 9.";
            }
            if (i > 0 && senha[i] == senha[i - 1]) {
                return "A senha pessoal nao pode conter digitos repetidos em sequencia (ex.: 11).";
            }
        }
        if (!Arrays.equals(senha, confirmacao)) {
            return "A senha pessoal e a confirmacao nao conferem.";
        }
        return null;
    }
}
