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

class TelaSaida extends JPanel {

    private static final long serialVersionUID = 1L;

    TelaSaida(final CofreDigital janela, Usuario usuario) {
        super(new BorderLayout());

        JButton botaoEncerrarSessao = new JButton("Encerrar Sess\u00e3o");
        botaoEncerrarSessao.addActionListener(e -> {
            janela.registrar(8002);
            janela.encerrarSessao();
        });
        JButton botaoEncerrarSistema = new JButton("Encerrar Sistema");
        botaoEncerrarSistema.addActionListener(e -> {
            janela.registrar(8003);
            janela.encerrarSistema();
        });
        JButton botaoVoltar = new JButton("Voltar de Sair para o Menu Principal");
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
        corpo2.add(Componentes.coluna(Componentes.titulo("Sa\u00edda do sistema:"),
                new JLabel("Pressione o bot\u00e3o Encerrar Sess\u00e3o ou o bot\u00e3o Encerrar Sistema para confirmar."),
                encerrar, voltar), BorderLayout.NORTH);

        add(Componentes.montarTela(usuario, new JLabel("Total de acessos do usu\u00e1rio: " + usuario.totalAcessos),
                corpo2), BorderLayout.CENTER);
    }
}
