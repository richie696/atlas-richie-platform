package com.richie.component.mongodb.service.impl;

import com.richie.component.mongodb.service.MongodbService;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * <b>通用MongoDB操作服务实现</b>
 * <p>
 * 封装了基于Spring Data MongoDB的CRUD、聚合、事务、索引、集合、批量、distinct、exists、变更流、GridFS等常用与高级操作，
 * 适用于业务层直接调用，便于平台统一封装和扩展。
 * <ul>
 *   <li>依赖：MongoTemplate、PlatformTransactionManager（推荐用MongoTransactionManager，需副本集/分片集群）</li>
 *   <li>异常：所有操作发生异常时抛出RuntimeException，建议业务层统一捕获处理</li>
 *   <li>事务：需在配置类声明MongoTransactionManager Bean，详见Spring官方文档</li>
 *   <li>返回值：部分方法可能返回null，已用@Nullable标注，调用方需判空</li>
 *   <li>线程安全：本类为无状态单例，线程安全</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025/07/04
 */
@Service
@RequiredArgsConstructor
public class MongodbServiceImpl implements MongodbService {

    /**
     * Spring Data MongoDB操作模板
     */
    private final MongoTemplate mongoTemplate;
    /**
     * 事务管理器，推荐用MongoTransactionManager，需副本集/分片集群
     */
    private final MongoTransactionManager transactionManager;

    // ====== CRUD 基础操作 ======

    /**
     * 插入单条文档
     *
     * @param collection 集合名
     * @param doc        文档对象
     * @return 插入后的文档对象
     * @throws RuntimeException 插入失败
     */
    @Override
    public <T> T insert(String collection, T doc) {
        return mongoTemplate.insert(doc, collection);
    }

    /**
     * 批量插入文档
     *
     * @param collection 集合名
     * @param docs       文档列表
     * @return 插入后的文档列表
     * @throws RuntimeException 插入失败
     */
    @Override
    public <T> List<T> insertMany(String collection, List<T> docs) {
        return (List<T>) mongoTemplate.insert(docs, collection);
    }

    /**
     * 按ID查找文档
     *
     * @param collection 集合名
     * @param id         主键ID
     * @param clazz      文档类型
     * @return 查到返回文档对象，否则null
     */
    @Override
    @Nullable
    public <T> T findById(String collection, Object id, Class<T> clazz) {
        return mongoTemplate.findById(id, clazz, collection);
    }

    /**
     * 条件查找文档列表
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @return 匹配文档列表，可能为空
     */
    @Override
    public <T> List<T> find(Query query, String collection, Class<T> clazz) {
        return mongoTemplate.find(query, clazz, collection);
    }

    /**
     * 条件查找单条文档
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @return 匹配文档，可能为null
     */
    @Override
    @Nullable
    public <T> T findOne(Query query, String collection, Class<T> clazz) {
        return mongoTemplate.findOne(query, clazz, collection);
    }

    /**
     * 条件更新单条文档，返回更新后的文档
     *
     * @param query      查询条件
     * @param update     更新内容
     * @param collection 集合名
     * @param clazz      文档类型
     * @return 更新后的文档，可能为null
     */
    @Override
    @Nullable
    public <T> T updateOne(Query query, Update update, String collection, Class<T> clazz) {
        return mongoTemplate.findAndModify(query, update, new FindAndModifyOptions().returnNew(true), clazz, collection);
    }

    /**
     * 条件批量更新文档，返回所有匹配文档
     *
     * @param query      查询条件
     * @param update     更新内容
     * @param collection 集合名
     * @param clazz      文档类型
     * @return 更新后的文档列表
     */
    @Override
    public <T> List<T> updateMany(Query query, Update update, String collection, Class<T> clazz) {
        BulkOperations ops = mongoTemplate.bulkOps(BulkMode.UNORDERED, clazz, collection);
        ops.updateMulti(query, update);
        ops.execute();
        return mongoTemplate.find(query, clazz, collection);
    }

    /**
     * 条件删除文档
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @return 删除条数
     */
    @Override
    public long delete(Query query, String collection, Class<?> clazz) {
        return mongoTemplate.remove(query, clazz, collection).getDeletedCount();
    }

    /**
     * 条件计数
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @return 匹配文档数
     */
    @Override
    public long count(Query query, String collection, Class<?> clazz) {
        return mongoTemplate.count(query, clazz, collection);
    }

    // ====== 聚合操作 ======

    /**
     * 聚合查询
     *
     * @param aggregation 聚合管道
     * @param collection  集合名
     * @param outputType  输出类型
     * @return 聚合结果
     */
    @Override
    public <T> AggregationResults<T> aggregate(Aggregation aggregation, String collection, Class<T> outputType) {
        return mongoTemplate.aggregate(aggregation, collection, outputType);
    }

    // ====== 事务操作 ======

    /**
     * 在MongoDB事务中执行操作（需副本集/分片集群）
     *
     * @param action 事务内操作
     * @param <T>    返回类型
     * @return 事务执行结果
     * @throws UnsupportedOperationException 未配置事务管理器
     */
    @Override
    public <T> T executeInTransaction(Supplier<T> action) {
        if (transactionManager == null) {
            throw new UnsupportedOperationException("MongoDB事务管理器未配置");
        }
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            T result = action.get();
            transactionManager.commit(status);
            return result;
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
    }

    // ====== 高级操作示例 ======

    /**
     * 投影查询（只返回部分字段）
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @param fields     需返回的字段名
     * @return 匹配文档列表
     */
    @Override
    public <T> List<T> findWithProjection(Query query, String collection, Class<T> clazz, List<String> fields) {
        fields.forEach(f -> query.fields().include(f)); // 指定返回字段
        return mongoTemplate.find(query, clazz, collection);
    }

    /**
     * 排序查询
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @param sort       排序对象
     * @return 匹配文档列表
     */
    @Override
    public <T> List<T> findWithSort(Query query, String collection, Class<T> clazz, Sort sort) {
        query.with(sort); // 应用排序
        return mongoTemplate.find(query, clazz, collection);
    }

    /**
     * 分页查询
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @param pageable   分页对象
     * @return 匹配文档列表
     */
    @Override
    public <T> List<T> findWithPagination(Query query, String collection, Class<T> clazz, Pageable pageable) {
        query.with(pageable); // 应用分页
        return mongoTemplate.find(query, clazz, collection);
    }

    /**
     * 执行原生Mongo命令
     *
     * @param command 命令文档
     * @return 命令结果
     */
    @Override
    public List<Document> runCommand(Document command) {
        return mongoTemplate.getDb().runCommand(command).get("cursor", Document.class).getList("firstBatch", Document.class);
    }

    // ====== 索引管理（Spring风格） ======

    /**
     * 创建索引
     *
     * @param collection 集合名
     * @param index      索引定义
     * @return 索引名
     */
    @Override
    public String createIndex(String collection, Index index) {
        return mongoTemplate.indexOps(collection).createIndex(index);
    }

    /**
     * 删除索引
     *
     * @param collection 集合名
     * @param indexName  索引名
     */
    @Override
    public void dropIndex(String collection, String indexName) {
        mongoTemplate.indexOps(collection).dropIndex(indexName);
    }

    /**
     * 查询集合所有索引信息
     *
     * @param collection 集合名
     * @return 索引信息列表
     */
    @Override
    public List<Document> listIndexes(String collection) {
        return mongoTemplate.indexOps(collection).getIndexInfo().stream().map(i -> new Document(i.getName(), i.getIndexFields())).collect(Collectors.toList());
    }

    // ====== 集合管理 ======

    /**
     * 创建集合
     *
     * @param name 集合名
     */
    @Override
    public void createCollection(String name) {
        mongoTemplate.getDb().createCollection(name);
    }

    /**
     * 删除集合
     *
     * @param name 集合名
     */
    @Override
    public void dropCollection(String name) {
        mongoTemplate.getDb().getCollection(name).drop();
    }

    /**
     * 查询所有集合名
     *
     * @return 集合名列表
     */
    @Override
    public List<String> listCollections() {
        List<String> names = new ArrayList<>();
        try (MongoCursor<String> cursor = mongoTemplate.getDb().listCollectionNames().iterator()) {
            while (cursor.hasNext()) {
                names.add(cursor.next());
            }
        }
        return names;
    }

    /**
     * 判断集合是否存在
     *
     * @param name 集合名
     * @return 是否存在
     */
    @Override
    public boolean collectionExists(String name) {
        return mongoTemplate.collectionExists(name);
    }

    // ====== 批量操作 ======

    /**
     * 批量写入（插入/更新/删除等）
     *
     * @param collection 集合名
     * @param operations 批量操作列表
     */
    @Override
    public void bulkWrite(String collection, List<WriteModel<Document>> operations) {
        MongoCollection<Document> coll = mongoTemplate.getCollection(collection);
        coll.bulkWrite(operations);
    }

    // ====== exists 判断 ======

    /**
     * 判断文档是否存在
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @return 是否存在
     */
    @Override
    public boolean exists(Query query, String collection, Class<?> clazz) {
        return mongoTemplate.exists(query, clazz, collection);
    }

    // ====== distinct 去重 ======

    /**
     * distinct去重查询
     *
     * @param collection 集合名
     * @param field      字段名
     * @param query      查询条件
     * @param resultType 结果类型
     * @return 去重结果列表
     */
    @Override
    public <T> List<T> distinct(String collection, String field, Query query, Class<T> resultType) {
        return mongoTemplate.findDistinct(query, field, collection, resultType);
    }

    // ====== 字段级操作 ======

    /**
     * 字段自增
     *
     * @param collection 集合名
     * @param query      查询条件
     * @param field      字段名
     * @param value      增量
     */
    @Override
    public void inc(String collection, Query query, String field, Number value) {
        mongoTemplate.updateMulti(query, new Update().inc(field, value), collection);
    }

    /**
     * 字段unset
     *
     * @param collection 集合名
     * @param query      查询条件
     * @param field      字段名
     */
    @Override
    public void unset(String collection, Query query, String field) {
        mongoTemplate.updateMulti(query, new Update().unset(field), collection);
    }

    /**
     * 字段重命名
     *
     * @param collection 集合名
     * @param query      查询条件
     * @param oldField   旧字段名
     * @param newField   新字段名
     */
    @Override
    public void rename(String collection, Query query, String oldField, String newField) {
        mongoTemplate.updateMulti(query, new Update().rename(oldField, newField), collection);
    }

    /**
     * 数组字段push
     *
     * @param collection 集合名
     * @param query      查询条件
     * @param field      字段名
     * @param value      元素值
     */
    @Override
    public void push(String collection, Query query, String field, Object value) {
        mongoTemplate.updateMulti(query, new Update().push(field, value), collection);
    }

    /**
     * 数组字段pull
     *
     * @param collection 集合名
     * @param query      查询条件
     * @param field      字段名
     * @param value      元素值
     */
    @Override
    public void pull(String collection, Query query, String field, Object value) {
        mongoTemplate.updateMulti(query, new Update().pull(field, value), collection);
    }

    /**
     * 数组字段addToSet
     *
     * @param collection 集合名
     * @param query      查询条件
     * @param field      字段名
     * @param value      元素值
     */
    @Override
    public void addToSet(String collection, Query query, String field, Object value) {
        mongoTemplate.updateMulti(query, new Update().addToSet(field, value), collection);
    }

    // ====== 变更流（ChangeStream） ======

    /**
     * 监听集合变更流（ChangeStream）
     *
     * @param collection 集合名
     * @param listener   变更事件回调
     */
    @Override
    public void watchChangeStream(String collection, Consumer<ChangeStreamDocument<Document>> listener) {
        MongoCollection<Document> coll = mongoTemplate.getCollection(collection);
        coll.watch().fullDocument(FullDocument.UPDATE_LOOKUP).forEach(listener);
    }

    // ====== GridFS 文件存储 ======

    /**
     * 上传文件到GridFS
     *
     * @param in       输入流
     * @param filename 文件名
     * @param bucket   bucket名
     * @return 文件ObjectId
     */
    @Override
    public ObjectId storeFileToGridFS(InputStream in, String filename, String bucket) {
        GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb(), bucket);
        return gridFSBucket.uploadFromStream(filename, in);
    }

    /**
     * 从GridFS下载文件
     *
     * @param fileId 文件ID
     * @param bucket bucket名
     * @param out    输出流
     */
    @Override
    public void getFileFromGridFS(String fileId, String bucket, OutputStream out) {
        GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb(), bucket);
        gridFSBucket.downloadToStream(new ObjectId(fileId), out);
    }

    /**
     * 删除GridFS文件
     *
     * @param fileId 文件ID
     * @param bucket bucket名
     */
    @Override
    public void deleteFileFromGridFS(String fileId, String bucket) {
        GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb(), bucket);
        gridFSBucket.delete(new ObjectId(fileId));
    }

}
