/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.pkcs11;

import cn.richie696.component.secret.api.SecretBackend;
import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.KeyWrappingBackend;
import cn.richie696.component.secret.api.crypto.SignatureValue;
import cn.richie696.component.secret.api.crypto.SigningBackend;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.api.provider.SecretProviderDescriptor;
import cn.richie696.component.secret.api.provider.SecretProviderSession;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.AuthProvider;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** PKCS#11 client backed by the JDK SunPKCS11 provider; no key material is persisted. */
public final class Pkcs11SecretClient implements
        SecretBootstrapClient, KeyWrappingBackend, SigningBackend, SecretProviderSession {
    private static final Object PROVIDER_LOCK = new Object();
    private static final Map<String, ProviderRegistration> PROVIDERS = new HashMap<>();
    private static final byte[] RFC3394_IV = {
            (byte) 0xA6, (byte) 0xA6, (byte) 0xA6, (byte) 0xA6,
            (byte) 0xA6, (byte) 0xA6, (byte) 0xA6, (byte) 0xA6};

    private final String providerId;
    private final String hash;
    private final Pkcs11SecretProperties properties;
    private final KeyStore keyStore;
    private final Provider provider;
    private final String providerName;
    private final char[] pin;
    private final Map<String, Key> keyCache = new ConcurrentHashMap<>();
    private final Map<String, Certificate> certificateCache = new ConcurrentHashMap<>();
    private final Set<SecretCapability> capabilities;
    private final AtomicBoolean closed = new AtomicBoolean();

    private Pkcs11SecretClient(
            String providerId,
            String hash,
            Pkcs11SecretProperties properties,
            KeyStore keyStore,
            Provider provider,
            String providerName,
            char[] pin) throws GeneralSecurityException {
        this.providerId = providerId;
        this.hash = hash;
        this.properties = properties;
        this.keyStore = keyStore;
        this.provider = provider;
        this.providerName = providerName;
        this.pin = pin;
        this.capabilities = preloadConfiguredAliases();
    }

    static Pkcs11SecretClient create(
            ConfigurableEnvironment environment,
            BootstrapSecretProperties bootstrap) {
        return create(environment, bootstrap, null);
    }

    static Pkcs11SecretClient create(
            ConfigurableEnvironment environment,
            BootstrapSecretProperties bootstrap,
            SecretBootstrapContext context) {
        String prefix = Pkcs11SecretProperties.PREFIX;
        String providerId = "pkcs11";
        if (context != null && context.providerId() != null && !context.providerId().isBlank()) {
            providerId = context.providerId();
            if (context.configurationPrefix() != null && !context.configurationPrefix().isBlank()) {
                prefix = context.configurationPrefix();
            }
        } else {
            String active = bootstrap.getActiveProvider();
            if (active != null && !active.isBlank() && bootstrap.getProviders().containsKey(active)) {
                prefix = BootstrapSecretProperties.PREFIX + ".providers." + active;
                providerId = active;
            }
        }
        Pkcs11SecretProperties properties = Binder.get(environment)
                .bind(prefix, Pkcs11SecretProperties.class)
                .orElseGet(Pkcs11SecretProperties::new);
        Pkcs11SecretConfiguration.validate(properties);
        char[] pin = properties.getPin();
        String registeredName = null;
        try {
            Provider base = Security.getProvider("SunPKCS11");
            if (base == null) {
                throw new SecretConfigurationException(
                        "SEC-BOOT-003", "JDK SunPKCS11 provider is unavailable");
            }
            Provider configured;
            Path config = Files.createTempFile("atlas-secret-pkcs11-", ".cfg");
            try {
                StringBuilder text = new StringBuilder("name=")
                        .append(providerConfigName(providerId, properties))
                        .append("\nlibrary=").append(properties.getLibrary());
                if (properties.getSlot() != null) text.append("\nslot=").append(properties.getSlot());
                text.append("\nexplicitCancel=true");
                Files.writeString(config, text.toString(), StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING);
                configured = base.configure(config.toString());
            } finally {
                Files.deleteIfExists(config);
            }
            configured = retainProvider(configured);
            registeredName = configured.getName();
            KeyStore keyStore = KeyStore.getInstance("PKCS11", configured);
            keyStore.load(null, pin);
            return new Pkcs11SecretClient(
                    providerId,
                    Pkcs11SecretConfiguration.hash(providerId, properties),
                    properties,
                    keyStore,
                    configured,
                    registeredName,
                    pin);
        } catch (SecretException exception) {
            releaseProvider(registeredName);
            Arrays.fill(pin, '\0');
            throw exception;
        } catch (Exception exception) {
            releaseProvider(registeredName);
            Arrays.fill(pin, '\0');
            throw new SecretConfigurationException(
                    "SEC-BOOT-003", "PKCS#11 token cannot be opened", exception);
        }
    }

    @Override
    public SecretBootstrapResult load(SecretBootstrapRequest request) {
        ensureOpen();
        return new SecretBootstrapResult(
                providerId, "kms-only", String.join(",", request.logicalPaths()),
                Instant.now(), Map.of(), null);
    }

    @Override
    public WrappedKey wrap(KeyReference reference, byte[] plaintext, CryptoContext context) {
        ensureOpen();
        requireCapability(SecretCapability.KEY_WRAP, "wrapping");
        if (plaintext == null || plaintext.length == 0) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "Plaintext data key must not be empty");
        }
        try {
            return new WrappedKey(
                    cipher(Cipher.WRAP_MODE, reference).wrap(new SecretKeySpec(plaintext, "AES")),
                    "pkcs11-aes-wrap");
        } catch (GeneralSecurityException unsupported) {
            try {
                return new WrappedKey(rfc3394Wrap(secretKey(reference), plaintext), "pkcs11-aes-wrap");
            } catch (Exception failure) {
                throw new SecretCryptoException("SEC-CRYPTO-001", "PKCS#11 wrap operation failed", failure);
            }
        } catch (Exception failure) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "PKCS#11 wrap operation failed", failure);
        }
    }

    @Override
    public byte[] unwrap(KeyReference reference, WrappedKey wrapped, CryptoContext context) {
        ensureOpen();
        requireCapability(SecretCapability.KEY_UNWRAP, "wrapping");
        if (!"pkcs11-aes-wrap".equals(wrapped.algorithm())) {
            throw new SecretCryptoException(
                    "SEC-CRYPTO-002", "Wrapped key algorithm is not supported by PKCS#11");
        }
        byte[] value = wrapped.value();
        try {
            Key key = cipher(Cipher.UNWRAP_MODE, reference)
                    .unwrap(value, "AES", Cipher.SECRET_KEY);
            byte[] encoded = key.getEncoded();
            if (encoded == null) {
                throw new SecretCryptoException(
                        "SEC-CRYPTO-002", "PKCS#11 returned a non-extractable unwrapped key");
            }
            return encoded;
        } catch (GeneralSecurityException unsupported) {
            try {
                return rfc3394Unwrap(secretKey(reference), value);
            } catch (Exception failure) {
                throw new SecretCryptoException("SEC-CRYPTO-002", "PKCS#11 unwrap operation failed", failure);
            }
        } catch (SecretException exception) {
            throw exception;
        } catch (Exception failure) {
            throw new SecretCryptoException("SEC-CRYPTO-002", "PKCS#11 unwrap operation failed", failure);
        } finally {
            Arrays.fill(value, (byte) 0);
        }
    }

    @Override
    public SignatureValue sign(KeyReference reference, byte[] payload, CryptoContext context) {
        ensureOpen();
        requireCapability(SecretCapability.SIGN, "signing");
        validateSigningKey(reference);
        if (payload == null || payload.length == 0) {
            throw new SecretCryptoException("SEC-SIGN-001", "Signing payload must not be empty");
        }
        try {
            Signature signature = Signature.getInstance(properties.getSigningAlgorithm(), provider);
            String alias = alias(reference);
            signature.initSign(privateKey(alias));
            signature.update(payload);
            return new SignatureValue("pkcs11:v2:"
                    + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(alias.getBytes(StandardCharsets.UTF_8)) + ":"
                    + properties.getSigningAlgorithm() + ":"
                    + Base64.getEncoder().encodeToString(signature.sign()));
        } catch (SecretException exception) {
            throw exception;
        } catch (Exception failure) {
            throw new SecretCryptoException("SEC-SIGN-001", "PKCS#11 signing operation failed", failure);
        }
    }

    @Override
    public boolean verify(
            KeyReference reference,
            byte[] payload,
            SignatureValue value,
            CryptoContext context) {
        ensureOpen();
        requireCapability(SecretCapability.VERIFY, "signing");
        validateSigningKey(reference);
        if (payload == null || payload.length == 0 || value == null) {
            throw new SecretCryptoException("SEC-SIGN-002", "Verification input must not be empty");
        }
        byte[] decoded = null;
        try {
            String[] parts = value.value().split(":", 5);
            if (parts.length != 5 || !"pkcs11".equals(parts[0]) || !"v2".equals(parts[1])
                    || !properties.getSigningAlgorithm().equals(parts[3])) {
                throw new SecretCryptoException("SEC-SIGN-002", "PKCS#11 signature value is malformed");
            }
            String alias = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
            if (!trustedSigningAliases(reference.logicalKey()).contains(alias)) {
                throw new SecretCryptoException(
                        "SEC-SIGN-002", "PKCS#11 signature alias is not trusted for the logical key binding");
            }
            Signature signature = Signature.getInstance(parts[3], provider);
            signature.initVerify(publicKey(alias));
            signature.update(payload);
            decoded = Base64.getDecoder().decode(parts[4]);
            return signature.verify(decoded);
        } catch (SecretException exception) {
            throw exception;
        } catch (Exception failure) {
            throw new SecretCryptoException("SEC-SIGN-002", "PKCS#11 verification operation failed", failure);
        } finally {
            if (decoded != null) Arrays.fill(decoded, (byte) 0);
        }
    }

    @Override
    public SecretProviderDescriptor descriptor() {
        return new SecretProviderDescriptor("pkcs11", providerId, capabilities);
    }

    @Override
    public Optional<SecretBackend> secretBackend() {
        return Optional.empty();
    }

    @Override
    public Optional<KeyWrappingBackend> keyWrappingBackend() {
        return capabilities.contains(SecretCapability.KEY_WRAP) ? Optional.of(this) : Optional.empty();
    }

    @Override
    public Optional<SigningBackend> signingBackend() {
        return capabilities.contains(SecretCapability.SIGN) ? Optional.of(this) : Optional.empty();
    }

    String configurationHash() {
        return hash;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        Arrays.fill(pin, '\0');
        keyCache.clear();
        certificateCache.clear();
        releaseProvider(providerName);
    }

    private Set<SecretCapability> preloadConfiguredAliases() throws GeneralSecurityException {
        boolean wrapping = false;
        boolean signing = false;
        java.util.Set<String> currentAliases = new java.util.LinkedHashSet<>(properties.getKeyBindings().values());
        java.util.Set<String> allAliases = new java.util.LinkedHashSet<>(currentAliases);
        properties.getVerificationKeyBindings().values().forEach(allAliases::addAll);
        for (String alias : allAliases) {
            Key key = keyStore.getKey(alias, pin);
            Certificate certificate = keyStore.getCertificate(alias);
            if (key == null && certificate == null) {
                throw new GeneralSecurityException("Configured PKCS#11 alias is unavailable: " + alias);
            }
            if (key != null) keyCache.put(alias, key);
            if (certificate != null) certificateCache.put(alias, certificate);
            if (currentAliases.contains(alias)) {
                wrapping |= key instanceof SecretKey;
                signing |= key instanceof PrivateKey
                        && certificate != null && certificate.getPublicKey() != null;
            }
        }
        Set<SecretCapability> detected = new java.util.LinkedHashSet<>();
        if (wrapping) {
            detected.add(SecretCapability.KEY_WRAP);
            detected.add(SecretCapability.KEY_UNWRAP);
        }
        if (signing) {
            detected.add(SecretCapability.SIGN);
            detected.add(SecretCapability.VERIFY);
        }
        if (detected.isEmpty()) {
            throw new GeneralSecurityException("Configured PKCS#11 aliases expose no supported capability");
        }
        return Set.copyOf(detected);
    }

    private Cipher cipher(int mode, KeyReference reference) throws Exception {
        SecretKey key = secretKey(reference);
        try {
            return init("AESWrap", mode, key);
        } catch (GeneralSecurityException first) {
            return init("AES/KW/NoPadding", mode, key);
        }
    }

    private SecretKey secretKey(KeyReference reference) {
        String alias = alias(reference);
        Key key = keyCache.get(alias);
        if (!(key instanceof SecretKey secret)) {
            throw new SecretCryptoException(
                    "SEC-CRYPTO-001", "PKCS#11 alias is not a secret key: " + alias);
        }
        return secret;
    }

    private byte[] rfc3394Wrap(SecretKey key, byte[] plaintext) throws Exception {
        if (plaintext.length < 16 || plaintext.length % 8 != 0) {
            throw new SecretCryptoException(
                    "SEC-CRYPTO-001", "AES key wrap requires a 16/24/32-byte plaintext");
        }
        int n = plaintext.length / 8;
        byte[] a = RFC3394_IV.clone();
        byte[][] blocks = split(plaintext, n, 0);
        try {
            Cipher aes = Cipher.getInstance("AES/ECB/NoPadding", provider);
            aes.init(Cipher.ENCRYPT_MODE, key);
            for (int j = 0; j < 6; j++) {
                for (int i = 1; i <= n; i++) {
                    byte[] combined = combine(a, blocks[i]);
                    byte[] encrypted = aes.update(combined);
                    Arrays.fill(combined, (byte) 0);
                    try {
                        System.arraycopy(encrypted, 0, a, 0, 8);
                        xorCounter(a, j * n + i);
                        System.arraycopy(encrypted, 8, blocks[i], 0, 8);
                    } finally {
                        Arrays.fill(encrypted, (byte) 0);
                    }
                }
            }
            Arrays.fill(aes.doFinal(), (byte) 0);
            byte[] result = new byte[(n + 1) * 8];
            System.arraycopy(a, 0, result, 0, 8);
            for (int i = 1; i <= n; i++) System.arraycopy(blocks[i], 0, result, i * 8, 8);
            return result;
        } finally {
            Arrays.fill(a, (byte) 0);
            wipe(blocks);
        }
    }

    private byte[] rfc3394Unwrap(SecretKey key, byte[] wrapped) throws Exception {
        if (wrapped.length < 24 || wrapped.length % 8 != 0) {
            throw new SecretCryptoException(
                    "SEC-CRYPTO-002", "AES key unwrap requires a valid RFC 3394 value");
        }
        int n = wrapped.length / 8 - 1;
        byte[] a = Arrays.copyOfRange(wrapped, 0, 8);
        byte[][] blocks = split(wrapped, n, 8);
        try {
            Cipher aes = Cipher.getInstance("AES/ECB/NoPadding", provider);
            aes.init(Cipher.DECRYPT_MODE, key);
            for (int j = 5; j >= 0; j--) {
                for (int i = n; i >= 1; i--) {
                    byte[] adjusted = a.clone();
                    xorCounter(adjusted, j * n + i);
                    byte[] combined = combine(adjusted, blocks[i]);
                    Arrays.fill(adjusted, (byte) 0);
                    byte[] decrypted = aes.update(combined);
                    Arrays.fill(combined, (byte) 0);
                    try {
                        System.arraycopy(decrypted, 0, a, 0, 8);
                        System.arraycopy(decrypted, 8, blocks[i], 0, 8);
                    } finally {
                        Arrays.fill(decrypted, (byte) 0);
                    }
                }
            }
            Arrays.fill(aes.doFinal(), (byte) 0);
            if (!MessageDigest.isEqual(a, RFC3394_IV)) {
                throw new SecretCryptoException(
                        "SEC-CRYPTO-002", "PKCS#11 wrapped key integrity check failed");
            }
            byte[] result = new byte[n * 8];
            for (int i = 1; i <= n; i++) {
                System.arraycopy(blocks[i], 0, result, (i - 1) * 8, 8);
            }
            return result;
        } finally {
            Arrays.fill(a, (byte) 0);
            wipe(blocks);
        }
    }

    private static byte[][] split(byte[] source, int count, int offset) {
        byte[][] blocks = new byte[count + 1][];
        for (int i = 1; i <= count; i++) {
            blocks[i] = Arrays.copyOfRange(source, offset + (i - 1) * 8, offset + i * 8);
        }
        return blocks;
    }

    private static byte[] combine(byte[] left, byte[] right) {
        byte[] result = new byte[16];
        System.arraycopy(left, 0, result, 0, 8);
        System.arraycopy(right, 0, result, 8, 8);
        return result;
    }

    private static void wipe(byte[][] blocks) {
        for (byte[] block : blocks) if (block != null) Arrays.fill(block, (byte) 0);
    }

    private static void xorCounter(byte[] value, int counter) {
        value[7] ^= (byte) counter;
        value[6] ^= (byte) (counter >>> 8);
        value[5] ^= (byte) (counter >>> 16);
        value[4] ^= (byte) (counter >>> 24);
    }

    private PrivateKey privateKey(String alias) {
        Key key = keyCache.get(alias);
        if (!(key instanceof PrivateKey privateKey)) {
            throw new SecretCryptoException(
                    "SEC-SIGN-001", "PKCS#11 alias is not a private key: " + alias);
        }
        return privateKey;
    }

    private PublicKey publicKey(String alias) {
        Certificate certificate = certificateCache.get(alias);
        if (certificate == null || certificate.getPublicKey() == null) {
            throw new SecretCryptoException(
                    "SEC-SIGN-002", "PKCS#11 certificate is missing for alias: " + alias);
        }
        return certificate.getPublicKey();
    }

    private String alias(KeyReference reference) {
        return properties.getKeyBindings().getOrDefault(reference.logicalKey(), reference.logicalKey());
    }

    private Set<String> trustedSigningAliases(String logicalKey) {
        Set<String> trusted = new java.util.LinkedHashSet<>();
        String current = properties.getKeyBindings().get(logicalKey);
        if (current != null && !current.isBlank()) trusted.add(current);
        trusted.addAll(properties.getVerificationKeyBindings().getOrDefault(logicalKey, java.util.List.of()));
        return Set.copyOf(trusted);
    }

    private void requireCapability(SecretCapability capability, String label) {
        if (!capabilities.contains(capability)) {
            throw new SecretCryptoException(
                    "SEC-CAP-001", "PKCS#11 token has no configured " + label + " key");
        }
    }

    private void validateSigningKey(KeyReference reference) {
        if (reference == null || reference.purpose() != KeyPurpose.SIGNING) {
            throw new SecretConfigurationException(
                    "SEC-KEY-003", "Signing requires a KeyReference with SIGNING purpose");
        }
    }

    private Cipher init(String algorithm, int mode, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(algorithm, provider);
        cipher.init(mode, key);
        return cipher;
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("PKCS#11 Secret Provider is closed");
    }

    private static String providerConfigName(
            String providerId,
            Pkcs11SecretProperties properties) throws GeneralSecurityException {
        String canonical = providerId + "\n" + properties.getLibrary() + "\n" + properties.getSlot();
        String suffix = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        return "AtlasSecret" + suffix;
    }

    private static Provider retainProvider(Provider configured) {
        synchronized (PROVIDER_LOCK) {
            ProviderRegistration existing = PROVIDERS.get(configured.getName());
            if (existing != null) {
                existing.references++;
                return existing.provider;
            }
            Provider installed = Security.getProvider(configured.getName());
            boolean owned = installed == null;
            if (owned) {
                if (Security.addProvider(configured) < 0) {
                    throw new SecretConfigurationException(
                            "SEC-BOOT-003", "PKCS#11 provider registration failed");
                }
                installed = configured;
            }
            PROVIDERS.put(configured.getName(), new ProviderRegistration(installed, owned));
            return installed;
        }
    }

    private static void releaseProvider(String providerName) {
        if (providerName == null) return;
        synchronized (PROVIDER_LOCK) {
            ProviderRegistration registration = PROVIDERS.get(providerName);
            if (registration == null || --registration.references > 0) return;
            PROVIDERS.remove(providerName);
            if (!registration.owned) return;
            if (registration.provider instanceof AuthProvider authProvider) {
                try {
                    authProvider.logout();
                } catch (javax.security.auth.login.LoginException ignored) {
                    // Removal remains safe because this component owns the registration.
                }
            }
            Security.removeProvider(providerName);
        }
    }

    private static final class ProviderRegistration {
        private final Provider provider;
        private final boolean owned;
        private int references = 1;

        private ProviderRegistration(Provider provider, boolean owned) {
            this.provider = provider;
            this.owned = owned;
        }
    }
}
