/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 *
 * Teste de ponta a ponta da interface do Cofre Digital: roda o cofre real no
 * mesmo processo, preenche campos, clica botoes, le os dialogos e confere os
 * codigos gravados na tabela Registros apos cada acao (roteiro do HowToTest.md,
 * secoes 1 a 7). Cada "runN" e uma execucao do sistema (o cofre termina com
 * System.exit), sempre no mesmo diretorio de trabalho (cofre.db proprio).
 *
 * Uso: ./testesUni/rodar.sh gui (a partir da pasta T3; abre janelas)
 */

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;

public class TesteGui {
    static int ok = 0, falhas = 0;
    static final String T3 = System.getProperty("t3", ".");
    static final String KEYS = new File(T3, "Pacote-T3/Keys").getAbsolutePath() + File.separator;
    static final String FILES = new File("Files").getAbsolutePath();
    static final Properties estado = new Properties();
    static final String ADMIN = "admin@inf1416.puc-rio.br", U1 = "user01@inf1416.puc-rio.br", U2 = "user02@inf1416.puc-rio.br";
    static final String S_ADMIN = "13572468", S_U1 = "2468135790", S_U2 = "9081726354";

    // ---------------------------------------------------------------- verificacao
    static synchronized void check(String n, boolean c, Object d) {
        if (c) { ok++; System.out.println("  [OK]    " + n); }
        else { falhas++; System.out.println("  [FALHA] " + n + "   -> obtido: " + d); }
        System.out.flush();
    }
    static void check(String n, boolean c) { check(n, c, ""); }
    static void secao(String s) { System.out.println("\n== " + s); }
    static void resumo(String run) { System.out.println("\nRESULTADO " + run + ": " + ok + " OK, " + falhas + " FALHA(S)"); System.out.flush(); }

    // ---------------------------------------------------------------- banco
    static Connection con() throws SQLException { return DriverManager.getConnection("jdbc:sqlite:cofre.db"); }
    static int marca() {
        try (Connection c = con(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT IFNULL(MAX(RID),0) FROM Registros")) { rs.next(); return rs.getInt(1); }
        catch (SQLException e) { return 0; }
    }
    static List<Integer> desde(int m) {
        List<Integer> r = new ArrayList<>();
        try (Connection c = con(); PreparedStatement ps = c.prepareStatement("SELECT MID FROM Registros WHERE RID > ? ORDER BY RID")) {
            ps.setInt(1, m);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) r.add(rs.getInt(1)); }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return r;
    }
    static void codigos(String desc, int m, Integer... esp) {
        List<Integer> obt = desde(m);
        check(desc + "  codigos " + Arrays.toString(esp), obt.equals(Arrays.asList(esp)), obt);
    }
    static long bloqueadoAte(String login) {
        try (Connection c = con(); PreparedStatement ps = c.prepareStatement("SELECT bloqueado_ate FROM Usuarios WHERE login = ?")) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0; }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    static void esperarDesbloqueio(String login) throws InterruptedException {
        long falta = bloqueadoAte(login) - System.currentTimeMillis();
        if (falta > 0) {
            System.out.println("  ...aguardando " + (falta / 1000 + 1) + " s ate o fim do bloqueio de " + login);
            Thread.sleep(falta + 1000);
        }
    }

    // ---------------------------------------------------------------- swing
    static <T> T edt(Callable<T> c) {
        if (SwingUtilities.isEventDispatchThread()) {
            try { return c.call(); } catch (Exception e) { throw new RuntimeException(e); }
        }
        AtomicReference<T> r = new AtomicReference<>();
        AtomicReference<Throwable> e = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> { try { r.set(c.call()); } catch (Throwable t) { e.set(t); } });
        } catch (Exception ex) { throw new RuntimeException(ex); }
        if (e.get() != null) throw new RuntimeException(e.get());
        return r.get();
    }
    static void edtRun(Runnable x) { edt(() -> { x.run(); return null; }); }
    static void sinc() { edtRun(() -> { }); }
    /** Acao que pode abrir dialogo modal: invokeLater + sincroniza (o laco modal processa o sinc). */
    static void depois(Runnable x) { SwingUtilities.invokeLater(x); sinc(); }

    static <T> T esperar(String desc, Supplier<T> s, long ms) {
        long fim = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < fim) {
            T v = s.get();
            if (v != null && !Boolean.FALSE.equals(v)) return v;
            try { Thread.sleep(40); } catch (InterruptedException e) { }
        }
        throw new IllegalStateException("Tempo esgotado esperando: " + desc + " | tela=" + nomeTela() + " dialogo=" + (dialogo() == null ? "-" : texto(dialogo())));
    }
    static CofreDigital frame() {
        for (Frame f : Frame.getFrames()) if (f instanceof CofreDigital && f.isDisplayable()) return (CofreDigital) f;
        return null;
    }
    static Container tela() { return edt(() -> { CofreDigital f = frame(); return f == null || !f.isShowing() ? null : f.getContentPane(); }); }
    static String nomeTela() { Container t = tela(); return t == null ? "-" : t.getClass().getSimpleName(); }
    static JDialog dialogo() {
        return edt(() -> { for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isShowing()) return (JDialog) w; return null; });
    }
    static JDialog esperarDialogo(String desc) { return esperar("dialogo " + desc, TesteGui::dialogo, 20000); }
    static void esperarTela(Class<?> cls) {
        esperar("tela " + cls.getSimpleName(), () -> { Container t = tela(); return t != null && cls.isInstance(t) && dialogo() == null; }, 20000);
    }
    static void semDialogo(String desc) {
        JDialog d = dialogo();
        check(desc + ": nenhum dialogo inesperado", d == null, d == null ? "" : texto(d));
        if (d != null) fechar(d, JOptionPane.OK_OPTION);
    }

    static List<Component> todos(Container c) {
        List<Component> l = new ArrayList<>();
        for (Component x : c.getComponents()) { l.add(x); if (x instanceof Container) l.addAll(todos((Container) x)); }
        return l;
    }
    static String html(String s) {
        return s == null ? "" : s.replaceAll("(?i)<br>", "\n").replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trim();
    }
    static String texto(Container c) {
        return edt(() -> {
            StringBuilder sb = new StringBuilder();
            Container raiz = c instanceof RootPaneContainer ? ((RootPaneContainer) c).getContentPane() : c;
            for (Component x : todos(raiz)) {
                if (x instanceof JLabel && ((JLabel) x).getText() != null && !((JLabel) x).getText().trim().isEmpty())
                    sb.append(html(((JLabel) x).getText())).append(" | ");
                else if (x instanceof JTextField && !(x instanceof JPasswordField)) sb.append(((JTextField) x).getText()).append(" | ");
            }
            return sb.toString();
        });
    }
    static List<String> rotulos(Container c) {
        return edt(() -> { List<String> l = new ArrayList<>(); for (Component x : todos(c)) if (x instanceof JLabel) l.add(html(((JLabel) x).getText())); return l; });
    }
    static List<String> botoes(Container c) {
        return edt(() -> { List<String> l = new ArrayList<>(); for (Component x : todos(c)) if (x instanceof JButton && x.isVisible()) l.add(((JButton) x).getText()); return l; });
    }
    static JButton botao(Container c, String txt) {
        return edt(() -> { for (Component x : todos(c)) if (x instanceof JButton && txt.equals(((JButton) x).getText())) return (JButton) x; return null; });
    }
    static JButton botaoPrefixo(Container c, String p) {
        return edt(() -> { for (Component x : todos(c)) if (x instanceof JButton && ((JButton) x).getText().startsWith(p)) return (JButton) x; return null; });
    }
    static void clicar(JButton b) { depois(() -> b.doClick(0)); }
    static void clicar(Container c, String txt) {
        JButton b = botao(c, txt);
        if (b == null) throw new IllegalStateException("botao '" + txt + "' nao encontrado em " + c.getClass().getSimpleName() + " " + botoes(c));
        clicar(b);
    }
    static void fechar(JDialog d, Object valor) {
        JOptionPane p = edt(() -> { for (Component x : todos(d.getContentPane())) if (x instanceof JOptionPane) return (JOptionPane) x; return null; });
        depois(() -> p.setValue(valor));
        esperar("fechar dialogo", () -> !edt(d::isShowing), 10000);
        sinc();
    }
    /** Espera um dialogo, le o texto e fecha com OK. */
    static String dialogoOk(String desc) {
        JDialog d = esperarDialogo(desc);
        String t = texto(d);
        fechar(d, JOptionPane.OK_OPTION);
        return t;
    }
    @SuppressWarnings("unchecked")
    static <T> T campo(Object o, String nome) {
        return edt(() -> {
            for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
                try { Field f = k.getDeclaredField(nome); f.setAccessible(true); return (T) f.get(o); } catch (NoSuchFieldException e) { }
            }
            throw new IllegalStateException("campo " + nome);
        });
    }
    static void setTexto(JTextComponent t, String s) { edtRun(() -> t.setText(s)); }
    static String getTexto(JTextComponent t) {
        return edt(() -> t instanceof JPasswordField ? new String(((JPasswordField) t).getPassword()) : t.getText());
    }

    // ---------------------------------------------------------------- TOTP
    static String totp(String chave, int deslocamento) {
        try {
            TOTP t = new TOTP(chave, 30);
            Method m = TOTP.class.getDeclaredMethod("TOTPCode", long.class);
            m.setAccessible(true);
            return (String) m.invoke(t, new java.util.Date().getTime() / 1000 / 30 + deslocamento);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    /** Evita digitar um codigo perto da troca de intervalo (o teste ficaria ambiguo). */
    static void longeDaTroca() throws InterruptedException {
        long resto = 30_000 - System.currentTimeMillis() % 30_000;
        if (resto < 5000) Thread.sleep(resto + 500);
    }

    // ---------------------------------------------------------------- acoes das telas
    static void loginName(String login) {
        Container t = tela();
        setTexto(campo(t, "campoLogin"), login);
        clicar(t, "OK");
    }
    static void digitar(String senha) {
        Container t = tela();
        for (char ch : senha.toCharArray()) {
            edtRun(() -> {
                JButton[] bs = campo(t, "botoesDigitos");
                int[][] dist = campo(t, "distribuicao");
                for (int i = 0; i < 5; i++) if (dist[i][0] == ch - '0' || dist[i][1] == ch - '0') { bs[i].doClick(0); return; }
                throw new IllegalStateException("digito " + ch + " nao esta no teclado");
            });
        }
    }
    /** Clica OK da senha; retorna o texto de situacao exibido logo apos o clique. */
    static String okSenha() {
        Container t = tela();
        JButton b = campo(t, "botaoOk");
        return edt(() -> { b.doClick(0); return ((JLabel) campo(t, "situacao")).getText(); });
    }
    static String senhaErrada(String errada) {
        digitar(errada);
        okSenha();
        return dialogoOk("erro de senha");
    }
    static void token(String codigo) {
        Container t = tela();
        setTexto(campo(t, "campoToken"), codigo);
        clicar(t, "OK");
    }
    static void entrar(String login, String senha, String chave) throws InterruptedException {
        loginName(login);
        esperarTela(TelaSenha.class);
        digitar(senha);
        okSenha();
        esperarTela(TelaToken.class);
        longeDaTroca();
        token(totp(chave, 0));
        esperarTela(TelaPrincipal.class);
    }
    static void menu(String n) { clicar(botaoPrefixo(tela(), n + " ")); }
    static void preencher(Container t, String cert, String chave, String frase, Integer grupo, String senha, String conf) {
        edtRun(() -> {
            ((JTextField) campo(t, "campoCertificado")).setText(cert);
            ((JTextField) campo(t, "campoChave")).setText(chave);
            ((JTextField) campo(t, "campoFrase")).setText(frase);
            if (grupo != null) ((JComboBox<?>) campo(t, "comboGrupo")).setSelectedIndex(grupo);
            ((JTextField) campo(t, "campoSenha")).setText(senha);
            ((JTextField) campo(t, "campoConfirmacao")).setText(conf);
        });
    }
    static List<String> formulario(Container t) {
        return Arrays.asList(getTexto(campo(t, "campoCertificado")), getTexto(campo(t, "campoChave")), getTexto(campo(t, "campoFrase")),
                getTexto(campo(t, "campoSenha")), getTexto(campo(t, "campoConfirmacao")));
    }
    /** Cadastra e confirma; retorna {texto da confirmacao, chave BASE32, URI}. */
    static String[] cadastrar(Container t, String u, int grupo, String senha) {
        preencher(t, KEYS + u + "-x509.crt", KEYS + u + "-pkcs8-aes.key", u, grupo, senha, senha);
        clicar(t, "Cadastrar");
        JDialog conf = esperarDialogo("confirmacao");
        String txtConf = texto(conf);
        fechar(conf, "Confirmar");
        JDialog dt = esperarDialogo("chave TOTP");
        String[] campos = edt(() -> {
            String k = null, uri = null;
            for (Component x : todos(dt.getContentPane())) if (x instanceof JTextField) {
                String s = ((JTextField) x).getText();
                if (s.matches("[A-Z2-7]{32}")) k = s; else if (s.startsWith("otpauth://")) uri = s;
            }
            return new String[]{k, uri};
        });
        String txtTotp = texto(dt);
        fechar(dt, JOptionPane.OK_OPTION);
        return new String[]{txtConf, campos[0], campos[1], txtTotp};
    }
    static void listar(String pasta, String frase) {
        Container t = tela();
        edtRun(() -> { ((JTextField) campo(t, "campoPasta")).setText(pasta); ((JTextField) campo(t, "campoFrase")).setText(frase); });
        clicar(t, "Listar");
    }
    static List<String> linhasTabela() {
        Container t = tela();
        return edt(() -> {
            DefaultTableModel m = campo(t, "modelo");
            List<String> l = new ArrayList<>();
            for (int i = 0; i < m.getRowCount(); i++) l.add(m.getValueAt(i, 0) + " " + m.getValueAt(i, 1) + " " + m.getValueAt(i, 2));
            return l;
        });
    }
    /** Clique duplo no nome secreto (evento de mouse com clickCount = 2). */
    static void cliqueDuplo(String nome) {
        Container t = tela();
        JTable tab = campo(t, "tabela");
        int linha = edt(() -> { for (int i = 0; i < tab.getRowCount(); i++) if (nome.equals(tab.getValueAt(i, 0))) return i; return -1; });
        if (linha < 0) throw new IllegalStateException(nome + " nao esta na tabela");
        depois(() -> {
            tab.setRowSelectionInterval(linha, linha);
            Rectangle r = tab.getCellRect(linha, 0, true);
            tab.dispatchEvent(new MouseEvent(tab, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, r.x + 5, r.y + 5, 2, false, MouseEvent.BUTTON1));
        });
    }
    static String rotuloComeca(Container t, String p) {
        for (String s : rotulos(t)) if (s.startsWith(p)) return s;
        return null;
    }
    static void verificarAoSair(String run, String desc, int m, Integer... esp) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { codigos(desc, m, esp); resumo(run); }));
    }
    static void iniciarCofre() { CofreDigital.main(new String[0]); }
    static void pararAqui() throws InterruptedException { Thread.sleep(60_000); System.out.println("  [FALHA] o sistema deveria ter encerrado"); System.exit(3); }

    // ---------------------------------------------------------------- estado entre execucoes
    static void salvarEstado() throws IOException { try (OutputStream o = new FileOutputStream("estado.properties")) { estado.store(o, null); } }
    static void carregarEstado() throws IOException { File f = new File("estado.properties"); if (f.exists()) try (InputStream i = new FileInputStream(f)) { estado.load(i); } }

    static void copiarPasta(File de, File para) throws IOException {
        para.mkdirs();
        for (File x : de.listFiles()) if (x.isFile() && !x.getName().endsWith(".docx"))
            Files.copy(x.toPath(), new File(para, x.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    static void adulterar(File f) throws IOException { byte[] b = Files.readAllBytes(f.toPath()); b[10] ^= (byte) 0xFF; Files.write(f.toPath(), b); }

    // =================================================================== RUN 1
    static void run1() throws Exception {
        secao("1. Primeira execucao: cadastro do administrador");
        int m = marca();
        iniciarCofre();
        esperarTela(TelaCadastro.class);
        Container t = tela();
        codigos("partida com banco vazio", m, 1001, 1005, 6001);
        check("titulo 'Partida do sistema: cadastro do administrador'", rotulos(t).contains("Partida do sistema: cadastro do administrador"), rotulos(t));
        check("corpo 1 'Total de usu00e1rios do sistema: 0'", rotulos(t).contains("Total de usu\u00e1rios do sistema: 0"), rotulos(t));
        JComboBox<?> combo = campo(t, "comboGrupo");
        check("grupo fixo em Administrador e desabilitado", edt(() -> "Administrador".equals(combo.getSelectedItem()) && !combo.isEnabled()));
        List<String> bts = botoes(t);
        check("so o botao Cadastrar (alem dos '...')", bts.contains("Cadastrar") && !bts.toString().contains("Voltar"), bts);

        secao("1.1 Erros de validacao do formulario");
        String cert = KEYS + "admin-x509.crt", chave = KEYS + "admin-pkcs8-aes.key";
        Object[][] casos = {
            {cert, chave, "admin", "1357246a", "1357246a", "A senha pessoal deve conter apenas digitos de 0 a 9.", 6003},
            {cert, chave, "admin", "1357246", "1357246", "A senha pessoal deve ter 8, 9 ou 10 digitos.", 6003},
            {cert, chave, "admin", "13572446", "13572446", "A senha pessoal nao pode conter digitos repetidos em sequencia (ex.: 11).", 6003},
            {cert, chave, "admin", "13572468", "13572460", "A senha pessoal e a confirmacao nao conferem.", 6003},
            {KEYS + "nao-existe.crt", chave, "admin", S_ADMIN, S_ADMIN, "Caminho do certificado digital invalido:", 6004},
            {KEYS + "admin-pkcs8-aes.key", chave, "admin", S_ADMIN, S_ADMIN, "Caminho do certificado digital invalido:", 6004},
            {cert, KEYS + "nao-existe.key", "admin", S_ADMIN, S_ADMIN, "Caminho do arquivo da chave privada invalido.", 6005},
            {cert, chave, "errada", S_ADMIN, S_ADMIN, "Frase secreta invalida para a chave privada.", 6006},
            {cert, KEYS + "user01-pkcs8-aes.key", "user01", S_ADMIN, S_ADMIN, "A chave privada nao corresponde ao certificado digital (assinatura digital invalida).", 6007}};
        for (Object[] c : casos) {
            preencher(t, (String) c[0], (String) c[1], (String) c[2], null, (String) c[3], (String) c[4]);
            List<String> antes = formulario(t);
            m = marca();
            clicar(t, "Cadastrar");
            String msg = dialogoOk("erro de cadastro");
            check("'" + c[5] + "'", msg.contains((String) c[5]), msg);
            codigos("   ", m, 6002, (Integer) c[6]);
            check("   formulario continua preenchido", formulario(t).equals(antes), formulario(t));
        }
        setTexto(campo(t, "campoSenha"), "12345678901");
        check("campo de senha aceita no maximo 10 caracteres", getTexto(campo(t, "campoSenha")).length() == 10, getTexto(campo(t, "campoSenha")));

        secao("1.2 Tela de confirmacao: rejeitar");
        preencher(t, cert, chave, "admin", null, S_ADMIN, S_ADMIN);
        m = marca();
        clicar(t, "Cadastrar");
        JDialog conf = esperarDialogo("confirmacao");
        String tc = texto(conf);
        String[] esperadosConf = {"Vers\u00e3o: | 3 |", "S\u00e9rie: | 23 (0x17) |", "Validade: | 23/04/2026", "a 23/04/2027", "Tipo de Assinatura: | SHA256withRSA |",
                "Emissor: | EMAILADDRESS=ca@grad.inf.puc-rio.br, CN=AC INF1416, OU=INF1416, O=PUC, L=Rio, ST=RJ, C=BR |",
                "Sujeito (Friendly Name): | Administrator |", "E-mail: | admin@inf1416.puc-rio.br |"};
        for (String e : esperadosConf) check("confirmacao mostra " + e, tc.contains(e), tc);
        List<String> antes = formulario(t);
        fechar(conf, "Rejeitar");
        codigos("Rejeitar", m, 6002, 6009);
        check("formulario continua preenchido apos Rejeitar", formulario(t).equals(antes), formulario(t));
        semDialogo("Rejeitar");

        secao("1.3 Cadastro com sucesso");
        m = marca();
        String[] r = cadastrar(t, "admin", 0, S_ADMIN);
        check("chave TOTP BASE32 com 32 caracteres", r[1] != null && r[1].length() == 32, r[1]);
        check("URI otpauth://totp/Cofre%20Digital:admin@...?secret=<chave>",
                ("otpauth://totp/Cofre%20Digital:admin@inf1416.puc-rio.br?secret=" + r[1]).equals(r[2]), r[2]);
        estado.setProperty("admin", r[1]);
        salvarEstado();
        esperarTela(TelaLogin.class);
        codigos("Confirmar -> tela de login", m, 6002, 6008, 2001);

        secao("2.1 Etapa 1: login name");
        m = marca();
        loginName("abc");
        String msg = dialogoOk("login invalido");
        check("'abc' -> 'Login name invalido: informe um e-mail valido.'", msg.contains("Login name invalido: informe um e-mail valido."), msg);
        check("permanece na etapa 1", tela() instanceof TelaLogin);
        codigos("'abc'", m, 2005);
        m = marca();
        loginName("naoexiste@inf1416.puc-rio.br");
        msg = dialogoOk("login nao identificado");
        check("'naoexiste@...' -> 'Login name nao identificado: ...'", msg.contains("Login name nao identificado: naoexiste@inf1416.puc-rio.br"), msg);
        check("permanece na etapa 1", tela() instanceof TelaLogin);
        codigos("nao cadastrado", m, 2005);
        m = marca();
        loginName(ADMIN);
        esperarTela(TelaSenha.class);
        codigos("admin -> etapa 2", m, 2003, 2002, 3001);

        secao("2.2 Etapa 2: teclado virtual");
        t = tela();
        Container ts = t;
        JButton[] bs = campo(ts, "botoesDigitos");
        JButton bOk = campo(ts, "botaoOk"), bLimpar = campo(ts, "botaoLimpar");
        JPasswordField cs = campo(ts, "campoSenha");
        Supplier<String> distribuicao = () -> edt(() -> { StringBuilder sb = new StringBuilder(); for (JButton b : bs) sb.append(b.getText()).append('|'); return sb.toString(); });
        String d0 = distribuicao.get();
        String digitos = d0.replaceAll("[^0-9]", "");
        char[] ord = digitos.toCharArray(); Arrays.sort(ord);
        check("5 botoes, 2 digitos cada, 0-9 sem repeticao: " + d0, bs.length == 5 && new String(ord).equals("0123456789")
                && d0.matches("(\\d\\s+\\d\\|){5}"), d0);
        boolean mudou = false;
        for (int i = 0; i < 3; i++) { String a = distribuicao.get(); digitar("1"); if (!distribuicao.get().equals(a)) mudou = true; }
        check("digitos redistribuidos a cada clique", mudou);
        check("campo mostra apenas asteriscos (3)", getTexto(cs).equals("***"), getTexto(cs));
        check("OK desabilitado com 3 digitos", !edt(bOk::isEnabled));
        digitar("1234");
        check("OK desabilitado com 7 digitos", !edt(bOk::isEnabled));
        digitar("5");
        check("OK habilitado com 8 digitos", edt(bOk::isEnabled));
        digitar("67");
        check("com 10 digitos os botoes numericos ficam desabilitados", edt(() -> { for (JButton b : bs) if (b.isEnabled()) return false; return true; }));
        check("campo mostra 10 asteriscos", getTexto(cs).equals("**********"), getTexto(cs));
        clicar(bLimpar);
        check("LIMPAR apaga o que foi digitado", getTexto(cs).isEmpty() && ((List<?>) campo(ts, "paresPressionados")).isEmpty(), getTexto(cs));
        semDialogo("teclado");
        m = marca();
        digitar(S_ADMIN);
        long t0 = System.currentTimeMillis();
        String sit = okSenha();
        check("mostra 'Verificando a senha...'", "Verificando a senha...".equals(sit), sit);
        esperarTela(TelaToken.class);
        System.out.println("         (verificacao de 8 digitos levou " + (System.currentTimeMillis() - t0) + " ms)");
        codigos("senha correta -> etapa 3", m, 3003, 3002, 4001);

        secao("2.3 Etapa 3: token TOTP");
        longeDaTroca();
        m = marca();
        token(totp(r[1], 0));
        esperarTela(TelaPrincipal.class);
        codigos("token atual -> Tela Principal", m, 4003, 4002, 1003, 5001);

        secao("3.1 Tela Principal (admin)");
        t = tela();
        List<String> rt = rotulos(t);
        check("cabecalho Login/Grupo/Nome", rt.contains("Login: " + ADMIN) && rt.contains("Grupo: Administrador") && rt.contains("Nome: Administrator"), rt);
        check("corpo 1 'Total de acessos do usu00e1rio: 1'", rt.contains("Total de acessos do usu\u00e1rio: 1"), rt);
        check("corpo 2 'Menu Principal:' com opcoes 1, 2 e 3", rt.contains("Menu Principal:") && botoes(t).equals(Arrays.asList(
                "1 \u2013 Cadastrar um novo usu\u00e1rio", "2 \u2013 Consultar pasta de arquivos secretos do usu\u00e1rio", "3 \u2013 Sair do Sistema")), botoes(t));

        secao("3.2 Cadastrar user01 e user02");
        m = marca();
        menu("1");
        esperarTela(TelaCadastro.class);
        codigos("opcao 1", m, 5002, 6001);
        t = tela();
        rt = rotulos(t);
        check("mesmo cabecalho e 'Total de usu00e1rios do sistema: 1'", rt.contains("Login: " + ADMIN) && rt.contains("Total de usu\u00e1rios do sistema: 1"), rt);
        JComboBox<?> cg = campo(t, "comboGrupo");
        check("lista de grupos Administrador/Usu00e1rio habilitada", edt(() -> cg.isEnabled() && cg.getItemCount() == 2
                && "Administrador".equals(cg.getItemAt(0)) && "Usu\u00e1rio".equals(cg.getItemAt(1))));
        m = marca();
        r = cadastrar(t, "user01", 1, S_U1);
        check("confirmacao user01: serie 18 (0x12), sujeito Usuario 01, grupo Usu00e1rio", r[0].contains("S\u00e9rie: | 18 (0x12) |")
                && r[0].contains("Sujeito (Friendly Name): | Usuario 01 |") && r[0].contains("Grupo: | Usu\u00e1rio |"), r[0]);
        estado.setProperty("user01", r[1]);
        codigos("cadastro user01", m, 6002, 6008);
        check("formulario volta vazio", formulario(t).equals(Arrays.asList("", "", "", "", "")), formulario(t));
        check("total passa para 2", rotulos(t).contains("Total de usu\u00e1rios do sistema: 2"), rotulos(t));
        m = marca();
        r = cadastrar(t, "user02", 1, S_U2);
        check("confirmacao user02: serie 19 (0x13)", r[0].contains("S\u00e9rie: | 19 (0x13) |"), r[0]);
        estado.setProperty("user02", r[1]);
        salvarEstado();
        codigos("cadastro user02", m, 6002, 6008);
        check("total passa para 3", rotulos(t).contains("Total de usu\u00e1rios do sistema: 3"), rotulos(t));
        preencher(t, KEYS + "user01-x509.crt", KEYS + "user01-pkcs8-aes.key", "user01", 1, S_U1, S_U1);
        antes = formulario(t);
        m = marca();
        clicar(t, "Cadastrar");
        fechar(esperarDialogo("confirmacao"), "Confirmar");
        msg = dialogoOk("duplicado");
        check("login duplicado: 'O login name user01@... ja esta cadastrado no sistema.'", msg.contains("O login name " + U1 + " ja esta cadastrado no sistema."), msg);
        check("formulario continua preenchido", formulario(t).equals(antes), formulario(t));
        codigos("duplicado", m, 6002, 6008);
        m = marca();
        clicar(t, "Voltar de Cadastrar para o Menu Principal");
        esperarTela(TelaPrincipal.class);
        codigos("Voltar de Cadastrar", m, 6010, 5001);

        secao("4. Consultar pasta (admin)");
        copiarPasta(new File(T3, "Pacote-T3/Files"), new File(FILES));
        m = marca();
        menu("2");
        esperarTela(TelaConsulta.class);
        codigos("opcao 2", m, 5003, 7001);
        t = tela();
        rt = rotulos(t);
        check("cabecalho + corpo 1 'Total de acessos do usu00e1rio: 1' + corpo 2 'Total de consultas do usu00e1rio: 0'",
                rt.contains("Login: " + ADMIN) && rt.contains("Total de acessos do usu\u00e1rio: 1") && rt.contains("Total de consultas do usu\u00e1rio: 0")
                && rt.indexOf("Total de acessos do usu\u00e1rio: 1") < rt.indexOf("Total de consultas do usu\u00e1rio: 0"), rt);
        check("campos 'Caminho da pasta:', 'Frase secreta:' e botoes Listar/Voltar", rt.contains("Caminho da pasta:") && rt.contains("Frase secreta:")
                && botoes(t).contains("Listar") && botoes(t).contains("Voltar de Consultar para o Menu Principal"), rt + " " + botoes(t));
        Object[][] errosConsulta = {
            {FILES, "user01", "Frase secreta invalida: ela nao decripta a sua chave privada.", new Integer[]{7003, 6006}},
            {"C:\\nao\\existe", "admin", "Caminho de pasta invalido: C:\\nao\\existe", new Integer[]{7003, 7004}},
            {"", "admin", "Caminho de pasta invalido: (vazio)", new Integer[]{7003, 7004}},
            {new File(T3, "Pacote-T3/Keys").getAbsolutePath(), "admin", "Falha na decriptacao do arquivo de indice", new Integer[]{7003, 7007}}};
        int consultas = 0;
        for (Object[] e : errosConsulta) {
            m = marca();
            listar((String) e[0], (String) e[1]);
            msg = dialogoOk("erro consulta");
            consultas++;
            check("'" + e[2] + "'", msg.contains((String) e[2]), msg);
            codigos("   ", m, (Integer[]) e[3]);
            check("   tabela vazia", linhasTabela().isEmpty(), linhasTabela());
            check("   'Total de consultas do usu00e1rio: " + consultas + "'", rotulos(t).contains("Total de consultas do usu\u00e1rio: " + consultas), rotulos(t));
        }
        m = marca();
        listar(FILES, "admin");
        semDialogo("listagem admin");
        check("admin lista apenas teste00.docx", linhasTabela().equals(Collections.singletonList("teste00.docx " + ADMIN + " administrador")), linhasTabela());
        codigos("listagem com sucesso", m, 7003, 7005, 7006, 7010, 7011, 7009, 7014);
        m = marca();
        cliqueDuplo("teste00.docx");
        msg = dialogoOk("arquivo aberto");
        File saida = new File(FILES, "teste00.docx");
        check("mensagem informa o caminho absoluto gravado", msg.contains("Gravado em: " + saida.getAbsolutePath()), msg);
        check("teste00.docx gravado e e um .docx (zip PK)", saida.isFile() && Files.readAllBytes(saida.toPath())[0] == 'P');
        codigos("arquivo aberto (dono)", m, 7015, 7016, 7018, 7019);
        m = marca();
        clicar(t, "Voltar de Consultar para o Menu Principal");
        esperarTela(TelaPrincipal.class);
        codigos("Voltar de Consultar", m, 7002, 5001);

        secao("6. Tela de Saida");
        m = marca();
        menu("3");
        esperarTela(TelaSaida.class);
        codigos("opcao 3", m, 5004, 8001);
        t = tela();
        rt = rotulos(t);
        check("cabecalho, corpo 1 'Total de acessos', 'Sa00edda do sistema:' e mensagem de saida", rt.contains("Login: " + ADMIN)
                && rt.contains("Total de acessos do usu\u00e1rio: 1") && rt.contains("Sa\u00edda do sistema:")
                && rt.contains("Pressione o bot\u00e3o Encerrar Sess\u00e3o ou o bot\u00e3o Encerrar Sistema para confirmar."), rt);
        check("botoes Encerrar Sess00e3o, Encerrar Sistema, Voltar de Sair", botoes(t).equals(Arrays.asList("Encerrar Sess\u00e3o", "Encerrar Sistema",
                "Voltar de Sair para o Menu Principal")), botoes(t));
        m = marca();
        clicar(t, "Voltar de Sair para o Menu Principal");
        esperarTela(TelaPrincipal.class);
        codigos("Voltar de Sair", m, 8004, 5001);
        menu("3");
        esperarTela(TelaSaida.class);
        m = marca();
        clicar(tela(), "Encerrar Sess\u00e3o");
        esperarTela(TelaLogin.class);
        codigos("Encerrar Sess00e3o -> login", m, 8002, 1004, 2001);

        secao("2.4 Erros de senha e bloqueio (user01)");
        loginName(U1);
        esperarTela(TelaSenha.class);
        String dAntes = edt(() -> { StringBuilder sb = new StringBuilder(); for (JButton b : (JButton[]) campo(tela(), "botoesDigitos")) sb.append(b.getText()); return sb.toString(); });
        m = marca();
        msg = senhaErrada("1357246809");
        check("1a: 'Senha pessoal incorreta (erro 1 de 3).'", msg.contains("Senha pessoal incorreta (erro 1 de 3)."), msg);
        check("   continua na etapa 2", tela() instanceof TelaSenha);
        codigos("   ", m, 3004);
        m = marca();
        msg = senhaErrada("1357246809");
        check("2a: '(erro 2 de 3)'", msg.contains("(erro 2 de 3)"), msg);
        codigos("   ", m, 3005);
        m = marca();
        msg = senhaErrada("1357246809");
        check("3a: 'Terceiro erro consecutivo: o acesso foi bloqueado por 2 minutos.'", msg.contains("Senha pessoal incorreta. Terceiro erro consecutivo: o acesso foi bloqueado por 2 minutos."), msg);
        esperarTela(TelaLogin.class);
        codigos("   volta para a etapa 1", m, 3006, 3007, 3002, 2001);
        m = marca();
        loginName(U1);
        msg = dialogoOk("bloqueado");
        check("login bloqueado: 'O acesso do usuario user01@... esta bloqueado. Tente novamente em N segundos.'",
                msg.matches("(?s).*O acesso do usuario " + U1.replace(".", "\\.") + " esta bloqueado\\. Tente novamente em 1[0-2]\\d segundos\\..*"), msg);
        check("   permanece na etapa 1", tela() instanceof TelaLogin);
        codigos("   ", m, 2004);

        secao("2.4/3.3/4 Outro usuario durante o bloqueio (user02) + margem de -30 s");
        m = marca();
        loginName(U2);
        esperarTela(TelaSenha.class);
        digitar(S_U2);
        t0 = System.currentTimeMillis();
        okSenha();
        esperarTela(TelaToken.class);
        System.out.println("         (verificacao de 10 digitos levou " + (System.currentTimeMillis() - t0) + " ms)");
        longeDaTroca();
        token(totp(estado.getProperty("user02"), -1));
        esperarTela(TelaPrincipal.class);
        codigos("user02 entra normalmente; token do intervalo anterior (-30 s) aceito", m, 2003, 2002, 3001, 3003, 3002, 4001, 4003, 4002, 1003, 5001);
        t = tela();
        rt = rotulos(t);
        check("cabecalho 'Grupo: Usu00e1rio', 'Nome: Usuario 02'", rt.contains("Grupo: Usu\u00e1rio") && rt.contains("Nome: Usuario 02"), rt);
        check("menu sem a opcao 1 (so 2 e 3)", botoes(t).equals(Arrays.asList("2 \u2013 Consultar pasta de arquivos secretos do usu\u00e1rio", "3 \u2013 Sair do Sistema")), botoes(t));
        check("corpo 1 'Total de acessos do usu00e1rio: 1'", rt.contains("Total de acessos do usu\u00e1rio: 1"), rt);
        menu("2");
        esperarTela(TelaConsulta.class);
        m = marca();
        listar(FILES, "user02");
        semDialogo("listagem user02");
        check("user02 lista teste01.docx e teste02.docx", linhasTabela().equals(Arrays.asList("teste01.docx " + U1 + " usuario", "teste02.docx " + U2 + " usuario")), linhasTabela());
        codigos("listagem user02", m, 7003, 7005, 7006, 7010, 7011, 7009, 7014);
        new File(FILES, "teste02.docx").delete();
        m = marca();
        cliqueDuplo("teste02.docx");
        msg = dialogoOk("abrir teste02");
        check("user02 abre teste02.docx (dono)", msg.contains("decriptado e verificado") && new File(FILES, "teste02.docx").isFile(), msg);
        codigos("   ", m, 7015, 7016, 7018, 7019);
        m = marca();
        cliqueDuplo("teste01.docx");
        msg = dialogoOk("acesso negado");
        check("user02 em teste01.docx: 'Voce nao tem permissao de acesso ao arquivo teste01.docx: ...'", msg.contains("Voce nao tem permissao de acesso ao arquivo teste01.docx:"), msg);
        check("   teste01.docx nao foi gravado", !new File(FILES, "teste01.docx").exists());
        codigos("   acesso negado", m, 7015, 7017);
        clicar(tela(), "Voltar de Consultar para o Menu Principal");
        esperarTela(TelaPrincipal.class);
        menu("3");
        esperarTela(TelaSaida.class);
        clicar(tela(), "Encerrar Sess\u00e3o");
        esperarTela(TelaLogin.class);

        secao("2.6 Erros consecutivos: um acerto zera a contagem (admin)");
        loginName(ADMIN);
        esperarTela(TelaSenha.class);
        m = marca();
        senhaErrada("1357246809");
        senhaErrada("1357246809");
        digitar(S_ADMIN);
        okSenha();
        esperarTela(TelaToken.class);
        codigos("2 erros + senha correta", m, 3004, 3005, 3003, 3002, 4001);
        longeDaTroca();
        token(totp(estado.getProperty("admin"), 0));
        esperarTela(TelaPrincipal.class);
        check("Total de acessos do usu00e1rio: 2", rotulos(tela()).contains("Total de acessos do usu\u00e1rio: 2"), rotulos(tela()));
        menu("3");
        esperarTela(TelaSaida.class);
        clicar(tela(), "Encerrar Sess\u00e3o");
        esperarTela(TelaLogin.class);
        loginName(ADMIN);
        esperarTela(TelaSenha.class);
        m = marca();
        msg = senhaErrada("1357246809");
        check("na autenticacao seguinte o erro e 'erro 1 de 3'", msg.contains("(erro 1 de 3)"), msg);
        codigos("   ", m, 3004);

        secao("2.3/2.5 Token fora da margem e bloqueio por token (admin)");
        digitar(S_ADMIN);
        okSenha();
        esperarTela(TelaToken.class);
        longeDaTroca();
        m = marca();
        token(totp(estado.getProperty("admin"), -2));
        msg = dialogoOk("token -60s");
        check("codigo de 2 intervalos atras (-60 s) rejeitado: 'Token invalido (erro 1 de 3)'", msg.contains("Token invalido (erro 1 de 3)"), msg);
        check("   continua na etapa 3 com o campo limpo", tela() instanceof TelaToken && getTexto(campo(tela(), "campoToken")).isEmpty());
        codigos("   ", m, 4004);
        String errado = totp(estado.getProperty("admin"), 0).equals("000000") ? "000001" : "000000";
        m = marca();
        token(errado);
        msg = dialogoOk("token 2");
        check("'(erro 2 de 3)'", msg.contains("Token invalido (erro 2 de 3)"), msg);
        codigos("   ", m, 4005);
        m = marca();
        token(errado);
        msg = dialogoOk("token 3");
        check("3o erro: bloqueio de 2 minutos", msg.contains("Terceiro erro consecutivo: o acesso foi bloqueado por 2 minutos."), msg);
        esperarTela(TelaLogin.class);
        codigos("   volta para a etapa 1", m, 4006, 4007, 4002, 2001);

        secao("2.7 (preparacao) user02 erra a senha 2 vezes e fecha a janela; 6. X durante a autenticacao");
        loginName(U2);
        esperarTela(TelaSenha.class);
        m = marca();
        senhaErrada("1357246809");
        senhaErrada("1357246809");
        codigos("2 erros de senha do user02", m, 3004, 3005);
        m = marca();
        verificarAoSair("run1", "X da janela durante a autenticacao", m, 1002);
        CofreDigital f = frame();
        depois(() -> f.dispatchEvent(new WindowEvent(f, WindowEvent.WINDOW_CLOSING)));
        pararAqui();
    }

    // =================================================================== RUN 2/3: partida com frase errada / cancelar
    static void run2() throws Exception {
        secao("7. Reinicio com frase errada");
        int m = marca();
        iniciarCofre();
        JDialog d = esperarDialogo("frase do admin");
        String txt = texto(d);
        check("dialogo pede a frase secreta do administrador (admin@...)", txt.contains("frase secreta da chave privada do administrador") && txt.contains("(" + ADMIN + "):"), txt);
        JPasswordField pf = edt(() -> { for (Component x : todos(d.getContentPane())) if (x instanceof JPasswordField) return (JPasswordField) x; return null; });
        setTexto(pf, "errada");
        fechar(d, JOptionPane.OK_OPTION);
        JDialog e = esperarDialogo("erro frase");
        String msg = texto(e);
        check("'Chave privada do administrador verificada negativamente: ... O sistema sera encerrado.'",
                msg.contains("Chave privada do administrador verificada negativamente:") && msg.contains("O sistema sera encerrado."), msg);
        verificarAoSair("run2", "frase errada encerra o sistema", m, 1001, 1006, 1002);
        fechar(e, JOptionPane.OK_OPTION);
        pararAqui();
    }
    static void run3() throws Exception {
        secao("7. Reinicio: Cancelar");
        int m = marca();
        iniciarCofre();
        JDialog d = esperarDialogo("frase do admin");
        fechar(d, JOptionPane.CANCEL_OPTION);
        JDialog e = esperarDialogo("cancelado");
        String msg = texto(e);
        check("'Partida do sistema cancelada.'", msg.contains("Partida do sistema cancelada."), msg);
        verificarAoSair("run3", "cancelar encerra o sistema", m, 1001, 1006, 1002);
        fechar(e, JOptionPane.OK_OPTION);
        pararAqui();
    }

    // =================================================================== RUN 4: partida normal, persistencia, adulteracao, Encerrar Sistema
    static void partidaNormal() {
        JDialog d = esperarDialogo("frase do admin");
        JPasswordField pf = edt(() -> { for (Component x : todos(d.getContentPane())) if (x instanceof JPasswordField) return (JPasswordField) x; return null; });
        setTexto(pf, "admin");
        fechar(d, JOptionPane.OK_OPTION);
        esperarTela(TelaLogin.class);
    }
    static void run4() throws Exception {
        secao("7. Reinicio com a frase correta");
        int m = marca();
        iniciarCofre();
        partidaNormal();
        codigos("frase 'admin' -> tela de login", m, 1001, 1006, 2001);

        secao("2.7 A contagem de erros sobrevive ao reinicio (user02)");
        loginName(U2);
        esperarTela(TelaSenha.class);
        m = marca();
        String msg = senhaErrada("1357246809");
        check("1 erro apos o reinicio ja bloqueia (terceiro consecutivo)", msg.contains("Terceiro erro consecutivo"), msg);
        esperarTela(TelaLogin.class);
        codigos("   ", m, 3006, 3007, 3002, 2001);

        secao("7. Persistencia: bloqueio do user01 + 2.4 'depois de 2 minutos'");
        if (bloqueadoAte(U1) > System.currentTimeMillis() + 3000) {
            m = marca();
            loginName(U1);
            msg = dialogoOk("ainda bloqueado");
            check("bloqueio do user01 continua valendo apos o reinicio", msg.contains("esta bloqueado"), msg);
            codigos("   ", m, 2004);
        }
        esperarDesbloqueio(U1);
        m = marca();
        loginName(U1);
        esperarTela(TelaSenha.class);
        codigos("depois de 2 minutos o login volta a funcionar", m, 2003, 2002, 3001);
        digitar(S_U1);
        okSenha();
        esperarTela(TelaToken.class);
        longeDaTroca();
        token(totp(estado.getProperty("user01"), 0));
        esperarTela(TelaPrincipal.class);
        check("usuarios continuam cadastrados (user01 entrou)", rotulos(tela()).contains("Login: " + U1));

        secao("4. Consulta do user01 (dono + compartilhamento)");
        menu("2");
        esperarTela(TelaConsulta.class);
        listar(FILES, "user01");
        semDialogo("listagem user01");
        check("user01 lista teste01.docx e teste02.docx", linhasTabela().equals(Arrays.asList("teste01.docx " + U1 + " usuario", "teste02.docx " + U2 + " usuario")), linhasTabela());
        for (String n : new String[]{"teste01.docx", "teste02.docx"}) {
            new File(FILES, n).delete();
            m = marca();
            cliqueDuplo(n);
            msg = dialogoOk("abrir " + n);
            check("user01 abre " + n, msg.contains("decriptado e verificado") && new File(FILES, n).isFile(), msg);
            codigos("   ", m, 7015, 7016, 7018, 7019);
        }

        secao("5. Integridade e autenticidade pela interface (copias adulteradas)");
        Object[][] casos = {
            {"index.asd", null, "Falha na verificacao de integridade e autenticidade do arquivo de indice", new Integer[]{7003, 7005, 7008}},
            {"index.enc", null, "Falha na verificacao de integridade e autenticidade do arquivo de indice", new Integer[]{7003, 7005, 7008}},
            {"-index.env", null, "Falha na decriptacao do arquivo de indice", new Integer[]{7003, 7007}},
            {"share.asd", null, "Falha na verificacao de integridade e autenticidade do arquivo de share", new Integer[]{7003, 7005, 7006, 7010, 7013}},
            {"share.env", null, "Falha na decriptacao do arquivo de share", new Integer[]{7003, 7005, 7006, 7012}},
            {"XXYYZZ11.asd", "teste01.docx", "Falha na verificacao de integridade e autenticidade do arquivo teste01.docx", new Integer[]{7015, 7016, 7018, 7021}},
            {"XXYYZZ11.enc", "teste01.docx", "Falha na verificacao de integridade e autenticidade do arquivo teste01.docx", new Integer[]{7015, 7016, 7018, 7021}},
            {"XXYYZZ11.env", "teste01.docx", "Falha na decriptacao do arquivo teste01.docx (sigilo)", new Integer[]{7015, 7016, 7020}},
            {"XXYYZZ22.1.shr", "teste02.docx", "Falha na decriptacao do arquivo teste02.docx (sigilo)", new Integer[]{7015, 7016, 7020}}};
        for (Object[] c : casos) {
            File dir = new File("FilesTeste");
            for (File x : dir.exists() ? dir.listFiles() : new File[0]) x.delete();
            copiarPasta(new File(T3, "Pacote-T3/Files"), dir);
            String alvo = (String) c[0];
            if (c[1] != null) { listar(dir.getAbsolutePath(), "user01"); semDialogo("listar antes de adulterar"); }
            if (alvo.startsWith("-")) new File(dir, alvo.substring(1)).delete(); else adulterar(new File(dir, alvo));
            m = marca();
            if (c[1] == null) listar(dir.getAbsolutePath(), "user01"); else cliqueDuplo((String) c[1]);
            msg = dialogoOk("adulteracao");
            boolean nada = true;
            for (File x : dir.listFiles()) if (x.getName().endsWith(".docx")) nada = false;
            check((alvo.startsWith("-") ? "remover " + alvo.substring(1) : "adulterar " + alvo) + ": '" + c[2] + "'", msg.contains((String) c[2]), msg);
            check("   nada decriptado gravado na pasta", nada);
            codigos("   ", m, (Integer[]) c[3]);
        }

        secao("6. Encerrar Sistema");
        clicar(tela(), "Voltar de Consultar para o Menu Principal");
        esperarTela(TelaPrincipal.class);
        menu("3");
        esperarTela(TelaSaida.class);
        m = marca();
        verificarAoSair("run4", "Encerrar Sistema", m, 8003, 1004, 1002);
        clicar(tela(), "Encerrar Sistema");
        pararAqui();
    }

    // =================================================================== RUN 5: total de acessos persistido + X com sessao aberta
    static void run5() throws Exception {
        secao("7. Persistencia do total de acessos + 6. X com sessao aberta");
        iniciarCofre();
        partidaNormal();
        esperarDesbloqueio(ADMIN);
        entrar(ADMIN, S_ADMIN, estado.getProperty("admin"));
        check("'Total de acessos' continua de onde parou (3 para o admin)", rotulos(tela()).contains("Total de acessos do usu\u00e1rio: 3"), rotulos(tela()));
        int m = marca();
        verificarAoSair("run5", "X da janela com sessao aberta", m, 1004, 1002);
        CofreDigital f = frame();
        depois(() -> f.dispatchEvent(new WindowEvent(f, WindowEvent.WINDOW_CLOSING)));
        pararAqui();
    }

    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        carregarEstado();
        try {
            switch (args[0]) {
                case "run1": run1(); break;
                case "run2": run2(); break;
                case "run3": run3(); break;
                case "run4": run4(); break;
                case "run5": run5(); break;
            }
        } catch (Throwable e) {
            falhas++;
            System.out.println("  [FALHA] excecao no teste: " + e);
            e.printStackTrace(System.out);
            resumo(args[0]);
            Runtime.getRuntime().halt(2);
        }
    }
}
