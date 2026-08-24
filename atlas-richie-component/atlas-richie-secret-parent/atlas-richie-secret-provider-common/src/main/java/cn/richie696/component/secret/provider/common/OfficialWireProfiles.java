package cn.richie696.component.secret.provider.common;

import java.util.Locale;

/** Vendor REST resource-shape defaults; credentials and request signing stay explicit. */
public final class OfficialWireProfiles {
    private static final String SECRET = "/secrets/{path}";
    private static final String WRAP = "/keys/{key}/wrap";
    private static final String UNWRAP = "/keys/{key}/unwrap";
    private OfficialWireProfiles() { }

    public static void apply(String provider, RemoteProviderProperties properties) {
        if (properties == null || properties.getWire() == null) return;
        RemoteProviderProperties.Wire wire = properties.getWire();
        String type = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
        if (properties.getAuthentication() != null
                && properties.getAuthentication().getType() == RemoteProviderProperties.AuthenticationType.ACCESS_KEY
                && properties.getAuthentication().getSignature() == RemoteProviderProperties.RequestSignature.NONE) {
            switch (type) {
                case "huawei" -> properties.getAuthentication().setSignature(RemoteProviderProperties.RequestSignature.HUAWEI_SDK_HMAC_SHA256);
                case "tencent" -> {
                    properties.getAuthentication().setSignature(RemoteProviderProperties.RequestSignature.TENCENT_TC3_HMAC_SHA256);
                    if (properties.getAuthentication().getSigningService() == null) properties.getAuthentication().setSigningService("kms");
                }
                case "volcengine" -> {
                    properties.getAuthentication().setSignature(RemoteProviderProperties.RequestSignature.VOLCENGINE_HMAC_SHA256);
                    if (properties.getAuthentication().getSigningService() == null) properties.getAuthentication().setSigningService("kms");
                }
                case "baidu" -> properties.getAuthentication().setSignature(RemoteProviderProperties.RequestSignature.BAIDU_BCE_V2);
                default -> { }
            }
        }
        switch (type) {
            case "azure" -> {
                defaults(wire, SECRET, "/keys/{key}/{version}/wrapkey", "/keys/{key}/{version}/unwrapkey");
                fields(wire, "value", "id", "attributes.created", "value", "value");
            }
            case "gcp" -> {
                defaults(wire, "/v1/projects/{projectId}/secrets/{path}/versions/{version}:access",
                        "/v1/projects/{projectId}/locations/{region}/keyRings/{namespace}/cryptoKeys/{key}:encrypt",
                        "/v1/projects/{projectId}/locations/{region}/keyRings/{namespace}/cryptoKeys/{key}:decrypt");
                fields(wire, "payload.data", "name", "createTime", "ciphertext", "plaintext");
                wire.setRequestValueField("plaintext");
                wire.setRequestAadField("additionalAuthenticatedData");
                if ("PLAIN".equalsIgnoreCase(wire.getSecretValueEncoding())) wire.setSecretValueEncoding("BASE64");
            }
            case "oci" -> {
                defaults(wire, "/20180608/secrets/{path}/versions/{version}/secretBundle",
                        "/20180608/encrypt", "/20180608/decrypt");
                fields(wire, "secretBundleContent.content", "versionName", "timeOfCreation", "ciphertext", "plaintext");
                wire.setRequestValueField("plaintext");
                wire.setRequestAadField("associatedData");
                wire.setRequestKeyField("keyId");
                if ("PLAIN".equalsIgnoreCase(wire.getSecretValueEncoding())) wire.setSecretValueEncoding("BASE64");
            }
            case "ibm-key-protect" -> {
                defaults(wire, SECRET, "/api/v2/keys/{key}/actions/wrap", "/api/v2/keys/{key}/actions/unwrap");
                fields(wire, "value", "version", "createdAt", "ciphertext", "plaintext");
                wire.setRequestValueField("plaintext");
            }
            case "huawei" -> defaults(wire, "/v1/{projectId}/secrets/{path}/versions/{version}",
                    "/v1/{projectId}/kms/encrypt", "/v1/{projectId}/kms/decrypt");
            case "tencent" -> defaults(wire, SECRET, "/v1/kms/{key}/encrypt", "/v1/kms/{key}/decrypt");
            case "baidu" -> defaults(wire, SECRET, "/v1/key/{key}/wrap", "/v1/key/{key}/unwrap");
            case "volcengine" -> defaults(wire, SECRET, "/", "/");
            default -> { }
        }
    }

    private static void defaults(RemoteProviderProperties.Wire wire, String secret, String wrap, String unwrap) {
        if (SECRET.equals(wire.getSecretPath())) wire.setSecretPath(secret);
        if (WRAP.equals(wire.getWrapPath())) wire.setWrapPath(wrap);
        if (UNWRAP.equals(wire.getUnwrapPath())) wire.setUnwrapPath(unwrap);
    }
    private static void fields(RemoteProviderProperties.Wire wire, String value, String version,
                               String created, String wrapped, String plaintext) {
        if ("value".equals(wire.getSecretValueField())) wire.setSecretValueField(value);
        if ("version".equals(wire.getSecretVersionField())) wire.setSecretVersionField(version);
        if ("createdAt".equals(wire.getSecretCreatedAtField())) wire.setSecretCreatedAtField(created);
        if ("wrappedKey".equals(wire.getWrappedKeyField())) wire.setWrappedKeyField(wrapped);
        if ("plaintext".equals(wire.getPlaintextField())) wire.setPlaintextField(plaintext);
    }
}
