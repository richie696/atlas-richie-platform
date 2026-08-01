/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package cn.richie696.context.utils.spring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantIdentityAssertionUtilsTest {

    @Test
    void createsAndVerifiesShortLivedAssertion() {
        long expiry = System.currentTimeMillis() + 60_000;
        String assertion = TenantIdentityAssertionUtils.create(1001L, expiry, "secret");

        assertThat(TenantIdentityAssertionUtils.verify(assertion, "secret", System.currentTimeMillis()))
                .isEqualTo(1001L);
        assertThat(TenantIdentityAssertionUtils.verify(assertion, "wrong", System.currentTimeMillis()))
                .isNull();
    }

    @Test
    void rejectsExpiredOrTamperedAssertion() {
        String assertion = TenantIdentityAssertionUtils.create(1001L,
                System.currentTimeMillis() - 1, "secret");
        assertThat(TenantIdentityAssertionUtils.verify(assertion, "secret", System.currentTimeMillis()))
                .isNull();

        String valid = TenantIdentityAssertionUtils.create(1001L,
                System.currentTimeMillis() + 60_000, "secret");
        assertThat(TenantIdentityAssertionUtils.verify(valid.replace("1001", "2001"),
                "secret", System.currentTimeMillis())).isNull();
    }
}
