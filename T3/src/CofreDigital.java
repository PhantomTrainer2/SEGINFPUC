/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 *
 * Uso:
 *   java -jar cofre.jar
 *   (ou: java -cp build/classes:lib/bcprov-jdk18on-1.86.jar:lib/sqlite-jdbc-3.53.4.0.jar CofreDigital)
 *
 * O banco de dados (cofre.db) e criado/aberto no diretorio corrente.
 */

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Arrays;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;

/**
 * Janela principal do Cofre Digital: partida do sistema, navegacao entre as
 * telas e estado da execucao (chave privada do administrador em memoria e
 * usuario corrente).
 */
public class CofreDigital extends JFrame {

    private static final long serialVersionUID = 1L;

    final BancoDeDados banco;
    final Autenticador autenticador;
    final ServicoCadastro servicoCadastro;

    // Chave privada do administrador: necessaria para abrir os envelopes dos
    // arquivos index e share. Mantida apenas em memoria durante a execucao.
    private PrivateKey chavePrivadaAdmin;
    private X509Certificate certificadoAdmin;

    // Usuario em autenticacao ou com sessao ativa
    private Usuario usuarioAtual;
    private boolean sessaoAtiva;

    CofreDigital(BancoDeDados banco) {
        super("Cofre Digital");
        this.banco = banco;
        this.autenticador = new Autenticador(banco);
        this.servicoCadastro = new ServicoCadastro(banco);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent evento) {
                encerrarSistema();
            }
        });
    }

    public static void main(String[] args) {
        final BancoDeDados banco;
        try {
            banco = new BancoDeDados(BancoDeDados.ARQUIVO_PADRAO);
        } catch (SQLException e) {
            System.err.println("Erro ao abrir o banco de dados " + BancoDeDados.ARQUIVO_PADRAO + ": " + e.getMessage());
            System.exit(1);
            return;
        }

        // Erros inesperados (ex.: falha no banco) sao apresentados ao usuario
        Thread.setDefaultUncaughtExceptionHandler((thread, erro) -> {
            erro.printStackTrace();
            SwingUtilities.invokeLater(() -> Componentes.erro(null, "Erro interno: " + erro.getMessage()));
        });

        banco.registrar(1001, null, null);
        SwingUtilities.invokeLater(() -> new CofreDigital(banco).partida());
    }

    /**
     * Partida do sistema: sem usuarios, cadastra o administrador; caso
     * contrario, valida a frase secreta da chave privada do administrador.
     */
    private void partida() {
        if (banco.contarUsuarios() == 0) {
            banco.registrar(1005, null, null);
            mostrarTela(new TelaCadastro(this, true));
        } else {
            banco.registrar(1006, null, null);
            if (!validarFraseAdministrador()) {
                encerrarSistema();
                return;
            }
            iniciarAutenticacao();
        }
        setVisible(true);
    }

    private boolean validarFraseAdministrador() {
        Usuario admin = banco.buscarAdministradorDoSistema();
        JPasswordField campoFrase = new JPasswordField(30);
        Componentes.limitar(campoFrase, 255);
        Componentes.focarAoExibir(campoFrase);
        Object[] conteudo = {
            new JLabel("Informe a frase secreta da chave privada do administrador"),
            new JLabel("(" + admin.login + "):"),
            campoFrase
        };
        int opcao = JOptionPane.showConfirmDialog(null, conteudo, "Cofre Digital - Partida do sistema",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        char[] frase = campoFrase.getPassword();
        try {
            if (opcao != JOptionPane.OK_OPTION) {
                Componentes.erro(null, "Partida do sistema cancelada. O sistema sera encerrado.");
                return false;
            }
            chavePrivadaAdmin = validarChaveDoUsuario(admin, frase);
            certificadoAdmin = carregarCertificado(admin);
            return true;
        } catch (ChavePrivadaInvalidaException e) {
            Componentes.erro(null, "Chave privada do administrador verificada negativamente: " + e.getMessage()
                    + "\nO sistema sera encerrado.");
            return false;
        } finally {
            Arrays.fill(frase, '\0');
        }
    }

    /**
     * Restaura a chave privada do usuario (guardada cifrada no Chaveiro) com
     * a frase secreta e verifica o par com o certificado digital.
     */
    PrivateKey validarChaveDoUsuario(Usuario usuario, char[] frase) throws ChavePrivadaInvalidaException {
        return Criptografia.validarChavePrivada(banco.buscarChavePrivada(usuario.kid), frase,
                carregarCertificado(usuario).getPublicKey(), ServicoCadastro.TAMANHO_TESTE_ASSINATURA);
    }

    private X509Certificate carregarCertificado(Usuario usuario) {
        try {
            return Criptografia.carregarCertificado(banco.buscarCertificado(usuario.kid));
        } catch (CertificateException e) {
            throw new IllegalStateException("Certificado digital invalido no Chaveiro: " + e.getMessage(), e);
        }
    }

    /** Chaves do administrador cadastrado na partida do sistema. */
    void definirChavesAdministrador(PrivateKey chavePrivada, X509Certificate certificado) {
        this.chavePrivadaAdmin = chavePrivada;
        this.certificadoAdmin = certificado;
    }

    PastaSegura criarPastaSegura() {
        return new PastaSegura(banco, chavePrivadaAdmin, certificadoAdmin.getPublicKey());
    }

    Usuario getUsuarioAtual() {
        return usuarioAtual;
    }

    String loginAtual() {
        return usuarioAtual == null ? null : usuarioAtual.login;
    }

    void registrar(int mid) {
        banco.registrar(mid, loginAtual(), null);
    }

    void registrar(int mid, String arquivo) {
        banco.registrar(mid, loginAtual(), arquivo);
    }

    void mostrarTela(JComponent tela) {
        setContentPane(tela);
        setMinimumSize(null);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
        tela.revalidate();
        repaint();
    }

    // ---- Autenticacao ----

    void iniciarAutenticacao() {
        usuarioAtual = null;
        sessaoAtiva = false;
        setTitle("Cofre Digital - Autenticacao");
        registrar(2001);
        mostrarTela(new TelaLogin(this));
    }

    void iniciarEtapa2(Usuario usuario) {
        usuarioAtual = usuario;
        registrar(2002);
        registrar(3001);
        mostrarTela(new TelaSenha(this, usuario));
    }

    void iniciarEtapa3(char[] senha) {
        registrar(3002);
        registrar(4001);
        mostrarTela(new TelaToken(this, usuarioAtual, senha));
    }

    /** Retorna a primeira etapa apos o bloqueio do usuario na etapa 2 ou 3. */
    void voltarParaEtapa1(int midEncerramentoEtapa) {
        registrar(midEncerramentoEtapa);
        iniciarAutenticacao();
    }

    void iniciarSessao() {
        registrar(4002);
        sessaoAtiva = true;
        banco.incrementarAcessos(usuarioAtual.uid);
        usuarioAtual.totalAcessos++;
        registrar(1003);
        setTitle("Cofre Digital");
        mostrarTelaPrincipal();
    }

    // ---- Telas da sessao ----

    void mostrarTelaPrincipal() {
        registrar(5001);
        mostrarTela(new TelaPrincipal(this, usuarioAtual));
    }

    void mostrarTelaCadastro() {
        registrar(6001);
        mostrarTela(new TelaCadastro(this, false));
    }

    void mostrarTelaConsulta() {
        registrar(7001);
        mostrarTela(new TelaConsulta(this, usuarioAtual));
    }

    void mostrarTelaSaida() {
        registrar(8001);
        mostrarTela(new TelaSaida(this, usuarioAtual));
    }

    void encerrarSessao() {
        registrar(1004);
        iniciarAutenticacao();
    }

    /** Encerra a execucao apagando da memoria a chave privada do administrador. */
    void encerrarSistema() {
        if (sessaoAtiva) {
            registrar(1004);
        }
        registrar(1002);
        chavePrivadaAdmin = null;
        certificadoAdmin = null;
        usuarioAtual = null;
        banco.fechar();
        dispose();
        System.exit(0);
    }
}
