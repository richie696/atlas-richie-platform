package com.richie.component.mongodb.integration;

import com.richie.component.mongodb.Mongodb;
import com.richie.component.mongodb.builder.PageResult;
import com.richie.component.mongodb.support.MongodbIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MongodbIntegrationTest
class MongodbObservabilityIT {

    @Autowired
    private Mongodb mongodb;

    @Autowired
    private MeterRegistry meterRegistry;

    private Class<TestDoc> docClass = TestDoc.class;

    @BeforeEach
    void cleanUp() {
        mongodb.dropCollection(docClass);
    }

    @Test
    void insert_shouldRecordMetrics() {
        TestDoc doc = new TestDoc();
        doc.setName("test");
        doc.setValue(10);
        mongodb.insert(doc);

        var counter = meterRegistry.find("mongodb.operation.count")
                .tag("operation", "insert")
                .tag("result", "success")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThan(0);
    }

    @Test
    void query_shouldRecordMetricsAndSpan() {
        insertTestDocs();

        List<TestDoc> results = mongodb.query(docClass)
                .eq(TestDoc::getName, "alpha")
                .list();

        assertThat(results).hasSize(2);

        var timer = meterRegistry.find("mongodb.operation.duration")
                .tag("operation", "find")
                .tag("result", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isGreaterThan(0);
    }

    @Test
    void update_shouldRecordMetrics() {
        insertTestDocs();

        mongodb.update(docClass)
                .eq(TestDoc::getName, "alpha")
                .set(TestDoc::getValue, 99)
                .execute();

        var counter = meterRegistry.find("mongodb.operation.count")
                .tag("operation", "update")
                .tag("result", "success")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThan(0);
    }

    @Test
    void delete_shouldRecordMetrics() {
        insertTestDocs();

        mongodb.delete(docClass)
                .eq(TestDoc::getName, "alpha")
                .execute();

        var counter = meterRegistry.find("mongodb.operation.count")
                .tag("operation", "delete")
                .tag("result", "success")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThan(0);
    }

    @Test
    void count_shouldRecordMetrics() {
        insertTestDocs();

        mongodb.query(docClass).count();

        var counter = meterRegistry.find("mongodb.operation.count")
                .tag("operation", "count")
                .tag("result", "success")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThan(0);
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
    }

    @org.springframework.data.mongodb.core.mapping.Document
    static class TestDoc {
        @org.springframework.data.annotation.Id
        private String id;
        private String name;
        private int value;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }
}
