package com.richie.component.mongodb.service.impl;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.transaction.TransactionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongodbServiceImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private MongoTransactionManager transactionManager;

    @InjectMocks
    private MongodbServiceImpl service;

    @Test
    void executeInTransaction_withoutManager_throwsUnsupported() {
        MongodbServiceImpl noTx = new MongodbServiceImpl(mongoTemplate, null);
        assertThatThrownBy(() -> noTx.executeInTransaction(() -> "x"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("事务管理器");
    }

    @Test
    void executeInTransaction_commitsOnSuccess() {
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(status);
        assertThat(service.executeInTransaction(() -> "ok")).isEqualTo("ok");
        verify(transactionManager).commit(status);
    }

    @Test
    void executeInTransaction_rollsBackOnFailure() {
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(status);
        assertThatThrownBy(() -> service.executeInTransaction(() -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        verify(transactionManager).rollback(status);
    }

    @Test
    void insert_delegatesToTemplate() {
        when(mongoTemplate.insert("doc", "col")).thenReturn("doc");
        assertThat(service.insert("col", "doc")).isEqualTo("doc");
    }

    @Test
    void insertMany_delegatesToTemplate() {
        List<String> docs = List.of("a", "b");
        when(mongoTemplate.insert(docs, "col")).thenReturn(docs);
        assertThat(service.insertMany("col", docs)).containsExactly("a", "b");
    }

    @Test
    void findById_delegatesToTemplate() {
        when(mongoTemplate.findById("1", String.class, "col")).thenReturn("ok");
        assertThat(service.findById("col", "1", String.class)).isEqualTo("ok");
    }

    @Test
    void find_delegatesToTemplate() {
        Query query = new Query();
        when(mongoTemplate.find(query, String.class, "col")).thenReturn(List.of("a"));
        assertThat(service.find(query, "col", String.class)).containsExactly("a");
    }

    @Test
    void findOne_delegatesToTemplate() {
        Query query = new Query();
        when(mongoTemplate.findOne(query, String.class, "col")).thenReturn("one");
        assertThat(service.findOne(query, "col", String.class)).isEqualTo("one");
    }

    @Test
    void updateOne_delegatesToTemplate() {
        Query query = new Query();
        Update update = new Update().set("k", "v");
        when(mongoTemplate.findAndModify(any(), eq(update), any(), eq(String.class), eq("col")))
                .thenReturn("updated");
        assertThat(service.updateOne(query, update, "col", String.class)).isEqualTo("updated");
    }

    @Test
    void updateMany_executesBulkAndFinds() {
        Query query = new Query();
        Update update = new Update().set("k", "v");
        BulkOperations bulkOps = mock(BulkOperations.class);
        when(mongoTemplate.bulkOps(BulkMode.UNORDERED, String.class, "col")).thenReturn(bulkOps);
        when(mongoTemplate.find(query, String.class, "col")).thenReturn(List.of("a"));
        assertThat(service.updateMany(query, update, "col", String.class)).containsExactly("a");
        verify(bulkOps).updateMulti(query, update);
        verify(bulkOps).execute();
    }

    @Test
    void delete_delegatesToTemplate() {
        Query query = new Query();
        DeleteResult result = mock(DeleteResult.class);
        when(result.getDeletedCount()).thenReturn(2L);
        when(mongoTemplate.remove(query, String.class, "col")).thenReturn(result);
        assertThat(service.delete(query, "col", String.class)).isEqualTo(2);
    }

    @Test
    void count_delegatesToTemplate() {
        when(mongoTemplate.count(any(Query.class), eq(String.class), eq("col"))).thenReturn(3L);
        assertThat(service.count(new Query(), "col", String.class)).isEqualTo(3);
    }

    @Test
    void aggregate_delegatesToTemplate() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("_id").exists(true)));
        AggregationResults<String> results = mock(AggregationResults.class);
        when(mongoTemplate.aggregate(aggregation, "col", String.class)).thenReturn(results);
        assertThat(service.aggregate(aggregation, "col", String.class)).isSameAs(results);
    }

    @Test
    void findWithProjection_includesFields() {
        Query query = new Query();
        when(mongoTemplate.find(query, String.class, "col")).thenReturn(List.of("x"));
        assertThat(service.findWithProjection(query, "col", String.class, List.of("name")))
                .containsExactly("x");
    }

    @Test
    void findWithSort_appliesSort() {
        Query query = new Query();
        when(mongoTemplate.find(query, String.class, "col")).thenReturn(List.of("x"));
        assertThat(service.findWithSort(query, "col", String.class, Sort.by("name")))
                .containsExactly("x");
    }

    @Test
    void findWithPagination_appliesPageable() {
        Query query = new Query();
        when(mongoTemplate.find(query, String.class, "col")).thenReturn(List.of("x"));
        assertThat(service.findWithPagination(query, "col", String.class, PageRequest.of(0, 10)))
                .containsExactly("x");
    }

    @Test
    void createIndex_delegatesToIndexOps() {
        IndexOperations indexOps = mock(IndexOperations.class);
        when(mongoTemplate.indexOps("col")).thenReturn(indexOps);
        when(indexOps.createIndex(any())).thenReturn("idx_name");
        assertThat(service.createIndex("col", new org.springframework.data.mongodb.core.index.Index()))
                .isEqualTo("idx_name");
    }

    @Test
    void dropIndex_delegatesToIndexOps() {
        IndexOperations indexOps = mock(IndexOperations.class);
        when(mongoTemplate.indexOps("col")).thenReturn(indexOps);
        service.dropIndex("col", "idx_name");
        verify(indexOps).dropIndex("idx_name");
    }

    @Test
    void listIndexes_mapsIndexInfo() {
        IndexOperations indexOps = mock(IndexOperations.class);
        when(mongoTemplate.indexOps("col")).thenReturn(indexOps);
        when(indexOps.getIndexInfo()).thenReturn(List.of());
        assertThat(service.listIndexes("col")).isEmpty();
    }

    @Test
    void createCollection_delegatesToDatabase() {
        MongoDatabase db = mock(MongoDatabase.class);
        when(mongoTemplate.getDb()).thenReturn(db);
        service.createCollection("col");
        verify(db).createCollection("col");
    }

    @Test
    void dropCollection_delegatesToDatabase() {
        MongoDatabase db = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(mongoTemplate.getDb()).thenReturn(db);
        when(db.getCollection("col")).thenReturn(collection);
        service.dropCollection("col");
        verify(collection).drop();
    }

    @Test
    void listCollections_readsCursor() {
        MongoDatabase db = mock(MongoDatabase.class);
        com.mongodb.client.ListCollectionNamesIterable iterable =
                mock(com.mongodb.client.ListCollectionNamesIterable.class);
        @SuppressWarnings("unchecked")
        MongoCursor<String> cursor = mock(MongoCursor.class);
        when(mongoTemplate.getDb()).thenReturn(db);
        when(db.listCollectionNames()).thenReturn(iterable);
        when(iterable.iterator()).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("col");
        assertThat(service.listCollections()).containsExactly("col");
    }

    @Test
    void collectionExists_delegatesToTemplate() {
        when(mongoTemplate.collectionExists("col")).thenReturn(true);
        assertThat(service.collectionExists("col")).isTrue();
    }

    @Test
    void bulkWrite_delegatesToCollection() {
        @SuppressWarnings("unchecked")
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(mongoTemplate.getCollection("col")).thenReturn(collection);
        List<WriteModel<Document>> ops = List.of();
        service.bulkWrite("col", ops);
        verify(collection).bulkWrite(ops);
    }

    @Test
    void exists_delegatesToTemplate() {
        Query query = new Query();
        when(mongoTemplate.exists(query, String.class, "col")).thenReturn(true);
        assertThat(service.exists(query, "col", String.class)).isTrue();
    }

    @Test
    void distinct_delegatesToTemplate() {
        Query query = new Query();
        when(mongoTemplate.findDistinct(query, "name", "col", String.class)).thenReturn(List.of("a"));
        assertThat(service.distinct("col", "name", query, String.class)).containsExactly("a");
    }

    @Test
    void inc_delegatesUpdate() {
        Query query = new Query();
        service.inc("col", query, "value", 1);
        verify(mongoTemplate).updateMulti(eq(query), any(Update.class), eq("col"));
    }

    @Test
    void unset_delegatesUpdate() {
        Query query = new Query();
        service.unset("col", query, "value");
        verify(mongoTemplate).updateMulti(eq(query), any(Update.class), eq("col"));
    }

    @Test
    void rename_delegatesUpdate() {
        Query query = new Query();
        service.rename("col", query, "old", "new");
        verify(mongoTemplate).updateMulti(eq(query), any(Update.class), eq("col"));
    }

    @Test
    void push_delegatesUpdate() {
        Query query = new Query();
        service.push("col", query, "tags", "x");
        verify(mongoTemplate).updateMulti(eq(query), any(Update.class), eq("col"));
    }

    @Test
    void pull_delegatesUpdate() {
        Query query = new Query();
        service.pull("col", query, "tags", "x");
        verify(mongoTemplate).updateMulti(eq(query), any(Update.class), eq("col"));
    }

    @Test
    void addToSet_delegatesUpdate() {
        Query query = new Query();
        service.addToSet("col", query, "tags", "x");
        verify(mongoTemplate).updateMulti(eq(query), any(Update.class), eq("col"));
    }

}
