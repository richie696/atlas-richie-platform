package com.richie.component.mongodb;

import com.richie.component.mongodb.builder.PageResult;
import com.richie.component.mongodb.support.MongodbIntegrationTest;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@MongodbIntegrationTest
class MongodbIT {

    @Autowired
    private Mongodb mongodb;

    private Class<TestDoc> docClass = TestDoc.class;

    @BeforeEach
    void cleanUp() {
        mongodb.dropCollection(docClass);
    }

    @Test
    void insertAndFindById_shouldWork() {
        TestDoc doc = new TestDoc();
        doc.setName("test");
        doc.setValue(10);
        TestDoc saved = mongodb.insert(doc);
        assertThat(saved.getId()).isNotNull();

        var found = mongodb.findById(docClass, saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("test");
    }

    @Test
    void queryWithEq_shouldFilter() {
        insertTestDocs();
        List<TestDoc> results = mongodb.query(docClass)
                .eq(TestDoc::getName, "alpha")
                .list();
        assertThat(results).hasSize(3);
        assertThat(results.getFirst().getValue()).isEqualTo(10);
    }

    @Test
    void queryWithGt_shouldFilter() {
        insertTestDocs();
        List<TestDoc> results = mongodb.query(docClass)
                .gt(TestDoc::getValue, 20)
                .list();
        assertThat(results).hasSize(3);
        assertThat(results.getFirst().getName()).isEqualTo("alpha");
    }

    @Test
    void queryWithIn_shouldFilter() {
        insertTestDocs();
        List<TestDoc> results = mongodb.query(docClass)
                .in(TestDoc::getName, List.of("alpha", "beta"))
                .list();
        assertThat(results).hasSize(3);
    }

    @Test
    void updateWithSet_shouldModify() {
        insertTestDocs();
        mongodb.update(docClass)
                .eq(TestDoc::getName, "alpha")
                .set(TestDoc::getValue, 99)
                .execute();

        List<TestDoc> results = mongodb.query(docClass)
                .eq(TestDoc::getName, "alpha")
                .list();
        assertThat(results).hasSize(3);
        assertThat(results.getFirst().getValue()).isEqualTo(99);
    }

    @Test
    void updateWithInc_shouldIncrement() {
        insertTestDocs();
        mongodb.update(docClass)
                .eq(TestDoc::getName, "alpha")
                .inc(TestDoc::getValue, 5)
                .execute();

        TestDoc result = mongodb.query(docClass)
                .eq(TestDoc::getName, "alpha")
                .one();
        assertThat(result.getValue()).isEqualTo(15);
    }

    @Test
    void delete_shouldRemove() {
        insertTestDocs();
        long deleted = mongodb.delete(docClass)
                .eq(TestDoc::getName, "alpha")
                .execute();
        assertThat(deleted).isEqualTo(3);

        long count = mongodb.query(docClass).count();
        assertThat(count).isEqualTo(0);
    }

    @Test
    void count_shouldReturnCorrectNumber() {
        insertTestDocs();
        long count = mongodb.query(docClass).count();
        assertThat(count).isEqualTo(3);
    }

    @Test
    void page_shouldReturnPaginatedResults() {
        insertTestDocs();
        PageResult<TestDoc> page = mongodb.query(docClass)
                .page(1, 2)
                .pageResult();
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void exists_shouldReturnBoolean() {
        insertTestDocs();
        boolean exists = mongodb.existsById(docClass, "nonexistent");
        assertThat(exists).isFalse();
    }

    private void insertTestDocs() {
        TestDoc alpha = new TestDoc();
        alpha.setName("alpha");
        alpha.setValue(10);
        mongodb.insert(alpha);

        TestDoc beta = new TestDoc();
        beta.setName("beta");
        beta.setValue(20);
        mongodb.insert(beta);

        TestDoc gamma = new TestDoc();
        gamma.setName("gamma");
        gamma.setValue(30);
        mongodb.insert(gamma);
    }

    @Document
    @Data
    static class TestDoc {
        @org.springframework.data.annotation.Id
        private String id;
        private String name;
        private int value;
    }
}
