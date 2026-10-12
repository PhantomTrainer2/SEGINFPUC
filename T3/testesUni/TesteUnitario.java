/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 *
 * Testes unitarios (sem interface grafica): TOTP com os vetores do RFC 6238,
 * Base32, certificados, chaves privadas, critica da senha, bcrypt, cadastro
 * no banco, regras de autenticacao e bloqueio, pasta segura (listagem,
 * controle de acesso e arquivos adulterados). Fica no pacote padrao para
 * acessar os membros package-private das classes do projeto.
 *
 * Uso: ./testesUni/rodar.sh unitario (a partir da pasta T3)
 */

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.crypto.SecretKey;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

public class TesteUnitario {
    static int ok = 0, falhas = 0;
    static final String T3 = System.getProperty("t3", ".");
    static final String KEYS = T3 + "/Pacote-T3/Keys/";
    static final String FILES = T3 + "/Pacote-T3/Files";
    // Banco e copias da pasta segura usados nos testes (apagados no final)
    static File TEMP;

    static void check(String nome, boolean cond, Object detalhe) {
        if (cond) { ok++; System.out.println("  [OK]   " + nome); }
        else { falhas++; System.out.println("  [FALHA] " + nome + "  -> " + detalhe); }
    }
    static void check(String nome, boolean cond) { check(nome, cond, ""); }
    static void secao(String s) { System.out.println("\n== " + s); }

    static byte[] ler(String p) throws Exception { return Files.readAllBytes(Paths.get(p)); }

    static String totpCode(TOTP t, long intervalo) throws Exception {
        Method m = TOTP.class.getDeclaredMethod("TOTPCode", long.class);
        m.setAccessible(true);
        return (String) m.invoke(t, intervalo);
    }

    static List<Integer> mids(String db, int depoisDe) throws Exception {
        List<Integer> r = new ArrayList<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             PreparedStatement ps = c.prepareStatement("SELECT MID FROM Registros WHERE RID > ? ORDER BY RID")) {
            ps.setInt(1, depoisDe);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) r.add(rs.getInt(1)); }
        }
        return r;
    }
    static int ultimoRid(String db) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT IFNULL(MAX(RID),0) FROM Registros")) {
            rs.next(); return rs.getInt(1);
        }
    }

    /** Pares do teclado virtual: cada digito da senha vem acompanhado de um digito aleatorio diferente. */
    static List<int[]> pares(String senha, Random r) {
        List<int[]> l = new ArrayList<>();
        for (char ch : senha.toCharArray()) {
            int d = ch - '0', o;
            do { o = r.nextInt(10); } while (o == d);
            l.add(r.nextBoolean() ? new int[]{d, o} : new int[]{o, d});
        }
        return l;
    }

    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        TEMP = Files.createTempDirectory("cofre-testes").toFile();
        Random rnd = new Random(42);

        secao("Ambiente");
        check("AES-256 disponivel (Java " + System.getProperty("java.version") + ")", Criptografia.suportaAES256());

        secao("TOTP - vetores de teste do RFC 6238 (SHA1, 6 digitos)");
        String b32rfc = new Base32(Base32.Alphabet.BASE32, false, false).toString("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        check("Base32 da chave do RFC = GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", b32rfc.equals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"), b32rfc);
        TOTP rfc = new TOTP(b32rfc, 30);
        long[] tempos = {59L, 1111111109L, 1111111111L, 1234567890L, 2000000000L, 20000000000L};
        String[] esperados = {"287082", "081804", "050471", "005924", "279037", "353130"};
        for (int i = 0; i < tempos.length; i++) {
            String c = totpCode(rfc, tempos[i] / 30);
            check("T=" + tempos[i] + " -> " + esperados[i], c.equals(esperados[i]), c);
        }
        long agora = new java.util.Date().getTime() / 1000 / 30;
        check("validateCode aceita o intervalo atual", rfc.validateCode(totpCode(rfc, agora)));
        check("validateCode aceita -30 s (intervalo anterior)", rfc.validateCode(totpCode(rfc, agora - 1)));
        check("validateCode aceita +30 s (proximo intervalo)", rfc.validateCode(totpCode(rfc, agora + 1)));
        String m2 = totpCode(rfc, agora - 2), p2 = totpCode(rfc, agora + 2);
        boolean colide = m2.equals(totpCode(rfc, agora)) || m2.equals(totpCode(rfc, agora - 1)) || m2.equals(totpCode(rfc, agora + 1));
        check("validateCode rejeita -60 s", colide || !rfc.validateCode(m2));
        check("validateCode rejeita +60 s", !rfc.validateCode(p2) || p2.equals(totpCode(rfc, agora)));
        check("validateCode rejeita 'abcdef' e '12345'", !rfc.validateCode("abcdef") && !rfc.validateCode("12345"));
        check("generateCode tem 6 digitos", rfc.generateCode().matches("\\d{6}"));
        boolean excecao = false;
        try { new TOTP("!!!invalido", 30); } catch (Exception e) { excecao = true; }
        check("Construtor gera Exception com BASE32 invalido", excecao);
        String fonteTotp = new String(ler(T3 + "/src/TOTP.java"), StandardCharsets.UTF_8);
        List<String> imports = new ArrayList<>();
        for (String l : fonteTotp.split("\n")) if (l.trim().startsWith("import")) imports.add(l.trim());
        check("TOTP.java importa apenas Mac, SecretKeySpec e Date", imports.equals(Arrays.asList(
                "import javax.crypto.Mac;", "import javax.crypto.spec.SecretKeySpec;", "import java.util.Date;")), imports);

        secao("Base32");
        byte[] vinte = new byte[20]; new SecureRandom().nextBytes(vinte);
        String b = new Base32(Base32.Alphabet.BASE32, false, false).toString(vinte);
        check("20 bytes -> 32 caracteres BASE32 maiusculos", b.matches("[A-Z2-7]{32}"), b);
        check("ida e volta BASE32", Arrays.equals(vinte, new Base32(Base32.Alphabet.BASE32, false, false).fromString(b)));

        secao("Certificados do pacote");
        String[][] certs = {{"admin", "23", "Administrator", "admin@inf1416.puc-rio.br"},
                            {"user01", "18", "Usuario 01", "user01@inf1416.puc-rio.br"},
                            {"user02", "19", "Usuario 02", "user02@inf1416.puc-rio.br"}};
        Map<String, X509Certificate> cert = new HashMap<>();
        for (String[] c : certs) {
            String pem = Criptografia.extrairPEMCertificado(ler(KEYS + c[0] + "-x509.crt"));
            X509Certificate x = Criptografia.carregarCertificado(pem);
            cert.put(c[0], x);
            check(c[0] + ": PEM extraido comeca com BEGIN CERTIFICATE", pem.startsWith("-----BEGIN CERTIFICATE-----"));
            check(c[0] + ": versao 3", x.getVersion() == 3);
            check(c[0] + ": serie " + c[1], x.getSerialNumber().toString().equals(c[1]), x.getSerialNumber());
            check(c[0] + ": nome (CN) = " + c[2], c[2].equals(Criptografia.extrairNome(x)), Criptografia.extrairNome(x));
            check(c[0] + ": e-mail = " + c[3], c[3].equals(Criptografia.extrairEmail(x)), Criptografia.extrairEmail(x));
            check(c[0] + ": tipo de assinatura SHA256withRSA", "SHA256withRSA".equals(x.getSigAlgName()), x.getSigAlgName());
        }
        String emissor = Criptografia.nomeDistinto(cert.get("admin").getIssuerX500Principal());
        check("Emissor legivel", emissor.equals("EMAILADDRESS=ca@grad.inf.puc-rio.br, CN=AC INF1416, OU=INF1416, O=PUC, L=Rio, ST=RJ, C=BR"), emissor);
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy");
        check("Validade 23/04/2026 a 23/04/2027", f.format(cert.get("admin").getNotBefore()).equals("23/04/2026")
                && f.format(cert.get("admin").getNotAfter()).equals("23/04/2027"));
        boolean naoPem = false;
        try { Criptografia.extrairPEMCertificado(ler(KEYS + "admin-pkcs8-aes.key")); } catch (Exception e) { naoPem = true; }
        check("Arquivo .key rejeitado como certificado", naoPem);

        secao("Chaves privadas (frase secreta + assinatura de teste)");
        Map<String, PrivateKey> priv = new HashMap<>();
        for (String u : new String[]{"admin", "user01", "user02"}) {
            PrivateKey k = Criptografia.validarChavePrivada(ler(KEYS + u + "-pkcs8-aes.key"), u.toCharArray(),
                    cert.get(u).getPublicKey(), 10240);
            priv.put(u, k);
            check(u + ": frase correta restaura a chave e valida o par", k != null);
        }
        for (String errada : new String[]{"errada", "", "Admin", "admin "}) {
            ChavePrivadaInvalidaException.Motivo mot = null;
            try { Criptografia.validarChavePrivada(ler(KEYS + "admin-pkcs8-aes.key"), errada.toCharArray(), cert.get("admin").getPublicKey(), 10240); }
            catch (ChavePrivadaInvalidaException e) { mot = e.getMotivo(); }
            check("frase '" + errada + "' -> FRASE_SECRETA_INVALIDA", mot == ChavePrivadaInvalidaException.Motivo.FRASE_SECRETA_INVALIDA, mot);
        }
        ChavePrivadaInvalidaException.Motivo mot = null;
        try { Criptografia.validarChavePrivada(ler(KEYS + "user01-pkcs8-aes.key"), "user01".toCharArray(), cert.get("admin").getPublicKey(), 10240); }
        catch (ChavePrivadaInvalidaException e) { mot = e.getMotivo(); }
        check("chave do user01 + certificado do admin -> ASSINATURA_INVALIDA", mot == ChavePrivadaInvalidaException.Motivo.ASSINATURA_INVALIDA, mot);
        check("assinatura de teste de 2048 bytes (logView) confere", Criptografia.verificarParDeChaves(priv.get("admin"), cert.get("admin").getPublicKey(), 2048));

        secao("Senha pessoal: critica do formulario");
        Object[][] casos = {
            {"13572468", "13572468", null}, {"135724680", "135724680", null}, {"1357246809", "1357246809", null},
            {"0101010101", "0101010101", null},
            {"1357246a", "1357246a", "apenas digitos"}, {"1357246", "1357246", "8, 9 ou 10"},
            {"13572446", "13572446", "repetidos"}, {"11111111", "11111111", "repetidos"},
            {"13572468", "13572469", "nao conferem"}, {"12345678901", "12345678901", "8, 9 ou 10"}, {"", "", "8, 9 ou 10"}};
        for (Object[] c : casos) {
            String r = ServicoCadastro.criticarSenha(((String) c[0]).toCharArray(), ((String) c[1]).toCharArray());
            boolean passou = c[2] == null ? r == null : r != null && r.contains((String) c[2]);
            check("senha '" + c[0] + "' / '" + c[1] + "' -> " + (c[2] == null ? "aceita" : c[2]), passou, r);
        }

        secao("bcrypt (OpenBSDBCrypt, versao 2y, custo 8)");
        check("hash do exemplo do enunciado confere com 13572468",
                OpenBSDBCrypt.checkPassword("$2y$08$ajDZdV0XFoYiLRE0ZrSx6OmRbhhB5qM.Ap6c.0LqRu2cIY1jpytuO", "13572468".toCharArray()));

        secao("Busca da senha no teclado sobrecarregado (2^n combinacoes)");
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        String h10 = OpenBSDBCrypt.generate("2y", "1357246809".toCharArray(), salt, 8);
        String h8 = OpenBSDBCrypt.generate("2y", "13572468".toCharArray(), salt, 8);
        long t0 = System.currentTimeMillis();
        char[] achou8 = Autenticador.buscarSenha(h8, pares("13572468", rnd));
        long t8 = System.currentTimeMillis() - t0;
        check("8 digitos: senha encontrada (" + t8 + " ms)", achou8 != null && new String(achou8).equals("13572468"));
        t0 = System.currentTimeMillis();
        char[] achou10 = Autenticador.buscarSenha(h10, pares("1357246809", rnd));
        long t10 = System.currentTimeMillis() - t0;
        check("10 digitos: senha encontrada (" + t10 + " ms)", achou10 != null && new String(achou10).equals("1357246809"));
        t0 = System.currentTimeMillis();
        check("senha errada (10 pares) nao encontrada", Autenticador.buscarSenha(h10, pares("2468024680", rnd)) == null);
        System.out.println("         pior caso (1024 bcrypts) levou " + (System.currentTimeMillis() - t0) + " ms");

        secao("Cadastro completo num banco temporario");
        String db = TEMP.getPath() + File.separator + "teste-unitario-" + System.nanoTime() + ".db";
        BancoDeDados banco = new BancoDeDados(db);
        ServicoCadastro sc = new ServicoCadastro(banco);
        int rid = ultimoRid(db);
        String chaveAdmin = null;
        ServicoCadastro.DadosCadastro dados = sc.validar(null, KEYS + "admin-x509.crt", KEYS + "admin-pkcs8-aes.key",
                "admin".toCharArray(), BancoDeDados.GID_ADMINISTRADOR, "13572468".toCharArray(), "13572468".toCharArray());
        check("validar admin: login e nome do Sujeito", dados.login.equals("admin@inf1416.puc-rio.br") && dados.nome.equals("Administrator"));
        chaveAdmin = sc.efetivar(dados);
        check("efetivar admin: chave TOTP de 32 caracteres BASE32", chaveAdmin.matches("[A-Z2-7]{32}"), chaveAdmin);
        check("nenhum registro gravado pelo servico no caminho feliz", mids(db, rid).isEmpty(), mids(db, rid));

        String[][] erros = {
            {"1357246a", "1357246a", KEYS + "admin-x509.crt", KEYS + "admin-pkcs8-aes.key", "admin", "6003"},
            {"13572468", "13572468", KEYS + "nao-existe.crt", KEYS + "admin-pkcs8-aes.key", "admin", "6004"},
            {"13572468", "13572468", KEYS + "admin-pkcs8-aes.key", KEYS + "admin-pkcs8-aes.key", "admin", "6004"},
            {"13572468", "13572468", "", KEYS + "admin-pkcs8-aes.key", "admin", "6004"},
            {"13572468", "13572468", KEYS + "admin-x509.crt", KEYS + "nao-existe.key", "admin", "6005"},
            {"13572468", "13572468", KEYS + "admin-x509.crt", "", "admin", "6005"},
            {"13572468", "13572468", KEYS + "admin-x509.crt", KEYS + "admin-pkcs8-aes.key", "errada", "6006"},
            {"13572468", "13572468", KEYS + "admin-x509.crt", KEYS + "user01-pkcs8-aes.key", "user01", "6007"}};
        for (String[] e : erros) {
            rid = ultimoRid(db);
            String msg = null;
            try { sc.validar("admin@inf1416.puc-rio.br", e[2], e[3], e[4].toCharArray(), 1, e[0].toCharArray(), e[1].toCharArray()); }
            catch (ServicoCadastro.CadastroException ex) { msg = ex.getMessage(); }
            List<Integer> m = mids(db, rid);
            check("erro de cadastro registra " + e[5] + " (" + msg + ")", msg != null && m.equals(Collections.singletonList(Integer.parseInt(e[5]))), m);
        }

        ServicoCadastro.DadosCadastro u1 = sc.validar("admin@inf1416.puc-rio.br", KEYS + "user01-x509.crt", KEYS + "user01-pkcs8-aes.key",
                "user01".toCharArray(), BancoDeDados.GID_USUARIO, "2468135790".toCharArray(), "2468135790".toCharArray());
        String chaveU1 = sc.efetivar(u1);
        ServicoCadastro.DadosCadastro u2 = sc.validar("admin@inf1416.puc-rio.br", KEYS + "user02-x509.crt", KEYS + "user02-pkcs8-aes.key",
                "user02".toCharArray(), BancoDeDados.GID_USUARIO, "9081726354".toCharArray(), "9081726354".toCharArray());
        sc.efetivar(u2);
        String dup = null;
        try {
            sc.efetivar(sc.validar("admin@inf1416.puc-rio.br", KEYS + "user01-x509.crt", KEYS + "user01-pkcs8-aes.key",
                    "user01".toCharArray(), BancoDeDados.GID_USUARIO, "2468135790".toCharArray(), "2468135790".toCharArray()));
        } catch (ServicoCadastro.CadastroException e) { dup = e.getMessage(); }
        check("login duplicado rejeitado", dup != null && dup.contains("ja esta cadastrado"), dup);
        check("total de usuarios = 3", banco.contarUsuarios() == 3, banco.contarUsuarios());

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement st = c.createStatement()) {
            List<String> tabelas = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
                while (rs.next()) tabelas.add(rs.getString(1));
            }
            check("cinco tabelas: Chaveiro, Grupos, Mensagens, Registros, Usuarios",
                    tabelas.equals(Arrays.asList("Chaveiro", "Grupos", "Mensagens", "Registros", "Usuarios")), tabelas);
            List<String> colunas = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("PRAGMA table_info(Registros)")) { while (rs.next()) colunas.add(rs.getString("name")); }
            check("Registros sem coluna de texto: " + colunas, !colunas.contains("texto") && colunas.contains("MID") && colunas.contains("data_hora"));
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM Mensagens")) { rs.next(); check("Mensagens tem os 64 codigos da tabela do enunciado", rs.getInt(1) == 64, rs.getInt(1)); }
            try (ResultSet rs = st.executeQuery("SELECT GID, nome FROM Grupos ORDER BY GID")) {
                rs.next(); String g1 = rs.getInt(1) + "=" + rs.getString(2); rs.next(); String g2 = rs.getInt(1) + "=" + rs.getString(2);
                check("Grupos 1=Administrador, 2=Usu\u00e1rio", g1.equals("1=Administrador") && g2.equals("2=Usu\u00e1rio"), g1 + " " + g2);
            }
            try (ResultSet rs = st.executeQuery("SELECT u.UID, u.KID, u.senha_hash, u.token_cifrado, c.UID, c.certificado_pem, c.chave_privada "
                    + "FROM Usuarios u JOIN Chaveiro c ON c.KID = u.KID WHERE u.login = 'user01@inf1416.puc-rio.br'")) {
                check("user01: KID gravado em Usuarios e associado ao UID no Chaveiro", rs.next() && rs.getInt(2) > 0 && rs.getInt(1) == rs.getInt(5));
                String hash = rs.getString(3);
                check("user01: hash bcrypt $2y$08$ com 60 caracteres", hash.matches("\\$2y\\$08\\$[./A-Za-z0-9]{53}"), hash);
                check("user01: hash confere com a senha", OpenBSDBCrypt.checkPassword(hash, "2468135790".toCharArray()));
                SecretKey k = Criptografia.gerarChaveAES("2468135790".getBytes(StandardCharsets.UTF_8));
                String tok = new String(Criptografia.decifrarAES(k, rs.getBytes(4)), StandardCharsets.US_ASCII);
                check("user01: token_cifrado (AES/SHA1PRNG da senha) decripta para a chave BASE32 mostrada", tok.equals(chaveU1), tok);
                check("user01: certificado no Chaveiro em PEM", rs.getString(6).startsWith("-----BEGIN CERTIFICATE-----") && rs.getString(6).trim().endsWith("-----END CERTIFICATE-----"));
                check("user01: chave privada no Chaveiro = binario cifrado original", Arrays.equals(rs.getBytes(7), ler(KEYS + "user01-pkcs8-aes.key")));
            }
        }

        secao("Autenticacao (regras e registros)");
        Autenticador aut = new Autenticador(banco);
        rid = ultimoRid(db);
        check("'abc' nao identificado", aut.identificarUsuario("abc") == null);
        check("'naoexiste@inf1416.puc-rio.br' nao identificado", aut.identificarUsuario("naoexiste@inf1416.puc-rio.br") == null);
        Usuario admin = aut.identificarUsuario("admin@inf1416.puc-rio.br");
        check("admin identificado", admin != null && admin.isAdministrador());
        check("registros 2005 2005 2003", mids(db, rid).equals(Arrays.asList(2005, 2005, 2003)), mids(db, rid));

        Usuario user01 = aut.identificarUsuario("user01@inf1416.puc-rio.br");
        rid = ultimoRid(db);
        aut.verificarSenha(user01, pares("1111111111", rnd));
        aut.verificarSenha(user01, pares("2222222222", rnd));
        char[] s = aut.verificarSenha(user01, pares("2468135790", rnd));
        check("2 erros + acerto: 3004 3005 3003 e contagem zerada", s != null && user01.errosSenha == 0
                && mids(db, rid).equals(Arrays.asList(3004, 3005, 3003)), mids(db, rid));
        rid = ultimoRid(db);
        aut.verificarSenha(user01, pares("1111111111", rnd));
        check("erro depois do acerto conta como primeiro (3004)", mids(db, rid).equals(Collections.singletonList(3004)), mids(db, rid));
        rid = ultimoRid(db);
        user01 = aut.identificarUsuario("user01@inf1416.puc-rio.br"); // "reinicio": recarrega do banco
        check("contagem de erros persistida no banco (erros_senha = 1)", user01.errosSenha == 1, user01.errosSenha);
        aut.verificarSenha(user01, pares("1111111111", rnd));
        aut.verificarSenha(user01, pares("1111111111", rnd));
        check("3 erros consecutivos: 3005 3006 3007 e bloqueio", user01.estaBloqueado()
                && mids(db, rid).equals(Arrays.asList(2003, 3005, 3006, 3007)), mids(db, rid));
        long restante = user01.segundosRestantesBloqueio();
        check("bloqueio de ~120 s (" + restante + " s)", restante > 115 && restante <= 120);
        rid = ultimoRid(db);
        Usuario bloq = aut.identificarUsuario("user01@inf1416.puc-rio.br");
        check("login durante bloqueio: 2004", bloq.estaBloqueado() && mids(db, rid).equals(Collections.singletonList(2004)), mids(db, rid));
        check("outro usuario (user02) nao esta bloqueado", !aut.identificarUsuario("user02@inf1416.puc-rio.br").estaBloqueado());

        rid = ultimoRid(db);
        char[] senhaAdmin = "13572468".toCharArray();
        TOTP totpAdmin = new TOTP(chaveAdmin, 30);
        boolean tokOk = aut.verificarToken(admin, senhaAdmin, totpAdmin.generateCode());
        check("token atual aceito: 4003", tokOk && mids(db, rid).equals(Collections.singletonList(4003)), mids(db, rid));
        rid = ultimoRid(db);
        long iv = new java.util.Date().getTime() / 1000 / 30;
        check("token do intervalo anterior aceito", aut.verificarToken(admin, senhaAdmin, totpCode(totpAdmin, iv - 1)));
        check("token com senha errada (chave AES errada) rejeitado", !aut.verificarToken(admin, "13572469".toCharArray(), totpAdmin.generateCode()));
        aut.verificarToken(admin, senhaAdmin, totpAdmin.generateCode()); // zera
        rid = ultimoRid(db);
        aut.verificarToken(admin, senhaAdmin, "000000".equals(totpAdmin.generateCode()) ? "000001" : "000000");
        aut.verificarToken(admin, senhaAdmin, "abc");
        aut.verificarToken(admin, senhaAdmin, "");
        check("3 tokens errados: 4004 4005 4006 4007 e bloqueio", admin.estaBloqueado()
                && mids(db, rid).equals(Arrays.asList(4004, 4005, 4006, 4007)), mids(db, rid));
        banco.bloquearUsuario(admin.uid, 0); // libera para os testes seguintes

        secao("Pasta segura: index e share do pacote");
        PastaSegura ps = new PastaSegura(banco, priv.get("admin"), cert.get("admin").getPublicKey());
        // Imprime o conteudo decriptado para referencia
        Method dec = PastaSegura.class.getDeclaredMethod("decriptar", File.class, String.class, String.class, PrivateKey.class);
        dec.setAccessible(true);
        System.out.println("  index:\n    " + new String((byte[]) dec.invoke(null, new File(FILES), "index", "index.env", priv.get("admin")), StandardCharsets.US_ASCII).trim().replace("\n", "\n    "));
        System.out.println("  share:\n    " + new String((byte[]) dec.invoke(null, new File(FILES), "share", "share.env", priv.get("admin")), StandardCharsets.US_ASCII).trim().replace("\n", "\n    "));

        File copia = new File(TEMP.getPath(), "cofre-files-" + System.nanoTime());
        copiar(new File(FILES), copia);
        String[][] esperadoLista = {{"admin", "teste00.docx"}, {"user01", "teste01.docx,teste02.docx"}, {"user02", "teste01.docx,teste02.docx"}};
        Map<String, Usuario> usuarios = new HashMap<>();
        for (String[] e : esperadoLista) {
            Usuario u = banco.buscarUsuarioPorLogin(e[0] + "@inf1416.puc-rio.br");
            usuarios.put(e[0], u);
            rid = ultimoRid(db);
            List<String> nomes = new ArrayList<>();
            for (PastaSegura.Arquivo a : ps.listar(copia, u)) nomes.add(a.nomeSecreto);
            check(e[0] + " lista " + e[1], String.join(",", nomes).equals(e[1]), nomes);
            check(e[0] + ": registros 7005 7006 7010 7011", mids(db, rid).equals(Arrays.asList(7005, 7006, 7010, 7011)), mids(db, rid));
        }

        secao("Pasta segura: decriptacao e controle de acesso");
        String[][] abrir = {{"admin", "teste00.docx", "ok"}, {"user01", "teste01.docx", "ok"}, {"user01", "teste02.docx", "ok"},
                            {"user02", "teste02.docx", "ok"}, {"user02", "teste01.docx", "negado"}};
        for (String[] a : abrir) {
            Usuario u = usuarios.get(a[0]);
            PastaSegura.Arquivo arq = null;
            for (PastaSegura.Arquivo x : ps.listar(copia, u)) if (x.nomeSecreto.equals(a[1])) arq = x;
            new File(copia, a[1]).delete();
            rid = ultimoRid(db);
            String erro = null; File saida = null;
            try { saida = ps.decriptarArquivo(copia, arq, u, priv.get(a[0])); } catch (PastaSegura.FalhaPastaSegura ex) { erro = ex.getMessage(); }
            if (a[2].equals("ok")) {
                byte[] conteudo = saida == null ? new byte[0] : ler(saida.getPath());
                check(a[0] + " abre " + a[1] + " (arquivo .docx valido: cabecalho PK)", erro == null && conteudo.length > 4
                        && conteudo[0] == 'P' && conteudo[1] == 'K' && mids(db, rid).equals(Arrays.asList(7016, 7018, 7019)), erro + " " + mids(db, rid));
            } else {
                check(a[0] + " recebe acesso negado em " + a[1] + " (7017, nada gravado)", erro != null && erro.contains("permissao")
                        && !new File(copia, a[1]).exists() && mids(db, rid).equals(Collections.singletonList(7017)), erro + " " + mids(db, rid));
            }
        }

        secao("Pasta segura: arquivos adulterados (copia temporaria)");
        Usuario u01 = usuarios.get("user01");
        Object[][] adult = {
            {"index.asd", "listar", Arrays.asList(7005, 7008)},
            {"index.enc", "listar", null},
            {"index.env", "remover", Arrays.asList(7007)},
            {"index.env", "listar", Arrays.asList(7007)},
            {"share.asd", "listar", Arrays.asList(7005, 7006, 7010, 7013)},
            {"share.env", "listar", Arrays.asList(7005, 7006, 7012)},
            {"share.enc", "listar", null},
            {"XXYYZZ11.asd", "teste01.docx", Arrays.asList(7016, 7018, 7021)},
            {"XXYYZZ11.enc", "teste01.docx", null},
            {"XXYYZZ11.env", "teste01.docx", Arrays.asList(7016, 7020)},
            {"XXYYZZ22.1.shr", "teste02.docx", Arrays.asList(7016, 7020)},
            {"XXYYZZ22.asd", "teste02.docx", Arrays.asList(7016, 7018, 7021)}};
        for (Object[] a : adult) {
            File d = new File(TEMP.getPath(), "cofre-adult-" + System.nanoTime());
            copiar(new File(FILES), d);
            for (File x : d.listFiles()) if (x.getName().endsWith(".docx")) x.delete();
            String alvo = (String) a[0], acao = (String) a[1];
            List<PastaSegura.Arquivo> lista = null;
            if (!acao.equals("listar") && !acao.equals("remover")) lista = ps.listar(d, u01); // lista antes de adulterar
            File fa = new File(d, alvo);
            if (acao.equals("remover")) fa.delete();
            else { byte[] bs = Files.readAllBytes(fa.toPath()); bs[10] ^= (byte) 0xFF; Files.write(fa.toPath(), bs); }
            rid = ultimoRid(db);
            String erro = null;
            try {
                if (acao.equals("listar") || acao.equals("remover")) ps.listar(d, u01);
                else {
                    PastaSegura.Arquivo arq = null;
                    for (PastaSegura.Arquivo x : lista) if (x.nomeSecreto.equals(acao)) arq = x;
                    ps.decriptarArquivo(d, arq, u01, priv.get("user01"));
                }
            } catch (PastaSegura.FalhaPastaSegura e) { erro = e.getMessage(); }
            List<Integer> m = mids(db, rid);
            boolean nadaGravado = true;
            for (File x : d.listFiles()) if (x.getName().endsWith(".docx")) nadaGravado = false;
            @SuppressWarnings("unchecked") List<Integer> esp = (List<Integer>) a[2];
            String desc = (acao.equals("remover") ? "remover " : "adulterar ") + alvo + (acao.equals("listar") || acao.equals("remover") ? " + Listar" : " + abrir " + acao);
            if (esp != null) check(desc + " -> " + esp + " | " + erro, erro != null && m.equals(esp) && nadaGravado, m);
            else check(desc + " -> detectado (" + m + ") | " + erro, erro != null && nadaGravado
                    && (m.contains(7007) || m.contains(7008) || m.contains(7012) || m.contains(7013) || m.contains(7020) || m.contains(7021)), m);
        }

        secao("Registros em ordem cronologica");
        List<BancoDeDados.LinhaRegistro> regs = banco.listarRegistros();
        boolean ordenado = true;
        for (int i = 1; i < regs.size(); i++) if (regs.get(i).dataHora.compareTo(regs.get(i - 1).dataHora) < 0) ordenado = false;
        check("listarRegistros em ordem de data/hora (" + regs.size() + " registros)", ordenado);
        banco.fechar();
        apagar(TEMP);

        System.out.println("\nRESULTADO: " + ok + " OK, " + falhas + " FALHA(S)");
        System.exit(falhas == 0 ? 0 : 1);
    }

    static void copiar(File de, File para) throws Exception {
        para.mkdirs();
        for (File x : de.listFiles()) if (x.isFile() && !x.getName().endsWith(".docx"))
            Files.copy(x.toPath(), new File(para, x.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    static void apagar(File f) {
        File[] filhos = f.listFiles();
        if (filhos != null) for (File x : filhos) apagar(x);
        f.delete();
    }
}
