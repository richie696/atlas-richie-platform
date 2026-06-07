package com.richie.component.mongodb.annotation;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.assertj.core.api.Assertions.assertThat;

class AnnotationsTest {

    @Test
    void softDelete_defaultFieldName() {
        SoftDelete annotation = SoftDeleteEntity.class.getAnnotation(SoftDelete.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("deleted");
    }

    @Test
    void softDelete_customFieldName() {
        SoftDelete annotation = CustomSoftDeleteEntity.class.getAnnotation(SoftDelete.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("isDeleted");
    }

    @Test
    void tenantScoped_defaultFieldName() {
        TenantScoped annotation = TenantScopedEntity.class.getAnnotation(TenantScoped.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("tenantId");
    }

    @Test
    void tenantScoped_customFieldName() {
        TenantScoped annotation = CustomTenantScopedEntity.class.getAnnotation(TenantScoped.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("organizationId");
    }

    @Test
    void auditFields_markerAnnotation() {
        AuditFields annotation = AuditedEntity.class.getAnnotation(AuditFields.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void expireAfter_requiredSeconds() throws NoSuchFieldException {
        Field field = ExpireAfterEntity.class.getDeclaredField("resetToken");
        ExpireAfter annotation = field.getAnnotation(ExpireAfter.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.seconds()).isEqualTo(3600);
    }

    @SoftDelete
    static class SoftDeleteEntity {
        private Boolean deleted;
    }

    @SoftDelete("isDeleted")
    static class CustomSoftDeleteEntity {
        private Boolean isDeleted;
    }

    @TenantScoped
    static class TenantScopedEntity {
        private String tenantId;
    }

    @TenantScoped("organizationId")
    static class CustomTenantScopedEntity {
        private String organizationId;
    }

    @AuditFields
    static class AuditedEntity {
        private Instant createdAt;
        private String createdBy;
        private Instant updatedAt;
        private String updatedBy;
    }

    static class ExpireAfterEntity {
        @ExpireAfter(seconds = 3600)
        private String resetToken;
    }

    private static class Instant {
    }
}