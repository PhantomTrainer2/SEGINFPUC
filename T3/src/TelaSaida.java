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

/**
 * Tela de Saida: confirma o encerramento da sessao ou do sistema.
 */
class TelaSaida extends JPanel {

    private static final long serialVersionUID = 1L;

    TelaSaida(final CofreDigital janela, Usuario usuario) {
        super(new BorderLayout());

        JButton botaoEncerrarSessao = new JButton("Encerrar Sessao");
        botaoEncerrarSessao.addActionListener(e -> {
            janela.registrar(8002);
            janela.encerrarSessao();
        });
        JButton botaoEncerrarSistema = new JButton("Encerrar Sistema");
        botaoEncerrarSistema.addActionListener(e -> {
            janela.registrar(8003);
            janela.encerrarSistema();
        });
        JButton botaoVoltar = new JButton("Voltar ao Menu Principal");
        botaoVoltar.addActionListener(e -> {
            janela.registrar(8004);
            janela.mostrarTelaPrincipal();
        });

        JPanel encerrar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        encerrar.add(botaoEncerrarSessao);
        encerrar.add(new JLabel("   "));
        encerrar.add(botaoEncerrarSistema);
        JPanel voltar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        voltar.add(botaoVoltar);

        JPanel corpo2 = new JPanel(new BorderLayout());
        corpo2.add(Componentes.coluna(Componentes.titulo("Saida do sistema:"),
                new JLabel("Pressione o botao Encerrar Sessao ou o botao Encerrar Sistema para confirmar."),
                encerrar, voltar), BorderLayout.NORTH);

        add(Componentes.montarTela(usuario, new JLabel("Total de acessos do usuario: " + usuario.totalAcessos),
                corpo2), BorderLayout.CENTER);
    }
}
