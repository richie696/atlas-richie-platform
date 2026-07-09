/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.util;

import com.auth0.jwt.interfaces.Claim;
import com.richie.gateway.util.HardwareFingerprintUtils.HardwareFingerprint;
import com.richie.gateway.util.HardwareFingerprintUtils.SignedFingerprintParts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HardwareFingerprintUtils}.
 * <p>
 * Covers JSON parse / serialize round-trip, signed-fingerprint separation,
 * HMAC-SHA256 signature verification, timestamp anti-replay check, similarity
 * verification, and JWT-claim extraction.
 */
@DisplayName("HardwareFingerprintUtils Tests")
class HardwareFingerprintUtilsTest {

    private static final String TEST_SECRET = "test-secret-key";

    private static String hmacSha256Base64(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig);
    }

    private static HardwareFingerprint sampleFingerprint() {
        HardwareFingerprint fp = new HardwareFingerprint();
        fp.setCanvas("canvas-hash");
        fp.setWebgl("webgl-hash");
        fp.setScreen("1920x1080");
        fp.setTimezone("Asia/Shanghai");
        fp.setLanguage("zh-CN");
        fp.setHardwareConcurrency(8);
        fp.setDeviceMemory(16);
        fp.setColorDepth(24);
        fp.setPixelRatio(2.0);
        fp.setPlatform("MacIntel");
        fp.setTimestamp(System.currentTimeMillis());
        fp.setNonce("nonce-value");
        return fp;
    }

    @Nested
    @DisplayName("parseFingerprint")
    class ParseFingerprint {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            assertThat(HardwareFingerprintUtils.parseFingerprint(null)).isNull();
        }

        @Test
        @DisplayName("should return null for blank input")
        void shouldReturnNullForBlankInput() {
            assertThat(HardwareFingerprintUtils.parseFingerprint("")).isNull();
            assertThat(HardwareFingerprintUtils.parseFingerprint("   ")).isNull();
        }

        @Test
        @DisplayName("should return null for invalid JSON")
        void shouldReturnNullForInvalidJson() {
            assertThat(HardwareFingerprintUtils.parseFingerprint("not-valid-json{")).isNull();
        }

        @Test
        @DisplayName("should parse a valid fingerprint JSON and populate all fields")
        void shouldParseValidFingerprintJson() {
            String json = "{\"canvas\":\"c1\",\"webgl\":\"w1\",\"screen\":\"1920x1080\","
                    + "\"timezone\":\"UTC\",\"language\":\"en-US\",\"hardwareConcurrency\":4,"
                    + "\"deviceMemory\":8,\"colorDepth\":24,\"pixelRatio\":1.0,"
                    + "\"platform\":\"MacIntel\",\"timestamp\":1700000000000,\"nonce\":\"n1\"}";

            HardwareFingerprint fp = HardwareFingerprintUtils.parseFingerprint(json);

            assertThat(fp).isNotNull();
            assertThat(fp.getCanvas()).isEqualTo("c1");
            assertThat(fp.getWebgl()).isEqualTo("w1");
            assertThat(fp.getScreen()).isEqualTo("1920x1080");
            assertThat(fp.getTimezone()).isEqualTo("UTC");
            assertThat(fp.getLanguage()).isEqualTo("en-US");
            assertThat(fp.getHardwareConcurrency()).isEqualTo(4);
            assertThat(fp.getDeviceMemory()).isEqualTo(8);
            assertThat(fp.getColorDepth()).isEqualTo(24);
            assertThat(fp.getPixelRatio()).isEqualTo(1.0);
            assertThat(fp.getPlatform()).isEqualTo("MacIntel");
            assertThat(fp.getTimestamp()).isEqualTo(1700000000000L);
            assertThat(fp.getNonce()).isEqualTo("n1");
        }

        @Test
        @DisplayName("should round-trip through serialize and parse")
        void shouldRoundTripThroughSerializeAndParse() {
            HardwareFingerprint original = sampleFingerprint();

            String json = HardwareFingerprintUtils.serializeFingerprint(original);
            HardwareFingerprint parsed = HardwareFingerprintUtils.parseFingerprint(json);

            assertThat(parsed).isNotNull();
            assertThat(parsed.getCanvas()).isEqualTo(original.getCanvas());
            assertThat(parsed.getWebgl()).isEqualTo(original.getWebgl());
            assertThat(parsed.getScreen()).isEqualTo(original.getScreen());
            assertThat(parsed.getTimezone()).isEqualTo(original.getTimezone());
            assertThat(parsed.getLanguage()).isEqualTo(original.getLanguage());
            assertThat(parsed.getHardwareConcurrency()).isEqualTo(original.getHardwareConcurrency());
            assertThat(parsed.getDeviceMemory()).isEqualTo(original.getDeviceMemory());
            assertThat(parsed.getColorDepth()).isEqualTo(original.getColorDepth());
            assertThat(parsed.getPixelRatio()).isEqualTo(original.getPixelRatio());
            assertThat(parsed.getPlatform()).isEqualTo(original.getPlatform());
            assertThat(parsed.getTimestamp()).isEqualTo(original.getTimestamp());
            assertThat(parsed.getNonce()).isEqualTo(original.getNonce());
        }
    }

    @Nested
    @DisplayName("serializeFingerprint")
    class SerializeFingerprint {

        @Test
        @DisplayName("should return null for null fingerprint")
        void shouldReturnNullForNullFingerprint() {
            assertThat(HardwareFingerprintUtils.serializeFingerprint(null)).isNull();
        }

        @Test
        @DisplayName("should serialize a fingerprint to a non-empty JSON string")
        void shouldSerializeFingerprintToNonEmptyJson() {
            String json = HardwareFingerprintUtils.serializeFingerprint(sampleFingerprint());

            assertThat(json).isNotNull().isNotBlank();
            assertThat(json).contains("canvas");
            assertThat(json).contains("webgl");
            assertThat(json).contains("nonce-value");
        }
    }

    @Nested
    @DisplayName("separateSignedFingerprint")
    class SeparateSignedFingerprint {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            assertThat(HardwareFingerprintUtils.separateSignedFingerprint(null)).isNull();
        }

        @Test
        @DisplayName("should return null for blank input")
        void shouldReturnNullForBlankInput() {
            assertThat(HardwareFingerprintUtils.separateSignedFingerprint("")).isNull();
            assertThat(HardwareFingerprintUtils.separateSignedFingerprint("   ")).isNull();
        }

        @Test
        @DisplayName("should return null when separator is missing")
        void shouldReturnNullWhenSeparatorIsMissing() {
            assertThat(HardwareFingerprintUtils.separateSignedFingerprint("no-separator-here")).isNull();
        }

        @Test
        @DisplayName("should return null when signature part is empty")
        void shouldReturnNullWhenSignaturePartIsEmpty() {
            assertThat(HardwareFingerprintUtils.separateSignedFingerprint("json-part.")).isNull();
        }

        @Test
        @DisplayName("should return null when signature is shorter than 20 chars")
        void shouldReturnNullWhenSignatureIsShorterThan20Chars() {
            assertThat(HardwareFingerprintUtils.separateSignedFingerprint("json-part.tooshort")).isNull();
        }

        @Test
        @DisplayName("should return null when signature is not valid base64")
        void shouldReturnNullWhenSignatureIsNotValidBase64() {
            String invalidBase64 = "json-part.##############################";
            assertThat(HardwareFingerprintUtils.separateSignedFingerprint(invalidBase64)).isNull();
        }

        @Test
        @DisplayName("should split a valid signed fingerprint into json and signature parts")
        void shouldSplitValidSignedFingerprint() {
            String validBase64Signature = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
            String signed = "{\"canvas\":\"c\"}." + validBase64Signature;

            SignedFingerprintParts parts = HardwareFingerprintUtils.separateSignedFingerprint(signed);

            assertThat(parts).isNotNull();
            assertThat(parts.getJsonPart()).isEqualTo("{\"canvas\":\"c\"}");
            assertThat(parts.getSignature()).isEqualTo(validBase64Signature);
        }

        @Test
        @DisplayName("should split on the last dot when json contains dots")
        void shouldSplitOnTheLastDotWhenJsonContainsDots() {
            String validBase64Signature = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
            String signed = "{\"a.b.c\":1}." + validBase64Signature;

            SignedFingerprintParts parts = HardwareFingerprintUtils.separateSignedFingerprint(signed);

            assertThat(parts).isNotNull();
            assertThat(parts.getJsonPart()).isEqualTo("{\"a.b.c\":1}");
            assertThat(parts.getSignature()).isEqualTo(validBase64Signature);
        }
    }

    @Nested
    @DisplayName("parseSignedFingerprint")
    class ParseSignedFingerprint {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            assertThat(HardwareFingerprintUtils.parseSignedFingerprint(null)).isNull();
        }

        @Test
        @DisplayName("should return null for blank input")
        void shouldReturnNullForBlankInput() {
            assertThat(HardwareFingerprintUtils.parseSignedFingerprint("")).isNull();
            assertThat(HardwareFingerprintUtils.parseSignedFingerprint("   ")).isNull();
        }

        @Test
        @DisplayName("should return null when separator is missing")
        void shouldReturnNullWhenSeparatorIsMissing() {
            assertThat(HardwareFingerprintUtils.parseSignedFingerprint("no-separator-here")).isNull();
        }

        @Test
        @DisplayName("should parse the json part of a signed fingerprint")
        void shouldParseTheJsonPartOfSignedFingerprint() {
            String validBase64Signature = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
            String json = "{\"canvas\":\"c\",\"webgl\":\"w\"}";
            String signed = json + "." + validBase64Signature;

            HardwareFingerprint fp = HardwareFingerprintUtils.parseSignedFingerprint(signed);

            assertThat(fp).isNotNull();
            assertThat(fp.getCanvas()).isEqualTo("c");
            assertThat(fp.getWebgl()).isEqualTo("w");
        }
    }

    @Nested
    @DisplayName("verifySignature")
    class VerifySignature {

        @Test
        @DisplayName("should return false when jsonPart is blank")
        void shouldReturnFalseWhenJsonPartIsBlank() {
            assertThat(HardwareFingerprintUtils.verifySignature("", "sig", TEST_SECRET)).isFalse();
            assertThat(HardwareFingerprintUtils.verifySignature(null, "sig", TEST_SECRET)).isFalse();
        }

        @Test
        @DisplayName("should return false when receivedSignature is blank")
        void shouldReturnFalseWhenReceivedSignatureIsBlank() {
            assertThat(HardwareFingerprintUtils.verifySignature("json", "", TEST_SECRET)).isFalse();
            assertThat(HardwareFingerprintUtils.verifySignature("json", null, TEST_SECRET)).isFalse();
        }

        @Test
        @DisplayName("should return false when secretKey is blank")
        void shouldReturnFalseWhenSecretKeyIsBlank() {
            assertThat(HardwareFingerprintUtils.verifySignature("json", "sig", "")).isFalse();
            assertThat(HardwareFingerprintUtils.verifySignature("json", "sig", null)).isFalse();
        }

        @Test
        @DisplayName("should return true for a valid HMAC-SHA256 signature")
        void shouldReturnTrueForValidHmacSignature() throws Exception {
            String json = "{\"canvas\":\"c\"}";
            String sig = hmacSha256Base64(TEST_SECRET, json);

            assertThat(HardwareFingerprintUtils.verifySignature(json, sig, TEST_SECRET)).isTrue();
        }

        @Test
        @DisplayName("should return false for a signature computed with a different secret")
        void shouldReturnFalseForSignatureWithDifferentSecret() throws Exception {
            String json = "{\"canvas\":\"c\"}";
            String sig = hmacSha256Base64("wrong-secret", json);

            assertThat(HardwareFingerprintUtils.verifySignature(json, sig, TEST_SECRET)).isFalse();
        }

        @Test
        @DisplayName("should return false for a signature over different data")
        void shouldReturnFalseForSignatureOverDifferentData() throws Exception {
            String sig = hmacSha256Base64(TEST_SECRET, "original");

            assertThat(HardwareFingerprintUtils.verifySignature("tampered", sig, TEST_SECRET)).isFalse();
        }
    }

    @Nested
    @DisplayName("verifyTimestamp")
    class VerifyTimestamp {

        @Test
        @DisplayName("should return false when fingerprint is null")
        void shouldReturnFalseWhenFingerprintIsNull() {
            assertThat(HardwareFingerprintUtils.verifyTimestamp(null, 300L)).isFalse();
        }

        @Test
        @DisplayName("should return false when timestamp is null")
        void shouldReturnFalseWhenTimestampIsNull() {
            HardwareFingerprint fp = new HardwareFingerprint();
            fp.setCanvas("c");

            assertThat(HardwareFingerprintUtils.verifyTimestamp(fp, 300L)).isFalse();
        }

        @Test
        @DisplayName("should return true when current time equals fingerprint time")
        void shouldReturnTrueWhenCurrentTimeEqualsFingerprintTime() {
            long now = System.currentTimeMillis();
            HardwareFingerprint fp = new HardwareFingerprint();
            fp.setTimestamp(now);

            assertThat(HardwareFingerprintUtils.verifyTimestamp(fp, 300L)).isTrue();
        }

        @Test
        @DisplayName("should return true when timestamp is within the valid window")
        void shouldReturnTrueWhenTimestampIsWithinValidWindow() {
            long now = System.currentTimeMillis();
            HardwareFingerprint fp = new HardwareFingerprint();
            fp.setTimestamp(now - 60_000L);

            assertThat(HardwareFingerprintUtils.verifyTimestamp(fp, 300L)).isTrue();
        }

        @Test
        @DisplayName("should return false when timestamp is outside the valid window")
        void shouldReturnFalseWhenTimestampIsOutsideValidWindow() {
            long now = System.currentTimeMillis();
            HardwareFingerprint fp = new HardwareFingerprint();
            fp.setTimestamp(now - 600_000L);

            assertThat(HardwareFingerprintUtils.verifyTimestamp(fp, 300L)).isFalse();
        }

        @Test
        @DisplayName("should accept future timestamps within the valid window")
        void shouldAcceptFutureTimestampsWithinValidWindow() {
            long now = System.currentTimeMillis();
            HardwareFingerprint fp = new HardwareFingerprint();
            fp.setTimestamp(now + 60_000L);

            assertThat(HardwareFingerprintUtils.verifyTimestamp(fp, 300L)).isTrue();
        }
    }

    @Nested
    @DisplayName("parseAndVerifySignedFingerprint")
    class ParseAndVerifySignedFingerprint {

        @Test
        @DisplayName("should return null when any input parameter is blank")
        void shouldReturnNullWhenAnyParameterIsBlank() {
            assertThat(HardwareFingerprintUtils.parseAndVerifySignedFingerprint(null, "sig", "secret", 300L)).isNull();
            assertThat(HardwareFingerprintUtils.parseAndVerifySignedFingerprint("json", null, "secret", 300L)).isNull();
            assertThat(HardwareFingerprintUtils.parseAndVerifySignedFingerprint("json", "sig", null, 300L)).isNull();
            assertThat(HardwareFingerprintUtils.parseAndVerifySignedFingerprint("json", "sig", "", 300L)).isNull();
        }

        @Test
        @DisplayName("should return null when signature verification fails")
        void shouldReturnNullWhenSignatureVerificationFails() {
            String json = "{\"canvas\":\"c\"}";

            assertThat(HardwareFingerprintUtils.parseAndVerifySignedFingerprint(json, "invalid-signature-data-12345", TEST_SECRET, 300L))
                    .isNull();
        }

        @Test
        @DisplayName("should return null when signature is valid but JSON is malformed")
        void shouldReturnNullWhenJsonIsMalformed() throws Exception {
            String json = "not-valid-json";
            String sig = hmacSha256Base64(TEST_SECRET, json);

            assertThat(HardwareFingerprintUtils.parseAndVerifySignedFingerprint(json, sig, TEST_SECRET, 300L))
                    .isNull();
        }

        @Test
        @DisplayName("should return null when timestamp is outside valid window")
        void shouldReturnNullWhenTimestampIsOutsideValidWindow() throws Exception {
            long now = System.currentTimeMillis();
            String json = "{\"canvas\":\"c\",\"timestamp\":" + (now - 600_000L) + "}";
            String sig = hmacSha256Base64(TEST_SECRET, json);

            assertThat(HardwareFingerprintUtils.parseAndVerifySignedFingerprint(json, sig, TEST_SECRET, 300L))
                    .isNull();
        }

        @Test
        @DisplayName("should return the fingerprint for a valid signed payload")
        void shouldReturnFingerprintForValidSignedPayload() throws Exception {
            long now = System.currentTimeMillis();
            String json = "{\"canvas\":\"c\",\"webgl\":\"w\",\"timestamp\":" + now + "}";
            String sig = hmacSha256Base64(TEST_SECRET, json);

            HardwareFingerprint fp = HardwareFingerprintUtils.parseAndVerifySignedFingerprint(json, sig, TEST_SECRET, 300L);

            assertThat(fp).isNotNull();
            assertThat(fp.getCanvas()).isEqualTo("c");
            assertThat(fp.getWebgl()).isEqualTo("w");
            assertThat(fp.getTimestamp()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("verifyFingerprint")
    class VerifyFingerprint {

        @Test
        @DisplayName("should return false when request fingerprint is null")
        void shouldReturnFalseWhenRequestFingerprintIsNull() {
            HardwareFingerprint token = sampleFingerprint();
            assertThat(HardwareFingerprintUtils.verifyFingerprint(null, token)).isFalse();
        }

        @Test
        @DisplayName("should return false when token fingerprint is null")
        void shouldReturnFalseWhenTokenFingerprintIsNull() {
            HardwareFingerprint request = sampleFingerprint();
            assertThat(HardwareFingerprintUtils.verifyFingerprint(request, null)).isFalse();
        }

        @Test
        @DisplayName("should return false when both fingerprints are null")
        void shouldReturnFalseWhenBothFingerprintsAreNull() {
            assertThat(HardwareFingerprintUtils.verifyFingerprint(null, null)).isFalse();
        }

        @Test
        @DisplayName("should return true for two identical fingerprints")
        void shouldReturnTrueForTwoIdenticalFingerprints() {
            HardwareFingerprint a = sampleFingerprint();
            HardwareFingerprint b = sampleFingerprint();

            assertThat(HardwareFingerprintUtils.verifyFingerprint(a, b)).isTrue();
        }

        @Test
        @DisplayName("should return true when canvas and webgl match even with minor changes")
        void shouldReturnTrueWhenCanvasAndWebglMatchWithMinorChanges() {
            HardwareFingerprint a = sampleFingerprint();
            HardwareFingerprint b = sampleFingerprint();
            b.setScreen("2560x1440");
            b.setLanguage("en-US");

            // canvas (0.4) + webgl (0.4) = 0.8 == threshold
            assertThat(HardwareFingerprintUtils.verifyFingerprint(a, b)).isTrue();
        }

        @Test
        @DisplayName("should return false when canvas differs")
        void shouldReturnFalseWhenCanvasDiffers() {
            HardwareFingerprint a = sampleFingerprint();
            HardwareFingerprint b = sampleFingerprint();
            b.setCanvas("different-canvas");

            assertThat(HardwareFingerprintUtils.verifyFingerprint(a, b)).isFalse();
        }

        @Test
        @DisplayName("should return false when webgl differs")
        void shouldReturnFalseWhenWebglDiffers() {
            HardwareFingerprint a = sampleFingerprint();
            HardwareFingerprint b = sampleFingerprint();
            b.setWebgl("different-webgl");

            assertThat(HardwareFingerprintUtils.verifyFingerprint(a, b)).isFalse();
        }

        @Test
        @DisplayName("should accept a small pixelRatio difference (within 0.1 tolerance)")
        void shouldAcceptSmallPixelRatioDifference() {
            HardwareFingerprint a = sampleFingerprint();
            HardwareFingerprint b = sampleFingerprint();
            b.setPixelRatio(2.05);

            assertThat(HardwareFingerprintUtils.verifyFingerprint(a, b)).isTrue();
        }
    }

    @Nested
    @DisplayName("extractFingerprintFromClaims")
    class ExtractFingerprintFromClaims {

        @Test
        @DisplayName("should return null when claims map is null")
        void shouldReturnNullWhenClaimsMapIsNull() {
            assertThat(HardwareFingerprintUtils.extractFingerprintFromClaims(null)).isNull();
        }

        @Test
        @DisplayName("should return null when claim value is null")
        void shouldReturnNullWhenClaimValueIsNull() {
            Claim claim = mock(Claim.class);
            when(claim.as(Object.class)).thenReturn(null);
            Map<String, Claim> claims = new HashMap<>();
            claims.put("hardwareFingerprint", claim);

            assertThat(HardwareFingerprintUtils.extractFingerprintFromClaims(claims)).isNull();
        }

        @Test
        @DisplayName("should parse claim when value is a JSON string")
        void shouldParseClaimWhenValueIsJsonString() {
            String json = "{\"canvas\":\"c1\",\"webgl\":\"w1\"}";
            Claim claim = mock(Claim.class);
            when(claim.as(Object.class)).thenReturn(json);
            Map<String, Claim> claims = new HashMap<>();
            claims.put("hardwareFingerprint", claim);

            HardwareFingerprint fp = HardwareFingerprintUtils.extractFingerprintFromClaims(claims);

            assertThat(fp).isNotNull();
            assertThat(fp.getCanvas()).isEqualTo("c1");
            assertThat(fp.getWebgl()).isEqualTo("w1");
        }

        @Test
        @DisplayName("should parse claim when value is a Map")
        void shouldParseClaimWhenValueIsMap() {
            Map<String, Object> mapValue = new HashMap<>();
            mapValue.put("canvas", "c-map");
            mapValue.put("webgl", "w-map");
            Claim claim = mock(Claim.class);
            when(claim.as(Object.class)).thenReturn(mapValue);
            Map<String, Claim> claims = new HashMap<>();
            claims.put("hardwareFingerprint", claim);

            HardwareFingerprint fp = HardwareFingerprintUtils.extractFingerprintFromClaims(claims);

            assertThat(fp).isNotNull();
            assertThat(fp.getCanvas()).isEqualTo("c-map");
            assertThat(fp.getWebgl()).isEqualTo("w-map");
        }

        @Test
        @DisplayName("should return null when claim value is an unsupported type")
        void shouldReturnNullWhenClaimValueIsUnsupportedType() {
            Claim claim = mock(Claim.class);
            when(claim.as(Object.class)).thenReturn(123);
            Map<String, Claim> claims = new HashMap<>();
            claims.put("hardwareFingerprint", claim);

            assertThat(HardwareFingerprintUtils.extractFingerprintFromClaims(claims)).isNull();
        }
    }
}
