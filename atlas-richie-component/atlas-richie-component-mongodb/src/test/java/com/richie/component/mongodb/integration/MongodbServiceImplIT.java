package com.richie.component.mongodb.integration;

import com.richie.component.mongodb.service.MongodbService;
import com.richie.component.mongodb.support.MongodbIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MongodbIntegrationTest
class MongodbServiceImplIT {

    private static final String COLLECTION = "it_mongodb_service";

    @Autowired
    private MongodbService mongodbService;

    @BeforeEach
    void cleanCollection() {
        if (mongodbService.collectionExists(COLLECTION)) {
            mongodbService.dropCollection(COLLECTION);
        }
        mongodbService.createCollection(COLLECTION);
    }

    @Test
    void crudAndQueryOperations_shouldWork() {
        ItDoc doc = new ItDoc("alpha", 10);
        ItDoc inserted = mongodbService.insert(COLLECTION, doc);
        assertThat(inserted.getId()).isNotNull();

        ItDoc found = mongodbService.findById(COLLECTION, inserted.getId(), ItDoc.class);
        assertThat(found.getName()).isEqualTo("alpha");

        Query byName = Query.query(Criteria.where("name").is("alpha"));
        assertThat(mongodbService.count(byName, COLLECTION, ItDoc.class)).isEqualTo(1);
        assertThat(mongodbService.exists(byName, COLLECTION, ItDoc.class)).isTrue();

        mongodbService.updateOne(byName, new Update().set("value", 20), COLLECTION, ItDoc.class);
        assertThat(mongodbService.findOne(byName, COLLECTION, ItDoc.class).getValue()).isEqualTo(20);

        List<ItDoc> page = mongodbService.findWithPagination(
                new Query(), COLLECTION, ItDoc.class, PageRequest.of(0, 10));
        assertThat(page).hasSize(1);

        List<ItDoc> sorted = mongodbService.findWithSort(
                new Query(), COLLECTION, ItDoc.class, Sort.by("name"));
        assertThat(sorted).extracting(ItDoc::getName).containsExactly("alpha");

        assertThat(mongodbService.distinct(COLLECTION, "name", new Query(), String.class))
                .containsExactly("alpha");

        mongodbService.inc(COLLECTION, byName, "value", 5);
        assertThat(mongodbService.findOne(byName, COLLECTION, ItDoc.class).getValue()).isEqualTo(25);

        assertThat(mongodbService.delete(byName, COLLECTION, ItDoc.class)).isEqualTo(1);
        assertThat(mongodbService.collectionExists(COLLECTION)).isTrue();
    }

    @Test
    void indexAndCollectionManagement_shouldWork() {
        String indexName = mongodbService.createIndex(COLLECTION, new Index().on("name", Sort.Direction.ASC));
        assertThat(indexName).isNotBlank();
        assertThat(mongodbService.listIndexes(COLLECTION)).isNotEmpty();
        mongodbService.dropIndex(COLLECTION, indexName);

        assertThat(mongodbService.listCollections()).contains(COLLECTION);
    }

    @Test
    void insertMany_shouldPersistAll() {
        List<ItDoc> docs = List.of(new ItDoc("a", 1), new ItDoc("b", 2));
        List<ItDoc> saved = mongodbService.insertMany(COLLECTION, docs);
        assertThat(saved).hasSize(2);
        assertThat(mongodbService.count(new Query(), COLLECTION, ItDoc.class)).isEqualTo(2);
    }

    @org.springframework.data.mongodb.core.mapping.Document
    static class ItDoc {
        @org.springframework.data.annotation.Id
        private String id;
        private String name;
        private int value;

        ItDoc() {
        }

        ItDoc(String name, int value) {
            this.name = name;
            this.value = value;
        }

        String getId() {
            return id;
        }

        void setId(String id) {
            this.id = id;
        }

        String getName() {
            return name;
        }

        int getValue() {
            return value;
        }
    }
}
