/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.mfa.management.manager;

import cn.richie696.component.mfa.core.entity.MfaUserInfo;
import cn.richie696.component.mfa.management.mapper.MfaUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Resumable migration from the legacy tenant/user reference to Secret.
 * <p>Only a reference is persisted; plaintext is never returned in the report or logs.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "platform.component.secret", name = "enabled", havingValue = "true")
public class MfaSecretMigrationService {
    private final MfaUserMapper userMapper;
    private final SecretKeyManager secretKeyManager;

    /**
     * Migrates at most {@code batchSize} records. Re-run until {@code remaining == 0}.
     * Dry-run performs reads and validation but does not write Secret or database state.
     */
    @Transactional
    public MigrationResult migrateBatch(int batchSize, boolean dryRun) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        List<MfaUserInfo> candidates = userMapper.selectSecretMigrationCandidates(batchSize);
        int migrated = 0;
        int skipped = 0;
        List<String> failures = new ArrayList<>();
        for (MfaUserInfo user : candidates) {
            if (user.getSecretReference() != null && !user.getSecretReference().isBlank()) {
                skipped++;
                continue;
            }
            String legacySecret = null;
            String newReference = null;
            try {
                legacySecret = secretKeyManager.retrieveSecret(user.getTenantId(), user.getUserId());
                if (legacySecret == null || legacySecret.isBlank()) {
                    throw new IllegalStateException("legacy Secret is empty");
                }
                if (!dryRun) {
                    newReference = secretKeyManager.storeSecret(
                            user.getTenantId(), user.getUserId(), legacySecret);
                    user.setSecretReference(newReference);
                    userMapper.updateById(user);
                }
                migrated++;
            } catch (RuntimeException exception) {
                if (!dryRun && newReference != null) {
                    try {
                        secretKeyManager.deleteSecret(newReference);
                    } catch (RuntimeException cleanupFailure) {
                        log.error("MFA Secret migration compensation failed for recordId={}", user.getId());
                    }
                }
                // Deliberately do not include user IDs or provider messages: they can contain Secret paths.
                failures.add("record-" + user.getId());
                log.warn("MFA Secret migration failed for recordId={}, dryRun={}", user.getId(), dryRun);
            } finally {
                legacySecret = null;
                newReference = null;
            }
        }
        return new MigrationResult(candidates.size(), migrated, skipped, failures,
                candidates.size() == batchSize && failures.isEmpty());
    }

    public record MigrationResult(int scanned, int migrated, int skipped,
                                  List<String> failures, boolean mayHaveMore) {
        public MigrationResult {
            failures = List.copyOf(failures);
        }
    }
}
