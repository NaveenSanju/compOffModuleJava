package com.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.apache.commons.codec.binary.Base64;

public class CipherText {
	
	Cipher ecipher;
	Cipher dcipher;
	private String encryptedValue;
	/*String[] AlphabetArr = {"a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z","A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z","0","1","2","3","4","5","6","7","8","9","=","/","+",".","#"};
	String[] substitueArr= {"Kt","Su","Zb","Pe","Vh","Iw","Nk","Wl","Oc","Xn","Tm","Df","Ag","Gi","Er","Us","Lo","Cp","Yx","Fd","By","Qz","Rj","Ja","Mq","Hv","8s","1A","5k","6B","9t","7K","0b","4M","3d","2V","eY","qE","xK","tP","bN","zG","gJ","iW","lQ","aO","mB","fA","rL","oI","kS","sX","nZ","uM","pC","hD","wT","jU","dV","vH","cF","yR","Gy","hW","kE","Lj","bV"}; 
*/
	String[] AlphabetArr = {"a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z","A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z","0","1","2","3","4","5","6","7","8","9","=","/","+"};
	String[] substitueArr= {"Kt","Su","Zb","Pe","Vh","Iw","Nk","Wl","Oc","Xn","Tm","Df","Ag","Gi","Er","Us","Lo","Cp","Yx","Fd","By","Qz","Rj","Ja","Mq","Hv","8s","1A","5k","6B","9t","7K","0b","4M","3d","2V","eY","qE","xK","tP","bN","zG","gJ","iW","lQ","aO","mB","fA","rL","oI","kS","sX","nZ","uM","pC","hD","wT","jU","dV","vH","cF","yR","Gy","hW","kE"}; 

	public CipherText() {

		// 8-bytes Salt
		byte[] salt = {
		        (byte) 0xA9, (byte) 0x9B, (byte) 0xC8, (byte) 0x32,
		        (byte) 0x56, (byte) 0x34, (byte) 0xE3, (byte) 0x03
		};

		// Iteration count
		int iterationCount = 19;

		try {

			KeySpec keySpec = new PBEKeySpec("HANDDY".toCharArray(), salt, iterationCount);
			SecretKey key = SecretKeyFactory.getInstance("PBEWithMD5AndDES").generateSecret(keySpec);

			ecipher = Cipher.getInstance(key.getAlgorithm());
			dcipher = Cipher.getInstance(key.getAlgorithm());

			// Prepare the parameters to the cipthers
			AlgorithmParameterSpec paramSpec = new PBEParameterSpec(salt, iterationCount);

			ecipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
			dcipher.init(Cipher.DECRYPT_MODE, key, paramSpec);

		} catch (InvalidAlgorithmParameterException e) {
			System.out.println("EXCEPTION: InvalidAlgorithmParameterException");
		} catch (InvalidKeySpecException e) {
			System.out.println("EXCEPTION: InvalidKeySpecException");
		} catch (NoSuchPaddingException e) {
			System.out.println("EXCEPTION: NoSuchPaddingException");
		} catch (NoSuchAlgorithmException e) {
			System.out.println("EXCEPTION: NoSuchAlgorithmException");
		} catch (InvalidKeyException e) {
			System.out.println("EXCEPTION: InvalidKeyException");
		}

	}

	CipherText(SecretKey key, String algorithm) {
		try {
			ecipher = Cipher.getInstance(algorithm);
			dcipher = Cipher.getInstance(algorithm);
			ecipher.init(Cipher.ENCRYPT_MODE, key);
			dcipher.init(Cipher.DECRYPT_MODE, key);
		} catch (NoSuchPaddingException e) {
			System.out.println("EXCEPTION: NoSuchPaddingException");
		} catch (NoSuchAlgorithmException e) {
			System.out.println("EXCEPTION: NoSuchAlgorithmException");
		} catch (InvalidKeyException e) {
			System.out.println("EXCEPTION: InvalidKeyException");
		}
	}

	/**
	 * Constructor used to create this object. Responsible for setting and
	 * initializing this object's encrypter and decrypter Chipher instances
	 * given a Pass Phrase and algorithm.
	 * 
	 * @param passPhrase
	 *            Pass Phrase used to initialize both the encrypter and
	 *            decrypter instances.
	 */
	CipherText(String passPhrase) {

		// 8-bytes Salt
		byte[] salt = {
		        (byte) 0xA9, (byte) 0x9B, (byte) 0xC8, (byte) 0x32,
		        (byte) 0x56, (byte) 0x34, (byte) 0xE3, (byte) 0x03
		};

		// Iteration count
		int iterationCount = 19;

		try {

			KeySpec keySpec = new PBEKeySpec(passPhrase.toCharArray(), salt, iterationCount);
			SecretKey key = SecretKeyFactory.getInstance("PBEWithMD5AndDES").generateSecret(keySpec);

			ecipher = Cipher.getInstance(key.getAlgorithm());
			dcipher = Cipher.getInstance(key.getAlgorithm());

			// Prepare the parameters to the cipthers
			AlgorithmParameterSpec paramSpec = new PBEParameterSpec(salt, iterationCount);

			ecipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
			dcipher.init(Cipher.DECRYPT_MODE, key, paramSpec);

		} catch (InvalidAlgorithmParameterException e) {
			System.out.println("EXCEPTION: InvalidAlgorithmParameterException");
		} catch (InvalidKeySpecException e) {
			System.out.println("EXCEPTION: InvalidKeySpecException");
		} catch (NoSuchPaddingException e) {
			System.out.println("EXCEPTION: NoSuchPaddingException");
		} catch (NoSuchAlgorithmException e) {
			System.out.println("EXCEPTION: NoSuchAlgorithmException");
		} catch (InvalidKeyException e) {
			System.out.println("EXCEPTION: InvalidKeyException");
		}
	}
	
	public String encrypt(String str) {
		try {
			str=str.trim();
			// Encode the string into bytes using utf-8
			byte[] utf8 = str.getBytes("UTF8");
			// Encrypt
			byte[] enc = ecipher.doFinal(utf8);
			// Encode bytes to base64 to get a string
			String substitutedText="";
			String subtitueLetter="";
			int index=0;
			//System.out.println("Input==="+str);
			String data=Base64.encodeBase64String(enc);
			//System.out.println("before encryption letter==="+data);
			for(int i=0;i<data.length();i++){
				String c=String.valueOf(data.charAt(i));
				index = Arrays.asList(AlphabetArr).indexOf(c);
				if(index!=-1){
					subtitueLetter=Arrays.asList(substitueArr).get(index);
					
				}else{
					subtitueLetter=String.valueOf(c);
				}
				substitutedText+=subtitueLetter;
			}
			//substitutedText=encryptArray(data);
			//System.out.println("After encryption letter==="+substitutedText.trim()+"======================");
			return substitutedText.trim();
			//return Base64.encode(enc);

		} catch (BadPaddingException e) {
		} catch (IllegalBlockSizeException e) {
		} catch (UnsupportedEncodingException e) {
		} catch (IOException e) {
		}
		return null;
	}
	
	public String decrypt(String str) {
		try {
			str=str.trim();
			String substitutedText="";
			String subtitueLetter="";
			int index=0;
			int wlength=str.length();
			//System.out.println("before decryption letter==="+str);
			if((wlength%2)==0){
				for(int i=0;i<str.length();i=i+2){
					String c=String.valueOf(str.substring(i, i+2));
					//System.out.println("c=="+c);
					index = Arrays.asList(substitueArr).indexOf(c);
					if(index!=-1){
						subtitueLetter=Arrays.asList(AlphabetArr).get(index);
						
					}else{
						subtitueLetter=String.valueOf(c);
					}
					substitutedText+=subtitueLetter;
				}
			}
			
			//System.out.println("After decryption letter==="+substitutedText);
			byte[] dec = Base64.decodeBase64(substitutedText.trim());
			// Decode base64 to get bytes
			//byte[] dec = Base64.decodeBase64(str);
			// Decrypt
			//System.out.println("After decryption letter1==="+dec);
			byte[] utf8 = dcipher.doFinal(dec);
			return new String(utf8, "UTF8");
		} catch (BadPaddingException e) {
		} catch (IllegalBlockSizeException e) {
		} catch (UnsupportedEncodingException e) {
		} catch (IOException e) {
		} catch (Exception e) {
		}
		return null;
	}
	
	public static void main(String[] args) {
		CipherText se = new CipherText();
		String en2=se.encrypt("Handdy_1234");
		System.out.println("Encrypt==="+en2);
		
		String se2=se.decrypt("rLCpjUeYCp5khDIw0brLwTGy");
		//https://handdy-accounts-7nTg-%2BNsz-Y_
		System.out.println("Decrypt==="+se2);
		//System.out.println("Decrypt==="+se2);
		//String arra="7KvHUseYMqVhsXHveYhWwTGy, Mq6B4MiWOcqELoEriWDfxKGy, 5kiWKtdVMq1AkEXn7KZb8sGy, QzkECpFd6BkEoInZRjGinZGy, nZyRhDoIhWvHUshDIwhWxKGy";
		//System.out.println(se.DecryptIDArray(arra));
	}

}
