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
                if (properties.getApiVersion() == null || properties.getApiVersion().isBlank()) {
                    properties.setApiVersion("2025-07-01");
                }
                defaults(wire,
                        "/secrets/{path}/{version}?api-version={apiVersion}",
                        "/keys/{key}/{version}/wrapkey?api-version={apiVersion}",
                        "/keys/{key}/{version}/unwrapkey?api-version={apiVersion}");
                if (wire.getSecretLatestPath().isBlank()) {
                    wire.setSecretLatestPath("/secrets/{path}?api-version={apiVersion}");
                }
                fields(wire, "value", "id", "attributes.created", "value", "value");
                wire.setRequestAadField("");
                wire.setRequestAlgorithmField("alg");
                if (wire.getRequestAlgorithm().isBlank()) wire.setRequestAlgorithm("RSA-OAEP-256");
                wire.setRequestValueEncoding(RemoteProviderProperties.ValueEncoding.BASE64_URL);
                wire.setResponseValueEncoding(RemoteProviderProperties.ValueEncoding.BASE64_URL);
            }
            case "gcp" -> {
                defaults(wire, "/v1/projects/{projectId}/secrets/{path}/versions/{version}:access",
                        "/v1/projects/{projectId}/locations/{region}/keyRings/{namespace}/cryptoKeys/{key}:encrypt",
                        "/v1/projects/{projectId}/locations/{region}/keyRings/{namespace}/cryptoKeys/{key}:decrypt");
                fields(wire, "payload.data", "name", "createTime", "ciphertext", "plaintext");
                requestFields(wire, "plaintext", "ciphertext", "additionalAuthenticatedData");
                if ("PLAIN".equalsIgnoreCase(wire.getSecretValueEncoding())) wire.setSecretValueEncoding("BASE64");
            }
            case "oci" -> {
                defaults(wire, "/20180608/secrets/{path}/versions/{version}/secretBundle",
                        "/20180608/encrypt", "/20180608/decrypt");
                fields(wire, "secretBundleContent.content", "versionName", "timeOfCreation", "ciphertext", "plaintext");
                requestFields(wire, "plaintext", "ciphertext", "associatedData");
                if (wire.getRequestKeyField().isBlank()) wire.setRequestKeyField("keyId");
                if ("PLAIN".equalsIgnoreCase(wire.getSecretValueEncoding())) wire.setSecretValueEncoding("BASE64");
            }
            case "ibm-key-protect" -> {
                defaults(wire, SECRET, "/api/v2/keys/{key}/actions/wrap", "/api/v2/keys/{key}/actions/unwrap");
                fields(wire, "value", "version", "createdAt", "ciphertext", "plaintext");
                wire.setRequestValueField("plaintext");
            }
            case "huawei" -> {
                defaults(wire, "/v1/{projectId}/secrets/{path}/versions/{version}",
                        "/v1.0/{projectId}/kms/encrypt-data", "/v1.0/{projectId}/kms/decrypt-data");
                fields(wire, "version.secret_string", "version.version_metadata.id",
                        "version.version_metadata.create_time", "cipher_text", "plain_text");
                requestFields(wire, "plain_text", "cipher_text", "additional_authenticated_data");
                if (wire.getRequestKeyField().isBlank()) wire.setRequestKeyField("key_id");
            }
            case "tencent" -> {
                defaults(wire, "/", "/", "/");
                wire.setSecretMethod(RemoteProviderProperties.HttpMethod.POST);
                wire.setSecretRequestNameField("SecretName");
                wire.setSecretRequestVersionField("VersionId");
                wire.setLatestVersionValue("SSM_Current");
                fields(wire, "Response.SecretString", "Response.VersionId",
                        "Response.RequestId", "Response.CiphertextBlob", "Response.Plaintext");
                requestFields(wire, "Plaintext", "CiphertextBlob", "EncryptionContext");
                if (wire.getRequestKeyField().isBlank()) wire.setRequestKeyField("KeyId");
                wire.setRequestAadEncoding(RemoteProviderProperties.RequestAadEncoding.ATTRIBUTES);
                wire.setSecretAction("GetSecretValue");
                wire.setSecretApiVersion("2019-09-23");
                wire.setSecretSigningService("ssm");
                wire.setWrapAction("Encrypt");
                wire.setUnwrapAction("Decrypt");
                wire.setKmsApiVersion("2019-01-18");
                wire.setKmsSigningService("kms");
            }
            case "baidu" -> {
                defaults(wire, SECRET, "/?action=Encrypt", "/?action=Decrypt");
                fields(wire, "value", "version", "createdAt", "ciphertext", "plaintext");
                requestFields(wire, "plaintext", "ciphertext", "");
                if (wire.getRequestKeyField().isBlank()) wire.setRequestKeyField("keyId");
                wire.setRequestAlgorithmField("algorithmMode");
                if (wire.getRequestAlgorithm().isBlank()) wire.setRequestAlgorithm("GCM");
            }
            case "volcengine" -> {
                defaults(wire, SECRET,
                        "/?Action=Encrypt&Version=2021-02-18&KeyringName={namespace}&KeyName={key}",
                        "/?Action=Decrypt&Version=2021-02-18");
                fields(wire, "value", "version", "createdAt",
                        "Result.CiphertextBlob", "Result.Plaintext");
                requestFields(wire, "Plaintext", "CiphertextBlob", "EncryptionContext");
                if (!wire.isRequestAadEncodingConfigured()) {
                    wire.setRequestAadEncoding(RemoteProviderProperties.RequestAadEncoding.ATTRIBUTES);
                }
            }
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
    private static void requestFields(
            RemoteProviderProperties.Wire wire,
            String wrap,
            String unwrap,
            String aad) {
        if (wire.getRequestWrapValueField().isBlank()
                && "value".equals(wire.getRequestValueField())) {
            wire.setRequestWrapValueField(wrap);
        }
        if (wire.getRequestUnwrapValueField().isBlank()
                && "value".equals(wire.getRequestValueField())) {
            wire.setRequestUnwrapValueField(unwrap);
        }
        if ("aad".equals(wire.getRequestAadField())) wire.setRequestAadField(aad);
    }
}
