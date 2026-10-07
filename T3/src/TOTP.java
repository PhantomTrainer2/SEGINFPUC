/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;

/**
 * Geracao e validacao de codigos TOTP (RFC 6238), compativel com o Google
 * Authenticator. Implementada APENAS com as classes javax.crypto.Mac,
 * javax.crypto.spec.SecretKeySpec, java.util.Date e Base32, conforme exigido
 * pelo enunciado (estrutura fornecida no anexo).
 */
public class TOTP {
    private byte [] key = null;
    private long timeStepInSeconds = 30;

    // Construtor da classe. Recebe a chave secreta em BASE32 e o intervalo
    // de tempo a ser adotado (default = 30 segundos). Deve decodificar a
    // chave secreta e armazenar em key. Em caso de erro, gera Exception.
    public TOTP(String base32EncodedSecret, long timeStepInSeconds)
    throws Exception {
        if (base32EncodedSecret == null) {
            throw new Exception("Chave secreta BASE32 nao fornecida.");
        }
        if (timeStepInSeconds <= 0) {
            throw new Exception("Intervalo de tempo invalido: " + timeStepInSeconds);
        }
        Base32 base32 = new Base32(Base32.Alphabet.BASE32, false, false);
        byte[] decoded = base32.fromString(base32EncodedSecret);
        if (decoded == null || decoded.length == 0) {
            throw new Exception("Chave secreta BASE32 invalida.");
        }
        this.key = decoded;
        this.timeStepInSeconds = timeStepInSeconds;
    }

    // Recebe o HASH HMAC-SHA1 e determina o codigo TOTP de 6 algarismos
    // decimais, prefixado com zeros quando necessario.
    private String getTOTPCodeFromHash(byte[] hash) {
        // Truncamento dinamico (RFC 4226, secao 5.3): os 4 bits menos
        // significativos do ultimo byte indicam onde comecam os 4 bytes usados
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

        String code = Integer.toString(binary % 1000000);
        while (code.length() < 6) {
            code = "0" + code;
        }
        return code;
    }

    // Recebe o contador e a chave secreta para produzir o hash HMAC-SHA1.
    private byte[] HMAC_SHA1(byte[] counter, byte[] keyByteArray) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(keyByteArray, "HmacSHA1"));
            return mac.doFinal(counter);
        } catch (Exception e) {
            // HmacSHA1 e obrigatorio em toda implementacao Java SE
            throw new IllegalStateException("Falha ao calcular o HMAC-SHA1: " + e.getMessage(), e);
        }
    }

    // Recebe o intervalo de tempo e executa o algoritmo TOTP para produzir
    // o codigo TOTP. Usa os metodos auxiliares getTOTPCodeFromHash e HMAC_SHA1.
    private String TOTPCode(long timeInterval) {
        // O contador e representado em 8 bytes big-endian (RFC 4226)
        byte[] counter = new byte[8];
        long value = timeInterval;
        for (int i = counter.length - 1; i >= 0; i--) {
            counter[i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
        return getTOTPCodeFromHash(HMAC_SHA1(counter, key));
    }

    // Metodo que e utilizado para solicitar a geracao do codigo TOTP.
    public String generateCode() {
        // Quantidade de intervalos decorridos desde 01/01/1970 00:00:00 GMT
        long timeInterval = new Date().getTime() / 1000L / timeStepInSeconds;
        return TOTPCode(timeInterval);
    }

    // Metodo que e utilizado para validar um codigo TOTP (inputTOTP).
    // Deve considerar um atraso ou adiantamento de 30 segundos no
    // relogio da maquina que gerou o codigo TOTP.
    public boolean validateCode(String inputTOTP) {
        if (inputTOTP == null || inputTOTP.trim().length() != 6) {
            return false;
        }
        String code = inputTOTP.trim();
        long timeInterval = new Date().getTime() / 1000L / timeStepInSeconds;

        // (i) intervalo atual, (ii) um intervalo a menos e (iii) um intervalo a mais
        return TOTPCode(timeInterval).equals(code)
                || TOTPCode(timeInterval - 1).equals(code)
                || TOTPCode(timeInterval + 1).equals(code);
    }
}
