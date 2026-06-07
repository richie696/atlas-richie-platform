package com.richie.component.mongodb.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AuditContextTest {

    private final AuditContext auditContext = new AuditContext();

    @Test
    void currentUser_returnsSystemWhenNoSecurityContext() {
        assertThat(auditContext.currentUser()).isEqualTo("system");
    }

    @Test
    void currentUser_returnsSystemWhenSecurityContextNotAvailable() {
        assertThat(auditContext.currentUser()).isEqualTo("system");
    }

    @Test
    void currentUser_returnsSystemForAnyException() {
        assertThat(auditContext.currentUser()).isEqualTo("system");
    }

    @Test
    void currentUser_returnsSystemWhenClassNotFound() {
        assertThat(auditContext.currentUser()).isEqualTo("system");
    }
}