/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mfa.validation.engine;

import com.richie.component.mfa.core.config.MfaProperties;
import com.richie.component.mfa.core.config.properties.MfaTotpProperties;
import org.apache.commons.codec.binary.Base32;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpValidationEngineTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    private TotpValidationEngine engine;

    @BeforeEach
    void setUp() {
        MfaProperties properties = new MfaProperties();
        MfaTotpProperties totp = properties.getTotp();
        totp.setTimeWindow(30);
        totp.setPeriod(30);
        totp.setDigits(6);
        totp.setCodeLength(6);
        totp.setAlgorithm("SHA1");
        engine = new TotpValidationEngine(properties);
    }

    @Test
    void verifyCode_acceptsCurrentTotp() {
        String code = totpAt(Instant.now().getEpochSecond(), 30, 6);

        assertThat(engine.verifyCode(SECRET, code, "u1", null, 1, "SHA1", 30, 6)).isTrue();
    }

    @Test
    void verifyCode_rejectsBlankSecretOrWrongCode() {
        assertThat(engine.verifyCode("", "123456", "u1", null, 1)).isFalse();
        assertThat(engine.verifyCode(SECRET, "000000", "u1", null, 0)).isFalse();
    }

    @Test
    void verifyCode_supportsSha256Algorithm() {
        MfaProperties props = new MfaProperties();
        props.getTotp().setAlgorithm("SHA256");
        TotpValidationEngine sha256Engine = new TotpValidationEngine(props);
        String code = totpAtSha256(Instant.now().getEpochSecond(), 30, 6);

        assertThat(sha256Engine.verifyCode(SECRET, code, "u1", null, 1, "SHA256", 30, 6)).isTrue();
    }

    @Test
    void verifyCode_fiveArgOverload_delegatesToSixArg() {
        String code = totpAt(Instant.now().getEpochSecond(), 30, 6);
        assertThat(engine.verifyCode(SECRET, code, "u1", null, 1, "SHA1")).isTrue();
    }

    @Test
    void verifyCode_supportsSha512Algorithm() {
        MfaProperties props = new MfaProperties();
        props.getTotp().setAlgorithm("SHA512");
        TotpValidationEngine sha512Engine = new TotpValidationEngine(props);
        String code = totpAtSha512(Instant.now().getEpochSecond(), 30, 6);

        assertThat(sha512Engine.verifyCode(SECRET, code, "u1", null, 1, "SHA512", 30, 6)).isTrue();
    }

    @Test
    void verifyCode_unknownAlgorithm_fallsBackToConfiguredDefault() {
        String code = totpAt(Instant.now().getEpochSecond(), 30, 6);
        assertThat(engine.verifyCode(SECRET, code, "u1", null, 1, "UNKNOWN", 30, 6)).isTrue();
    }

    @Test
    void verifyCode_eightArg_rejectsBlankSecret() {
        assertThat(engine.verifyCode("", "123456", "u1", null, 1, "SHA1", 30, 6)).isFalse();
    }

    @Test
    void normalizeAlgorithm_emptyConfig_fallsBackToSha1() {
        MfaProperties props = new MfaProperties();
        props.getTotp().setAlgorithm("");
        props.getTotp().setTimeWindow(30);
        props.getTotp().setCodeLength(6);
        TotpValidationEngine emptyEngine = new TotpValidationEngine(props);

        String code = totpAt(Instant.now().getEpochSecond(), 30, 6);
        assertThat(emptyEngine.verifyCode(SECRET, code, "u1", null, 1)).isTrue();
    }

    @Test
    void normalizeAlgorithm_invalidConfig_fallsBackToSha1() {
        MfaProperties props = new MfaProperties();
        props.getTotp().setAlgorithm("MD5");
        props.getTotp().setTimeWindow(30);
        props.getTotp().setCodeLength(6);
        TotpValidationEngine badConfigEngine = new TotpValidationEngine(props);

        String code = totpAt(Instant.now().getEpochSecond(), 30, 6);
        assertThat(badConfigEngine.verifyCode(SECRET, code, "u1", null, 1)).isTrue();
    }

    @Test
    void normalizeAlgorithm_nullAlgorithm_fallsBackToConfigDefault() {
        MfaProperties props = new MfaProperties();
        props.getTotp().setAlgorithm("SHA256");
        props.getTotp().setTimeWindow(30);
        props.getTotp().setCodeLength(6);
        TotpValidationEngine engine = new TotpValidationEngine(props);
        String code = totpAtSha256(Instant.now().getEpochSecond(), 30, 6);

        // Pass null algorithm, should use config default SHA256
        assertThat(engine.verifyCode(SECRET, code, "u1", null, 1, null, 30, 6)).isTrue();
    }

    @Test
    void generateCode_invalidBase32Secret_throwsRuntimeException() throws Exception {
        // Use reflection to directly test the private generateCode method
        // since Commons Codec's Base32 is too lenient to throw on invalid input
        java.lang.reflect.Method generateCodeMethod = TotpValidationEngine.class.getDeclaredMethod(
                "generateCode", String.class, long.class, int.class, String.class);
        generateCodeMethod.setAccessible(true);

        // Passing null will cause NPE in Base32.decode() which is caught and rethrown as RuntimeException
        try {
            generateCodeMethod.invoke(engine, null, 12345L, 6, "SHA1");
            org.junit.jupiter.api.Assertions.fail("Expected RuntimeException to be thrown");
        } catch (java.lang.reflect.InvocationTargetException e) {
            org.junit.jupiter.api.Assertions.assertInstanceOf(RuntimeException.class, e.getCause());
        }
    }

    @Test
    void normalizeAlgorithm_hmacPrefix_stripsHmacAndReturnsSha1() {
        // Algorithm = "HmacSHA1" should trigger replaceFirst("^Hmac", "") path
        String code = totpAt(Instant.now().getEpochSecond(), 30, 6);
        assertThat(engine.verifyCode(SECRET, code, "u1", null, 1, "HmacSHA1", 30, 6)).isTrue();
    }

    private static String totpAt(long epochSeconds, int period, int digits) {
        long counter = epochSeconds / period;
        byte[] key = new Base32().decode(SECRET);
        byte[] data = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(counter).array();
        Mac hmac = new HMac(new SHA1Digest());
        hmac.init(new KeyParameter(key));
        hmac.update(data, 0, data.length);
        byte[] hash = new byte[hmac.getMacSize()];
        hmac.doFinal(hash, 0);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", otp);
    }

    private static String totpAtSha256(long epochSeconds, int period, int digits) {
        long counter = epochSeconds / period;
        byte[] key = new Base32().decode(SECRET);
        byte[] data = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(counter).array();
        Mac hmac = new HMac(new SHA256Digest());
        hmac.init(new KeyParameter(key));
        hmac.update(data, 0, data.length);
        byte[] hash = new byte[hmac.getMacSize()];
        hmac.doFinal(hash, 0);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", otp);
    }

    private static String totpAtSha512(long epochSeconds, int period, int digits) {
        long counter = epochSeconds / period;
        byte[] key = new Base32().decode(SECRET);
        byte[] data = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(counter).array();
        Mac hmac = new HMac(new SHA512Digest());
        hmac.init(new KeyParameter(key));
        hmac.update(data, 0, data.length);
        byte[] hash = new byte[hmac.getMacSize()];
        hmac.doFinal(hash, 0);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", otp);
    }
}
