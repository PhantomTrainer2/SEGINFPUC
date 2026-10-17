/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

class TelaSenha extends JPanel {

    private static final long serialVersionUID = 1L;

    private final CofreDigital janela;
    private final Usuario usuario;
    private final SecureRandom aleatorio = new SecureRandom();

    private final List<int[]> paresPressionados = new ArrayList<int[]>();
    private final int[][] distribuicao = new int[5][2];

    private final JButton[] botoesDigitos = new JButton[5];
    private final JPasswordField campoSenha = new JPasswordField(22);
    private final JButton botaoOk = new JButton("OK");
    private final JButton botaoLimpar = new JButton("LIMPAR");
    private final JLabel situacao = new JLabel(" ");

    TelaSenha(CofreDigital janela, Usuario usuario) {
        super(new BorderLayout(0, 12));
        this.janela = janela;
        this.usuario = usuario;
        setBorder(new EmptyBorder(20, 25, 20, 25));

        campoSenha.setEditable(false);
        campoSenha.setFocusable(false);
        JPanel linhaSenha = new JPanel(new FlowLayout(FlowLayout.LEFT));
        linhaSenha.add(new JLabel("Senha pessoal:"));
        linhaSenha.add(campoSenha);

        JPanel teclado = new JPanel(new GridLayout(2, 4, 8, 8));
        for (int i = 0; i < botoesDigitos.length; i++) {
            final int indice = i;
            botoesDigitos[i] = new JButton();
            botoesDigitos[i].setFont(botoesDigitos[i].getFont().deriveFont(Font.BOLD, 15f));
            botoesDigitos[i].setFocusable(false);
            botoesDigitos[i].addActionListener(e -> pressionar(indice));
        }
        teclado.add(botoesDigitos[0]);
        teclado.add(botoesDigitos[1]);
        teclado.add(botoesDigitos[2]);
        teclado.add(botaoOk);
        teclado.add(botoesDigitos[3]);
        teclado.add(botoesDigitos[4]);
        teclado.add(new JLabel());
        teclado.add(botaoLimpar);

        JPanel centro = new JPanel(new BorderLayout(0, 10));
        centro.add(linhaSenha, BorderLayout.NORTH);
        centro.add(teclado, BorderLayout.CENTER);
        centro.add(situacao, BorderLayout.SOUTH);

        add(Componentes.coluna(Componentes.titulo("Etapa 2 de 3: senha pessoal"),
                new JLabel("Usuario: " + usuario.login),
                new JLabel("Use o teclado virtual para digitar a senha (8 a 10 digitos).")), BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);

        botaoOk.addActionListener(e -> confirmar());
        botaoLimpar.addActionListener(e -> limpar());

        redistribuir();
        atualizar();
    }

    private void redistribuir() {
        List<Integer> digitos = new ArrayList<Integer>();
        for (int d = 0; d <= 9; d++) {
            digitos.add(d);
        }
        Collections.shuffle(digitos, aleatorio);
        for (int i = 0; i < botoesDigitos.length; i++) {
            distribuicao[i][0] = digitos.get(2 * i);
            distribuicao[i][1] = digitos.get(2 * i + 1);
            botoesDigitos[i].setText(distribuicao[i][0] + "   " + distribuicao[i][1]);
        }
    }

    private void pressionar(int indice) {
        if (paresPressionados.size() >= Autenticador.MAX_DIGITOS_SENHA) {
            return;
        }
        paresPressionados.add(distribuicao[indice].clone());
        redistribuir();
        atualizar();
    }

    private void limpar() {
        apagarPares();
        redistribuir();
        atualizar();
    }

    private void atualizar() {
        char[] mascara = new char[paresPressionados.size()];
        Arrays.fill(mascara, '*');
        campoSenha.setText(new String(mascara));
        int quantidade = paresPressionados.size();
        for (JButton botao : botoesDigitos) {
            botao.setEnabled(quantidade < Autenticador.MAX_DIGITOS_SENHA);
        }
        botaoOk.setEnabled(quantidade >= Autenticador.MIN_DIGITOS_SENHA);
        botaoLimpar.setEnabled(quantidade > 0);
    }

    private void confirmar() {
        final List<int[]> pares = new ArrayList<int[]>(paresPressionados);
        for (JButton botao : botoesDigitos) {
            botao.setEnabled(false);
        }
        botaoOk.setEnabled(false);
        botaoLimpar.setEnabled(false);
        situacao.setText("Verificando a senha...");

        new SwingWorker<char[], Void>() {
            @Override
            protected char[] doInBackground() {
                return janela.autenticador.verificarSenha(usuario, pares);
            }

            @Override
            protected void done() {
                char[] senha;
                try {
                    senha = get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new IllegalStateException("Falha na verificacao da senha: " + e.getMessage(), e);
                }
                situacao.setText(" ");
                apagarPares();
                if (senha != null) {
                    janela.iniciarEtapa3(senha);
                } else if (usuario.estaBloqueado()) {
                    Componentes.erro(TelaSenha.this, "Senha pessoal incorreta. Terceiro erro consecutivo: o acesso "
                            + "foi bloqueado por 2 minutos.");
                    janela.voltarParaEtapa1(3002);
                } else {
                    Componentes.erro(TelaSenha.this, "Senha pessoal incorreta (erro " + usuario.errosSenha + " de "
                            + Autenticador.MAX_ERROS + ").");
                    redistribuir();
                    atualizar();
                }
            }
        }.execute();
    }

    private void apagarPares() {
        for (int[] par : paresPressionados) {
            Arrays.fill(par, 0);
        }
        paresPressionados.clear();
    }
}
