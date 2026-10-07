/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/**
 * Autenticacao - etapa 1: identificacao do usuario pelo login name (e-mail).
 */
class TelaLogin extends JPanel {

    private static final long serialVersionUID = 1L;

    private final CofreDigital janela;
    private final JTextField campoLogin = new JTextField(28);

    TelaLogin(CofreDigital janela) {
        super(new BorderLayout(0, 15));
        this.janela = janela;
        setBorder(new EmptyBorder(20, 25, 20, 25));

        Componentes.limitar(campoLogin, 255);
        Componentes.focarAoExibir(campoLogin);
        JPanel linha = new JPanel(new FlowLayout(FlowLayout.LEFT));
        linha.add(new JLabel("Login name:"));
        linha.add(campoLogin);

        JButton botaoOk = new JButton("OK");
        JButton botaoLimpar = new JButton("LIMPAR");
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        botoes.add(botaoOk);
        botoes.add(botaoLimpar);

        add(Componentes.titulo("Etapa 1 de 3: identificacao do usuario"), BorderLayout.NORTH);
        add(linha, BorderLayout.CENTER);
        add(botoes, BorderLayout.SOUTH);

        botaoOk.addActionListener(e -> confirmar());
        campoLogin.addActionListener(e -> confirmar());
        botaoLimpar.addActionListener(e -> {
            campoLogin.setText("");
            campoLogin.requestFocusInWindow();
        });
    }

    private void confirmar() {
        String login = campoLogin.getText().trim();
        Usuario usuario = janela.autenticador.identificarUsuario(login);
        if (usuario == null) {
            if (!Autenticador.loginValido(login)) {
                Componentes.erro(this, "Login name invalido: informe um e-mail valido.");
            } else {
                Componentes.erro(this, "Login name nao identificado: " + login);
            }
            campoLogin.requestFocusInWindow();
            return;
        }
        if (usuario.estaBloqueado()) {
            Componentes.erro(this, "O acesso do usuario " + usuario.login + " esta bloqueado. Tente novamente em "
                    + usuario.segundosRestantesBloqueio() + " segundos.");
            return;
        }
        janela.iniciarEtapa2(usuario);
    }
}
