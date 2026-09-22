import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
//
// encripta e desencripta utilizando DES
public class SymmetricKeyCipherCBCExample {

  public static void main (String[] args) throws Exception {
    //
    // verifica args e recebe o texto plano
    if (args.length !=1) {
      System.err.println("Usage: java SymmetricKeyCipherExample text");
      System.exit(1);
    }
    byte[] plainText = args[0].getBytes("UTF8");
    //
    // gera uma chave para o DES
    System.out.println( "\nStart generating symetric key" );
    //KeyGenerator keyGen = KeyGenerator.getInstance("DES");
	//KeyGenerator keyGen = KeyGenerator.getInstance("DESede");
	KeyGenerator keyGen = KeyGenerator.getInstance("AES");
    //keyGen.init(56);
	//keyGen.init(168);
	keyGen.init(256);
    Key key = keyGen.generateKey();
    System.out.println( "Finish generating symetric key" );
	//
	// define um IV para o DES (8 bytes)
	//System.out.println( "\nStart creating DES IV" );
	//byte[] iv = new byte[] { 0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7 };
	System.out.println( "\nStart creating AES IV" );
	byte[] iv = new byte[] { 0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15 };
    IvParameterSpec ivSpec = new IvParameterSpec(iv);
	System.out.println( "Finish creating IV" );
    //
    // define um objeto de cifra DES e imprime o provider utilizado
    //Cipher cipher = Cipher.getInstance("DES/CBC/PKCS5Padding");
	//Cipher cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding");
	Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    System.out.println( "\n" + cipher.getProvider().getInfo() );
    //
    // encripta utilizando a chave e o texto plano
    System.out.println( "\nStart encryption" );
	cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
    byte[] cipherText = cipher.doFinal(plainText);
    System.out.println( "Finish encryption (hex output): " );

    // converte o cipherText para hexadecimal
    StringBuffer buf = new StringBuffer();
    for(int i = 0; i < cipherText.length; i++) {
       String hex = Integer.toHexString(0x0100 + (cipherText[i] & 0x00FF)).substring(1);
       buf.append((hex.length() < 2 ? "0" : "") + hex);
    }

    // imprime o ciphertext em hexadecimal
    System.out.println( buf.toString() );
	
    //
    // desencripta o texto cifrado com a chave
    System.out.println( "\nStart decryption" );
	cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
    byte[] newPlainText = cipher.doFinal(cipherText);
    System.out.println( "Finish decryption: " );
    System.out.println( new String(newPlainText, "UTF8") );
  }
}
