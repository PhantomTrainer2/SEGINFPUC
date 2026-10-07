/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/**
 * Autenticacao - etapa 3: codigo TOTP gerado pelo Google Authenticator.
 */
class TelaToken extends JPanel {

    private static final long serialVersionUID = 1L;

    private final CofreDigital janela;
    private final Usuario usuario;
    // Senha pessoal verificada na etapa 2: deriva a chave AES que decripta a chave TOTP
    private final char[] senha;
    private final JTextField campoToken = new JTextField(8);

    TelaToken(CofreDigital janela, Usuario usuario, char[] senha) {
        super(new BorderLayout(0, 15));
        this.janela = janela;
        this.usuario = usuario;
        this.senha = senha;
        setBorder(new EmptyBorder(20, 25, 20, 25));

        Componentes.limitarDigitos(campoToken, 6);
        Componentes.focarAoExibir(campoToken);
        JPanel linha = new JPanel(new FlowLayout(FlowLayout.LEFT));
        linha.add(new JLabel("TOTP:"));
        linha.add(campoToken);

        JButton botaoOk = new JButton("OK");
        JButton botaoLimpar = new JButton("LIMPAR");
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        botoes.add(botaoOk);
        botoes.add(botaoLimpar);

        add(Componentes.coluna(Componentes.titulo("Etapa 3 de 3: token"),
                new JLabel("Usuario: " + usuario.login),
                new JLabel("Informe o codigo de 6 digitos do Google Authenticator.")), BorderLayout.NORTH);
        add(linha, BorderLayout.CENTER);
        add(botoes, BorderLayout.SOUTH);

        botaoOk.addActionListener(e -> confirmar());
        campoToken.addActionListener(e -> confirmar());
        botaoLimpar.addActionListener(e -> {
            campoToken.setText("");
            campoToken.requestFocusInWindow();
        });
    }

    private void confirmar() {
        if (janela.autenticador.verificarToken(usuario, senha, campoToken.getText().trim())) {
            Arrays.fill(senha, '\0');
            janela.iniciarSessao();
            return;
        }
        campoToken.setText("");
        if (usuario.estaBloqueado()) {
            Arrays.fill(senha, '\0');
            Componentes.erro(this, "Token invalido. Terceiro erro consecutivo: o acesso foi bloqueado por 2 minutos.");
            janela.voltarParaEtapa1(4002);
            return;
        }
        Componentes.erro(this, "Token invalido (erro " + usuario.errosToken + " de " + Autenticador.MAX_ERROS + ").");
        campoToken.requestFocusInWindow();
    }
}
