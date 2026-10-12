/*
 * INF1416 - Seguranca da Informacao - Trabalho 3 (Cofre Digital)
 * Rodrigo - 2210814
 * Breno Gallo - 2110183
 */

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;

/**
 * Operacoes criptograficas do Cofre Digital (JCA/JCE do proprio JDK):
 * derivacao da chave AES via SHA1PRNG, cifragem AES/ECB/PKCS5Padding,
 * restauracao da chave privada (PKCS8) e do certificado (X.509),
 * envelope digital RSA e assinatura digital SHA1withRSA.
 */
public final class Criptografia {

    static final String ALG_SIMETRICO = "AES/ECB/PKCS5Padding";
    static final String ALG_ENVELOPE = "RSA/ECB/PKCS1Padding";
    static final String ALG_ASSINATURA = "SHA1withRSA";

    // OID do atributo E-mail (PKCS#9) presente no campo Sujeito dos certificados
    static final String OID_EMAIL = "1.2.840.113549.1.9.1";

    private static final String INICIO_CERTIFICADO = "-----BEGIN CERTIFICATE-----";
    private static final String FIM_CERTIFICADO = "-----END CERTIFICATE-----";
    private static final String INICIO_CHAVE = "-----BEGIN PRIVATE KEY-----";
    private static final String FIM_CHAVE = "-----END PRIVATE KEY-----";

    private static final Map<String, String> NOMES_OID = new HashMap<String, String>();
    static {
        NOMES_OID.put(OID_EMAIL, "EMAILADDRESS");
    }

    private Criptografia() {
    }

    /**
     * JREs 8 anteriores ao update 161 vem com a politica de criptografia
     * restrita (AES limitado a 128 bits): sem esta checagem, a falha ao usar
     * a chave AES de 256 bits apareceria como "frase secreta invalida".
     */
    static boolean suportaAES256() {
        try {
            return Cipher.getMaxAllowedKeyLength("AES") >= 256;
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    static String mensagemSemAES256() {
        return "O Java em uso (" + System.getProperty("java.version") + ") limita o AES a 128 bits, mas o Cofre "
                + "Digital usa chaves AES de 256 bits. Execute com o Java 8u161 ou superior.";
    }

    /**
     * Gera a chave AES de 256 bits a partir de uma semente (frase secreta,
     * senha pessoal ou semente de um envelope digital) usando o PRNG SHA1PRNG.
     */
    static SecretKey gerarChaveAES(byte[] semente) throws GeneralSecurityException {
        SecureRandom prng = SecureRandom.getInstance("SHA1PRNG");
        prng.setSeed(semente);
        KeyGenerator gerador = KeyGenerator.getInstance("AES");
        gerador.init(256, prng);
        return gerador.generateKey();
    }

    static byte[] cifrarAES(SecretKey chave, byte[] dados) throws GeneralSecurityException {
        Cipher cifra = Cipher.getInstance(ALG_SIMETRICO);
        cifra.init(Cipher.ENCRYPT_MODE, chave);
        return cifra.doFinal(dados);
    }

    static byte[] decifrarAES(SecretKey chave, byte[] dados) throws GeneralSecurityException {
        Cipher cifra = Cipher.getInstance(ALG_SIMETRICO);
        cifra.init(Cipher.DECRYPT_MODE, chave);
        return cifra.doFinal(dados);
    }

    /**
     * Extrai o bloco PEM do certificado. Os arquivos .crt fornecidos possuem
     * um texto descritivo antes do bloco, que nao deve ir para o banco.
     */
    static String extrairPEMCertificado(byte[] conteudo) throws CertificateException {
        String texto = new String(conteudo, StandardCharsets.US_ASCII);
        int inicio = texto.indexOf(INICIO_CERTIFICADO);
        int fim = inicio < 0 ? -1 : texto.indexOf(FIM_CERTIFICADO, inicio);
        if (inicio < 0 || fim < 0) {
            throw new CertificateException("O arquivo nao contem um certificado no formato PEM.");
        }
        return texto.substring(inicio, fim + FIM_CERTIFICADO.length()) + "\n";
    }

    static X509Certificate carregarCertificado(String pem) throws CertificateException {
        CertificateFactory fabrica = CertificateFactory.getInstance("X.509");
        return (X509Certificate) fabrica.generateCertificate(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * Decripta o arquivo binario da chave privada com a chave AES derivada da
     * frase secreta e restaura o objeto PrivateKey (PKCS8 codificado em BASE64).
     */
    static PrivateKey restaurarChavePrivada(byte[] chaveCifrada, char[] frase) throws GeneralSecurityException {
        byte[] semente = paraBytes(frase);
        byte[] pem = null;
        byte[] pkcs8 = null;
        try {
            pem = decifrarAES(gerarChaveAES(semente), chaveCifrada);
            String texto = new String(pem, StandardCharsets.US_ASCII);
            int inicio = texto.indexOf(INICIO_CHAVE);
            int fim = texto.indexOf(FIM_CHAVE);
            if (inicio < 0 || fim < inicio) {
                throw new InvalidKeyException("O conteudo decriptado nao e uma chave privada PEM.");
            }
            String base64 = texto.substring(inicio + INICIO_CHAVE.length(), fim).replaceAll("\\s", "");
            pkcs8 = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (IllegalArgumentException e) {
            throw new InvalidKeyException("A chave privada possui codificacao BASE64 invalida.", e);
        } finally {
            Arrays.fill(semente, (byte) 0);
            if (pem != null) {
                Arrays.fill(pem, (byte) 0);
            }
            if (pkcs8 != null) {
                Arrays.fill(pkcs8, (byte) 0);
            }
        }
    }

    /**
     * Verifica se a chave privada forma par com a chave publica, assinando um
     * array aleatorio de tamanho bytes e verificando a assinatura.
     */
    static boolean verificarParDeChaves(PrivateKey privada, PublicKey publica, int tamanho)
            throws GeneralSecurityException {
        byte[] teste = new byte[tamanho];
        new SecureRandom().nextBytes(teste);
        Signature assinador = Signature.getInstance(ALG_ASSINATURA);
        assinador.initSign(privada);
        assinador.update(teste);
        return verificarAssinatura(teste, assinador.sign(), publica);
    }

    /**
     * Validacao completa da chave privada: testa a frase secreta e depois o
     * par com a chave publica do certificado (assinatura de array aleatorio).
     */
    static PrivateKey validarChavePrivada(byte[] chaveCifrada, char[] frase, PublicKey publica, int tamanhoTeste)
            throws ChavePrivadaInvalidaException {
        PrivateKey privada;
        try {
            privada = restaurarChavePrivada(chaveCifrada, frase);
        } catch (GeneralSecurityException e) {
            throw new ChavePrivadaInvalidaException(ChavePrivadaInvalidaException.Motivo.FRASE_SECRETA_INVALIDA,
                    "A frase secreta nao decripta a chave privada.", e);
        }
        boolean parValido;
        try {
            parValido = verificarParDeChaves(privada, publica, tamanhoTeste);
        } catch (GeneralSecurityException e) {
            parValido = false;
        }
        if (!parValido) {
            throw new ChavePrivadaInvalidaException(ChavePrivadaInvalidaException.Motivo.ASSINATURA_INVALIDA,
                    "A chave privada nao corresponde a chave publica do certificado digital.", null);
        }
        return privada;
    }

    static boolean verificarAssinatura(byte[] dados, byte[] assinatura, PublicKey publica)
            throws GeneralSecurityException {
        Signature verificador = Signature.getInstance(ALG_ASSINATURA);
        verificador.initVerify(publica);
        verificador.update(dados);
        try {
            return verificador.verify(assinatura);
        } catch (SignatureException e) {
            // Assinatura mal formada (ex.: tamanho incorreto)
            return false;
        }
    }

    /**
     * Abre um envelope digital (RSA/ECB/PKCS1Padding) com a chave privada,
     * retornando a semente do SHA1PRNG que gera a chave AES do arquivo.
     */
    static byte[] abrirEnvelope(byte[] envelope, PrivateKey privada) throws GeneralSecurityException {
        Cipher cifra = Cipher.getInstance(ALG_ENVELOPE);
        cifra.init(Cipher.DECRYPT_MODE, privada);
        return cifra.doFinal(envelope);
    }

    /** Converte caracteres para bytes UTF-8 sem criar String (permite apagar a copia). */
    static byte[] paraBytes(char[] caracteres) {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(caracteres));
        byte[] bytes = Arrays.copyOfRange(buffer.array(), buffer.position(), buffer.limit());
        Arrays.fill(buffer.array(), (byte) 0);
        return bytes;
    }

    /** Nome distinto legivel (ex.: EMAILADDRESS=..., CN=..., OU=...). */
    static String nomeDistinto(X500Principal principal) {
        return principal.getName(X500Principal.RFC1779, NOMES_OID);
    }

    /** Login name (e-mail) extraido do campo Sujeito do certificado. */
    static String extrairEmail(X509Certificate certificado) {
        return extrairAtributo(certificado.getSubjectX500Principal(), "EMAILADDRESS");
    }

    /** Nome do usuario (Friendly Name / CN) extraido do campo Sujeito do certificado. */
    static String extrairNome(X509Certificate certificado) {
        return extrairAtributo(certificado.getSubjectX500Principal(), "CN");
    }

    private static String extrairAtributo(X500Principal principal, String tipo) {
        try {
            LdapName nome = new LdapName(principal.getName(X500Principal.RFC2253, NOMES_OID));
            for (Rdn rdn : nome.getRdns()) {
                if (rdn.getType().equalsIgnoreCase(tipo) && rdn.getValue() instanceof String) {
                    return ((String) rdn.getValue()).trim();
                }
            }
        } catch (InvalidNameException e) {
            // nome distinto mal formado: trata como atributo ausente
        }
        return null;
    }
}
