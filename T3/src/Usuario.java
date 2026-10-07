/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

/**
 * Registro de um usuario da tabela Usuarios, acompanhado do nome do seu grupo.
 */
public class Usuario {
    int uid;
    String login;
    String nome;
    int gid;
    String nomeGrupo;
    int kid;
    String hashSenha;
    byte[] tokenCifrado;
    int totalAcessos;
    int totalConsultas;
    int errosSenha;
    int errosToken;
    long bloqueadoAte;

    boolean isAdministrador() {
        return gid == BancoDeDados.GID_ADMINISTRADOR;
    }

    boolean estaBloqueado() {
        return System.currentTimeMillis() < bloqueadoAte;
    }

    long segundosRestantesBloqueio() {
        long restante = bloqueadoAte - System.currentTimeMillis();
        return restante <= 0 ? 0 : (restante + 999) / 1000;
    }
}
