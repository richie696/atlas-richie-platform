# Richie MongoDB 组件

## 概述

`richie-component-mongodb` 是Richie平台的MongoDB集成组件，提供了对MongoDB数据库的封装和优化。该组件基于Spring Data MongoDB，提供了大对象缓存、数据类型转换、连接池管理等功能。

## 组件架构

### 核心模块

1. **缓存模块 (cache)**
   - `CollectionKey`: 集合键接口
   - `ObjectCache`: 大对象缓存静态工具类
   - `ObjectCacheManager`: 缓存管理器

2. **回调模块 (callback)**
   - `DataAfterConvertCallback`: 数据转换回调处理器

3. **配置模块 (config)**
   - `MongodbConfig`: MongoDB连接配置类
   - `MongodbAutoConfiguration`: 自动配置类

4. **服务模块 (service)**
   - `MongodbService`: MongoDB服务接口
   - `MongodbServiceImpl`: MongoDB服务实现

5. **监听模块 (listener)**
   - `DefaultMongoServerListener`: 服务器监听器
   - `DefaultMongoServerMonitorListener`: 服务器监控监听器

## 实现原理

### 缓存机制

组件采用双层缓存设计：
- **静态工具类层**: `ObjectCache` 提供静态方法，便于全局访问
- **管理器层**: `ObjectCacheManager` 封装MongoDB操作，基于 `MongoTemplate`

### 数据转换

通过 `DataAfterConvertCallback` 实现MongoDB文档到Java对象的智能转换：
- 支持BigDecimal类型的自动转换
- 处理复合文档结构
- 反射机制字段映射

### 自动配置

基于Spring Boot自动配置机制，通过配置文件自动初始化MongoDB连接。

## 配置参数

### 基础连接配置

| 配置项                                   | 类型      | 默认值       | 描述                                                |
|---------------------------------------|---------|-----------|---------------------------------------------------|
| `platform.component.mongodb.uri`      | String  | -         | MongoDB连接URI（优先级高于host/port等单独参数，适合Atlas集群或SRV连接） |
| `platform.component.mongodb.host`     | String  | localhost | 主机名（如localhost或Atlas节点域名）                         |
| `platform.component.mongodb.port`     | Integer | 27017     | 端口号                                               |
| `platform.component.mongodb.database` | String  | example   | 目标数据库名称                                           |

### 认证配置

| 配置项                                        | 类型     | 默认值   | 描述                     |
|--------------------------------------------|--------|-------|------------------------|
| `platform.component.mongodb.username`      | String | -     | 用户名                    |
| `platform.component.mongodb.password`      | String | -     | 密码                     |
| `platform.component.mongodb.auth-database` | String | admin | 认证数据库（如admin，默认为admin） |

### 连接池与超时配置

| 配置项                                                   | 类型      | 默认值   | 描述                          |
|-------------------------------------------------------|---------|-------|-----------------------------|
| `platform.component.mongodb.connect-timeout-ms`       | Integer | 10000 | 连接超时时间（毫秒）                  |
| `platform.component.mongodb.socket-timeout-ms`        | Integer | 10000 | 读取超时时间（毫秒）                  |
| `platform.component.mongodb.max-wait-time-ms`         | Integer | 2000  | 连接池最大等待时间（毫秒）               |
| `platform.component.mongodb.connection-idle-time`     | Integer | 0     | 连接池最大空闲时间（毫秒）               |
| `platform.component.mongodb.max-connection-pool-size` | Integer | 100   | 连接池最大连接数                    |
| `platform.component.mongodb.min-connection-pool-size` | Integer | 0     | 连接池最小连接数                    |
| `platform.component.mongodb.max-connecting`           | Integer | 2     | 最大并发连接数（新建连接时最大并发数）         |
| `platform.component.mongodb.max-pool-size`            | Integer | 20    | 连接池最大连接数（兼容旧参数名maxPoolSize） |
| `platform.component.mongodb.min-pool-size`            | Integer | 5     | 连接池最小连接数（兼容旧参数名minPoolSize） |

### 心跳与监控配置

| 配置项                                                  | 类型                   | 默认值   | 描述                            |
|------------------------------------------------------|----------------------|-------|-------------------------------|
| `platform.component.mongodb.heartbeat-frequency`     | Long                 | 10000 | 心跳检测频率（毫秒）                    |
| `platform.component.mongodb.min-heartbeat-frequency` | Long                 | 500   | 最小心跳检测频率（毫秒）                  |
| `platform.component.mongodb.server-monitoring-mode`  | ServerMonitoringMode | AUTO  | 服务器监控模式（AUTO/SCANNER/COMMAND） |

### SSL/TLS安全配置

| 配置项                                                    | 类型      | 默认值   | 描述                    |
|--------------------------------------------------------|---------|-------|-----------------------|
| `platform.component.mongodb.ssl-enabled`               | Boolean | false | 是否启用SSL/TLS安全连接       |
| `platform.component.mongodb.invalid-host-name-allowed` | Boolean | false | 是否允许主机名不匹配（仅测试环境建议开启） |
| `platform.component.mongodb.ssl-certificate-path`      | String  | -     | SSL证书文件路径（JKS格式）      |
| `platform.component.mongodb.ssl-certificate-password`  | String  | -     | SSL证书密码               |

## API 接口文档

### ObjectCache 静态工具类

| 方法名                  | 参数                                                           | 返回值             | 描述         |
|----------------------|--------------------------------------------------------------|-----------------|------------|
| `addObject`          | `CollectionKey key, T cacheValue`                            | `T`             | 添加对象到指定集合  |
| `addObjects`         | `CollectionKey key, List<T> cacheValues`                     | `Collection<T>` | 批量添加对象     |
| `mergeObject`        | `CollectionKey key, T cacheValue`                            | `T`             | 合并对象到指定集合  |
| `mergeObjects`       | `CollectionKey key, List<T> cacheValues`                     | `Collection<T>` | 批量合并对象     |
| `getObjectById`      | `CollectionKey key, String documentId, Class<T> entityClass` | `T`             | 根据ID获取对象   |
| `getObject`          | `CollectionKey key, T condition, Class<T> entityClass`       | `T`             | 根据条件获取单个对象 |
| `getObjects`         | `CollectionKey key, T condition, Class<T> entityClass`       | `List<T>`       | 根据条件获取对象列表 |
| `getAllObjects`      | `CollectionKey key, Class<T> entityClass`                    | `List<T>`       | 获取集合中所有对象  |
| `removeObjects`      | `CollectionKey key`                                          | `void`          | 删除集合中所有对象  |
| `removeObjects`      | `CollectionKey key, Object condition, Class<?> entityClass`  | `DeleteResult`  | 根据条件删除对象   |
| `getCollectionCount` | `CollectionKey key`                                          | `long`          | 获取集合对象数量   |

### MongodbService 通用服务接口

#### CRUD 基础操作

| 方法名          | 参数                                                              | 返回值       | 描述       |
|--------------|-----------------------------------------------------------------|-----------|----------|
| `insert`     | `String collection, T doc`                                      | `T`       | 插入单条文档   |
| `insertMany` | `String collection, List<T> docs`                               | `List<T>` | 批量插入文档   |
| `findById`   | `String collection, Object id, Class<T> clazz`                  | `T`       | 按ID查找文档  |
| `find`       | `Query query, String collection, Class<T> clazz`                | `List<T>` | 条件查找文档列表 |
| `findOne`    | `Query query, String collection, Class<T> clazz`                | `T`       | 条件查找单条文档 |
| `updateOne`  | `Query query, Update update, String collection, Class<T> clazz` | `T`       | 条件更新单条文档 |
| `updateMany` | `Query query, Update update, String collection, Class<T> clazz` | `List<T>` | 条件批量更新文档 |
| `delete`     | `Query query, String collection, Class<?> clazz`                | `long`    | 条件删除文档   |
| `count`      | `Query query, String collection, Class<?> clazz`                | `long`    | 条件计数     |

#### 聚合与事务操作

| 方法名                    | 参数                                                                | 返回值                     | 描述              |
|------------------------|-------------------------------------------------------------------|-------------------------|-----------------|
| `aggregate`            | `Aggregation aggregation, String collection, Class<T> outputType` | `AggregationResults<T>` | 聚合查询            |
| `executeInTransaction` | `Supplier<T> action`                                              | `T`                     | 在MongoDB事务中执行操作 |

#### 高级查询操作

| 方法名                  | 参数                                                                    | 返回值       | 描述            |
|----------------------|-----------------------------------------------------------------------|-----------|---------------|
| `findWithProjection` | `Query query, String collection, Class<T> clazz, List<String> fields` | `List<T>` | 投影查询（只返回部分字段） |
| `findWithSort`       | `Query query, String collection, Class<T> clazz, Sort sort`           | `List<T>` | 排序查询          |
| `findWithPagination` | `Query query, String collection, Class<T> clazz, Pageable pageable`   | `List<T>` | 分页查询          |
| `distinct`           | `String collection, String field, Query query, Class<T> resultType`   | `List<T>` | distinct去重查询  |
| `exists`             | `Query query, String collection, Class<?> clazz`                      | `boolean` | 判断文档是否存在      |

#### 索引管理操作

| 方法名           | 参数                                    | 返回值              | 描述         |
|---------------|---------------------------------------|------------------|------------|
| `createIndex` | `String collection, Index index`      | `String`         | 创建索引       |
| `dropIndex`   | `String collection, String indexName` | `void`           | 删除索引       |
| `listIndexes` | `String collection`                   | `List<Document>` | 查询集合所有索引信息 |

#### 集合管理操作

| 方法名                | 参数            | 返回值            | 描述       |
|--------------------|---------------|----------------|----------|
| `createCollection` | `String name` | `void`         | 创建集合     |
| `dropCollection`   | `String name` | `void`         | 删除集合     |
| `listCollections`  | -             | `List<String>` | 查询所有集合名  |
| `collectionExists` | `String name` | `boolean`      | 判断集合是否存在 |

#### 字段级操作

| 方法名        | 参数                                                                 | 返回值    | 描述           |
|------------|--------------------------------------------------------------------|--------|--------------|
| `inc`      | `String collection, Query query, String field, Number value`       | `void` | 字段自增         |
| `unset`    | `String collection, Query query, String field`                     | `void` | 字段unset      |
| `rename`   | `String collection, Query query, String oldField, String newField` | `void` | 字段重命名        |
| `push`     | `String collection, Query query, String field, Object value`       | `void` | 数组字段push     |
| `pull`     | `String collection, Query query, String field, Object value`       | `void` | 数组字段pull     |
| `addToSet` | `String collection, Query query, String field, Object value`       | `void` | 数组字段addToSet |

#### GridFS 文件存储

| 方法名                    | 参数                                               | 返回值        | 描述          |
|------------------------|--------------------------------------------------|------------|-------------|
| `storeFileToGridFS`    | `InputStream in, String filename, String bucket` | `ObjectId` | 上传文件到GridFS |
| `getFileFromGridFS`    | `String fileId, String bucket, OutputStream out` | `void`     | 从GridFS下载文件 |
| `deleteFileFromGridFS` | `String fileId, String bucket`                   | `void`     | 删除GridFS文件  |

#### 其他操作

| 方法名                 | 参数                                                                     | 返回值              | 描述              |
|---------------------|------------------------------------------------------------------------|------------------|-----------------|
| `runCommand`        | `Document command`                                                     | `List<Document>` | 执行原生Mongo命令     |
| `bulkWrite`         | `String collection, List<WriteModel<Document>> operations`             | `void`           | 批量写入（插入/更新/删除等） |
| `watchChangeStream` | `String collection, Consumer<ChangeStreamDocument<Document>> listener` | `void`           | 监听集合变更流         |

## 使用示例

### 1. 依赖配置

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-mongodb</artifactId>
</dependency>
```

### 2. 配置文件

#### 基础配置示例
```yaml
platform:
  component:
    mongodb:
      host: localhost
      port: 27017
      database: myapp
      username: admin
      password: password
      auth-database: admin
      max-connection-pool-size: 50
      min-connection-pool-size: 5
      connect-timeout-ms: 10000
```

#### 完整配置示例
```yaml
platform:
  component:
    mongodb:
      # 基础连接配置
      uri: mongodb://username:password@localhost:27017/myapp?authSource=admin
      host: localhost
      port: 27017
      database: myapp
      
      # 认证配置
      username: admin
      password: password
      auth-database: admin
      
      # 连接池与超时配置
      connect-timeout-ms: 10000
      socket-timeout-ms: 10000
      max-wait-time-ms: 2000
      connection-idle-time: 0
      max-connection-pool-size: 100
      min-connection-pool-size: 0
      max-connecting: 2
      
      # 心跳与监控配置
      heartbeat-frequency: 10000
      min-heartbeat-frequency: 500
      server-monitoring-mode: AUTO
      
      # SSL配置
      ssl-enabled: false
      invalid-host-name-allowed: false
      ssl-certificate-path: /path/to/certificate.jks
      ssl-certificate-password: certificatePassword
```

### 3. 定义集合键

```java
public enum UserCollectionKey implements CollectionKey {
    USER_CACHE("user_cache"),
    USER_SESSION("user_session");
    
    private final String key;
    
    UserCollectionKey(String key) {
        this.key = key;
    }
    
    @Override
    public String getKey() {
        return key;
    }
}
```

### 4. 定义实体类

```java
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

@Data
@Document
public class User {
    @Id
    private String id;
    private String username;
    private String email;
    private Integer age;
    private BigDecimal salary;
    private Date createTime;
}
```

### 5. 基本操作示例

#### 添加对象
```java
@Service
public class UserService {
    
    // 添加单个用户
    public User createUser(User user) {
        return ObjectCache.addObject(UserCollectionKey.USER_CACHE, user);
    }
    
    // 批量添加用户
    public Collection<User> createUsers(List<User> users) {
        return ObjectCache.addObjects(UserCollectionKey.USER_CACHE, users);
    }
}
```

#### 查询对象
```java
@Service
public class UserService {
    
    // 根据ID查询用户
    public User getUserById(String userId) {
        return ObjectCache.getObjectById(UserCollectionKey.USER_CACHE, userId, User.class);
    }
    
    // 根据条件查询单个用户
    public User getUserByUsername(String username) {
        User condition = new User();
        condition.setUsername(username);
        return ObjectCache.getObject(UserCollectionKey.USER_CACHE, condition, User.class);
    }
    
    // 根据条件查询用户列表
    public List<User> getUsersByAge(Integer age) {
        User condition = new User();
        condition.setAge(age);
        return ObjectCache.getObjects(UserCollectionKey.USER_CACHE, condition, User.class);
    }
    
    // 获取所有用户
    public List<User> getAllUsers() {
        return ObjectCache.getAllObjects(UserCollectionKey.USER_CACHE, User.class);
    }
}
```

#### 更新对象
```java
@Service
public class UserService {
    
    // 更新用户信息
    public User updateUser(User user) {
        return ObjectCache.mergeObject(UserCollectionKey.USER_CACHE, user);
    }
    
    // 批量更新用户
    public Collection<User> updateUsers(List<User> users) {
        return ObjectCache.mergeObjects(UserCollectionKey.USER_CACHE, users);
    }
}
```

#### 删除对象
```java
@Service
public class UserService {
    
    // 删除指定年龄的用户
    public DeleteResult deleteUsersByAge(Integer age) {
        User condition = new User();
        condition.setAge(age);
        return ObjectCache.removeObjects(UserCollectionKey.USER_CACHE, condition, User.class);
    }
    
    // 清空用户缓存
    public void clearUserCache() {
        ObjectCache.removeObjects(UserCollectionKey.USER_CACHE);
    }
    
    // 获取用户数量
    public long getUserCount() {
        return ObjectCache.getCollectionCount(UserCollectionKey.USER_CACHE);
    }
}
```

### 6. MongodbService 服务示例

```java
@Service
public class UserService {
    
    @Autowired
    private MongodbService mongodbService;
    
    // 使用通用服务进行CRUD操作
    public User createUser(User user) {
        return mongodbService.insert("users", user);
    }
    
    public List<User> createUsers(List<User> users) {
        return mongodbService.insertMany("users", users);
    }
    
    public User getUserById(String userId) {
        return mongodbService.findById("users", userId, User.class);
    }
    
    public List<User> findUsersByAge(Integer age) {
        Query query = new Query(Criteria.where("age").is(age));
        return mongodbService.find(query, "users", User.class);
    }
    
    // 高级查询操作
    public List<User> findUsersWithProjection(List<String> fields) {
        Query query = new Query();
        return mongodbService.findWithProjection(query, "users", User.class, fields);
    }
    
    // 聚合查询
    public AggregationResults<Document> getUserAgeStatistics() {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.group("age").count().as("count"),
            Aggregation.sort(Sort.Direction.DESC, "count")
        );
        return mongodbService.aggregate(aggregation, "users", Document.class);
    }
    
    // 事务操作
    public void transferUserData(String fromUserId, String toUserId, BigDecimal amount) {
        mongodbService.executeInTransaction(() -> {
            User fromUser = mongodbService.findById("users", fromUserId, User.class);
            User toUser = mongodbService.findById("users", toUserId, User.class);
            
            if (fromUser.getSalary().compareTo(amount) < 0) {
                throw new IllegalArgumentException("余额不足");
            }
            
            Query fromQuery = new Query(Criteria.where("id").is(fromUserId));
            Query toQuery = new Query(Criteria.where("id").is(toUserId));
            
            Update fromUpdate = new Update().inc("salary", amount.negate());
            Update toUpdate = new Update().inc("salary", amount);
            
            mongodbService.updateOne(fromQuery, fromUpdate, "users", User.class);
            mongodbService.updateOne(toQuery, toUpdate, "users", User.class);
            
            return null;
        });
    }
    
    // 索引管理
    public void createUserIndexes() {
        Index usernameIndex = new Index().on("username", Sort.Direction.ASC).unique();
        mongodbService.createIndex("users", usernameIndex);
    }
    
    // GridFS 文件操作
    public ObjectId uploadUserAvatar(String userId, InputStream avatarStream) {
        String filename = "avatar_" + userId + ".jpg";
        return mongodbService.storeFileToGridFS(avatarStream, filename, "avatars");
    }
}
```

### 7. 复杂查询示例

```java
@Service
public class UserService {
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    // 复杂条件查询
    public List<User> findUsersByComplexCondition(String username, Integer minAge, Integer maxAge) {
        Query query = new Query();
        
        if (username != null) {
            query.addCriteria(Criteria.where("username").regex(username, "i"));
        }
        
        if (minAge != null && maxAge != null) {
            query.addCriteria(Criteria.where("age").gte(minAge).lte(maxAge));
        }
        
        return mongoTemplate.find(query, User.class, UserCollectionKey.USER_CACHE.getKey());
    }
    
    // 分页查询
    public Page<User> findUsersWithPagination(int page, int size) {
        Query query = new Query();
        query.with(PageRequest.of(page, size));
        
        List<User> users = mongoTemplate.find(query, User.class, UserCollectionKey.USER_CACHE.getKey());
        long total = mongoTemplate.count(new Query(), UserCollectionKey.USER_CACHE.getKey());
        
        return new PageImpl<>(users, PageRequest.of(page, size), total);
    }
    
    // 聚合查询
    public List<AggregationResult> getUserStatistics() {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.group("age").count().as("count"),
            Aggregation.sort(Sort.Direction.DESC, "count")
        );
        
        AggregationResults<AggregationResult> results = mongoTemplate.aggregate(
            aggregation, UserCollectionKey.USER_CACHE.getKey(), AggregationResult.class);
        
        return results.getMappedResults();
    }
}

@Data
public class AggregationResult {
    private Integer age;
    private Long count;
}
```

### 8. 事务处理示例

```java
@Service
@Transactional
public class UserService {
    
    @Autowired
    private MongoTransactionManager transactionManager;
    
    // 事务操作
    public void transferUserData(String fromUserId, String toUserId, BigDecimal amount) {
        User fromUser = ObjectCache.getObjectById(UserCollectionKey.USER_CACHE, fromUserId, User.class);
        User toUser = ObjectCache.getObjectById(UserCollectionKey.USER_CACHE, toUserId, User.class);
        
        if (fromUser.getSalary().compareTo(amount) < 0) {
            throw new IllegalArgumentException("余额不足");
        }
        
        fromUser.setSalary(fromUser.getSalary().subtract(amount));
        toUser.setSalary(toUser.getSalary().add(amount));
        
        ObjectCache.mergeObject(UserCollectionKey.USER_CACHE, fromUser);
        ObjectCache.mergeObject(UserCollectionKey.USER_CACHE, toUser);
    }
}
```

### 9. 监听器配置示例

```java
@Configuration
public class MongoListenerConfig {
    
    // 自定义服务器监听器
    @Bean
    @ConditionalOnMissingBean(ServerListener.class)
    public ServerListener customServerListener() {
        return new ServerListener() {
            @Override
            public void serverOpening(ServerOpeningEvent event) {
                log.info("MongoDB服务器连接开启: {}", event.getServerId());
            }
            
            @Override
            public void serverClosed(ServerClosedEvent event) {
                log.info("MongoDB服务器连接关闭: {}", event.getServerId());
            }
            
            @Override
            public void serverDescriptionChanged(ServerDescriptionChangedEvent event) {
                log.debug("MongoDB服务器描述变更: {} -> {}",
                    event.getPreviousDescription(), event.getNewDescription());
            }
        };
    }
    
    // 自定义心跳监听器
    @Bean
    @ConditionalOnMissingBean(ServerMonitorListener.class)
    public ServerMonitorListener customServerMonitorListener() {
        return new ServerMonitorListener() {
            @Override
            public void serverHearbeatStarted(ServerHeartbeatStartedEvent event) {
                log.debug("MongoDB心跳开始: {}", event.getConnectionId());
            }
            
            @Override
            public void serverHeartbeatSucceeded(ServerHeartbeatSucceededEvent event) {
                log.debug("MongoDB心跳成功: {} 耗时{}ms",
                    event.getConnectionId(), 
                    event.getElapsedTime(TimeUnit.MILLISECONDS));
            }
            
            @Override
            public void serverHeartbeatFailed(ServerHeartbeatFailedEvent event) {
                log.warn("MongoDB心跳失败: {} - {}",
                    event.getConnectionId(), 
                    event.getThrowable().getMessage());
            }
        };
    }
}
```

### 10. 自定义配置示例

```java
@Configuration
public class MongoConfig {
    
    // 自定义MongoTemplate
    @Bean
    @Primary
    public MongoTemplate customMongoTemplate(MongoDatabaseFactory mongoDbFactory) {
        MongoTemplate template = new MongoTemplate(mongoDbFactory);
        
        // 设置写关注
        template.setWriteConcern(WriteConcern.MAJORITY);
        
        // 设置读偏好
        template.setReadPreference(ReadPreference.secondaryPreferred());
        
        return template;
    }
    
    // 自定义转换器
    @Bean
    public MongoCustomConversions customConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new BigDecimalToDecimal128Converter());
        converters.add(new Decimal128ToBigDecimalConverter());
        return new MongoCustomConversions(converters);
    }
}
```

## 监听器功能

### 服务器监听器

组件提供了默认的MongoDB服务器监听器 `DefaultMongoServerListener`，用于监控MongoDB连接状态：

- **serverOpening**: 监听服务器连接开启事件
- **serverClosed**: 监听服务器连接关闭事件
- **serverDescriptionChanged**: 监听服务器描述变更事件

### 心跳监听器

组件提供了默认的心跳监听器 `DefaultMongoServerMonitorListener`，用于监控MongoDB心跳状态：

- **serverHearbeatStarted**: 监听心跳开始事件
- **serverHeartbeatSucceeded**: 监听心跳成功事件
- **serverHeartbeatFailed**: 监听心跳失败事件

### 自定义监听器

可以通过实现相应接口并注册为Spring Bean来自定义监听器：

```java
@Component
public class CustomServerListener implements ServerListener {
    // 自定义实现
}

@Component  
public class CustomServerMonitorListener implements ServerMonitorListener {
    // 自定义实现
}
```

## 注意事项

1. **索引优化**: 为查询条件字段设置合适的索引以提升性能
2. **连接池配置**: 根据应用负载调整连接池参数
3. **事务支持**: MongoDB 4.0+ 支持多文档事务，需要副本集或分片集群
4. **数据类型**: 注意BigDecimal等Java类型与MongoDB BSON类型的转换
5. **异常处理**: 合理处理网络异常和数据库异常
6. **URI配置**: 推荐使用URI配置方式，支持副本集、分片集群等高级特性
7. **SSL配置**: 生产环境建议启用SSL，注意证书配置和主机名验证
8. **监听器功能**: 可自定义服务器和心跳监听器，用于监控和调试

