/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 *
 * Uso:
 *   java -jar logview.jar <Caminho_Chave_Privada_Admin> [Caminho_Banco_de_Dados]
 *
 * Programa de auditoria, separado do Cofre Digital (nao faz parte do cofre.jar).
 * Apenas um administrador pode executa-lo: a chave privada informada e
 * decriptada com a frase secreta (lida sem eco) e validada com a assinatura
 * de um array aleatorio de 2048 bytes, verificada com o certificado digital
 * de um usuario do grupo Administrador.
 */

import java.io.Console;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

public class LogView {

    private static final int TAMANHO_TESTE_ASSINATURA = 2048;

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Uso: java -jar logview.jar <Caminho_Chave_Privada_Admin> [Caminho_Banco_de_Dados]");
            System.exit(1);
        }
        if (!Criptografia.suportaAES256()) {
            sair("Erro: " + Criptografia.mensagemSemAES256());
        }
        String caminhoBanco = args.length == 2 ? args[1] : BancoDeDados.ARQUIVO_PADRAO;
        if (!new File(caminhoBanco).isFile()) {
            sair("Erro: banco de dados nao encontrado: " + caminhoBanco);
        }

        byte[] chaveCifrada = null;
        try {
            chaveCifrada = Files.readAllBytes(Paths.get(args[0]));
        } catch (Exception e) {
            sair("Erro: caminho do arquivo da chave privada invalido: " + args[0]);
        }

        Console console = System.console();
        if (console == null) {
            sair("Erro: execute o logView em um terminal (a frase secreta e lida do teclado, sem eco).");
        }
        char[] frase = console.readPassword("Frase secreta da chave privada: ");
        if (frase == null) {
            sair("Erro: frase secreta nao informada.");
        }

        BancoDeDados banco = null;
        try {
            banco = new BancoDeDados(caminhoBanco);
            Usuario administrador = autenticarAdministrador(banco, chaveCifrada, frase);
            System.out.println("Administrador autenticado: " + administrador.login);
            System.out.println();
            exibirRegistros(banco.listarRegistros());
        } catch (SQLException e) {
            sair("Erro ao abrir o banco de dados: " + e.getMessage());
        } catch (GeneralSecurityException e) {
            sair("Autenticacao negada: " + e.getMessage());
        } finally {
            Arrays.fill(frase, '\0');
            Arrays.fill(chaveCifrada, (byte) 0);
            if (banco != null) {
                banco.fechar();
            }
        }
    }

    /** A chave deve ser decriptada pela frase e formar par com o certificado de um administrador. */
    private static Usuario autenticarAdministrador(BancoDeDados banco, byte[] chaveCifrada, char[] frase)
            throws GeneralSecurityException {
        PrivateKey chavePrivada;
        try {
            chavePrivada = Criptografia.restaurarChavePrivada(chaveCifrada, frase);
        } catch (GeneralSecurityException e) {
            throw new GeneralSecurityException("frase secreta invalida para a chave privada informada.");
        }
        for (Usuario administrador : banco.listarAdministradores()) {
            X509Certificate certificado = Criptografia.carregarCertificado(banco.buscarCertificado(administrador.kid));
            if (Criptografia.verificarParDeChaves(chavePrivada, certificado.getPublicKey(), TAMANHO_TESTE_ASSINATURA)) {
                return administrador;
            }
        }
        throw new GeneralSecurityException("a chave privada nao pertence a um administrador do sistema "
                + "(assinatura digital invalida).");
    }

    /** Mensagens de registro em ordem cronologica, com data, hora e codigo. */
    private static void exibirRegistros(List<BancoDeDados.LinhaRegistro> registros) {
        SimpleDateFormat formatoBanco = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        SimpleDateFormat formatoTela = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        System.out.println("Data       Hora      Codigo  Mensagem");
        System.out.println("---------- --------  ------  --------");
        for (BancoDeDados.LinhaRegistro registro : registros) {
            String dataHora;
            try {
                dataHora = formatoTela.format(formatoBanco.parse(registro.dataHora));
            } catch (ParseException e) {
                dataHora = registro.dataHora;
            }
            String mensagem = registro.texto
                    .replace("<login_name>", registro.login != null ? registro.login : "-")
                    .replace("<arq_name>", registro.arquivo != null ? registro.arquivo : "-");
            System.out.println(dataHora + "  " + registro.mid + "    " + mensagem);
        }
        System.out.println();
        System.out.println("Total de registros: " + registros.size());
    }

    private static void sair(String mensagem) {
        System.err.println(mensagem);
        System.exit(1);
    }
}
