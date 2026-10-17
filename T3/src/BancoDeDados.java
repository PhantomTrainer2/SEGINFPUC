/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BancoDeDados {

    static final int GID_ADMINISTRADOR = 1;
    static final int GID_USUARIO = 2;
    static final String ARQUIVO_PADRAO = "cofre.db";

    private static final String FORMATO_DATA_HORA = "yyyy-MM-dd HH:mm:ss.SSS";

    private static final String SELECT_USUARIO =
            "SELECT u.*, g.nome AS nome_grupo FROM Usuarios u JOIN Grupos g ON g.GID = u.GID ";

    private static final Object[][] MENSAGENS = {
        {1001, "Sistema iniciado."},
        {1002, "Sistema encerrado."},
        {1003, "Sess\u00e3o iniciada para <login_name>."},
        {1004, "Sess\u00e3o encerrada para <login_name>."},
        {1005, "Partida do sistema iniciada para cadastro do administrador."},
        {1006, "Partida do sistema iniciada para opera\u00e7\u00e3o normal pelos usu\u00e1rios."},
        {2001, "Autentica\u00e7\u00e3o etapa 1 iniciada."},
        {2002, "Autentica\u00e7\u00e3o etapa 1 encerrada."},
        {2003, "Login name <login_name> identificado com acesso liberado."},
        {2004, "Login name <login_name> identificado com acesso bloqueado."},
        {2005, "Login name <login_name> n\u00e3o identificado."},
        {3001, "Autentica\u00e7\u00e3o etapa 2 iniciada para <login_name>."},
        {3002, "Autentica\u00e7\u00e3o etapa 2 encerrada para <login_name>."},
        {3003, "Senha pessoal verificada positivamente para <login_name>."},
        {3004, "Primeiro erro da senha pessoal contabilizado para <login_name>."},
        {3005, "Segundo erro da senha pessoal contabilizado para <login_name>."},
        {3006, "Terceiro erro da senha pessoal contabilizado para <login_name>."},
        {3007, "Acesso do usuario <login_name> bloqueado pela autentica\u00e7\u00e3o etapa 2."},
        {4001, "Autentica\u00e7\u00e3o etapa 3 iniciada para <login_name>."},
        {4002, "Autentica\u00e7\u00e3o etapa 3 encerrada para <login_name>."},
        {4003, "Token verificado positivamente para <login_name>."},
        {4004, "Primeiro erro de token contabilizado para <login_name>."},
        {4005, "Segundo erro de token contabilizado para <login_name>."},
        {4006, "Terceiro erro de token contabilizado para <login_name>."},
        {4007, "Acesso do usuario <login_name> bloqueado pela autentica\u00e7\u00e3o etapa 3."},
        {5001, "Tela principal apresentada para <login_name>."},
        {5002, "Op\u00e7\u00e3o 1 do menu principal selecionada por <login_name>."},
        {5003, "Op\u00e7\u00e3o 2 do menu principal selecionada por <login_name>."},
        {5004, "Op\u00e7\u00e3o 3 do menu principal selecionada por <login_name>."},
        {6001, "Tela de cadastro apresentada para <login_name>."},
        {6002, "Bot\u00e3o cadastrar pressionado por <login_name>."},
        {6003, "Senha pessoal inv\u00e1lida fornecida por <login_name>."},
        {6004, "Caminho do certificado digital inv\u00e1lido fornecido por <login_name>."},
        {6005, "Chave privada verificada negativamente para <login_name> (caminho inv\u00e1lido)."},
        {6006, "Chave privada verificada negativamente para <login_name> (frase secreta inv\u00e1lida)."},
        {6007, "Chave privada verificada negativamente para <login_name> (assinatura digital inv\u00e1lida)."},
        {6008, "Confirma\u00e7\u00e3o de dados aceita por <login_name>."},
        {6009, "Confirma\u00e7\u00e3o de dados rejeitada por <login_name>."},
        {6010, "Bot\u00e3o voltar de cadastro para o menu principal pressionado por <login_name>."},
        {7001, "Tela de consulta de arquivos secretos apresentada para <login_name>."},
        {7002, "Bot\u00e3o voltar de consulta para o menu principal pressionado por <login_name>."},
        {7003, "Bot\u00e3o Listar de consulta pressionado por <login_name>."},
        {7004, "Caminho de pasta inv\u00e1lido fornecido por <login_name>."},
        {7005, "Arquivo de \u00edndice decriptado com sucesso para <login_name>."},
        {7006, "Arquivo de \u00edndice verificado (integridade e autenticidade) com sucesso para <login_name>."},
        {7007, "Falha na decripta\u00e7\u00e3o do arquivo de \u00edndice para <login_name>."},
        {7008, "Falha na verifica\u00e7\u00e3o (integridade e autenticidade) do arquivo de \u00edndice para <login_name>."},
        {7009, "Lista de arquivos presentes no \u00edndice apresentada para <login_name>."},
        {7010, "Arquivo de share decriptado com sucesso para <login_name>."},
        {7011, "Arquivo de share verificado (integridade e autenticidade) com sucesso para <login_name>."},
        {7012, "Falha na decripta\u00e7\u00e3o do arquivo de share para <login_name>."},
        {7013, "Falha na verifica\u00e7\u00e3o (integridade e autenticidade) do arquivo de share para <login_name>."},
        {7014, "Lista de arquivos presentes no share apresentada para <login_name>."},
        {7015, "Arquivo <arq_name> selecionado por <login_name> para decripta\u00e7\u00e3o."},
        {7016, "Acesso permitido ao arquivo <arq_name> para <login_name>."},
        {7017, "Acesso negado ao arquivo <arq_name> para <login_name>."},
        {7018, "Arquivo <arq_name> decriptado com sucesso para <login_name>."},
        {7019, "Arquivo <arq_name> verificado (integridade e autenticidade) com sucesso para <login_name>."},
        {7020, "Falha na decripta\u00e7\u00e3o do arquivo <arq_name> para <login_name>."},
        {7021, "Falha na verifica\u00e7\u00e3o (integridade e autenticidade) do arquivo <arq_name> para <login_name>."},
        {8001, "Tela de sa\u00edda apresentada para <login_name>."},
        {8002, "Bot\u00e3o encerrar sess\u00e3o pressionado por <login_name>."},
        {8003, "Bot\u00e3o encerrar sistema pressionado por <login_name>."},
        {8004, "Bot\u00e3o voltar de sair para o menu principal pressionado por <login_name>."},
    };

    static class LinhaRegistro {
        String dataHora;
        int mid;
        String texto;
        String login;
        String arquivo;
    }

    private final Connection conexao;

    BancoDeDados(String caminhoArquivo) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver JDBC do SQLite (sqlite-jdbc) nao encontrado no classpath.", e);
        }
        conexao = DriverManager.getConnection("jdbc:sqlite:" + caminhoArquivo);
        try (Statement st = conexao.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        criarTabelas();
        popularGrupos();
        popularMensagens();
    }

    private void criarTabelas() throws SQLException {
        try (Statement st = conexao.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS Grupos ("
                    + " GID INTEGER PRIMARY KEY,"
                    + " nome TEXT NOT NULL UNIQUE)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS Usuarios ("
                    + " UID INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + " login TEXT NOT NULL UNIQUE COLLATE NOCASE,"
                    + " nome TEXT NOT NULL,"
                    + " GID INTEGER NOT NULL REFERENCES Grupos(GID),"
                    + " KID INTEGER REFERENCES Chaveiro(KID),"
                    + " senha_hash TEXT NOT NULL,"
                    + " token_cifrado BLOB NOT NULL,"
                    + " total_acessos INTEGER NOT NULL DEFAULT 0,"
                    + " total_consultas INTEGER NOT NULL DEFAULT 0,"
                    + " erros_senha INTEGER NOT NULL DEFAULT 0,"
                    + " erros_token INTEGER NOT NULL DEFAULT 0,"
                    + " bloqueado_ate INTEGER NOT NULL DEFAULT 0)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS Chaveiro ("
                    + " KID INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + " UID INTEGER NOT NULL REFERENCES Usuarios(UID),"
                    + " certificado_pem TEXT NOT NULL,"
                    + " chave_privada BLOB NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS Mensagens ("
                    + " MID INTEGER PRIMARY KEY,"
                    + " texto TEXT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS Registros ("
                    + " RID INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + " MID INTEGER NOT NULL REFERENCES Mensagens(MID),"
                    + " data_hora TEXT NOT NULL,"
                    + " UID INTEGER REFERENCES Usuarios(UID),"
                    + " login_name TEXT,"
                    + " arquivo TEXT)");
        }
    }

    private void popularGrupos() throws SQLException {
        try (PreparedStatement ps = conexao.prepareStatement(
                "INSERT OR IGNORE INTO Grupos (GID, nome) VALUES (?, ?)")) {
            ps.setInt(1, GID_ADMINISTRADOR);
            ps.setString(2, "Administrador");
            ps.executeUpdate();
            ps.setInt(1, GID_USUARIO);
            ps.setString(2, "Usu\u00e1rio");
            ps.executeUpdate();
        }
    }

    private void popularMensagens() throws SQLException {
        conexao.setAutoCommit(false);
        try (PreparedStatement ps = conexao.prepareStatement(
                "INSERT INTO Mensagens (MID, texto) VALUES (?, ?)"
                + " ON CONFLICT(MID) DO UPDATE SET texto = excluded.texto")) {
            for (Object[] mensagem : MENSAGENS) {
                ps.setInt(1, (Integer) mensagem[0]);
                ps.setString(2, (String) mensagem[1]);
                ps.addBatch();
            }
            ps.executeBatch();
            conexao.commit();
        } catch (SQLException e) {
            conexao.rollback();
            throw e;
        } finally {
            conexao.setAutoCommit(true);
        }
    }

    synchronized int contarUsuarios() {
        try (Statement st = conexao.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM Usuarios")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw erro(e);
        }
    }

    synchronized Usuario buscarUsuarioPorLogin(String login) {
        try (PreparedStatement ps = conexao.prepareStatement(SELECT_USUARIO + "WHERE u.login = ?")) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? lerUsuario(rs) : null;
            }
        } catch (SQLException e) {
            throw erro(e);
        }
    }

    synchronized Usuario buscarAdministradorDoSistema() {
        try (PreparedStatement ps = conexao.prepareStatement(
                SELECT_USUARIO + "WHERE u.GID = ? ORDER BY u.UID LIMIT 1")) {
            ps.setInt(1, GID_ADMINISTRADOR);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? lerUsuario(rs) : null;
            }
        } catch (SQLException e) {
            throw erro(e);
        }
    }

    synchronized List<Usuario> listarAdministradores() {
        List<Usuario> administradores = new ArrayList<Usuario>();
        try (PreparedStatement ps = conexao.prepareStatement(SELECT_USUARIO + "WHERE u.GID = ? ORDER BY u.UID")) {
            ps.setInt(1, GID_ADMINISTRADOR);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    administradores.add(lerUsuario(rs));
                }
            }
        } catch (SQLException e) {
            throw erro(e);
        }
        return administradores;
    }

    synchronized String buscarCertificado(int kid) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT certificado_pem FROM Chaveiro WHERE KID = ?")) {
            ps.setInt(1, kid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw erro(e);
        }
    }

    synchronized byte[] buscarChavePrivada(int kid) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT chave_privada FROM Chaveiro WHERE KID = ?")) {
            ps.setInt(1, kid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBytes(1) : null;
            }
        } catch (SQLException e) {
            throw erro(e);
        }
    }

    synchronized String buscarCertificadoPorLogin(String login) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT c.certificado_pem FROM Usuarios u JOIN Chaveiro c ON c.KID = u.KID WHERE u.login = ?")) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw erro(e);
        }
    }

    synchronized int inserirUsuario(String login, String nome, int gid, String hashSenha, byte[] tokenCifrado,
                                    String certificadoPem, byte[] chavePrivadaCifrada) {
        try {
            conexao.setAutoCommit(false);
            int uid;
            int kid;
            try (PreparedStatement ps = conexao.prepareStatement(
                    "INSERT INTO Usuarios (login, nome, GID, senha_hash, token_cifrado) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, login);
                ps.setString(2, nome);
                ps.setInt(3, gid);
                ps.setString(4, hashSenha);
                ps.setBytes(5, tokenCifrado);
                ps.executeUpdate();
            }
            uid = ultimoIdInserido();
            try (PreparedStatement ps = conexao.prepareStatement(
                    "INSERT INTO Chaveiro (UID, certificado_pem, chave_privada) VALUES (?, ?, ?)")) {
                ps.setInt(1, uid);
                ps.setString(2, certificadoPem);
                ps.setBytes(3, chavePrivadaCifrada);
                ps.executeUpdate();
            }
            kid = ultimoIdInserido();
            try (PreparedStatement ps = conexao.prepareStatement("UPDATE Usuarios SET KID = ? WHERE UID = ?")) {
                ps.setInt(1, kid);
                ps.setInt(2, uid);
                ps.executeUpdate();
            }
            conexao.commit();
            return uid;
        } catch (SQLException e) {
            try {
                conexao.rollback();
            } catch (SQLException ignorada) {
            }
            throw erro(e);
        } finally {
            try {
                conexao.setAutoCommit(true);
            } catch (SQLException ignorada) {
            }
        }
    }

    synchronized void atualizarErrosSenha(int uid, int erros) {
        atualizarInteiro("UPDATE Usuarios SET erros_senha = ? WHERE UID = ?", erros, uid);
    }

    synchronized void atualizarErrosToken(int uid, int erros) {
        atualizarInteiro("UPDATE Usuarios SET erros_token = ? WHERE UID = ?", erros, uid);
    }

    synchronized void bloquearUsuario(int uid, long bloqueadoAte) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "UPDATE Usuarios SET bloqueado_ate = ?, erros_senha = 0, erros_token = 0 WHERE UID = ?")) {
            ps.setLong(1, bloqueadoAte);
            ps.setInt(2, uid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw erro(e);
        }
    }

    synchronized void incrementarAcessos(int uid) {
        atualizarInteiro("UPDATE Usuarios SET total_acessos = total_acessos + ? WHERE UID = ?", 1, uid);
    }

    synchronized void incrementarConsultas(int uid) {
        atualizarInteiro("UPDATE Usuarios SET total_consultas = total_consultas + ? WHERE UID = ?", 1, uid);
    }

    synchronized void registrar(int mid, String login, String arquivo) {
        String dataHora = new SimpleDateFormat(FORMATO_DATA_HORA).format(new Date());
        try (PreparedStatement ps = conexao.prepareStatement(
                "INSERT INTO Registros (MID, data_hora, UID, login_name, arquivo)"
                + " VALUES (?, ?, (SELECT UID FROM Usuarios WHERE login = ?), ?, ?)")) {
            ps.setInt(1, mid);
            ps.setString(2, dataHora);
            ps.setString(3, login);
            ps.setString(4, login);
            ps.setString(5, arquivo);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Falha ao gravar o registro " + mid + ": " + e.getMessage());
        }
    }

    synchronized List<LinhaRegistro> listarRegistros() {
        List<LinhaRegistro> linhas = new ArrayList<LinhaRegistro>();
        try (Statement st = conexao.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT r.data_hora, r.MID, m.texto, r.login_name, r.arquivo"
                     + " FROM Registros r JOIN Mensagens m ON m.MID = r.MID"
                     + " ORDER BY r.data_hora, r.RID")) {
            while (rs.next()) {
                LinhaRegistro linha = new LinhaRegistro();
                linha.dataHora = rs.getString(1);
                linha.mid = rs.getInt(2);
                linha.texto = rs.getString(3);
                linha.login = rs.getString(4);
                linha.arquivo = rs.getString(5);
                linhas.add(linha);
            }
        } catch (SQLException e) {
            throw erro(e);
        }
        return linhas;
    }

    synchronized void fechar() {
        try {
            conexao.close();
        } catch (SQLException e) {
            System.err.println("Falha ao fechar o banco de dados: " + e.getMessage());
        }
    }

    private void atualizarInteiro(String sql, int valor, int uid) {
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setInt(1, valor);
            ps.setInt(2, uid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw erro(e);
        }
    }

    private int ultimoIdInserido() throws SQLException {
        try (Statement st = conexao.createStatement();
             ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Usuario lerUsuario(ResultSet rs) throws SQLException {
        Usuario usuario = new Usuario();
        usuario.uid = rs.getInt("UID");
        usuario.login = rs.getString("login");
        usuario.nome = rs.getString("nome");
        usuario.gid = rs.getInt("GID");
        usuario.nomeGrupo = rs.getString("nome_grupo");
        usuario.kid = rs.getInt("KID");
        usuario.hashSenha = rs.getString("senha_hash");
        usuario.tokenCifrado = rs.getBytes("token_cifrado");
        usuario.totalAcessos = rs.getInt("total_acessos");
        usuario.totalConsultas = rs.getInt("total_consultas");
        usuario.errosSenha = rs.getInt("erros_senha");
        usuario.errosToken = rs.getInt("erros_token");
        usuario.bloqueadoAte = rs.getLong("bloqueado_ate");
        return usuario;
    }

    private static IllegalStateException erro(SQLException e) {
        return new IllegalStateException("Erro no banco de dados: " + e.getMessage(), e);
    }
}
