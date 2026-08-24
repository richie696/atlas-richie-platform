package cn.richie696.component.secret.provider.common;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Vendor request signing for REST Provider modules. Secrets never enter logs or exceptions. */
final class RemoteRequestSigner {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private RemoteRequestSigner() { }

    static void apply(HttpRequest.Builder builder, URI uri, String method, byte[] body,
                      RemoteProviderProperties.Authentication authentication,
                      Map<String, String> variables) {
        RemoteProviderProperties.RequestSignature signature = authentication.getSignature();
        if (signature == null || signature == RemoteProviderProperties.RequestSignature.NONE) return;
        String timestamp = UTC.format(Instant.now());
        String region = nonBlank(authentication.getSigningRegion(), variables.get("region"), "global");
        String service = nonBlank(authentication.getSigningService(), variables.get("service"), "kms");
        String host = uri.getHost();
        String contentType = "application/json";
        builder.header("Content-Type", contentType);
        switch (signature) {
            case HUAWEI_SDK_HMAC_SHA256 -> huawei(builder, uri, method, body, authentication, timestamp, host, contentType);
            case TENCENT_TC3_HMAC_SHA256 -> tencent(builder, uri, method, body, authentication, timestamp, host, region, service, contentType);
            case VOLCENGINE_HMAC_SHA256 -> volcengine(builder, uri, method, body, authentication, timestamp, host, region, service, contentType);
            case BAIDU_BCE_V2 -> baidu(builder, uri, method, body, authentication, timestamp, host, region, service, contentType);
            case NONE -> { }
        }
    }

    private static void huawei(HttpRequest.Builder builder, URI uri, String method, byte[] body,
                               RemoteProviderProperties.Authentication auth, String timestamp,
                               String host, String contentType) {
        String signed = "content-type;host;x-sdk-date";
        String canonical = canonicalRequest(method, uri, body,
                Map.of("content-type", contentType, "host", host, "x-sdk-date", timestamp), signed);
        String toSign = "SDK-HMAC-SHA256\n" + timestamp + "\n" + sha256(canonical);
        byte[] raw = secret(auth);
        try {
            String value = "SDK-HMAC-SHA256 Access=" + auth.getAccessKeyId()
                    + ", SignedHeaders=" + signed + ", Signature=" + hmacHex(raw, toSign);
            builder.header("X-Sdk-Date", timestamp).header("Authorization", value);
        } finally { java.util.Arrays.fill(raw, (byte) 0); }
    }

    private static void tencent(HttpRequest.Builder builder, URI uri, String method, byte[] body,
                                RemoteProviderProperties.Authentication auth, String timestamp,
                                String host, String region, String service, String contentType) {
        long epoch = Instant.now().getEpochSecond();
        String date = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(epoch));
        String action = nonBlank(auth.getApiAction(), "Encrypt");
        String version = nonBlank(auth.getApiVersion(), "2019-11-15");
        String signed = "content-type;host;x-tc-action";
        String requestTimestamp = Long.toString(epoch);
        builder.header("X-TC-Action", action).header("X-TC-Version", version)
                .header("X-TC-Timestamp", requestTimestamp).header("X-TC-Region", region);
        if (auth.getSecurityToken() != null && !auth.getSecurityToken().isBlank()) {
            builder.header("X-TC-Token", auth.getSecurityToken());
        }
        String canonical = canonicalRequest(method, uri, body,
                Map.of("content-type", contentType, "host", host, "x-tc-action", action), signed);
        String scope = date + "/" + service + "/tc3_request";
        String toSign = "TC3-HMAC-SHA256\n" + requestTimestamp + "\n" + scope + "\n" + sha256(canonical);
        byte[] raw = secret(auth);
        byte[] dateKey;
        try { dateKey = hmacBytes(("TC3" + new String(raw, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8), date); }
        finally { java.util.Arrays.fill(raw, (byte) 0); }
        byte[] serviceKey = hmacBytes(dateKey, service);
        byte[] signingKey = hmacBytes(serviceKey, "tc3_request");
        try {
            String value = "TC3-HMAC-SHA256 Credential=" + auth.getAccessKeyId() + "/" + scope
                    + ", SignedHeaders=" + signed + ", Signature=" + hmacHex(signingKey, toSign);
            builder.header("Authorization", value);
        } finally {
            java.util.Arrays.fill(dateKey, (byte) 0); java.util.Arrays.fill(serviceKey, (byte) 0);
            java.util.Arrays.fill(signingKey, (byte) 0);
        }
    }

    private static void volcengine(HttpRequest.Builder builder, URI uri, String method, byte[] body,
                                   RemoteProviderProperties.Authentication auth, String timestamp,
                                   String host, String region, String service, String contentType) {
        String date = timestamp.substring(0, 8);
        String bodyHash = sha256(body);
        String signed = "content-type;host;x-content-sha256;x-date";
        builder.header("X-Date", timestamp).header("X-Content-Sha256", bodyHash);
        if (auth.getSecurityToken() != null && !auth.getSecurityToken().isBlank()) {
            builder.header("X-Security-Token", auth.getSecurityToken());
        }
        String canonical = canonicalRequest(method, uri, body,
                Map.of("content-type", contentType, "host", host, "x-content-sha256", bodyHash, "x-date", timestamp), signed);
        String scope = date + "/" + region + "/" + service + "/request";
        String toSign = "HMAC-SHA256\n" + timestamp + "\n" + scope + "\n" + sha256(canonical);
        byte[] raw = secret(auth);
        byte[] dateKey;
        try { dateKey = hmacBytes(raw, date); }
        finally { java.util.Arrays.fill(raw, (byte) 0); }
        byte[] regionKey = hmacBytes(dateKey, region);
        byte[] serviceKey = hmacBytes(regionKey, service);
        byte[] signingKey = hmacBytes(serviceKey, "request");
        try {
            String value = "HMAC-SHA256 Credential=" + auth.getAccessKeyId() + "/" + scope
                    + ", SignedHeaders=" + signed + ", Signature=" + hmacHex(signingKey, toSign);
            builder.header("Authorization", value);
        } finally {
            java.util.Arrays.fill(dateKey, (byte) 0); java.util.Arrays.fill(regionKey, (byte) 0);
            java.util.Arrays.fill(serviceKey, (byte) 0); java.util.Arrays.fill(signingKey, (byte) 0);
        }
    }

    private static void baidu(HttpRequest.Builder builder, URI uri, String method, byte[] body,
                              RemoteProviderProperties.Authentication auth, String timestamp,
                              String host, String region, String service, String contentType) {
        String date = timestamp.substring(0, 8);
        String signed = "content-type;host;x-bce-date";
        builder.header("X-Bce-Date", timestamp);
        String canonical = canonicalRequest(method, uri, body,
                Map.of("content-type", contentType, "host", host, "x-bce-date", timestamp), signed);
        String prefix = "bce-auth-v2/" + auth.getAccessKeyId() + "/" + date + "/" + region + "/" + service;
        byte[] raw = secret(auth);
        byte[] signingKey;
        try { signingKey = hmacBytes(raw, prefix); }
        finally { java.util.Arrays.fill(raw, (byte) 0); }
        try {
            String value = prefix + "/" + signed + "/" + hmacHex(signingKey, canonical);
            builder.header("Authorization", value);
        } finally { java.util.Arrays.fill(signingKey, (byte) 0); }
    }

    private static String canonicalRequest(String method, URI uri, byte[] body,
                                           Map<String, String> headers, String signedHeaders) {
        StringBuilder canonicalHeaders = new StringBuilder();
        for (String name : signedHeaders.split(";")) {
            canonicalHeaders.append(name).append(':').append(headers.get(name)).append('\n');
        }
        return method + "\n" + (uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath())
                + "\n" + canonicalQuery(uri.getRawQuery()) + "\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + sha256(body);
    }

    private static String canonicalQuery(String query) {
        if (query == null || query.isBlank()) return "";
        java.util.List<String> values = new java.util.ArrayList<>(java.util.List.of(query.split("&", -1)));
        java.util.Collections.sort(values);
        return String.join("&", values);
    }

    private static byte[] secret(RemoteProviderProperties.Authentication auth) {
        char[] chars = auth.getAccessKeySecret();
        if (chars == null || chars.length == 0) return new byte[0];
        // getAccessKeySecret() returns a defensive copy; wipe only this request-local copy.
        try { return new String(chars).getBytes(StandardCharsets.UTF_8); }
        finally { java.util.Arrays.fill(chars, '\0'); }
    }
    private static String nonBlank(String first, String second, String fallback) {
        return first != null && !first.isBlank() ? first : (second != null && !second.isBlank() ? second : fallback);
    }
    private static String nonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }
    private static String sha256(byte[] value) { try { return hex(MessageDigest.getInstance("SHA-256").digest(value == null ? new byte[0] : value)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private static byte[] hmacBytes(byte[] key, String value) { try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256")); return mac.doFinal(value.getBytes(StandardCharsets.UTF_8)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String hmacHex(byte[] key, String value) { return hex(hmacBytes(key, value)); }
    private static String hex(byte[] value) { return java.util.HexFormat.of().formatHex(value); }
}
