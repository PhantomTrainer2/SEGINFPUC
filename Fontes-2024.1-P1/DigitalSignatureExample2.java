import java.security.*;
import javax.crypto.*;
import java.security.spec.*;
//
// este exemplo utililiza facilidades para a geracao e verificacao
// de assinatura digital
public class DigitalSignatureExample2 {

  public static void main (String[] args) throws Exception {
    //
    // verifica args e recebe o texto plano
    if (args.length !=1) {
      System.err.println("Usage: java DigitalSignatureExample2 text");
      System.exit(1);
    }
    byte[] plainText = args[0].getBytes("UTF8");
    //
    // gera o par de chaves EC
    System.out.println( "\nStart generating EC key" );
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
	ECGenParameterSpec keyParams = new ECGenParameterSpec("secp256k1");
    keyGen.initialize(keyParams);
    KeyPair key = keyGen.generateKeyPair();
    System.out.println( "Finish generating EC key" );
    //
    // define um objeto signature para utilizar SHA256 e ECDSA
    // e assina o texto plano com a chave privada,
    // o provider utilizado tambem eh impresso
    Signature sig = Signature.getInstance("SHA256WithECDSA");
    sig.initSign(key.getPrivate());
    sig.update(plainText);
    byte[] signature = sig.sign();
    System.out.println( sig.getProvider().getInfo() );
    System.out.println( "\nSignature:" );

    // converte o signature para hexadecimal
    StringBuffer buf = new StringBuffer();
    for(int i = 0; i < signature.length; i++) {
       String hex = Integer.toHexString(0x0100 + (signature[i] & 0x00FF)).substring(1);
       buf.append((hex.length() < 2 ? "0" : "") + hex);
    }

    // imprime o signature em hexadecimal
    System.out.println( buf.toString() );

    //
    // verifica a assinatura com a chave publica
    System.out.println( "\nStart signature verification" );
    sig.initVerify(key.getPublic());
    sig.update(plainText);
    try {
      if (sig.verify(signature)) {
        System.out.println( "Signature verified" );
      } else System.out.println( "Signature failed" );
    } catch (SignatureException se) {
      System.out.println( "Singature failed" );
    }
  }
}
