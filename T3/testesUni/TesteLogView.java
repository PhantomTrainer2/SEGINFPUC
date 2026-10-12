/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 *
 * Testes do programa de auditoria logView (HowToTest.md, secao 8). A
 * autenticacao e a listagem sao chamadas diretamente: a frase secreta e lida
 * do Console (sem eco), o que so pode ser conferido num terminal de verdade
 * (HowToTest.md, secao 10.2). Roda no diretorio do cofre.db gerado pelo TesteGui.
 *
 * Uso: ./testesUni/rodar.sh logview (a partir da pasta T3)
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class TesteLogView {
    static int ok = 0, falhas = 0;
    static final String T3 = System.getProperty("t3", ".");
    static final String KEYS = T3 + "/Pacote-T3/Keys/";

    static void check(String nome, boolean cond, Object detalhe) {
        if (cond) { ok++; System.out.println("  [OK]    " + nome); }
        else { falhas++; System.out.println("  [FALHA] " + nome + "   -> obtido: " + detalhe); }
    }
    static void check(String nome, boolean cond) { check(nome, cond, ""); }
    static void secao(String s) { System.out.println("\n== " + s); }

    public static void main(String[] args) throws Exception {
        if (!new File("cofre.db").isFile()) {
            System.out.println("  [FALHA] cofre.db nao encontrado no diretorio corrente: rode antes o TesteGui");
            System.exit(1);
        }

        secao("8. logView: autenticacao do administrador (chave privada + certificado)");
        BancoDeDados banco = new BancoDeDados("cofre.db");
        Method autenticar = LogView.class.getDeclaredMethod("autenticarAdministrador", BancoDeDados.class, byte[].class, char[].class);
        autenticar.setAccessible(true);
        String[][] casos = {
            {"admin", "admin", "Administrador autenticado: admin@inf1416.puc-rio.br"},
            {"admin", "errada", "Autenticacao negada: frase secreta invalida para a chave privada informada."},
            {"user01", "user01", "Autenticacao negada: a chave privada nao pertence a um administrador do sistema"},
            {"user02", "user02", "Autenticacao negada: a chave privada nao pertence a um administrador do sistema"}};
        for (String[] c : casos) {
            String resultado;
            try {
                Usuario u = (Usuario) autenticar.invoke(null, banco,
                        Files.readAllBytes(Paths.get(KEYS + c[0] + "-pkcs8-aes.key")), c[1].toCharArray());
                resultado = "Administrador autenticado: " + u.login;
            } catch (InvocationTargetException e) {
                resultado = "Autenticacao negada: " + e.getCause().getMessage();
            }
            check("chave " + c[0] + ", frase '" + c[1] + "' -> '" + c[2] + "'", resultado.startsWith(c[2]), resultado);
        }

        secao("8. logView: registros com data, hora e codigo, em ordem cronologica");
        Method exibir = LogView.class.getDeclaredMethod("exibirRegistros", List.class);
        exibir.setAccessible(true);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer, true, "UTF-8"));
        try {
            exibir.invoke(null, banco.listarRegistros());
        } finally {
            System.setOut(original);
        }
        banco.fechar();
        String saida = buffer.toString("UTF-8");

        int total;
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:cofre.db"); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM Registros")) {
            rs.next();
            total = rs.getInt(1);
        }
        List<String> linhas = new ArrayList<String>();
        for (String l : saida.split("\\r?\\n")) {
            if (l.matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}  \\d{4}    .+")) linhas.add(l);
        }
        check("uma linha 'data hora codigo mensagem' por registro (" + total + ")", total > 0 && linhas.size() == total, linhas.size());
        SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        boolean cronologica = true;
        Date anterior = null;
        for (String l : linhas) {
            Date atual = formato.parse(l.substring(0, 19));
            if (anterior != null && atual.before(anterior)) cronologica = false;
            anterior = atual;
        }
        check("registros em ordem cronologica", cronologica);
        check("primeiro registro: 1001 Sistema iniciado.", !linhas.isEmpty() && linhas.get(0).endsWith("1001    Sistema iniciado."),
                linhas.isEmpty() ? "" : linhas.get(0));
        check("<login_name> e <arq_name> sempre substituidos", !saida.contains("<login_name>") && !saida.contains("<arq_name>"));
        check("mensagem com login e arquivo (7017 do user02 em teste01.docx)",
                saida.contains("7017    Acesso negado ao arquivo teste01.docx para user02@inf1416.puc-rio.br."));
        check("registros sem usuario mostram '-'", saida.contains("6001    Tela de cadastro apresentada para -."));
        check("rodape 'Total de registros: " + total + "'", saida.contains("Total de registros: " + total));

        secao("8. logView pela linha de comando");
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String jar = new File(T3, "logview.jar").getAbsolutePath();
        String r = executar(java, "-jar", jar);
        check("sem argumentos -> 'Uso: java -jar logview.jar <Caminho_Chave_Privada_Admin> [Caminho_Banco_de_Dados]'",
                r.contains("Uso: java -jar logview.jar <Caminho_Chave_Privada_Admin> [Caminho_Banco_de_Dados]"), r);
        String inexistente = new File("nao-existe.key").getAbsolutePath();
        r = executar(java, "-jar", jar, inexistente);
        check("chave inexistente -> 'Erro: caminho do arquivo da chave privada invalido'",
                r.contains("Erro: caminho do arquivo da chave privada invalido: " + inexistente), r);
        r = executar(java, "-jar", jar, KEYS + "admin-pkcs8-aes.key", "nao-existe.db");
        check("banco inexistente -> 'Erro: banco de dados nao encontrado'", r.contains("Erro: banco de dados nao encontrado: nao-existe.db"), r);

        secao("8. logView separado do programa do cofre");
        List<String> noCofre = entradas(new File(T3, "cofre.jar"));
        List<String> noLogView = entradas(new File(T3, "logview.jar"));
        check("cofre.jar nao contem classes do LogView", noCofre.contains("CofreDigital.class") && !temPrefixo(noCofre, "LogView"), noCofre);
        check("logview.jar nao contem as telas do cofre", noLogView.contains("LogView.class")
                && !temPrefixo(noLogView, "Tela") && !temPrefixo(noLogView, "CofreDigital"), noLogView);
        check("Main-Class: CofreDigital / LogView", "CofreDigital".equals(mainClass(new File(T3, "cofre.jar")))
                && "LogView".equals(mainClass(new File(T3, "logview.jar"))));

        System.out.println("\nRESULTADO logview: " + ok + " OK, " + falhas + " FALHA(S)");
        System.exit(falhas == 0 ? 0 : 1);
    }

    /** Executa o comando (sem entrada padrao) e retorna a saida e os erros juntos. */
    static String executar(String... comando) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(comando);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().close();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        InputStream in = p.getInputStream();
        byte[] bloco = new byte[4096];
        for (int n; (n = in.read(bloco)) > 0; ) saida.write(bloco, 0, n);
        p.waitFor();
        return saida.toString().trim();
    }

    static List<String> entradas(File jar) throws Exception {
        List<String> nomes = new ArrayList<String>();
        try (JarFile j = new JarFile(jar)) {
            for (Enumeration<JarEntry> e = j.entries(); e.hasMoreElements(); ) nomes.add(e.nextElement().getName());
        }
        return nomes;
    }

    static boolean temPrefixo(List<String> nomes, String prefixo) {
        for (String n : nomes) if (n.startsWith(prefixo)) return true;
        return false;
    }

    static String mainClass(File jar) throws Exception {
        try (JarFile j = new JarFile(jar)) {
            return j.getManifest().getMainAttributes().getValue("Main-Class");
        }
    }
}
