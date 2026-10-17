/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PastaSegura {

    static class Arquivo {
        final String nomeCodigo;
        final String nomeSecreto;
        final String dono;
        final String grupo;
        final List<String> compartilhamentos = new ArrayList<String>();

        Arquivo(String nomeCodigo, String nomeSecreto, String dono, String grupo) {
            this.nomeCodigo = nomeCodigo;
            this.nomeSecreto = nomeSecreto;
            this.dono = dono;
            this.grupo = grupo;
        }
    }

    static class FalhaPastaSegura extends Exception {
        private static final long serialVersionUID = 1L;

        FalhaPastaSegura(String mensagem) {
            super(mensagem);
        }
    }

    private final BancoDeDados banco;
    private final PrivateKey chavePrivadaAdmin;
    private final PublicKey chavePublicaAdmin;

    PastaSegura(BancoDeDados banco, PrivateKey chavePrivadaAdmin, PublicKey chavePublicaAdmin) {
        this.banco = banco;
        this.chavePrivadaAdmin = chavePrivadaAdmin;
        this.chavePublicaAdmin = chavePublicaAdmin;
    }

    List<Arquivo> listar(File pasta, Usuario usuario) throws FalhaPastaSegura {
        String index = abrirArquivoDeControle(pasta, "index", "indice", usuario.login, 7005, 7006, 7007, 7008);
        String share = abrirArquivoDeControle(pasta, "share", "share", usuario.login, 7010, 7011, 7012, 7013);

        Map<String, List<String>> compartilhados = new HashMap<String, List<String>>();
        for (String[] campos : linhas(share, 3)) {
            if (campos[2].equalsIgnoreCase(usuario.login) && campos[1].matches("\\d+")) {
                List<String> ids = compartilhados.get(campos[0]);
                if (ids == null) {
                    ids = new ArrayList<String>();
                    compartilhados.put(campos[0], ids);
                }
                ids.add(campos[1]);
            }
        }

        List<Arquivo> visiveis = new ArrayList<Arquivo>();
        for (String[] campos : linhas(index, 4)) {
            Arquivo arquivo = new Arquivo(campos[0], campos[1], campos[2], campos[3]);
            List<String> ids = compartilhados.get(arquivo.nomeCodigo);
            if (ids != null) {
                arquivo.compartilhamentos.addAll(ids);
            }
            boolean dono = arquivo.dono.equalsIgnoreCase(usuario.login);
            boolean mesmoGrupo = normalizar(arquivo.grupo).equals(normalizar(usuario.nomeGrupo));
            if (dono || mesmoGrupo || !arquivo.compartilhamentos.isEmpty()) {
                visiveis.add(arquivo);
            }
        }
        return visiveis;
    }

    File decriptarArquivo(File pasta, Arquivo arquivo, Usuario usuario, PrivateKey chavePrivadaUsuario)
            throws FalhaPastaSegura {
        String login = usuario.login;
        String nome = arquivo.nomeSecreto;

        String envelope;
        if (arquivo.dono.equalsIgnoreCase(login)) {
            envelope = arquivo.nomeCodigo + ".env";
        } else if (!arquivo.compartilhamentos.isEmpty()) {
            envelope = arquivo.nomeCodigo + "." + arquivo.compartilhamentos.get(0) + ".shr";
        } else {
            banco.registrar(7017, login, nome);
            throw new FalhaPastaSegura("Voce nao tem permissao de acesso ao arquivo " + nome
                    + ": ele nao pertence a voce nem foi compartilhado com voce.");
        }
        banco.registrar(7016, login, nome);

        byte[] conteudo;
        try {
            conteudo = decriptar(pasta, arquivo.nomeCodigo, envelope, chavePrivadaUsuario);
        } catch (Exception e) {
            banco.registrar(7020, login, nome);
            throw new FalhaPastaSegura("Falha na decriptacao do arquivo " + nome + " (sigilo): o envelope "
                    + "digital nao pode ser aberto com a sua chave privada ou o criptograma esta corrompido.");
        }
        banco.registrar(7018, login, nome);

        PublicKey chaveDono = null;
        try {
            String pemDono = banco.buscarCertificadoPorLogin(arquivo.dono);
            if (pemDono != null) {
                chaveDono = Criptografia.carregarCertificado(pemDono).getPublicKey();
            }
        } catch (GeneralSecurityException e) {
            chaveDono = null;
        }
        if (chaveDono == null || !assinaturaValida(pasta, arquivo.nomeCodigo, conteudo, chaveDono)) {
            Arrays.fill(conteudo, (byte) 0);
            banco.registrar(7021, login, nome);
            throw new FalhaPastaSegura("Falha na verificacao de integridade e autenticidade do arquivo " + nome
                    + (chaveDono == null ? ": o dono do arquivo nao esta cadastrado no sistema." : "."));
        }
        banco.registrar(7019, login, nome);

        File saida = new File(pasta, nomeSeguro(nome));
        try {
            Files.write(saida.toPath(), conteudo);
        } catch (IOException e) {
            throw new FalhaPastaSegura("Arquivo " + nome + " decriptado e verificado, mas nao foi possivel "
                    + "grava-lo em " + saida.getPath() + ": " + e.getMessage());
        } finally {
            Arrays.fill(conteudo, (byte) 0);
        }
        return saida;
    }

    private String abrirArquivoDeControle(File pasta, String nomeCodigo, String descricao, String login,
                                          int midDecriptado, int midVerificado,
                                          int midFalhaDecriptacao, int midFalhaVerificacao)
            throws FalhaPastaSegura {
        byte[] conteudo;
        try {
            conteudo = decriptar(pasta, nomeCodigo, nomeCodigo + ".env", chavePrivadaAdmin);
        } catch (Exception e) {
            banco.registrar(midFalhaDecriptacao, login, null);
            throw new FalhaPastaSegura("Falha na decriptacao do arquivo de " + descricao + " (" + nomeCodigo
                    + ".enc / " + nomeCodigo + ".env) da pasta informada.");
        }
        banco.registrar(midDecriptado, login, null);

        if (!assinaturaValida(pasta, nomeCodigo, conteudo, chavePublicaAdmin)) {
            banco.registrar(midFalhaVerificacao, login, null);
            throw new FalhaPastaSegura("Falha na verificacao de integridade e autenticidade do arquivo de "
                    + descricao + " (" + nomeCodigo + ".asd).");
        }
        banco.registrar(midVerificado, login, null);
        return new String(conteudo, StandardCharsets.US_ASCII);
    }

    private static byte[] decriptar(File pasta, String nomeCodigo, String nomeEnvelope, PrivateKey chave)
            throws IOException, GeneralSecurityException {
        byte[] semente = Criptografia.abrirEnvelope(ler(pasta, nomeEnvelope), chave);
        try {
            return Criptografia.decifrarAES(Criptografia.gerarChaveAES(semente), ler(pasta, nomeCodigo + ".enc"));
        } finally {
            Arrays.fill(semente, (byte) 0);
        }
    }

    private static boolean assinaturaValida(File pasta, String nomeCodigo, byte[] conteudo, PublicKey chave) {
        try {
            return Criptografia.verificarAssinatura(conteudo, ler(pasta, nomeCodigo + ".asd"), chave);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] ler(File pasta, String nome) throws IOException {
        return Files.readAllBytes(new File(pasta, nome).toPath());
    }

    private static List<String[]> linhas(String texto, int quantidadeCampos) {
        List<String[]> resultado = new ArrayList<String[]>();
        for (String linha : texto.split("\\r?\\n")) {
            String[] campos = linha.trim().split("\\s+");
            if (campos.length == quantidadeCampos && !campos[0].isEmpty()) {
                resultado.add(campos);
            }
        }
        return resultado;
    }

    static String normalizar(String texto) {
        String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcentos.toLowerCase(Locale.ROOT);
    }

    private static String nomeSeguro(String nomeSecreto) throws FalhaPastaSegura {
        String nome = new File(nomeSecreto).getName();
        if (nome.isEmpty() || nome.equals(".") || nome.equals("..")) {
            throw new FalhaPastaSegura("Nome secreto de arquivo invalido: " + nomeSecreto);
        }
        return nome;
    }
}
