/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;

/**
 * Componentes e utilitarios de interface compartilhados pelas telas.
 */
final class Componentes {

    private Componentes() {
    }

    /** Limita o campo a uma quantidade maxima de caracteres. */
    static void limitar(JTextComponent campo, int maximo) {
        ((AbstractDocument) campo.getDocument()).setDocumentFilter(new FiltroCampo(maximo, false));
    }

    /** Limita o campo a uma quantidade maxima de digitos (descarta o resto). */
    static void limitarDigitos(JTextComponent campo, int maximo) {
        ((AbstractDocument) campo.getDocument()).setDocumentFilter(new FiltroCampo(maximo, true));
    }

    private static class FiltroCampo extends DocumentFilter {
        private final int maximo;
        private final boolean somenteDigitos;

        FiltroCampo(int maximo, boolean somenteDigitos) {
            this.maximo = maximo;
            this.somenteDigitos = somenteDigitos;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String texto, AttributeSet atributos)
                throws BadLocationException {
            replace(fb, offset, 0, texto, atributos);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int tamanho, String texto, AttributeSet atributos)
                throws BadLocationException {
            String novo = texto == null ? "" : texto;
            if (somenteDigitos) {
                novo = novo.replaceAll("[^0-9]", "");
            }
            int disponivel = maximo - (fb.getDocument().getLength() - tamanho);
            if (novo.length() > disponivel) {
                novo = novo.substring(0, Math.max(0, disponivel));
            }
            super.replace(fb, offset, tamanho, novo, atributos);
        }
    }

    static JLabel titulo(String texto) {
        JLabel rotulo = new JLabel(texto);
        rotulo.setFont(rotulo.getFont().deriveFont(Font.BOLD, 14f));
        return rotulo;
    }

    /**
     * Monta uma tela no formato do enunciado: cabecalho (login, grupo e nome),
     * corpo 1 e corpo 2. Sem usuario (partida do sistema) o cabecalho e um titulo.
     */
    static JPanel montarTela(Usuario usuario, JComponent corpo1, JComponent corpo2) {
        JPanel cabecalho;
        if (usuario != null) {
            cabecalho = coluna(new JLabel("Login: " + usuario.login),
                    new JLabel("Grupo: " + usuario.nomeGrupo),
                    new JLabel("Nome: " + usuario.nome));
        } else {
            cabecalho = coluna(titulo("Partida do sistema: cadastro do administrador"));
        }

        JPanel topo = coluna(cabecalho, new JSeparator(), corpo1, new JSeparator());
        JPanel tela = new JPanel(new BorderLayout(0, 10));
        tela.setBorder(new EmptyBorder(15, 20, 15, 20));
        tela.add(topo, BorderLayout.NORTH);
        tela.add(corpo2, BorderLayout.CENTER);
        return tela;
    }

    /** Empilha os componentes verticalmente, alinhados a esquerda. */
    static JPanel coluna(JComponent... itens) {
        JPanel painel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 0, 3, 0);
        for (int i = 0; i < itens.length; i++) {
            c.gridy = i;
            painel.add(itens[i], c);
        }
        return painel;
    }

    /** Botao "..." que preenche o campo com um arquivo (ou pasta) escolhido. */
    static JButton botaoProcurar(final JTextField campo, final boolean somentePastas) {
        JButton botao = new JButton("...");
        botao.setMargin(new Insets(2, 6, 2, 6));
        botao.setToolTipText(somentePastas ? "Escolher pasta" : "Escolher arquivo");
        botao.addActionListener(e -> {
            String atual = campo.getText().trim();
            JFileChooser seletor = new JFileChooser(atual.isEmpty() ? new File(".") : new File(atual));
            if (somentePastas) {
                seletor.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            }
            if (seletor.showOpenDialog(botao) == JFileChooser.APPROVE_OPTION) {
                campo.setText(seletor.getSelectedFile().getPath());
            }
        });
        return botao;
    }

    /** Coloca o foco no campo assim que ele aparecer na tela. */
    static void focarAoExibir(final JComponent campo) {
        campo.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent evento) {
                SwingUtilities.invokeLater(() -> campo.requestFocusInWindow());
                campo.removeAncestorListener(this);
            }

            @Override
            public void ancestorRemoved(AncestorEvent evento) {
            }

            @Override
            public void ancestorMoved(AncestorEvent evento) {
            }
        });
    }

    static void informar(Component pai, String mensagem) {
        JOptionPane.showMessageDialog(pai, quebrarLinhas(mensagem), "Cofre Digital", JOptionPane.INFORMATION_MESSAGE);
    }

    static void erro(Component pai, String mensagem) {
        JOptionPane.showMessageDialog(pai, quebrarLinhas(mensagem), "Cofre Digital", JOptionPane.ERROR_MESSAGE);
    }

    /** Mensagens longas sao apresentadas com quebra automatica de linha. */
    private static JLabel quebrarLinhas(String mensagem) {
        String html = mensagem.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
        return new JLabel("<html><body style='width: 360px'>" + html + "</body></html>");
    }
}
