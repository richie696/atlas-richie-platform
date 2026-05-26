# 搜索引擎组件(Search)

## 概述

Richie Search Component 是一个现代化的搜索引擎组件，采用模块化架构设计，支持多种搜索引擎。该组件遵循 Spring Data 最新版本的最佳实践，为每种搜索引擎提供独立的、优化的接口实现。

## 主要特性

- 🏗️ **模块化架构**：每种搜索引擎独立实现，互不干扰
- 🚀 **现代化设计**：基于最新版本的 Spring Data 技术栈
- 🔍 **多种查询模式**：支持 CriteriaQuery、StringQuery、NativeQuery
- 📝 **注解驱动**：支持 `@Query` 注解，类似 JPA 的查询方式
- 🎯 **类型安全**：提供 Lambda 表达式支持，编译时类型检查
- ⚡ **性能优化**：针对每种搜索引擎的特性进行优化
- 🔧 **灵活配置**：支持自定义查询条件对象和链式查询构建

# Elasticsearch 模块

## 概述

Elasticsearch 模块是当前最成熟的实现，基于 Spring Data Elasticsearch 5.5.2，提供完整的搜索功能支持。

## 快速开始

### 1. 依赖配置

```xml
<dependency>
    <groupId>com.richie</groupId>
    <artifactId>atlas-richie-component-search</artifactId>
</dependency>
```

### 2. 基础配置

```yaml
# 基础配置
platform:
  component:
    search:
      
      # 服务地址（支持单节点和多节点）
      hosts: http://localhost:9200
      # 多节点示例：http://es1:9200,http://es2:9200,http://es3:9200
      
      # 认证信息（如果启用安全认证）
      username: elastic
      password: your_password
      
      # 连接超时配置
      connect-timeout: 5000      # 连接超时：5秒
      socket-timeout: 60000      # 读取超时：60秒
      
      # 连接池配置
      pool:
        max-total: 100           # 最大总连接数
        max-per-route: 100      # 单节点最大连接数
        connect-timeout: 5000    # 连接超时
        socket-timeout: 60000    # 读取超时
        connection-request-timeout: 5000  # 获取连接超时
        keep-alive-time: 20000  # 连接保活时间
        idle-connection-timeout: 30000    # 空闲连接超时
        validate-after-inactivity: true   # 连接有效性验证
        enable-metrics: false    # 连接池统计
      
      # SSL安全配置
      ssl:
        enabled: false           # 是否启用HTTPS
        protocol: TLSv1.2       # SSL协议版本
        keystore-path: /path/to/keystore.p12
        keystore-password: keystore_password
        keystore-type: PKCS12   # 证书类型：JKS, PKCS12, PEM
        truststore-path: /path/to/truststore.p12
        truststore-password: truststore_password
        trust-all: false        # 是否信任所有证书
      
      # Elasticsearch特有配置
      elasticsearch:
        shards: 1               # 默认分片数
        replicas: 1             # 默认副本数
        refresh-interval: 1s    # 刷新间隔
        cluster:
          health-check: true    # 启用健康检查
          health-check-timeout: 30000  # 健康检查超时
          name: elasticsearch   # 集群名称
          minimum-master-nodes: 1      # 最小主节点数

# 生产环境配置示例
platform:
  component:
    search:
      provider: ELASTICSEARCH
      hosts: https://es-cluster.example.com:9200
      username: ${ES_USERNAME:elastic}
      password: ${ES_PASSWORD:}
      connect-timeout: 3000
      socket-timeout: 30000
      
      pool:
        max-total: 200
        max-per-route: 100
        connect-timeout: 3000
        socket-timeout: 30000
        connection-request-timeout: 3000
        keep-alive-time: 60000
        idle-connection-timeout: 60000
        validate-after-inactivity: true
        enable-metrics: true
      
      ssl:
        enabled: true
        protocol: TLSv1.3
        keystore-path: ${ES_KEYSTORE_PATH:/etc/elasticsearch/certs/client.p12}
        keystore-password: ${ES_KEYSTORE_PASSWORD:}
        keystore-type: PKCS12
        truststore-path: ${ES_TRUSTSTORE_PATH:/etc/elasticsearch/certs/ca.p12}
        truststore-password: ${ES_TRUSTSTORE_PASSWORD:}
        trust-all: false
      
      elasticsearch:
        shards: 3
        replicas: 2
        refresh-interval: 30s
        cluster:
          health-check: true
          health-check-timeout: 60000
          name: production-cluster
          minimum-master-nodes: 2
```

### 3. 配置说明

#### 3.1 配置优先级
- **环境变量** > **配置文件** > **默认值**
- 敏感信息（密码、证书）建议使用环境变量

#### 3.2 连接池调优建议
- **低并发应用**：max-total: 50-100, max-per-route: 50-100
- **中等并发应用**：max-total: 100-150, max-per-route: 100-150  
- **高并发应用**：max-total: 150-200, max-per-route: 100-150

#### 3.3 超时时间配置
- **内网环境**：connect-timeout: 3000-5000ms, socket-timeout: 30000-60000ms
- **公网环境**：connect-timeout: 5000-8000ms, socket-timeout: 60000-120000ms
- **网络较差**：connect-timeout: 8000-10000ms, socket-timeout: 120000ms以上

#### 3.4 SSL安全配置
- **生产环境**：必须启用SSL，使用TLSv1.2或TLSv1.3
- **开发环境**：可以禁用SSL或使用自签名证书
- **证书管理**：建议使用PKCS12格式，便于部署和管理

### 4. 基本使用

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final ElasticsearchService elasticsearchService;
    
    public PageResult<Order> searchOrders(String keyword, int page, int size) {
        SearchQueryWrapper<Order> wrapper = SearchQueryWrapper
            .create(Order.class, "orders")
            .page(page, size);
            
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Order::getOrderName, keyword);
        }
        
        return elasticsearchService.search(wrapper);
    }
}
```

## 核心组件

### 1. SearchQueryWrapper - 通用查询包装器

`SearchQueryWrapper` 是主要的查询构建器，支持链式调用和多种查询操作。

#### 基础查询示例

```java
// 简单条件查询
SearchQueryWrapper<Order> wrapper = SearchQueryWrapper
    .create(Order.class, "orders")
    .eq(Order::getStatus, "ACTIVE")
    .ge(Order::getAmount, 100.0)
    .orderByDesc(Order::getCreateTime)
    .page(0, 10);

PageResult<Order> result = elasticsearchService.search(wrapper);
```

#### 复杂条件查询

```java
// 多条件组合查询
SearchQueryWrapper<Order> wrapper = SearchQueryWrapper
    .create(Order.class, "orders")
    .eq(Order::getStatus, "ACTIVE")
    .between(Order::getCreateTime, startDate, endDate)
    .in(Order::getCategory, Arrays.asList("ELECTRONICS", "CLOTHING"))
    .like(Order::getDescription, "重要")
    .orderByDesc(Order::getCreateTime)
    .page(0, 20);

PageResult<Order> result = elasticsearchService.search(wrapper);
```

#### 嵌套字段查询

```java
// 查询嵌套数组中的字段
SearchQueryWrapper<Order> wrapper = SearchQueryWrapper
    .create(Order.class, "orders")
    .nestedLike("orderItems", "productName", "iPhone")
    .eq(Order::getStatus, "ACTIVE")
    .page(0, 10);

PageResult<Order> result = elasticsearchService.search(wrapper);
```

### 2. StringQueryWrapper - JSON 字符串查询

`StringQueryWrapper` 用于直接使用 Elasticsearch JSON 查询字符串，适合复杂查询场景。

#### 基础使用

```java
StringQueryWrapper<Order> wrapper = StringQueryWrapper
    .create(Order.class, "orders")
    .page(page, size)
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
        """);

PageResult<Order> result = elasticsearchService.search(wrapper);
```

#### 复杂聚合查询

```java
StringQueryWrapper<Order> wrapper = StringQueryWrapper
    .create(Order.class, "orders")
    .page(0, 0)  // 聚合查询不需要分页
    .stringQuery("""
        {
            "query": {
                "bool": {
                    "must": [
                        {"term": {"status": "ACTIVE"}},
                        {"range": {"amount": {"gte": 100}}}
                    ]
                }
            },
            "aggs": {
                "category_stats": {
                    "terms": {"field": "category"},
                    "aggs": {
                        "total_amount": {"sum": {"field": "amount"}}
                    }
                }
            }
        }
        """);

PageResult<Order> result = elasticsearchService.search(wrapper);
```

### 3. NativeQueryWrapper - 原生查询包装器

`NativeQueryWrapper` 使用 Spring Data Elasticsearch 的 NativeQuery，提供类型安全的查询构建。

#### 基础使用

```java
NativeQueryWrapper<Order> wrapper = NativeQueryWrapper
    .create(Order.class, "orders")
    .page(page, size)
    .nativeQuery(q -> q
        .bool(b -> b
            .must(m -> m.term(t -> t.field("status").value("ACTIVE")))
            .must(m -> m.range(r -> r.field("amount").gte(gte -> gte.value(100.0))))
    );

PageResult<Order> result = elasticsearchService.search(wrapper);
```

#### 嵌套查询

```java
NativeQueryWrapper<Order> wrapper = NativeQueryWrapper
    .create(Order.class, "orders")
    .page(page, size)
    .nativeQuery(q -> q
        .nested(n -> n
            .path("orderItems")
            .query(nq -> nq
                .match(m -> m
                    .field("orderItems.productName")
                    .query("iPhone")
                )
            )
        )
    );

PageResult<Order> result = elasticsearchService.search(wrapper);
```

## 查询操作符

### 基础比较操作符

| 操作符 | 方法 | 描述 | 示例 |
|--------|------|------|------|
| 等于 | `eq(column, value)` | 精确匹配 | `.eq(Order::getStatus, "ACTIVE")` |
| 不等于 | `ne(column, value)` | 不匹配 | `.ne(Order::getStatus, "DELETED")` |
| 大于 | `gt(column, value)` | 大于指定值 | `.gt(Order::getAmount, 100.0)` |
| 大于等于 | `ge(column, value)` | 大于等于指定值 | `.ge(Order::getAmount, 100.0)` |
| 小于 | `lt(column, value)` | 小于指定值 | `.lt(Order::getAmount, 1000.0)` |
| 小于等于 | `le(column, value)` | 小于等于指定值 | `.le(Order::getAmount, 1000.0)` |
| 区间 | `between(column, from, to)` | 在指定范围内 | `.between(Order::getCreateTime, start, end)` |

### 集合操作符

| 操作符 | 方法 | 描述 | 示例 |
|--------|------|------|------|
| 包含 | `in(column, values)` | 在指定集合中 | `.in(Order::getCategory, Arrays.asList("A", "B"))` |
| 不包含 | `notIn(column, values)` | 不在指定集合中 | `.notIn(Order::getStatus, Arrays.asList("DELETED"))` |

### 文本操作符

| 操作符 | 方法 | 描述 | 示例 |
|--------|------|------|------|
| 模糊匹配 | `like(column, value)` | 包含指定字符串 | `.like(Order::getDescription, "重要")` |
| 存在 | `exists(column)` | 字段存在 | `.exists(Order::getTags)` |
| 不存在 | `notExists(column)` | 字段不存在 | `.notExists(Order::getDeletedAt)` |

### 嵌套字段操作符

| 操作符 | 方法 | 描述 | 示例 |
|--------|------|------|------|
| 嵌套模糊匹配 | `nestedLike(path, field, value)` | 嵌套字段模糊匹配 | `.nestedLike("orderItems", "productName", "iPhone")` |
| 嵌套精确匹配 | `nestedEq(path, field, value)` | 嵌套字段精确匹配 | `.nestedEq("orderItems", "productId", "PROD-001")` |
| 嵌套范围查询 | `nestedBetween(path, field, from, to)` | 嵌套字段范围查询 | `.nestedBetween("orderItems", "price", 100.0, 500.0)` |

## 高级功能

### 1. 高亮查询

```java
SearchQueryWrapper<Order> wrapper = SearchQueryWrapper
    .create(Order.class, "orders")
    .like(Order::getDescription, "重要")
    .highlight(Order::getDescription, Order::getOrderName)  // 设置高亮字段
    .page(0, 10);

PageResult<Order> result = elasticsearchService.search(wrapper);

// 获取高亮结果
Map<String, Map<String, List<String>>> highlights = result.getHighlights();
```

### 2. 搜索建议

```java
SearchQueryWrapper<Order> wrapper = SearchQueryWrapper
    .create(Order.class, "orders")
    .suggest(Order::getOrderName, "iPh", 5)  // 字段名，前缀，建议数量
    .page(0, 10);

PageResult<Order> result = elasticsearchService.search(wrapper);

// 获取建议结果
Map<String, List<String>> suggestions = result.getSuggestions();
```

### 3. 聚合查询

```java
// 使用 dslSearch 进行聚合查询（推荐）
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
                },
                "status_distribution": {
                    "terms": {"field": "status"}
                }
            }
        }
        """);

PageResult<Order> result = elasticsearchService.dslSearch(wrapper);

// 获取聚合结果
Map<String, Object> aggregations = result.getAggregations();
```

#### 复杂聚合查询示例

```java
// 多层级聚合查询
StringQueryWrapper<Order> wrapper = StringQueryWrapper
    .create(Order.class, "orders")
    .page(0, 0)
    .stringQuery("""
        {
            "query": {
                "bool": {
                    "must": [
                        {"range": {"createTime": {"gte": "2024-01-01"}}},
                        {"range": {"amount": {"gte": 100}}}
                    ]
                }
            },
            "aggs": {
                "monthly_stats": {
                    "date_histogram": {
                        "field": "createTime",
                        "calendar_interval": "month",
                        "format": "yyyy-MM"
                    },
                    "aggs": {
                        "total_amount": {"sum": {"field": "amount"}},
                        "order_count": {"value_count": {"field": "id"}},
                        "category_breakdown": {
                            "terms": {"field": "category"},
                            "aggs": {
                                "category_amount": {"sum": {"field": "amount"}}
                            }
                        }
                    }
                },
                "top_products": {
                    "nested": {"path": "orderItems"},
                    "aggs": {
                        "product_stats": {
                            "terms": {"field": "orderItems.productName", "size": 10},
                            "aggs": {
                                "total_quantity": {"sum": {"field": "orderItems.quantity"}},
                                "total_revenue": {"sum": {"field": "orderItems.subtotal"}}
                            }
                        }
                    }
                }
            }
        }
        """);

PageResult<Order> result = elasticsearchService.dslSearch(wrapper);
```

#### 使用 NativeQueryWrapper 进行聚合查询

```java
// 使用 NativeQueryWrapper 进行类型安全的聚合查询
NativeQueryWrapper<Order> wrapper = NativeQueryWrapper
    .create(Order.class, "orders")
    .page(0, 0)
    .nativeQuery(q -> q
        .query(qb -> qb
            .term(t -> t.field("status").value("ACTIVE"))
        )
        .aggregations("category_stats", a -> a
            .terms(t -> t.field("category"))
            .aggregations("total_amount", sa -> sa
                .sum(s -> s.field("amount"))
            )
        )
        .aggregations("amount_stats", a -> a
            .stats(s -> s.field("amount"))
        )
    );

PageResult<Order> result = elasticsearchService.nativeSearch(wrapper);
```

## 注解驱动查询

根据[Spring Data Elasticsearch Repository官方文档](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/repositories/elasticsearch-repositories.html)，Spring Data Elasticsearch提供了强大的Repository抽象，支持多种查询方式。

### 1. Repository接口定义

#### 基础Repository接口

```java
// 基础Repository接口
public interface OrderRepository extends Repository<Order, String> {
    // 基础方法
}

// CRUD Repository接口
public interface OrderRepository extends CrudRepository<Order, String> {
    // 继承所有CRUD方法
}

// Elasticsearch专用Repository接口
public interface OrderRepository extends ElasticsearchRepository<Order, String> {
    // 继承所有CRUD方法 + Elasticsearch特有方法
}

// 分页和排序Repository接口
public interface OrderRepository extends PagingAndSortingRepository<Order, String> {
    // 继承分页和排序方法
}

// List版本Repository接口（推荐）
public interface OrderRepository extends ListCrudRepository<Order, String> {
    // 返回List而不是Iterable
}
```

#### Repository配置

```java
@Configuration
@EnableElasticsearchRepositories(
    basePackages = "com.richie.repository",
    basePackageClasses = OrderRepository.class,
    namedQueriesLocation = "classpath:elasticsearch-queries.properties"
)
public class ElasticsearchConfig extends AbstractElasticsearchConfiguration {
    
    @Override
    public RestHighLevelClient elasticsearchClient() {
        // 客户端配置
        return new RestHighLevelClient(/* 配置 */);
    }
}
```

### 2. @Query 注解使用

#### 基础查询示例

```java
@Repository
public interface OrderRepository extends ElasticsearchRepository<Order, String> {
    
    // 简单查询
    @Query("{\"term\": {\"status\": \"?0\"}}")
    List<Order> findByStatus(String status);
    
    // 嵌套查询
    @Query("""
        {
            "nested": {
                "path": "orderItems",
                "query": {
                    "match": {
                        "orderItems.productName": "?0"
                    }
                }
            }
        }
        """)
    List<Order> findByProductName(String productName);
    
    // 复杂查询
    @Query("""
        {
            "bool": {
                "must": [
                    {"term": {"status": "?0"}},
                    {"range": {"amount": {"gte": "?1"}}}
                ]
            }
        }
        """)
    List<Order> findByStatusAndMinAmount(String status, Double minAmount);
    
    // 使用 SpEL 表达式
    @Query("""
        {
            "bool": {
                "must": [
                    {"term": {"status": "#{#status}"}},
                    {"range": {"amount": {"gte": "#{#minAmount}"}}}
                ]
            }
        }
        """)
    List<Order> findByStatusAndMinAmountSpEL(@Param("status") String status, 
                                           @Param("minAmount") Double minAmount);
    
    // 聚合查询
    @Query("""
        {
            "query": {"term": {"status": "?0"}},
            "aggs": {
                "category_stats": {
                    "terms": {"field": "category"},
                    "aggs": {
                        "total_amount": {"sum": {"field": "amount"}}
                    }
                }
            }
        }
        """)
    SearchHits<Order> findByStatusWithAggregations(String status);
    
    // 高亮查询
    @Query("""
        {
            "query": {"match": {"description": "?0"}},
            "highlight": {
                "fields": {
                    "description": {},
                    "orderName": {}
                }
            }
        }
        """)
    SearchHits<Order> findByDescriptionWithHighlight(String description);
}
```

### 3. 查询方法派生

根据[Spring Data Elasticsearch查询方法官方文档](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/repositories/elasticsearch-repository-queries.html)，支持通过方法名自动派生查询。

#### 基础查询方法

```java
@Repository
public interface OrderRepository extends ElasticsearchRepository<Order, String> {
    
    // 精确匹配
    List<Order> findByStatus(String status);
    List<Order> findByStatusAndAmount(String status, Double amount);
    List<Order> findByStatusOrAmount(String status, Double amount);
    
    // 范围查询
    List<Order> findByAmountBetween(Double minAmount, Double maxAmount);
    List<Order> findByAmountGreaterThan(Double amount);
    List<Order> findByAmountLessThan(Double amount);
    List<Order> findByAmountGreaterThanEqual(Double amount);
    List<Order> findByAmountLessThanEqual(Double amount);
    
    // 存在性查询
    List<Order> findByDescriptionExists();
    List<Order> findByDescriptionNotExists();
    
    // 空值查询
    List<Order> findByDescriptionIsNull();
    List<Order> findByDescriptionIsNotNull();
    
    // 集合查询
    List<Order> findByCategoryIn(Collection<String> categories);
    List<Order> findByCategoryNotIn(Collection<String> categories);
    
    // 文本查询
    List<Order> findByDescriptionLike(String description);
    List<Order> findByDescriptionNotLike(String description);
    List<Order> findByDescriptionStartingWith(String prefix);
    List<Order> findByDescriptionEndingWith(String suffix);
    List<Order> findByDescriptionContaining(String text);
    
    // 日期查询
    List<Order> findByCreateTimeAfter(LocalDateTime time);
    List<Order> findByCreateTimeBefore(LocalDateTime time);
    List<Order> findByCreateTimeBetween(LocalDateTime start, LocalDateTime end);
}
```

#### 嵌套字段查询

```java
@Repository
public interface OrderRepository extends ElasticsearchRepository<Order, String> {
    
    // 嵌套字段查询
    List<Order> findByOrderItemsProductName(String productName);
    List<Order> findByOrderItemsPriceBetween(Double minPrice, Double maxPrice);
    List<Order> findByOrderItemsProductIdIn(Collection<String> productIds);
    
    // 嵌套字段存在性查询
    List<Order> findByOrderItemsExists();
    List<Order> findByOrderItemsNotExists();
}
```

#### 地理位置查询

```java
@Repository
public interface OrderRepository extends ElasticsearchRepository<Order, String> {
    
    // 地理位置查询
    List<Order> findByDeliveryLocationNear(Point point, Distance distance);
    List<Order> findByDeliveryLocationWithin(Point point, Distance distance);
    List<Order> findByDeliveryLocationWithin(GeoJsonPolygon polygon);
}
```

### 4. 查询关键字参考

根据[Spring Data查询关键字官方文档](https://docs.spring.io/spring-data/elasticsearch/reference/repositories/query-keywords-reference.html)，支持以下查询关键字：

#### 逻辑操作符

| 关键字 | 示例 | Elasticsearch查询 |
|--------|------|-------------------|
| `And` | `findByStatusAndAmount` | `bool.must` |
| `Or` | `findByStatusOrAmount` | `bool.should` |
| `Not` | `findByStatusNot` | `bool.must_not` |

#### 比较操作符

| 关键字 | 示例 | Elasticsearch查询 |
|--------|------|-------------------|
| `Is` | `findByStatusIs` | `term` |
| `Equals` | `findByStatusEquals` | `term` |
| `Between` | `findByAmountBetween` | `range` |
| `LessThan` | `findByAmountLessThan` | `range.lt` |
| `LessThanEqual` | `findByAmountLessThanEqual` | `range.lte` |
| `GreaterThan` | `findByAmountGreaterThan` | `range.gt` |
| `GreaterThanEqual` | `findByAmountGreaterThanEqual` | `range.gte` |

#### 字符串操作符

| 关键字 | 示例 | Elasticsearch查询 |
|--------|------|-------------------|
| `Like` | `findByDescriptionLike` | `wildcard` |
| `NotLike` | `findByDescriptionNotLike` | `bool.must_not.wildcard` |
| `StartingWith` | `findByDescriptionStartingWith` | `prefix` |
| `EndingWith` | `findByDescriptionEndingWith` | `wildcard` |
| `Containing` | `findByDescriptionContaining` | `match` |

#### 集合操作符

| 关键字 | 示例 | Elasticsearch查询 |
|--------|------|-------------------|
| `In` | `findByCategoryIn` | `terms` |
| `NotIn` | `findByCategoryNotIn` | `bool.must_not.terms` |
| `Exists` | `findByDescriptionExists` | `exists` |
| `NotExists` | `findByDescriptionNotExists` | `bool.must_not.exists` |

### 5. 分页和排序

```java
@Repository
public interface OrderRepository extends ElasticsearchRepository<Order, String> {
    
    // 分页查询
    Page<Order> findByStatus(String status, Pageable pageable);
    
    // 排序查询
    List<Order> findByStatusOrderByCreateTimeDesc(String status);
    List<Order> findByStatusOrderByAmountAscCreateTimeDesc(String status);
    
    // 分页+排序
    Page<Order> findByStatus(String status, Pageable pageable);
    
    // 使用示例
    Pageable pageable = PageRequest.of(0, 20, Sort.by("createTime").descending());
    Page<Order> page = repository.findByStatus("ACTIVE", pageable);
}
```

### 6. 投影查询

根据[Spring Data投影官方文档](https://docs.spring.io/spring-data/elasticsearch/reference/repositories/projections.html)，支持投影查询来优化数据传输。

#### 接口投影

```java
// 定义投影接口
public interface OrderSummary {
    String getOrderId();
    String getStatus();
    Double getAmount();
    LocalDateTime getCreateTime();
    
    // 计算属性
    default String getDisplayName() {
        return getOrderId() + " - " + getStatus();
    }
}

// 使用投影
@Repository
public interface OrderRepository extends ElasticsearchRepository<Order, String> {
    List<OrderSummary> findByStatus(String status);
    Page<OrderSummary> findByStatus(String status, Pageable pageable);
}
```

#### 类投影

```java
// 定义投影类
public class OrderProjection {
    private final String orderId;
    private final String status;
    private final Double amount;
    
    public OrderProjection(String orderId, String status, Double amount) {
        this.orderId = orderId;
        this.status = status;
        this.amount = amount;
    }
    
    // getter方法
    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public Double getAmount() { return amount; }
}

// 使用投影
@Repository
public interface OrderRepository extends ElasticsearchRepository<Order, String> {
    List<OrderProjection> findByStatus(String status);
}
```

#### 动态投影

```java
@Repository
public interface OrderRepository extends ElasticsearchRepository<Order, String> {
    <T> List<T> findByStatus(String status, Class<T> type);
}

// 使用示例
List<OrderSummary> summaries = repository.findByStatus("ACTIVE", OrderSummary.class);
List<OrderProjection> projections = repository.findByStatus("ACTIVE", OrderProjection.class);
```

### 7. 自定义Repository实现

根据[Spring Data自定义实现官方文档](https://docs.spring.io/spring-data/elasticsearch/reference/repositories/custom-implementations.html)，支持自定义Repository实现。

#### 自定义Repository接口

```java
public interface OrderRepositoryCustom {
    List<Order> findComplexOrders(OrderSearchCriteria criteria);
    Map<String, Object> getOrderStatistics(String status);
}
```

#### 自定义Repository实现

```java
public class OrderRepositoryImpl implements OrderRepositoryCustom {
    
    @Autowired
    private ElasticsearchTemplate template;
    
    @Override
    public List<Order> findComplexOrders(OrderSearchCriteria criteria) {
        // 自定义查询逻辑
        NativeQuery query = NativeQuery.builder()
            .withQuery(q -> q
                .bool(b -> b
                    .must(m -> m.term(t -> t.field("status").value(criteria.getStatus())))
                    .must(m -> m.range(r -> r.field("amount").gte(gte -> gte.value(criteria.getMinAmount()))))
            )
            .build();
        
        return template.search(query, Order.class).getSearchHits().stream()
            .map(SearchHit::getContent)
            .collect(Collectors.toList());
    }
    
    @Override
    public Map<String, Object> getOrderStatistics(String status) {
        // 自定义聚合查询
        NativeQuery query = NativeQuery.builder()
            .withQuery(q -> q.term(t -> t.field("status").value(status)))
            .withAggregations("category_stats", a -> a.terms(t -> t.field("category")))
            .withAggregations("amount_stats", a -> a.stats(s -> s.field("amount")))
            .build();
        
        SearchHits<Order> searchHits = template.search(query, Order.class);
        // 处理聚合结果...
        return new HashMap<>();
    }
}
```

#### 组合Repository接口

```java
@Repository
public interface OrderRepository extends 
    ElasticsearchRepository<Order, String>, 
    OrderRepositoryCustom {
    
    // 继承所有方法
}
```

### 8. 领域事件发布

根据[Spring Data领域事件官方文档](https://docs.spring.io/spring-data/elasticsearch/reference/repositories/core-domain-events.html)，支持发布领域事件。

#### 实体事件

```java
@Document(indexName = "orders")
public class Order extends AbstractAggregateRoot<Order> {
    
    @Id
    private String id;
    private String orderId;
    private String status;
    
    public void activate() {
        this.status = "ACTIVE";
        // 发布领域事件
        registerEvent(new OrderActivatedEvent(this.orderId));
    }
    
    public void cancel() {
        this.status = "CANCELLED";
        // 发布领域事件
        registerEvent(new OrderCancelledEvent(this.orderId));
    }
}
```

#### 事件监听器

```java
@Component
public class OrderEventListener {
    
    @EventListener
    public void handleOrderActivated(OrderActivatedEvent event) {
        log.info("订单 {} 已激活", event.getOrderId());
        // 处理订单激活逻辑
    }
    
    @EventListener
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("订单 {} 已取消", event.getOrderId());
        // 处理订单取消逻辑
    }
}
```

### 9. 空值处理

根据[Spring Data空值处理官方文档](https://docs.spring.io/spring-data/elasticsearch/reference/repositories/null-handling.html)，支持多种空值处理策略。

#### 空值处理配置

```java
@Repository
public interface OrderRepository extends ElasticsearchRepository<Order, String> {
    
    // 默认行为：忽略空参数
    List<Order> findByStatusAndAmount(String status, Double amount);
    
    // 显式处理空值
    @Query("""
        {
            "bool": {
                "must": [
                    {"term": {"status": "?0"}},
                    {"range": {"amount": {"gte": "?1"}}}
                ]
            }
        }
        """)
    List<Order> findByStatusAndAmountExplicit(String status, Double amount);
}
```

#### 空值安全查询

```java
@Repository
public interface OrderRepository extends ElasticsearchRepository<Order, String> {
    
    // 使用Optional处理可能为空的值
    Optional<Order> findByOrderId(String orderId);
    
    // 使用@Nullable注解
    List<Order> findByStatusAndAmount(@Nullable String status, @Nullable Double amount);
}
```

### 10. 查询返回类型

根据[Spring Data查询返回类型官方文档](https://docs.spring.io/spring-data/elasticsearch/reference/repositories/query-return-types-reference.html)，支持多种返回类型。

#### 支持的返回类型

```java
@Repository
public interface OrderRepository extends ElasticsearchRepository<Order, String> {
    
    // 单个实体
    Order findByOrderId(String orderId);
    Optional<Order> findByOrderId(String orderId);
    
    // 集合
    List<Order> findByStatus(String status);
    Set<Order> findByStatus(String status);
    Iterable<Order> findByStatus(String status);
    
    // 分页
    Page<Order> findByStatus(String status, Pageable pageable);
    Slice<Order> findByStatus(String status, Pageable pageable);
    
    // 流式查询
    Stream<Order> findByStatus(String status);
    
    // 计数
    long countByStatus(String status);
    boolean existsByOrderId(String orderId);
    
    // 删除
    long deleteByStatus(String status);
    void removeByStatus(String status);
}
```

#### 异步查询

```java
@Repository
public interface OrderRepository extends ElasticsearchRepository<Order, String> {
    
    // 异步查询
    @Async
    CompletableFuture<Order> findByOrderId(String orderId);
    
    @Async
    CompletableFuture<List<Order>> findByStatus(String status);
    
    @Async
    CompletableFuture<Page<Order>> findByStatus(String status, Pageable pageable);
}
```

### 11. 查询方法最佳实践

```java
@Repository
public interface OrderRepository extends ElasticsearchRepository<Order, String> {
    
    // 1. 使用有意义的方法名
    List<Order> findByStatusAndCreateTimeBetweenOrderByAmountDesc(
        String status, LocalDateTime start, LocalDateTime end);
    
    // 2. 使用@Query注解处理复杂查询
    @Query("""
        {
            "bool": {
                "must": [
                    {"term": {"status": "?0"}},
                    {"range": {"createTime": {"gte": "?1", "lte": "?2"}}}
                ]
            }
        }
        """)
    List<Order> findActiveOrdersInDateRange(LocalDateTime start, LocalDateTime end);
    
    // 3. 使用投影优化数据传输
    List<OrderSummary> findOrderSummariesByStatus(String status);
    
    // 4. 使用分页避免大量数据查询
    Page<Order> findByStatus(String status, Pageable pageable);
    
    // 5. 使用流式查询处理大量数据
    Stream<Order> streamByStatus(String status);
}
```

## 实体映射与配置

### 1. 基础映射注解

根据[Spring Data Elasticsearch官方文档](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/object-mapping.html)，Spring Data Elasticsearch提供了丰富的映射注解来配置实体类：

```java
@Document(indexName = "orders", createIndex = true)
public class Order {
    
    @Id
    private String id;
    
    @Field(type = FieldType.Keyword)
    private String orderId;
    
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String description;
    
    @Field(type = FieldType.Date, format = {DateFormat.basic_date, DateFormat.basic_time})
    private LocalDateTime createTime;
    
    @Field(type = FieldType.Nested)
    private List<OrderItem> orderItems;
    
    @Field(type = FieldType.GeoPoint)
    private Point deliveryLocation;
    
    @Field(type = FieldType.Object)
    private Map<String, Object> metadata;
}
```

#### 常用字段类型

| 类型 | 说明 | 适用场景 |
|------|------|----------|
| `FieldType.Text` | 全文搜索字段 | 商品描述、评论内容 |
| `FieldType.Keyword` | 精确匹配字段 | 状态、分类、ID |
| `FieldType.Date` | 日期时间字段 | 创建时间、更新时间 |
| `FieldType.Nested` | 嵌套对象字段 | 订单项、标签列表 |
| `FieldType.GeoPoint` | 地理位置字段 | 配送地址、门店位置 |
| `FieldType.Object` | 动态对象字段 | 扩展属性、元数据 |

### 2. 日期格式映射

```java
@Field(type = FieldType.Date, 
       format = {DateFormat.basic_date, DateFormat.basic_time},
       pattern = "yyyy-MM-dd HH:mm:ss")
private LocalDateTime createTime;

// 或者使用自定义格式
@Field(type = FieldType.Date, 
       format = {}, 
       pattern = "dd.MM.yyyy")
private LocalDate orderDate;
```

### 3. 地理位置类型

```java
// 使用 Point 类型
@Field(type = FieldType.GeoPoint)
private Point deliveryLocation;

// 使用 GeoJson 类型
@Field(type = FieldType.GeoShape)
private GeoJsonPoint location;

// 在代码中使用
Point point = new Point(-118.3026284, 34.118347);
GeoJsonPoint geoPoint = new GeoJsonPoint(-118.3026284, 34.118347);
```

### 4. 集合和映射类型

```java
// 集合类型
@Field(type = FieldType.Nested)
private List<OrderItem> orderItems;

@Field(type = FieldType.Keyword)
private Set<String> tags;

// 映射类型
@Field(type = FieldType.Object)
private Map<String, Address> knownLocations;

@Field(type = FieldType.Object)
private Map<String, Object> metadata;
```

## 实体生命周期管理

### 1. 实体回调

根据[Spring Data Elasticsearch实体回调文档](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/entity-callbacks.html)，Spring Data Elasticsearch支持多种实体生命周期回调：

```java
@Document(indexName = "orders")
public class Order {
    
    @Id
    private String id;
    
    private String orderId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    // 保存前回调
    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        updateTime = LocalDateTime.now();
    }
    
    // 更新前回调
    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }
    
    // 保存后回调
    @PostPersist
    public void postPersist() {
        log.info("订单 {} 已保存到ES", orderId);
    }
    
    // 加载后回调
    @PostLoad
    public void postLoad() {
        log.debug("订单 {} 已从ES加载", orderId);
    }
}
```

### 2. 自定义回调实现

```java
@Component
public class OrderAuditCallback implements BeforeConvertCallback<Order> {
    
    @Override
    public Order onBeforeConvert(Order order, IndexCoordinates indexCoordinates) {
        if (order.getCreateTime() == null) {
            order.setCreateTime(LocalDateTime.now());
        }
        order.setUpdateTime(LocalDateTime.now());
        return order;
    }
}

@Component
public class OrderAfterLoadCallback implements AfterLoadCallback<Order> {
    
    @Override
    public Order onAfterLoad(Order order, IndexCoordinates indexCoordinates) {
        // 处理加载后的逻辑，如计算字段、格式化等
        if (order.getOrderItems() != null) {
            order.setTotalItems(order.getOrderItems().size());
        }
        return order;
    }
}
```

## 审计功能

### 1. 启用审计

根据[Spring Data Elasticsearch审计文档](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/auditing.html)，可以通过配置启用审计功能：

```java
@Configuration
@EnableElasticsearchAuditing
public class ElasticsearchConfig extends AbstractElasticsearchConfiguration {
    
    @Override
    public RestHighLevelClient elasticsearchClient() {
        // 客户端配置
        return new RestHighLevelClient(/* 配置 */);
    }
    
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName);
    }
}
```

### 2. 审计注解使用

```java
@Document(indexName = "orders")
@EntityListeners(AuditingEntityListener.class)
public class Order {
    
    @Id
    private String id;
    
    @CreatedDate
    @Field(type = FieldType.Date)
    private LocalDateTime createTime;
    
    @LastModifiedDate
    @Field(type = FieldType.Date)
    private LocalDateTime updateTime;
    
    @CreatedBy
    @Field(type = FieldType.Keyword)
    private String createdBy;
    
    @LastModifiedBy
    @Field(type = FieldType.Keyword)
    private String lastModifiedBy;
    
    @Version
    private Long version;
}
```

## 高级映射特性

### 1. Join类型支持

根据[Spring Data Elasticsearch Join类型文档](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/join-types.html)，支持父子文档关系：

```java
@Document(indexName = "orders")
public class Order {
    
    @Id
    private String id;
    
    @Field(type = FieldType.Keyword)
    private String orderId;
    
    @JoinTypeRelations(
        relations = {
            @JoinTypeRelation(parent = "order", children = {"orderItem", "orderLog"})
        }
    )
    private JoinField<String> relation;
}

@Document(indexName = "orders")
public class OrderItem {
    
    @Id
    private String id;
    
    @Field(type = FieldType.Keyword)
    private String productId;
    
    @JoinTypeRelations(
        relations = {
            @JoinTypeRelation(parent = "order", children = {"orderItem", "orderLog"})
        }
    )
    private JoinField<String> relation;
}
```

### 2. 路由值配置

根据[Spring Data Elasticsearch路由文档](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/routing.html)，支持自定义路由策略：

```java
@Document(indexName = "orders")
public class Order {
    
    @Id
    private String id;
    
    @Field(type = FieldType.Keyword)
    private String orderId;
    
    @Routing
    private String tenantId;  // 使用租户ID作为路由值
    
    @Field(type = FieldType.Keyword)
    private String status;
}

// 在查询时指定路由
SearchQueryWrapper<Order> wrapper = SearchQueryWrapper
    .create(Order.class, "orders")
    .routing("tenant_001")  // 指定路由值
    .eq(Order::getStatus, "ACTIVE");
```

### 3. 类型别名

```java
@Document(indexName = "orders")
@TypeAlias("order")  // 自定义类型别名
public class Order {
    
    @Id
    private String id;
    
    @Field(type = FieldType.Keyword)
    private String orderId;
}

// 配置类型别名
@Configuration
public class ElasticsearchConfig extends AbstractElasticsearchConfiguration {
    
    @Override
    protected Set<Class<?>> getInitialEntitySet() {
        return Set.of(Order.class);
    }
}
```

## 自定义转换器

### 1. 类型转换器

```java
@Configuration
public class ElasticsearchConfig extends AbstractElasticsearchConfiguration {
    
    @Bean
    @Override
    public ElasticsearchCustomConversions elasticsearchCustomConversions() {
        return new ElasticsearchCustomConversions(
            Arrays.asList(new AddressToMap(), new MapToAddress())
        );
    }
    
    @WritingConverter
    static class AddressToMap implements Converter<Address, Map<String, Object>> {
        
        @Override
        public Map<String, Object> convert(Address source) {
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("city", source.getCity());
            target.put("street", source.getStreet());
            target.put("location", Arrays.asList(source.getLatitude(), source.getLongitude()));
            return target;
        }
    }
    
    @ReadingConverter
    static class MapToAddress implements Converter<Map<String, Object>, Address> {
        
        @Override
        public Address convert(Map<String, Object> source) {
            Address address = new Address();
            address.setCity((String) source.get("city"));
            address.setStreet((String) source.get("street"));
            
            @SuppressWarnings("unchecked")
            List<Double> coords = (List<Double>) source.get("location");
            if (coords != null && coords.size() == 2) {
                address.setLatitude(coords.get(0));
                address.setLongitude(coords.get(1));
            }
            
            return address;
        }
    }
}
```

### 2. 字段值转换器

```java
@Document(indexName = "orders")
public class Order {
    
    @Id
    private String id;
    
    @Field(type = FieldType.Keyword)
    private String orderId;
    
    @ValueConverter(OrderStatusConverter.class)
    private OrderStatus status;
}

// 自定义字段值转换器
public class OrderStatusConverter implements ValueConverter<OrderStatus, String> {
    
    @Override
    public String write(OrderStatus value) {
        return value != null ? value.getCode() : null;
    }
    
    @Override
    public OrderStatus read(String value) {
        return OrderStatus.fromCode(value);
    }
}
```

## 索引管理

### 1. 索引创建与配置

```java
@Service
public class IndexManagementService {
    
    @Autowired
    private ElasticsearchTemplate template;
    
    public void createOrderIndex() {
        IndexOperations indexOps = template.indexOps(Order.class);
        
        if (!indexOps.exists()) {
            // 创建索引
            indexOps.create();
            
            // 创建映射
            Document document = Document.create();
            document.put("mappings", createOrderMappings());
            indexOps.putMapping(document);
        }
    }
    
    private Map<String, Object> createOrderMappings() {
        Map<String, Object> mappings = new HashMap<>();
        // 详细的映射配置...
        return mappings;
    }
}
```

### 2. 索引别名管理

```java
@Service
public class IndexAliasService {
    
    @Autowired
    private ElasticsearchTemplate template;
    
    public void createAlias(String indexName, String aliasName) {
        IndexOperations indexOps = template.indexOps(IndexCoordinates.of(indexName));
        
        AliasQuery aliasQuery = new AliasQuery();
        aliasQuery.setAliasName(aliasName);
        
        indexOps.addAlias(aliasQuery);
    }
    
    public void switchAlias(String oldIndex, String newIndex, String aliasName) {
        // 原子性切换别名
        AliasActions aliasActions = new AliasActions();
        aliasActions.add(new AliasAction.Add(IndexCoordinates.of(newIndex), aliasName));
        aliasActions.add(new AliasAction.Remove(IndexCoordinates.of(oldIndex), aliasName));
        
        template.execute(aliasActions);
    }
}
```

## 最佳实践

### 1. 实体设计原则

- **合理使用字段类型**：根据查询需求选择合适的字段类型
- **避免过度嵌套**：嵌套层级不要超过3层，避免查询性能问题
- **使用类型别名**：为复杂实体类设置简短的类型别名
- **合理设置分片数**：根据数据量和集群规模设置合适的分片数

### 2. 性能优化建议

- **批量操作**：使用批量API进行大量数据的增删改操作
- **合理分页**：避免深度分页，使用search_after进行深度分页
- **索引优化**：定期进行索引优化和合并操作
- **查询优化**：使用filter context减少评分计算开销

### 3. 监控与维护

- **启用审计**：记录数据变更历史，便于问题追踪
- **健康检查**：定期检查集群健康状态
- **性能监控**：监控查询响应时间和资源使用情况
- **日志记录**：记录关键操作的日志信息

### 4. 安全考虑

- **访问控制**：使用Elasticsearch的安全功能控制访问权限
- **数据加密**：敏感数据使用加密存储
- **审计日志**：记录所有数据访问和修改操作
- **定期备份**：建立数据备份和恢复机制

---

**更多信息请参考**：[Search-API文档.md](Search-API文档.md)

**技术支持**：如有问题，请提交 [Issue](../../issues) 或联系开发团队。
