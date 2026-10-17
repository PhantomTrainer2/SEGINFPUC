/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;

public class TOTP {
    private byte [] key = null;
    private long timeStepInSeconds = 30;

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

    private String getTOTPCodeFromHash(byte[] hash) {
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

    private byte[] HMAC_SHA1(byte[] counter, byte[] keyByteArray) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(keyByteArray, "HmacSHA1"));
            return mac.doFinal(counter);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular o HMAC-SHA1: " + e.getMessage(), e);
        }
    }

    private String TOTPCode(long timeInterval) {
        byte[] counter = new byte[8];
        long value = timeInterval;
        for (int i = counter.length - 1; i >= 0; i--) {
            counter[i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
        return getTOTPCodeFromHash(HMAC_SHA1(counter, key));
    }

    public String generateCode() {
        long timeInterval = new Date().getTime() / 1000L / timeStepInSeconds;
        return TOTPCode(timeInterval);
    }

    public boolean validateCode(String inputTOTP) {
        if (inputTOTP == null || inputTOTP.trim().length() != 6) {
            return false;
        }
        String code = inputTOTP.trim();
        long timeInterval = new Date().getTime() / 1000L / timeStepInSeconds;

        return TOTPCode(timeInterval).equals(code)
                || TOTPCode(timeInterval - 1).equals(code)
                || TOTPCode(timeInterval + 1).equals(code);
    }
}
