package com.richie.component.mongodb;

import com.richie.component.mongodb.annotation.AuditFields;
import com.richie.component.mongodb.annotation.ExpireAfter;
import com.richie.component.mongodb.annotation.SoftDelete;
import com.richie.component.mongodb.annotation.TenantScoped;
import com.richie.component.mongodb.builder.IndexBuilder;
import com.richie.component.mongodb.core.TenantContext;
import com.richie.component.mongodb.support.MongodbIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@MongodbIntegrationTest
class MongodbAnnotationsIT {

    @Autowired
    private Mongodb mongodb;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private IndexBuilder indexBuilder;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        mongodb.dropCollection(SoftDeleteDoc.class);
        mongodb.dropCollection(TenantDoc.class);
        mongodb.dropCollection(AuditDoc.class);
        mongodb.dropCollection(CombinedDoc.class);
    }

    @Test
    void softDelete_insertAndQuery_excludesDeletedByDefault() {
        SoftDeleteDoc doc = new SoftDeleteDoc();
        doc.setName("test");
        mongodb.insert(doc);

        List<SoftDeleteDoc> all = mongodb.query(SoftDeleteDoc.class).list();
        assertThat(all).hasSize(1);

        mongodb.delete(SoftDeleteDoc.class).eq(SoftDeleteDoc::getId, doc.getId()).execute();

        List<SoftDeleteDoc> afterDelete = mongodb.query(SoftDeleteDoc.class).list();
        assertThat(afterDelete).isEmpty();

        List<SoftDeleteDoc> includingDeleted = mongodb.query(SoftDeleteDoc.class).ignoreSoftDelete().list();
        assertThat(includingDeleted).hasSize(1);
    }

    @Test
    void softDelete_forceDelete_physicallyRemoves() {
        SoftDeleteDoc doc = new SoftDeleteDoc();
        doc.setName("test");
        mongodb.insert(doc);

        mongodb.delete(SoftDeleteDoc.class).eq(SoftDeleteDoc::getId, doc.getId()).force().execute();

        List<SoftDeleteDoc> all = mongodb.query(SoftDeleteDoc.class).ignoreSoftDelete().list();
        assertThat(all).isEmpty();
    }

    @Test
    void tenantScoped_insertAndQuery_autoFiltersByTenant() {
        TenantContext.set("tenant-A");
        TenantDoc doc = new TenantDoc();
        doc.setName("test");
        mongodb.insert(doc);

        TenantContext.set("tenant-B");
        TenantDoc doc2 = new TenantDoc();
        doc2.setName("test2");
        mongodb.insert(doc2);

        TenantContext.set("tenant-A");
        List<TenantDoc> results = mongodb.query(TenantDoc.class).list();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("test");
        assertThat(results.get(0).getTenantId()).isEqualTo("tenant-A");
    }

    @Test
    void tenantScoped_bypassTenant_includesAll() {
        TenantContext.set("tenant-A");
        TenantDoc doc = new TenantDoc();
        doc.setName("test");
        mongodb.insert(doc);

        TenantContext.set("tenant-B");
        TenantDoc doc2 = new TenantDoc();
        doc2.setName("test2");
        mongodb.insert(doc2);

        TenantContext.set("tenant-A");
        List<TenantDoc> results = mongodb.query(TenantDoc.class).bypassTenant().list();
        assertThat(results).hasSize(2);
    }

    @Test
    void auditFields_insert_fillsAllFourFields() {
        AuditDoc doc = new AuditDoc();
        doc.setName("test");
        AuditDoc saved = mongodb.insert(doc);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("system");
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedBy()).isEqualTo("system");
    }

    @Test
    void auditFields_update_fillsUpdatedFields() {
        AuditDoc doc = new AuditDoc();
        doc.setName("test");
        AuditDoc saved = mongodb.insert(doc);
        Instant originalUpdatedAt = saved.getUpdatedAt();

        mongodb.update(AuditDoc.class)
                .eq(AuditDoc::getId, saved.getId())
                .set(AuditDoc::getName, "updated")
                .execute();

        AuditDoc updated = mongodb.findById(AuditDoc.class, saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("updated");
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void expireAfter_ensureIndex_createsTTLIndex() {
        indexBuilder.ensureIndexes(ExpireAfterDoc.class);

        List<IndexInfo> indexes = mongoTemplate.indexOps(ExpireAfterDoc.class).getIndexInfo();
        assertThat(indexes).isNotEmpty();
    }

    @Test
    void combinedAnnotations_allFourWorkTogether() {
        TenantContext.set("tenant-1");
        CombinedDoc doc = new CombinedDoc();
        doc.setName("combined");
        CombinedDoc saved = mongodb.insert(doc);

        assertThat(saved.getDeleted()).isFalse();
        assertThat(saved.getTenantId()).isEqualTo("tenant-1");
        assertThat(saved.getCreatedAt()).isNotNull();

        mongodb.delete(CombinedDoc.class).eq(CombinedDoc::getId, saved.getId()).execute();

        List<CombinedDoc> afterDelete = mongodb.query(CombinedDoc.class).list();
        assertThat(afterDelete).isEmpty();

        List<CombinedDoc> withBypass = mongodb.query(CombinedDoc.class).bypassTenant().ignoreSoftDelete().list();
        assertThat(withBypass).hasSize(1);
    }

    @SoftDelete
    private static class SoftDeleteDoc {
        @org.springframework.data.annotation.Id
        private String id;
        private Boolean deleted = false;
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Boolean getDeleted() { return deleted; }
        public void setDeleted(Boolean deleted) { this.deleted = deleted; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @TenantScoped
    private static class TenantDoc {
        @org.springframework.data.annotation.Id
        private String id;
        private String tenantId;
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @AuditFields
    private static class AuditDoc {
        @org.springframework.data.annotation.Id
        private String id;
        private String name;
        private Instant createdAt;
        private String createdBy;
        private Instant updatedAt;
        private String updatedBy;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    }

    private static class ExpireAfterDoc {
        @org.springframework.data.annotation.Id
        private String id;
        @ExpireAfter(seconds = 3600)
        private Instant expiresAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Instant getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    }

    @SoftDelete
    @TenantScoped
    @AuditFields
    private static class CombinedDoc {
        @org.springframework.data.annotation.Id
        private String id;
        private Boolean deleted = false;
        private String tenantId;
        private String name;
        private Instant createdAt;
        private String createdBy;
        private Instant updatedAt;
        private String updatedBy;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Boolean getDeleted() { return deleted; }
        public void setDeleted(Boolean deleted) { this.deleted = deleted; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    }
}