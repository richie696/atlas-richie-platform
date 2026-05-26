package com.richie.component.mongodb.service;

import com.mongodb.client.model.WriteModel;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import jakarta.annotation.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * <b>通用MongoDB操作服务接口</b>
 * <p>
 * 封装了基于Spring Data MongoDB的CRUD、聚合、事务、索引、集合、批量、distinct、exists、变更流、GridFS等常用与高级操作，
 * 适用于业务层直接调用，便于平台统一封装和扩展。
 * <ul>
 *   <li>依赖：MongoTemplate、MongoTransactionManager）</li>
 *   <li>异常：所有操作发生异常时抛出RuntimeException，建议业务层统一捕获处理</li>
 *   <li>事务：需在配置类声明MongoTransactionManager Bean，详见Spring官方文档</li>
 *   <li>返回值：部分方法可能返回null，已用@Nullable标注，调用方需判空</li>
 *   <li>线程安全：本接口建议由无状态单例实现，线程安全</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025/07/04
 */
public interface MongodbService {
    /**
     * 插入单条文档
     *
     * @param collection 集合名
     * @param doc        文档对象
     * @return 插入后的文档对象
     * @throws RuntimeException 插入失败
     */
    <T> T insert(String collection, T doc);
    /**
     * 批量插入文档
     *
     * @param collection 集合名
     * @param docs       文档列表
     * @return 插入后的文档列表
     * @throws RuntimeException 插入失败
     */
    <T> List<T> insertMany(String collection, List<T> docs);
    /**
     * 按ID查找文档
     *
     * @param collection 集合名
     * @param id         主键ID
     * @param clazz      文档类型
     * @return 查到返回文档对象，否则null
     */
    @Nullable <T> T findById(String collection, Object id, Class<T> clazz);
    /**
     * 条件查找文档列表
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @return 匹配文档列表，可能为空
     */
    <T> List<T> find(Query query, String collection, Class<T> clazz);
    /**
     * 条件查找单条文档
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @return 匹配文档，可能为null
     */
    @Nullable <T> T findOne(Query query, String collection, Class<T> clazz);
    /**
     * 条件更新单条文档，返回更新后的文档
     *
     * @param query      查询条件
     * @param update     更新内容
     * @param collection 集合名
     * @param clazz      文档类型
     * @return 更新后的文档，可能为null
     */
    @Nullable <T> T updateOne(Query query, Update update, String collection, Class<T> clazz);
    /**
     * 条件批量更新文档，返回所有匹配文档
     *
     * @param query      查询条件
     * @param update     更新内容
     * @param collection 集合名
     * @param clazz      文档类型
     * @return 更新后的文档列表
     */
    <T> List<T> updateMany(Query query, Update update, String collection, Class<T> clazz);
    /**
     * 条件删除文档
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @return 删除条数
     */
    long delete(Query query, String collection, Class<?> clazz);
    /**
     * 条件计数
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @return 匹配文档数
     */
    long count(Query query, String collection, Class<?> clazz);
    /**
     * 聚合查询
     *
     * @param aggregation 聚合管道
     * @param collection  集合名
     * @param outputType  输出类型
     * @return 聚合结果
     */
    <T> AggregationResults<T> aggregate(Aggregation aggregation, String collection, Class<T> outputType);
    /**
     * 在MongoDB事务中执行操作（需副本集/分片集群）
     *
     * @param action 事务内操作
     * @param <T>    返回类型
     * @return 事务执行结果
     * @throws UnsupportedOperationException 未配置事务管理器
     */
    <T> T executeInTransaction(Supplier<T> action);
    /**
     * 投影查询（只返回部分字段）
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @param fields     需返回的字段名
     * @return 匹配文档列表
     */
    <T> List<T> findWithProjection(Query query, String collection, Class<T> clazz, List<String> fields);
    /**
     * 排序查询
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @param sort       排序对象
     * @return 匹配文档列表
     */
    <T> List<T> findWithSort(Query query, String collection, Class<T> clazz, Sort sort);
    /**
     * 分页查询
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @param pageable   分页对象
     * @return 匹配文档列表
     */
    <T> List<T> findWithPagination(Query query, String collection, Class<T> clazz, Pageable pageable);
    /**
     * 执行原生Mongo命令
     *
     * @param command 命令文档
     * @return 命令结果
     */
    List<Document> runCommand(Document command);
    /**
     * 创建索引
     *
     * @param collection 集合名
     * @param index      索引定义
     * @return 索引名
     */
    String createIndex(String collection, Index index);
    /**
     * 删除索引
     *
     * @param collection 集合名
     * @param indexName  索引名
     */
    void dropIndex(String collection, String indexName);
    /**
     * 查询集合所有索引信息
     *
     * @param collection 集合名
     * @return 索引信息列表
     */
    List<Document> listIndexes(String collection);
    /**
     * 创建集合
     *
     * @param name 集合名
     */
    void createCollection(String name);
    /**
     * 删除集合
     *
     * @param name 集合名
     */
    void dropCollection(String name);
    /**
     * 查询所有集合名
     *
     * @return 集合名列表
     */
    List<String> listCollections();
    /**
     * 判断集合是否存在
     *
     * @param name 集合名
     * @return 是否存在
     */
    boolean collectionExists(String name);
    /**
     * 批量写入（插入/更新/删除等）
     *
     * @param collection 集合名
     * @param operations 批量操作列表
     */
    void bulkWrite(String collection, List<WriteModel<Document>> operations);
    /**
     * 判断文档是否存在
     *
     * @param query      查询条件
     * @param collection 集合名
     * @param clazz      文档类型
     * @return 是否存在
     */
    boolean exists(Query query, String collection, Class<?> clazz);
    /**
     * distinct去重查询
     *
     * @param collection 集合名
     * @param field      字段名
     * @param query      查询条件
     * @param resultType 结果类型
     * @return 去重结果列表
     */
    <T> List<T> distinct(String collection, String field, Query query, Class<T> resultType);
    /**
     * 字段自增
     *
     * @param collection 集合名
     * @param query      查询条件
     * @param field      字段名
     * @param value      增量
     */
    void inc(String collection, Query query, String field, Number value);
    /**
     * 字段unset
     *
     * @param collection 集合名
     * @param query      查询条件
     * @param field      字段名
     */
    void unset(String collection, Query query, String field);
    /**
     * 字段重命名
     *
     * @param collection 集合名
     * @param query      查询条件
     * @param oldField   旧字段名
     * @param newField   新字段名
     */
    void rename(String collection, Query query, String oldField, String newField);
    /**
     * 数组字段push
     *
     * @param collection 集合名
     * @param query      查询条件
     * @param field      字段名
     * @param value      元素值
     */
    void push(String collection, Query query, String field, Object value);
    /**
     * 数组字段pull
     *
     * @param collection 集合名
     * @param query      查询条件
     * @param field      字段名
     * @param value      元素值
     */
    void pull(String collection, Query query, String field, Object value);
    /**
     * 数组字段addToSet
     *
     * @param collection 集合名
     * @param query      查询条件
     * @param field      字段名
     * @param value      元素值
     */
    void addToSet(String collection, Query query, String field, Object value);
    /**
     * 监听集合变更流（ChangeStream）
     *
     * @param collection 集合名
     * @param listener   变更事件回调
     */
    void watchChangeStream(String collection, Consumer<ChangeStreamDocument<Document>> listener);
    /**
     * 上传文件到GridFS
     *
     * @param in       输入流
     * @param filename 文件名
     * @param bucket   bucket名
     * @return 文件ObjectId
     */
    ObjectId storeFileToGridFS(InputStream in, String filename, String bucket);
    /**
     * 从GridFS下载文件
     *
     * @param fileId 文件ID
     * @param bucket bucket名
     * @param out    输出流
     */
    void getFileFromGridFS(String fileId, String bucket, OutputStream out);
    /**
     * 删除GridFS文件
     *
     * @param fileId 文件ID
     * @param bucket bucket名
     */
    void deleteFileFromGridFS(String fileId, String bucket);
}
