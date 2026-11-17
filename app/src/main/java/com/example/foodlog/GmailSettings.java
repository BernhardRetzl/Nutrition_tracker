package com.example.foodlog;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class GmailSettings {
    private static final String KEY = "foodlog.gmail.password";
    private final SharedPreferences prefs;
    GmailSettings(Context context) { prefs=context.getSharedPreferences("gmail",Context.MODE_PRIVATE); }
    String sender() { return prefs.getString("sender",""); }
    String recipient() { return prefs.getString("recipient",""); }
    boolean configured() { return !sender().isEmpty() && !recipient().isEmpty() && prefs.contains("ciphertext"); }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(KEY))return (SecretKey)store.getKey(KEY,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    void save(String sender,String recipient,String password) throws Exception {
        var edit=prefs.edit().putString("sender",sender).putString("recipient",recipient);
        if(!password.isEmpty()) {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
            cipher.updateAAD(sender.getBytes(StandardCharsets.UTF_8));
            edit.putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP));
            edit.putString("ciphertext",Base64.encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP));
        } else if(!configured() || !sender.equals(sender())) throw new IllegalArgumentException("App password required");
        if(!edit.commit())throw new java.io.IOException("Settings could not be saved");
    }
    String password() throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
        cipher.updateAAD(sender().getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.decode(prefs.getString("ciphertext",""),Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    void clear() { prefs.edit().clear().apply(); }
}
