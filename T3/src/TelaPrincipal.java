/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Tela Principal: total de acessos do usuario e Menu Principal. A opcao de
 * cadastro so e apresentada para o grupo Administrador.
 */
class TelaPrincipal extends JPanel {

    private static final long serialVersionUID = 1L;

    TelaPrincipal(final CofreDigital janela, Usuario usuario) {
        super(new BorderLayout());

        List<JComponent> menu = new ArrayList<JComponent>();
        menu.add(Componentes.titulo("Menu Principal:"));
        if (usuario.isAdministrador()) {
            menu.add(botao("1 - Cadastrar um novo usuario", () -> {
                janela.registrar(5002);
                janela.mostrarTelaCadastro();
            }));
        }
        menu.add(botao("2 - Consultar pasta de arquivos secretos do usuario", () -> {
            janela.registrar(5003);
            janela.mostrarTelaConsulta();
        }));
        menu.add(botao("3 - Sair do Sistema", () -> {
            janela.registrar(5004);
            janela.mostrarTelaSaida();
        }));

        JPanel corpo2 = new JPanel(new BorderLayout());
        corpo2.add(Componentes.coluna(menu.toArray(new JComponent[0])), BorderLayout.NORTH);
        add(Componentes.montarTela(usuario, new JLabel("Total de acessos do usuario: " + usuario.totalAcessos),
                corpo2), BorderLayout.CENTER);
    }

    private static JButton botao(String texto, Runnable acao) {
        JButton botao = new JButton(texto);
        botao.setHorizontalAlignment(SwingConstants.LEFT);
        botao.addActionListener(e -> acao.run());
        return botao;
    }
}
