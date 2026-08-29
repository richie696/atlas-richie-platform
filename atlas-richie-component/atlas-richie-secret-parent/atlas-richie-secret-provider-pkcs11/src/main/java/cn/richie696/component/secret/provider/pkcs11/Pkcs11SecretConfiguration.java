/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.pkcs11;

import cn.richie696.component.secret.api.exception.SecretConfigurationException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Set;
import java.util.TreeMap;

final class Pkcs11SecretConfiguration {
    private static final Set<String> SIGNING_ALGORITHMS = Set.of(
            "SHA256withRSA", "SHA384withRSA", "SHA512withRSA",
            "SHA256withECDSA", "SHA384withECDSA", "SHA512withECDSA");

    private Pkcs11SecretConfiguration() {
    }

    static void validate(Pkcs11SecretProperties properties) {
        String library = properties.getLibrary();
        if (library == null || library.isBlank()
                || library.contains("\n") || library.contains("\r")) {
            invalid("PKCS#11 library is required");
        }
        Path libraryPath = Path.of(library);
        if (!libraryPath.isAbsolute() || !Files.isRegularFile(libraryPath)) {
            invalid("PKCS#11 library must be an existing absolute file");
        }
        if (properties.getSlot() != null && properties.getSlot() < 0) {
            invalid("PKCS#11 slot must not be negative");
        }
        if (properties.getTokenLabel() != null && !properties.getTokenLabel().isBlank()) {
            invalid("PKCS#11 token-label is not a portable SunPKCS11 selector; configure slot instead");
        }
        if (properties.getPin().length == 0) {
            invalid("PKCS#11 PIN is required");
        }
        if (properties.getKeyBindings().isEmpty()) {
            invalid("PKCS#11 key-bindings are required");
        }
        if (!SIGNING_ALGORITHMS.contains(properties.getSigningAlgorithm())) {
            invalid("PKCS#11 signing-algorithm is not allowed");
        }
        properties.getKeyBindings().forEach((logical, alias) -> {
            safe(logical, "PKCS#11 logical key");
            safe(alias, "PKCS#11 key alias");
        });
        properties.getVerificationKeyBindings().forEach((logical, aliases) -> {
            safe(logical, "PKCS#11 logical verification key");
            if (aliases.isEmpty()) invalid("PKCS#11 verification key history must not be empty");
            aliases.forEach(alias -> safe(alias, "PKCS#11 historical verification alias"));
        });
    }

    static String hash(String providerId, Pkcs11SecretProperties properties) {
        String canonical = providerId + "\n" + properties.getLibrary() + "\n"
                + properties.getSlot() + "\n" + properties.getSigningAlgorithm() + "\n"
                + new TreeMap<>(properties.getKeyBindings()) + "\n"
                + new TreeMap<>(properties.getVerificationKeyBindings());
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void safe(String value, String label) {
        if (value == null || value.isBlank() || value.contains("..") || value.contains("/")) {
            invalid(label + " is invalid");
        }
    }

    private static void invalid(String message) {
        throw new SecretConfigurationException("SEC-BOOT-003", message);
    }
}
