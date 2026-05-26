# ElasticsearchService API 接口文档

## 概述

ElasticsearchService 是一个统一的搜索服务接口，封装了多种搜索引擎的常用操作，支持 Elasticsearch 和 Solr，提供索引管理、文档操作、查询搜索、聚合分析等功能。

**版本**: 2.0  
**作者**: richie696  
**创建日期**: 2025-08-26

## 主要功能

- 索引/集合管理（创建、删除、存在性检查）
- 文档操作（保存、删除、查询）
- 搜索查询（分页、条件、聚合、高亮、建议）
- 批量操作支持
- 高级查询（布尔查询、嵌套查询、脚本查询）
- 聚合分析（统计、分组、管道聚合）

## 支持的搜索引擎

- Elasticsearch（完整功能支持）
- Solr（基础功能支持，部分高级功能可能受限）
- 其他搜索引擎（根据具体实现支持相应功能）

## API 接口概览

| 分类       | 方法名                 | 描述                                | 返回类型                | 参数                                                 |
|----------|---------------------|-----------------------------------|---------------------|----------------------------------------------------|
| **索引管理** | `createIndex`       | 创建索引（Elasticsearch）或集合（SolrCloud） | `boolean`           | `(String indexName, String mappingJson)`           |
|          | `deleteIndex`       | 删除索引（Elasticsearch）或集合（SolrCloud） | `boolean`           | `(String indexName)`                               |
|          | `indexExists`       | 判断索引/集合是否存在                       | `boolean`           | `(String indexName)`                               |
| **文档操作** | `save`              | 保存单条文档                            | `<T> T`             | `(String indexName, T document)`                   |
|          | `deleteById`        | 根据ID删除单条文档                        | `boolean`           | `(String indexName, String id)`                    |
|          | `findByDocId`       | 根据ID查询单条文档                        | `<T> Optional<T>`   | `(String indexName, String docId, Class<T> clazz)` |
|          | `findOne`           | 根据查询条件查找单个文档                      | `<T> T`             | `(SearchQueryWrapper<T> wrapper)`                  |
| **批量操作** | `saveBatch`         | 批量保存文档                            | `<T> List<T>`       | `(String indexName, List<T> documents)`            |
|          | `deleteBatchByIds`  | 批量根据ID删除文档                        | `boolean`           | `(String indexName, List<String> ids)`             |
| **基础查询** | `count`             | 查询文档总数                            | `long`              | `(SearchQueryWrapper<T> wrapper)`                  |
|          | `deleteByCondition` | 根据查询条件删除文档                        | `long`              | `(SearchQueryWrapper<T> wrapper)`                  |
| **高级查询** | `search`            | 高级查询（支持常规查询、布尔查询、嵌套查询、脚本查询等）      | `<T> PageResult<T>` | `(SearchQueryWrapper<T> wrapper)`                  |
| **原生查询** | `dslSearch`         | 原生查询（使用搜索引擎原生DSL语法）               | `<T> PageResult<T>` | `(StringQueryWrapper<T> wrapper)`                  |
|          | `nativeSearch`      | 原生查询（使用搜索引擎原生API语法）               | `<T> PageResult<T>` | `(NativeQueryWrapper<T> wrapper)`                  |

## API 接口列表

### 1. 索引管理接口

#### 1.1 创建索引

| 属性   | 值                                 |
|------|-----------------------------------|
| 方法名  | `createIndex`                     |
| 描述   | 创建索引（Elasticsearch）或集合（SolrCloud） |
| 返回类型 | `boolean`                         |

**参数表**

| 参数名         | 类型     | 必填 | 描述                                |
|-------------|--------|----|-----------------------------------|
| indexName   | String | 是  | 索引/集合名称                           |
| mappingJson | String | 否  | mapping或schema定义（ES为JSON，Solr可忽略） |

**返回值**
- `true`: 创建成功
- `false`: 已存在或不支持

**使用示例**
```java
boolean created = elasticsearchService.createIndex("users", mappingJson);
```

#### 1.2 删除索引

| 属性   | 值                                 |
|------|-----------------------------------|
| 方法名  | `deleteIndex`                     |
| 描述   | 删除索引（Elasticsearch）或集合（SolrCloud） |
| 返回类型 | `boolean`                         |

**参数表**

| 参数名       | 类型     | 必填 | 描述      |
|-----------|--------|----|---------|
| indexName | String | 是  | 索引/集合名称 |

**返回值**
- `true`: 删除成功
- `false`: 不存在或不支持

**使用示例**
```java
boolean deleted = elasticsearchService.deleteIndex("users");
```

#### 1.3 检查索引是否存在

| 属性   | 值             |
|------|---------------|
| 方法名  | `indexExists` |
| 描述   | 判断索引/集合是否存在   |
| 返回类型 | `boolean`     |

**参数表**

| 参数名       | 类型     | 必填 | 描述      |
|-----------|--------|----|---------|
| indexName | String | 是  | 索引/集合名称 |

**返回值**
- `true`: 存在
- `false`: 不存在

**使用示例**
```java
boolean exists = elasticsearchService.indexExists("users");
```

### 2. 文档操作接口

#### 2.1 保存单条文档

| 属性   | 值                          |
|------|----------------------------|
| 方法名  | `save`                     |
| 描述   | 将文档保存到指定的索引或集合中，如果文档已存在则更新 |
| 返回类型 | `<T> T`                    |

**参数表**

| 参数名       | 类型     | 必填 | 描述      |
|-----------|--------|----|---------|
| indexName | String | 是  | 索引/集合名称 |
| document  | T      | 是  | 文档对象    |

**返回值**
- 保存后的文档对象（带ID）

**使用示例**
```java
User user = new User("张三", 25);
User saved = elasticsearchService.save("users", user);
```

#### 2.2 根据ID删除单条文档

| 属性   | 值             |
|------|---------------|
| 方法名  | `deleteById`  |
| 描述   | 根据文档ID删除指定的文档 |
| 返回类型 | `boolean`     |

**参数表**

| 参数名       | 类型     | 必填 | 描述      |
|-----------|--------|----|---------|
| indexName | String | 是  | 索引/集合名称 |
| id        | String | 是  | 文档ID    |

**返回值**
- `true`: 删除成功
- `false`: 删除失败

**使用示例**
```java
boolean deleted = elasticsearchService.deleteById("users", "user_001");
```

#### 2.3 根据ID查询单条文档

| 属性   | 值                 |
|------|-------------------|
| 方法名  | `findByDocId`     |
| 描述   | 根据文档ID查询指定的文档     |
| 返回类型 | `<T> Optional<T>` |

**参数表**

| 参数名       | 类型       | 必填 | 描述        |
|-----------|----------|----|-----------|
| indexName | String   | 是  | 索引/集合名称   |
| docId     | String   | 是  | 文档ID      |
| clazz     | Class<T> | 是  | 文档类型Class |

**返回值**
- `Optional<T>`: 包装的文档对象

**使用示例**
```java
Optional<User> user = elasticsearchService.findByDocId("users", "user_001", User.class);
```

#### 2.4 根据查询条件查找单个文档

| 属性   | 值            |
|------|--------------|
| 方法名  | `findOne`    |
| 描述   | 根据查询条件查找单个文档 |
| 返回类型 | `<T> T`      |

**参数表**

| 参数名     | 类型                    | 必填 | 描述      |
|---------|-----------------------|----|---------|
| wrapper | SearchQueryWrapper<T> | 是  | 查询参数构建器 |

**返回值**
- 匹配的文档对象，如果未找到返回null

**使用示例**
```java
User user = elasticsearchService.findOne(
    SearchQueryWrapper.create(User.class, "users")
        .eq(User::getName, "张三")
);
```

### 3. 批量操作接口

#### 3.1 批量保存文档

| 属性   | 值                  |
|------|--------------------|
| 方法名  | `saveBatch`        |
| 描述   | 批量保存多个文档到指定的索引或集合中 |
| 返回类型 | `<T> List<T>`      |

**参数表**

| 参数名       | 类型      | 必填 | 描述      |
|-----------|---------|----|---------|
| indexName | String  | 是  | 索引/集合名称 |
| documents | List<T> | 是  | 文档列表    |

**返回值**
- 保存后的文档列表

**使用示例**
```java
List<User> users = Arrays.asList(
    new User("张三", 25),
    new User("李四", 30)
);
List<User> savedUsers = elasticsearchService.saveBatch("users", users);
```

#### 3.2 批量根据ID删除文档

| 属性   | 值                  |
|------|--------------------|
| 方法名  | `deleteBatchByIds` |
| 描述   | 根据文档ID列表批量删除文档     |
| 返回类型 | `boolean`          |

**参数表**

| 参数名       | 类型           | 必填 | 描述      |
|-----------|--------------|----|---------|
| indexName | String       | 是  | 索引/集合名称 |
| ids       | List<String> | 是  | 文档ID列表  |

**返回值**
- `true`: 删除成功
- `false`: 删除失败

**使用示例**
```java
List<String> ids = Arrays.asList("1", "2", "3");
boolean deleted = elasticsearchService.deleteBatchByIds("users", ids);
```

### 4. 基础查询接口

#### 4.1 查询文档总数

| 属性   | 值               |
|------|-----------------|
| 方法名  | `count`         |
| 描述   | 根据查询条件统计匹配的文档总数 |
| 返回类型 | `long`          |

**参数表**

| 参数名     | 类型                    | 必填 | 描述      |
|---------|-----------------------|----|---------|
| wrapper | SearchQueryWrapper<T> | 是  | 查询参数构建器 |

**返回值**
- 匹配文档总数

**使用示例**
```java
long count = elasticsearchService.count(
    SearchQueryWrapper.create(User.class, "users")
        .eq(User::getStatus, "active")
);
```

#### 4.2 根据查询条件删除文档

| 属性   | 值                   |
|------|---------------------|
| 方法名  | `deleteByCondition` |
| 描述   | 根据指定的查询条件删除匹配的所有文档  |
| 返回类型 | `long`              |

**参数表**

| 参数名     | 类型                    | 必填 | 描述      |
|---------|-----------------------|----|---------|
| wrapper | SearchQueryWrapper<T> | 是  | 查询参数构建器 |

**返回值**
- 删除的文档数量

**使用示例**
```java
long deletedCount = elasticsearchService.deleteByCondition(
    SearchQueryWrapper.create(User.class, "users")
        .eq(User::getStatus, "inactive")
);
```

### 5. 高级查询接口

#### 5.1 高级查询

| 属性   | 值                           |
|------|-----------------------------|
| 方法名  | `search`                    |
| 描述   | 高级查询，支持常规查询、布尔查询、嵌套查询、脚本查询等 |
| 返回类型 | `<T> PageResult<T>`         |

**参数表**

| 参数名     | 类型                    | 必填 | 描述      |
|---------|-----------------------|----|---------|
| wrapper | SearchQueryWrapper<T> | 是  | 查询参数构建器 |

**返回值**
- 分页结果PageResult

**使用示例**
```java
PageResult<User> result = elasticsearchService.search(
    SearchQueryWrapper.create(User.class, "users")
        .eq(User::getStatus, "active")
        .ge(User::getAge, 18)
        .like(User::getName, "张")
        .page(0, 10)
        .orderByDesc(User::getCreateTime)
);
```

### 6. 原生查询接口

#### 6.1 DSL查询

| 属性   | 值                   |
|------|---------------------|
| 方法名  | `dslSearch`         |
| 描述   | 原生查询，使用搜索引擎原生DSL语法  |
| 返回类型 | `<T> PageResult<T>` |

**参数表**

| 参数名     | 类型                    | 必填 | 描述      |
|---------|-----------------------|----|---------|
| wrapper | StringQueryWrapper<T> | 是  | 查询参数构建器 |

**返回值**
- 查询结果PageResult

**使用示例**
```java
PageResult<Order> result = elasticsearchService.dslSearch(
    StringQueryWrapper.create(Order.class, "orders")
        .page(0, 10)
        .stringQuery("""
            {
                "nested": {
                    "path": "orderItems",
                    "query": {
                        "match": {
                            "orderItems.productName": "iPhone"
                        }
                    }
                }
            }
            """)
);
```

#### 6.2 Native查询

| 属性   | 值                   |
|------|---------------------|
| 方法名  | `nativeSearch`      |
| 描述   | 原生查询，使用搜索引擎原生API语法  |
| 返回类型 | `<T> PageResult<T>` |

**参数表**

| 参数名     | 类型                    | 必填 | 描述      |
|---------|-----------------------|----|---------|
| wrapper | NativeQueryWrapper<T> | 是  | 查询参数构建器 |

**返回值**
- 查询结果PageResult

**使用示例**
```java
PageResult<Order> result = elasticsearchService.nativeSearch(
    NativeQueryWrapper.create(Order.class, "orders")
        .page(0, 10)
        .nativeQuery(q -> q
            .bool(b -> b
                .must(m -> m.term(t -> t.field("status").value("ACTIVE")))
                .must(m -> m.range(r -> r.field("amount").gte(gte -> gte.value(100.0))))
        )
);
```

## 数据结构说明

### SearchQueryWrapper<T>

查询参数构建器，用于构建复杂的搜索条件。**推荐使用此方式构建查询，内部会自动处理build操作。**

#### 基础查询方法

| 方法名                         | 描述      | 示例                                                  |
|-----------------------------|---------|-----------------------------------------------------|
| `eq(column, value)`         | 等于      | `.eq(User::getName, "张三")`                          |
| `ne(column, value)`         | 不等于     | `.ne(User::getStatus, "inactive")`                  |
| `in(column, values)`        | 包含在集合中  | `.in(User::getCategory, Arrays.asList("A", "B"))`   |
| `notIn(column, values)`     | 不包含在集合中 | `.notIn(User::getStatus, Arrays.asList("deleted"))` |
| `gt(column, value)`         | 大于      | `.gt(User::getAge, 18)`                             |
| `ge(column, value)`         | 大于等于    | `.ge(User::getAge, 18)`                             |
| `lt(column, value)`         | 小于      | `.lt(User::getAge, 65)`                             |
| `le(column, value)`         | 小于等于    | `.le(User::getAge, 65)`                             |
| `between(column, from, to)` | 区间查询    | `.between(User::getCreateTime, startDate, endDate)` |
| `like(column, value)`       | 模糊匹配    | `.like(User::getName, "张")`                         |
| `exists(column)`            | 字段存在    | `.exists(User::getEmail)`                           |
| `notExists(column)`         | 字段不存在   | `.notExists(User::getDeletedAt)`                    |

#### 排序和分页方法

| 方法名                   | 描述   | 示例                                  |
|-----------------------|------|-------------------------------------|
| `orderByAsc(column)`  | 升序排序 | `.orderByAsc(User::getCreateTime)`  |
| `orderByDesc(column)` | 降序排序 | `.orderByDesc(User::getUpdateTime)` |
| `page(page, size)`    | 分页设置 | `.page(0, 10)`                      |

#### 高亮和建议方法

| 方法名                             | 描述           | 示例                                                |
|---------------------------------|--------------|---------------------------------------------------|
| `highlight(columns...)`         | 设置高亮字段       | `.highlight(User::getName, User::getDescription)` |
| `suggest(column, prefix)`       | 设置建议查询       | `.suggest(User::getName, "张")`                    |
| `suggest(column, prefix, size)` | 设置建议查询（指定数量） | `.suggest(User::getName, "张", 5)`                 |

### StringQueryWrapper

JSON字符串查询包装器，用于直接使用Elasticsearch JSON查询字符串。

| 字段名           | 类型     | 描述          |
|---------------|--------|-------------|
| `stringQuery` | String | 原生查询JSON字符串 |

**使用示例**
```java
StringQueryWrapper<Order> wrapper = StringQueryWrapper
    .create(Order.class, "orders")
    .page(0, 10)
    .stringQuery("""
        {
            "bool": {
                "must": [
                    {"term": {"status": "ACTIVE"}},
                    {"range": {"amount": {"gte": 100}}}
                ]
            }
        }
        """);
```

### NativeQueryWrapper

类型安全的原生查询包装器，使用Spring Data Elasticsearch的NativeQuery。

| 字段名           | 类型    | 描述                  |
|---------------|-------|---------------------|
| `nativeQuery` | Query | Elasticsearch原生查询对象 |

**使用示例**
```java
NativeQueryWrapper<Order> wrapper = NativeQueryWrapper
    .create(Order.class, "orders")
    .page(0, 10)
    .nativeQuery(q -> q
        .bool(b -> b
            .must(m -> m.term(t -> t.field("status").value("ACTIVE")))
            .must(m -> m.range(r -> r.field("amount").gte(gte -> gte.value(100.0))))
    );
```

### PageResult

统一分页结果对象。

| 字段名            | 类型                                     | 描述         |
|----------------|----------------------------------------|------------|
| `content`      | List<T>                                | 当前页数据列表    |
| `total`        | long                                   | 总条数        |
| `page`         | int                                    | 当前页码（从0开始） |
| `size`         | int                                    | 每页大小       |
| `aggregations` | Map<String, Object>                    | 聚合结果       |
| `highlights`   | Map<String, Map<String, List<String>>> | 高亮内容       |
| `suggestions`  | Map<String, List<String>>              | 建议/自动补全结果  |

## 使用示例

### 基本查询示例

```java
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final ElasticsearchService elasticsearchService;
    
    public PageResult<User> searchUsers(String keyword, int page, int size) {
        SearchQueryWrapper<User> wrapper = SearchQueryWrapper
            .create(User.class, "users")
            .page(page, size);
            
        if (StringUtils.hasText(keyword)) {
            wrapper.like(User::getName, keyword);
        }
        
        return elasticsearchService.search(wrapper);
    }
    
    public User findUserByName(String name) {
        return elasticsearchService.findOne(
            SearchQueryWrapper.create(User.class, "users")
                .eq(User::getName, name)
        );
    }
    
    public long countActiveUsers() {
        return elasticsearchService.count(
            SearchQueryWrapper.create(User.class, "users")
                .eq(User::getStatus, "active")
        );
    }
}
```

### 复杂查询示例

```java
// 多条件组合查询
SearchQueryWrapper<Order> wrapper = SearchQueryWrapper
    .create(Order.class, "orders")
    .eq(Order::getStatus, "ACTIVE")
    .between(Order::getCreateTime, startDate, endDate)
    .in(Order::getCategory, Arrays.asList("ELECTRONICS", "CLOTHING"))
    .like(Order::getDescription, "重要")
    .orderByDesc(Order::getCreateTime)
    .page(0, 20)
    .highlight(Order::getDescription, Order::getOrderName);

PageResult<Order> result = elasticsearchService.search(wrapper);
```

### 聚合查询示例

```java
// 使用 dslSearch 进行聚合查询
StringQueryWrapper<Order> wrapper = StringQueryWrapper
    .create(Order.class, "orders")
    .page(0, 0)  // 聚合查询不需要分页
    .stringQuery("""
        {
            "query": {
                "term": {"status": "ACTIVE"}
            },
            "aggs": {
                "category_stats": {
                    "terms": {"field": "category"},
                    "aggs": {
                        "total_amount": {"sum": {"field": "amount"}},
                        "avg_amount": {"avg": {"field": "amount"}}
                    }
                },
                "amount_stats": {
                    "stats": {"field": "amount"}
                }
            }
        }
        """);

PageResult<Order> result = elasticsearchService.dslSearch(wrapper);
Map<String, Object> aggregations = result.getAggregations();
```

### 批量操作示例

```java
// 批量保存
List<User> users = Arrays.asList(
    new User("张三", 25),
    new User("李四", 30)
);
List<User> savedUsers = elasticsearchService.saveBatch("users", users);

// 批量删除
List<String> ids = Arrays.asList("1", "2", "3");
boolean deleted = elasticsearchService.deleteBatchByIds("users", ids);
```

## 注意事项

1. **推荐使用方式**: 优先使用 `SearchQueryWrapper` 进行查询，它提供了更好的类型安全和代码可读性。**无需手动调用build()方法，服务内部会自动处理。**

2. **API简化**: 所有接受 `SearchQuery<T>` 的方法现在都改为接受 `SearchQueryWrapper<T>`，简化了外部调用方式。

3. **索引命名**: 索引名称应遵循搜索引擎的命名规范，避免使用特殊字符。

4. **文档ID**: 如果文档对象没有显式指定ID，系统会自动生成。

5. **分页参数**: 页码从0开始计算，size建议不要过大以避免性能问题。

6. **聚合查询**: 复杂聚合查询可能消耗较多资源，建议在生产环境中进行性能测试。

7. **错误处理**: 所有方法都可能抛出运行时异常，调用时应适当处理异常情况。

8. **性能优化**: 对于大量数据的查询，建议使用分页和适当的查询条件来优化性能。

## 兼容性说明

- **Elasticsearch**: 支持 6.x 及以上版本
- **Solr**: 支持 8.x 及以上版本
- **Spring Boot**: 需要 3.1 及以上版本
- **Java**: 需要 JDK 17 及以上版本

## 版本更新说明

### v2.0 (2025-08-01)
- 接口名称从 `SearchService` 改为 `ElasticsearchService`
- 新增 `deleteByCondition` 方法
- 优化查询构建器，支持Lambda表达式
- 改进分页结果对象结构
- 新增原生查询支持（DSL和Native）

### v1.0 (2025-07-23)
- 初始版本发布
- 基础索引管理功能
- 基础文档操作功能
- 基础查询功能

---

**更多信息请参考**：[README.md](README.md)

**技术支持**：如有问题，请提交 [Issue](../../issues) 或联系开发团队。

