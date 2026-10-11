/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * Tela de Consultar Pasta de Arquivos Secretos do Usuario: total de consultas,
 * caminho da pasta segura, frase secreta, listagem e decriptacao de arquivos.
 */
class TelaConsulta extends JPanel {

    private static final long serialVersionUID = 1L;

    private final CofreDigital janela;
    private final Usuario usuario;

    private final JLabel totalConsultas = new JLabel();
    private final JTextField campoPasta = new JTextField(30);
    private final JPasswordField campoFrase = new JPasswordField(30);
    private final DefaultTableModel modelo = new DefaultTableModel(new Object[] {"Nome secreto", "Dono", "Grupo"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int linha, int coluna) {
            return false;
        }
    };
    private final JTable tabela = new JTable(modelo);

    // Estado da ultima listagem bem-sucedida
    private List<PastaSegura.Arquivo> arquivos = new ArrayList<PastaSegura.Arquivo>();
    private File pastaListada;
    private PrivateKey chavePrivadaUsuario;

    TelaConsulta(final CofreDigital janela, Usuario usuario) {
        super(new BorderLayout());
        this.janela = janela;
        this.usuario = usuario;

        Componentes.limitar(campoPasta, 255);
        Componentes.limitar(campoFrase, 255);
        Componentes.focarAoExibir(campoPasta);

        JPanel campos = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 4, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        campos.add(new JLabel("Caminho da pasta:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        campos.add(campoPasta, c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(4, 0, 4, 0);
        campos.add(Componentes.botaoProcurar(campoPasta, true), c);
        c.gridy = 1;
        c.gridx = 0;
        c.insets = new Insets(4, 0, 4, 8);
        campos.add(new JLabel("Frase secreta:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        campos.add(campoFrase, c);

        JButton botaoListar = new JButton("Listar");
        botaoListar.addActionListener(e -> listar());
        campoFrase.addActionListener(e -> listar());
        JPanel linhaListar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        linhaListar.add(botaoListar);

        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabela.getTableHeader().setReorderingAllowed(false);
        tabela.setFillsViewportHeight(true);
        tabela.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evento) {
                if (evento.getClickCount() == 2 && tabela.rowAtPoint(evento.getPoint()) >= 0) {
                    decriptarSelecionado();
                }
            }
        });
        tabela.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "decriptar");
        tabela.getActionMap().put("decriptar", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent evento) {
                decriptarSelecionado();
            }
        });
        JScrollPane rolagem = new JScrollPane(tabela);
        rolagem.setPreferredSize(new Dimension(520, 150));

        JButton botaoVoltar = new JButton("Voltar de Consultar para o Menu Principal");
        botaoVoltar.addActionListener(e -> {
            janela.registrar(7002);
            chavePrivadaUsuario = null;
            janela.mostrarTelaPrincipal();
        });
        JPanel linhaVoltar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        linhaVoltar.add(botaoVoltar);

        JPanel areaTabela = new JPanel(new BorderLayout(0, 4));
        areaTabela.add(new JLabel("Arquivos secretos (clique duas vezes no nome secreto para decriptar):"),
                BorderLayout.NORTH);
        areaTabela.add(rolagem, BorderLayout.CENTER);

        // Corpo 1 igual ao da Tela Principal; o total de consultas abre o corpo 2
        JPanel corpo2 = new JPanel(new BorderLayout(0, 10));
        corpo2.add(Componentes.coluna(totalConsultas, campos, linhaListar), BorderLayout.NORTH);
        corpo2.add(areaTabela, BorderLayout.CENTER);
        corpo2.add(linhaVoltar, BorderLayout.SOUTH);

        atualizarTotal();
        JLabel totalAcessos = new JLabel("Total de acessos do usu\u00e1rio: " + usuario.totalAcessos);
        add(Componentes.montarTela(usuario, totalAcessos, corpo2), BorderLayout.CENTER);
    }

    private void atualizarTotal() {
        totalConsultas.setText("Total de consultas do usu\u00e1rio: " + usuario.totalConsultas);
    }

    private void listar() {
        janela.registrar(7003);
        janela.banco.incrementarConsultas(usuario.uid);
        usuario.totalConsultas++;
        atualizarTotal();

        modelo.setRowCount(0);
        arquivos = new ArrayList<PastaSegura.Arquivo>();
        pastaListada = null;
        chavePrivadaUsuario = null;

        // A frase secreta e validada como no cadastro (decriptacao + assinatura de teste)
        char[] frase = campoFrase.getPassword();
        try {
            chavePrivadaUsuario = janela.validarChaveDoUsuario(usuario, frase);
        } catch (ChavePrivadaInvalidaException e) {
            boolean fraseInvalida = e.getMotivo() == ChavePrivadaInvalidaException.Motivo.FRASE_SECRETA_INVALIDA;
            janela.registrar(fraseInvalida ? 6006 : 6007);
            Componentes.erro(this, fraseInvalida
                    ? "Frase secreta invalida: ela nao decripta a sua chave privada."
                    : "A sua chave privada nao corresponde ao seu certificado digital.");
            return;
        } finally {
            Arrays.fill(frase, '\0');
        }

        String caminho = campoPasta.getText().trim();
        File pasta = new File(caminho);
        if (caminho.isEmpty() || !pasta.isDirectory()) {
            janela.registrar(7004);
            Componentes.erro(this, "Caminho de pasta invalido: " + (caminho.isEmpty() ? "(vazio)" : caminho));
            return;
        }

        try {
            arquivos = janela.criarPastaSegura().listar(pasta, usuario);
        } catch (PastaSegura.FalhaPastaSegura e) {
            Componentes.erro(this, e.getMessage());
            return;
        }
        pastaListada = pasta;
        for (PastaSegura.Arquivo arquivo : arquivos) {
            modelo.addRow(new Object[] {arquivo.nomeSecreto, arquivo.dono, arquivo.grupo});
        }
        janela.registrar(7009);
        janela.registrar(7014);
        if (arquivos.isEmpty()) {
            Componentes.informar(this, "Nenhum arquivo desta pasta pode ser visualizado por voce.");
        }
    }

    private void decriptarSelecionado() {
        int linha = tabela.getSelectedRow();
        if (linha < 0 || pastaListada == null) {
            return;
        }
        PastaSegura.Arquivo arquivo = arquivos.get(tabela.convertRowIndexToModel(linha));
        janela.registrar(7015, arquivo.nomeSecreto);
        try {
            File saida = janela.criarPastaSegura().decriptarArquivo(pastaListada, arquivo, usuario,
                    chavePrivadaUsuario);
            Componentes.informar(this, "Arquivo " + arquivo.nomeSecreto + " decriptado e verificado "
                    + "(integridade e autenticidade) com sucesso.\nGravado em: " + saida.getAbsolutePath());
        } catch (PastaSegura.FalhaPastaSegura e) {
            Componentes.erro(this, e.getMessage());
        }
    }
}
