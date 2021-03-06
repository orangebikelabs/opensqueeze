/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.util.Base64;

import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.annotation.Nonnull;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Simple encryption tools based on a string key generated by a static method. This is used simply for obfuscation when we store username
 * and passwords in preferences.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class EncryptionTools {
    final static private String ALGORITHM = "AES";
    final static private String ALGORITHM_TOTAL = "AES/CBC/PKCS5Padding";
    final static private int IV_SIZE = 128 / 8;
    final static private int KEY_SIZE = 128;

    final static private SecureRandom sRandom = new SecureRandom();

    @Nonnull
    static public String generateKey() throws GeneralSecurityException {
        KeyGenerator kgen = KeyGenerator.getInstance(ALGORITHM);
        kgen.init(KEY_SIZE);

        // Generate the secret key specs.
        SecretKey skey = kgen.generateKey();
        byte[] raw = skey.getEncoded();
        return Base64.encodeToString(raw, Base64.NO_WRAP);
    }

    @Nonnull
    static public String encrypt(String key, byte[] data) throws GeneralSecurityException {
        SecretKeySpec keySpec = getKey(key);

        Cipher cipher = Cipher.getInstance(ALGORITHM_TOTAL);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);

        return Base64.encodeToString(Bytes.concat(cipher.getIV(), cipher.doFinal(data)), Base64.NO_WRAP);
    }

    @Nonnull
    static public byte[] decrypt(String key, String encryptedText) throws GeneralSecurityException {
        try {
            SecretKeySpec keySpec = getKey(key);

            byte[] decoded = Base64.decode(encryptedText, Base64.NO_WRAP);
            IvParameterSpec pspec = new IvParameterSpec(decoded, 0, IV_SIZE);

            Cipher cipher = Cipher.getInstance(ALGORITHM_TOTAL);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, pspec, sRandom);

            try (InputStream is = new CipherInputStream(new ByteArrayInputStream(decoded, IV_SIZE, decoded.length - IV_SIZE), cipher)) {
                return ByteStreams.toByteArray(is);
            }
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
    }

    @Nonnull
    static protected SecretKeySpec getKey(String key) {
        return new SecretKeySpec(Base64.decode(key, Base64.NO_WRAP), ALGORITHM);
    }
}
