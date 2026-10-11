/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 * Tela de Cadastro: total de usuarios do sistema e Formulario de Cadastro.
 * Tambem usada na partida do sistema para cadastrar o administrador.
 */
class TelaCadastro extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String[] GRUPOS = {"Administrador", "Usu\u00e1rio"};

    private final CofreDigital janela;
    private final boolean partida;

    private final JLabel totalUsuarios = new JLabel();
    private final JTextField campoCertificado = new JTextField(30);
    private final JTextField campoChave = new JTextField(30);
    private final JPasswordField campoFrase = new JPasswordField(30);
    private final JComboBox<String> comboGrupo = new JComboBox<String>(GRUPOS);
    private final JPasswordField campoSenha = new JPasswordField(12);
    private final JPasswordField campoConfirmacao = new JPasswordField(12);

    TelaCadastro(final CofreDigital janela, boolean partida) {
        super(new BorderLayout());
        this.janela = janela;
        this.partida = partida;

        Componentes.limitar(campoCertificado, 255);
        Componentes.limitar(campoChave, 255);
        Componentes.limitar(campoFrase, 255);
        Componentes.limitar(campoSenha, 10);
        Componentes.limitar(campoConfirmacao, 10);
        // O primeiro usuario do sistema e o administrador; depois o padrao e Usuario
        comboGrupo.setSelectedIndex(partida ? 0 : 1);
        comboGrupo.setEnabled(!partida);

        JPanel formulario = new JPanel(new GridBagLayout());
        adicionarLinha(formulario, 0, "Caminho do arquivo do certificado digital:", campoCertificado,
                Componentes.botaoProcurar(campoCertificado, false));
        adicionarLinha(formulario, 1, "Caminho do arquivo da chave privada:", campoChave,
                Componentes.botaoProcurar(campoChave, false));
        adicionarLinha(formulario, 2, "Frase secreta:", campoFrase, null);
        adicionarLinha(formulario, 3, "Grupo:", comboGrupo, null);
        adicionarLinha(formulario, 4, "Senha pessoal:", campoSenha, null);
        adicionarLinha(formulario, 5, "Confirma\u00e7\u00e3o senha pessoal:", campoConfirmacao, null);

        JButton botaoCadastrar = new JButton("Cadastrar");
        botaoCadastrar.addActionListener(e -> cadastrar());
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        botoes.add(botaoCadastrar);
        if (!partida) {
            JButton botaoVoltar = new JButton("Voltar de Cadastrar para o Menu Principal");
            botaoVoltar.addActionListener(e -> {
                janela.registrar(6010);
                janela.mostrarTelaPrincipal();
            });
            botoes.add(new JLabel("   "));
            botoes.add(botaoVoltar);
        }

        JPanel corpo2 = new JPanel(new BorderLayout(0, 10));
        corpo2.add(Componentes.titulo("Formul\u00e1rio de Cadastro:"), BorderLayout.NORTH);
        corpo2.add(formulario, BorderLayout.CENTER);
        corpo2.add(botoes, BorderLayout.SOUTH);

        atualizarTotal();
        add(Componentes.montarTela(partida ? null : janela.getUsuarioAtual(), totalUsuarios, corpo2),
                BorderLayout.CENTER);
        Componentes.focarAoExibir(campoCertificado);
    }

    private static void adicionarLinha(JPanel painel, int linha, String rotulo, JComponent campo, JComponent extra) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = linha;
        c.insets = new Insets(4, 0, 4, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        painel.add(new JLabel(rotulo), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = campo instanceof JTextField ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        painel.add(campo, c);
        if (extra != null) {
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(4, 0, 4, 0);
            painel.add(extra, c);
        }
    }

    private void atualizarTotal() {
        totalUsuarios.setText("Total de usu\u00e1rios do sistema: " + janela.banco.contarUsuarios());
    }

    private void cadastrar() {
        janela.registrar(6002);
        char[] frase = campoFrase.getPassword();
        char[] senha = campoSenha.getPassword();
        char[] confirmacao = campoConfirmacao.getPassword();
        ServicoCadastro.DadosCadastro dados = null;
        try {
            int gid = comboGrupo.getSelectedIndex() == 0 ? BancoDeDados.GID_ADMINISTRADOR : BancoDeDados.GID_USUARIO;
            dados = janela.servicoCadastro.validar(janela.loginAtual(), campoCertificado.getText(),
                    campoChave.getText(), frase, gid, senha, confirmacao);

            if (!confirmarDados(dados)) {
                janela.registrar(6009);
                return;
            }
            janela.registrar(6008);

            String chaveTotp = janela.servicoCadastro.efetivar(dados);
            mostrarChaveTotp(dados.login, chaveTotp);

            if (partida) {
                // A chave privada do administrador permanece em memoria durante a execucao
                janela.definirChavesAdministrador(dados.chavePrivada, dados.certificado);
                janela.iniciarAutenticacao();
                return;
            }
            limparFormulario();
            atualizarTotal();
        } catch (ServicoCadastro.CadastroException e) {
            // O formulario permanece preenchido para correcao
            Componentes.erro(this, e.getMessage());
        } finally {
            Arrays.fill(frase, '\0');
            Arrays.fill(senha, '\0');
            Arrays.fill(confirmacao, '\0');
            if (dados != null) {
                dados.limpar();
            }
        }
    }

    /** Tela de confirmacao com os campos do certificado digital. */
    private boolean confirmarDados(ServicoCadastro.DadosCadastro dados) {
        X509Certificate certificado = dados.certificado;
        SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        String[][] campos = {
            {"Vers\u00e3o:", String.valueOf(certificado.getVersion())},
            {"S\u00e9rie:", certificado.getSerialNumber() + " (0x"
                    + certificado.getSerialNumber().toString(16).toUpperCase() + ")"},
            {"Validade:", formato.format(certificado.getNotBefore()) + " a "
                    + formato.format(certificado.getNotAfter())},
            {"Tipo de Assinatura:", certificado.getSigAlgName()},
            {"Emissor:", Criptografia.nomeDistinto(certificado.getIssuerX500Principal())},
            {"Sujeito (Friendly Name):", dados.nome},
            {"E-mail:", dados.login},
            {"Grupo:", GRUPOS[dados.gid == BancoDeDados.GID_ADMINISTRADOR ? 0 : 1]},
        };

        JPanel painel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 10);
        c.gridy = 0;
        c.gridwidth = 2;
        painel.add(Componentes.titulo("Confirme os dados do certificado digital:"), c);
        c.gridwidth = 1;
        for (String[] campo : campos) {
            c.gridy++;
            c.gridx = 0;
            painel.add(new JLabel(campo[0]), c);
            c.gridx = 1;
            painel.add(new JLabel(campo[1]), c);
        }

        Object[] opcoes = {"Confirmar", "Rejeitar"};
        int escolha = JOptionPane.showOptionDialog(this, painel, "Cofre Digital - Confirmacao de dados",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opcoes, opcoes[0]);
        return escolha == 0;
    }

    /** Apresenta a chave TOTP (BASE32) para cadastro no Google Authenticator. */
    private void mostrarChaveTotp(String login, String chaveBase32) {
        JTextField campoChaveTotp = new JTextField(chaveBase32);
        campoChaveTotp.setEditable(false);
        JTextField campoUri = new JTextField("otpauth://totp/Cofre%20Digital:" + login + "?secret=" + chaveBase32, 40);
        campoUri.setEditable(false);
        Object[] conteudo = {
            new JLabel("Usuario " + login + " cadastrado com sucesso."),
            new JLabel(" "),
            new JLabel("Cadastre a chave secreta abaixo no Google Authenticator"),
            new JLabel("(\"Inserir chave de configuracao\", tipo \"Baseada no horario\"):"),
            campoChaveTotp,
            new JLabel("URI otpauth equivalente:"),
            campoUri
        };
        JOptionPane.showMessageDialog(this, conteudo, "Cofre Digital - Chave TOTP",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void limparFormulario() {
        campoCertificado.setText("");
        campoChave.setText("");
        campoFrase.setText("");
        comboGrupo.setSelectedIndex(partida ? 0 : 1);
        campoSenha.setText("");
        campoConfirmacao.setText("");
        campoCertificado.requestFocusInWindow();
    }
}
