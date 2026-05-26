## 5.0.0-SNAPSHOT (2026年04月21日)

#### 重大变更：

- `JDK` 版本升级至 **25**
- `spring-boot` 更新版本至 **4.0.5**（全面支持 Spring Boot 4.x 新特性）
- `spring-cloud` 更新版本至 **2025.1.1**
- `spring-cloud-alibaba` 更新版本至 **2025.1.0.0**
- `spring-cloud-azure` 更新版本至 **7.0.0**
- `spring-ai` 更新版本至 **2.0.0-M4**
- `spring-ai-alibaba` 更新版本至 **1.1.2.2**
- `agentscope` 新增版本管理至 **1.0.11**
- 新增 `richie-sso` 模块，提供完整的 **SSO统一认证系统**，包含用户端门户和管理后台
- 新增 `MCP BOM` 依赖管理
- `richie-component-cache` 组件对 `GlobalCache` 进行破坏性重构：移除已废弃及高时间复杂度方法，删除 `LowPerformanceFunction` 与 `RedisLowPerformanceManager` 低性能管理入口，统一收敛到受控 API 体系
- `richie-component-statemachine` 组件完成 Jackson 相关能力迁移至 tools 包并升级至 5.0.0 版本体系
- 适配 **Spring Boot 4.x HTTP 消息转换器新 API**
- 在 richie-base 中拆分独立 contract 模块，承载跨服务共享契约（如网关公共配置/事件模型），将“稳定契约”与“运行实现”解耦，避免上下游对上下文实现包产生不必要依赖。
- 对 GatewayConfig 及相关网关配置类进行拆分迁移（由 base context 向 gateway service/contract 边界收敛），将网关治理规则与基础上下文职责分离，降低配置耦合与变更扩散风险，提升网关模块自治能力。

#### 功能：
- `richie-component-cache` 组件，新增 Redis Key 管理功能
- `richie-component-cache` 组件，新增 HTTP 客户端实现支持
- `richie-component-storage` 组件，新增客户端直传策略接口与预签名能力


#### 优化：
- `richie-component-cache` 组件，重构分布式锁实现，优化缓存管理性能
- `richie-component-cache` 组件，重构 Redisson 客户端配置以支持 SSL 和 URL 连接方式
- `richie-component-cache` 组件，引入 Redis 操作复杂度分级与性能守卫机制，按 O(1)/O(logN)/O(N) 等复杂度模型标记能力边界
- `richie-component-cache` 组件，`GlobalCache` 增加复杂度 ToC 策略注解，对高复杂度操作默认收紧，避免在核心链路中误用
- `richie-component-cache` 组件，针对批量、遍历、模糊匹配等潜在高开销行为增加调用约束与治理能力，降低 Redis 大 Key/慢查询风险
- `richie-component-cache` 组件，接口语义进一步标准化：鼓励使用带锁回源与可控过期策略接口，减少非受控批量写入导致的缓存击穿与抖动
- `richie-component-dao` 组件，检查 getter 存在性以避免空指针异常
- 网关 MFA 模块功能增强，新增 MFA 验证、备份码验证、受信任设备注册等接口支持

#### 改进：

- 移除未使用的设备指纹和设备ID生成代码
- `richie-gateway-service` 服务文档完善，添加完整的网关设计文档和部署文档

#### BUG修复：

- `richie-component-dao` 组件，修复检查 getter 存在性相关问题

## 4.6.0 (2026年04月21日)

#### 重大变更：

- `spring-boot` 更新版本至 3.5.13
- `spring-cloud` 更新版本至 2025.0.2
- `spring-cloud-alibaba` 更新版本至 2023.0.3.4
- `richie-component-cache`组件，重构`LocalCache`本地缓存模块，基于JSR107标准接口，支持多种缓存实现方式（如：Ehcache、Caffeine、Hazelcast、Infinispan、Memcached、Oracle Coherence等）
- `richie-component-cache`组件，重构`GlobalCache`全局缓存模块，通过配置文件参数`spring.data.redis.enable-l2-caching: true`手工为全局缓存启用L2 Caching，提升全局缓存的性能，但需要注意不合理的全局缓存使用可能会导致服务实例的内存占用过高，请合理使用全局缓存，不要储存过大的对象。且启用二级缓存后，仍然需要通过配置文件参数`spring.data.redis.l2-caching-data`来配置启用需要启用L2 Caching 的数据类型，默认情况下不启用。
- `richie-component-storage-s3`组件，升级SDK到2.x版本，并完整重构S3客户端，提供S3对象存储的连接、查询、上传、下载、删除、复制、签名等功能
- `richie-gateway-service`服务重大更新，支持`防重复提交`、`ECC+AES-GCM加密通信`、`网关支持服务的限流、降级和熔断的配置`
- `richie-gateway-service`服务进行完整的重构，支持了全局异常捕获，将会屏蔽所有后端服务往客户端抛出的异常，同时优化了代码结构和性能，提升了服务的稳定性和可维护性

#### 功能：

- 新增`richie-component-ai`组件（支持Deepseek、ChatGPT、Gemini、Qwen、Claude），用于提供AI能力
- 新增`richie-component-vector`系列组件支持向量数据的处理、索引和查询功能
- 新增`richie-component-statemachine`组件，提供通用状态机能力的支持
- `richie-component-cache`组件，新增基于Redis Stream的高效MQ功能支持，完全支持自动/手动ACK机制、死信队列、监控和统计、链路追踪的TraceId透传和基于事件驱动模型的响应式消费者，吞吐量数倍于普通StreamMQ实现
- 新增`richie-component-mfa`组件，提供多因子认证能力，并集成Spring Cloud Vault密钥管理
- `richie-gateway-service`新增第三方系统 OAuth2.0 认证能力，补充完整令牌管理链路并增强令牌接口安全控制
- `richie-gateway-service`新增网关认证配置校验器，并补充 OAuth2.0 专属安全配置
- `richie-component-cache`组件新增 Redis Stream 分布式唯一ID生成能力与消息数据清理能力
- `richie-component-cache`组件新增 Dragonfly DB 缓存提供者支持

#### 优化：

- `richie-component-cache`组件，分布式锁增加二级锁优化的支持，默认情况下不启用二级锁，需通过配置文件参数`spring.data.redis.enable-local-lock: true`手工启用二级锁
- `richie-component-cache`组件，增加布隆过滤器的支持，默认情况下不启用布隆过滤器，需通过配置文件参数`spring.data.redis.bloom-filter.enable: true`手工启用布隆过滤器
- `richie-component-cache`组件，增加常用数据类型的可防止缓存击穿的系列API方法：`getStringCacheWithLock`|`getObjectCacheWithLock`|`getListCacheWithLock`|`getSetCacheWithLock`|`getHashCacheWithLock`
- `richie-component-cache`组件，优化Redis客户端的配置，提升Redis连接客户端的健壮性
- `richie-component-cache`组件，优化性能和协议安全，默认不支持Redis6.0以下版本的支持，从5.0.0版本开始Redis中间件支持的最低版本为6.0~最新，如需支持老版本redis，请在配置中将`protocol-version`参数设置为`RESP2`，否则默认使用`RESP3`协议
- `richie-component-mqtt`组件，优化消息发布重试机制，将原有递归重试改为最大5次循环重试，避免因网络异常导致内存栈溢出，提升健壮性。
- `richie-component-mqtt`组件，优化持久化机制，默认使用数据库持久化（MqttPersistenceDB，路径为 /tmp/mqtt_persistence），防止客户端重启或断电导致消息丢失，更适合强网环境。
- `richie-component-mqtt`组件，优化自动重连与会话持久化，客户端连接参数强制启用 automaticReconnect（自动重连）和 cleanSession=false（持久化会话），保证断线期间消息和订阅状态不丢失。
- `richie-component-mqtt`组件，优化心跳线程健壮性提升，心跳线程在检测到断线时，最多重试3次自动重连，异常捕获更健壮，防止线程意外终止。
- `richie-component-mqtt`组件，优化断线重连处理，断线后仅做日志记录，重连交由 Paho 内部自动处理，避免多线程环境下的重连冲突。
- `richie-component-mqtt`组件，优化日志与监控增强，所有关键重试、失败、异常均有详细日志输出，便于后续监控和问题排查。
- `richie-component-mqtt`组件，新增`hivemq mqtt client`客户端支持，提供更高性能的 MQTT 客户端，支持 MQTT 3.x 和 5.0，API更加现代，性能优异，文档完善，支持异步和响应式编程，其从5.0.0版本开始作为默认客户端，如需使用`paho`客户端，请在配置文件中设置`platform.component.mqtt.provider=paho`，否则默认使用`hivemq`客户端。
- `richie-component-cache`组件，进一步重构分布式锁与本地缓存管理逻辑，引入高性能序列化框架以优化本地缓存深拷贝性能。
- `richie-component-cache`组件，优化布隆过滤器保护策略，移除高风险的List批量写入接口，统一通过`getListCacheWithLock`进行回源写入以降低缓存击穿风险。
- `richie-component-cache`组件，优化 Redis Stream 数据处理实现，使用 `MultiStringRedisTemplate` 提升处理稳定性。
- `richie-component-mqtt`组件，新增共享订阅能力，并优化消息分发、去重与灰度过滤处理机制。
- `richie-component-statemachine`组件，增强异步线程池存储管理能力，补充定时刷新持久化机制并优化存储配置默认值与扫描配置。
- `richie-base`与`richie-component-dao`，增强基础域对象默认填充与时区序列化能力，并增加批量更新阈值保护插件以降低大批量数据变更风险。
- `richie-gateway-service`服务，优化接口认证过滤器 IP 白名单校验、全局异常处理与多语言能力。

#### BUG修复：

- `richie-component-storage`组件，修复对象存储组件下载文件内容为空的BUG
- `richie-component-mqtt`：组件，修复MQTT3.1.1实现里面一些已知BUG
- `richie-component-cache`组件，修复启用二级缓存后 `GlobalCache` 的 `get/remove` 行为异常问题，并修复全局缓存方法中的静态方法缺失与代码健壮性问题。
- `richie-component-dao`组件，修复批量更新与Getter检查相关空指针问题，提升数据访问稳定性。
- `richie-gateway-service`服务，修复国际化资源替换相关问题并修正重复提交过滤器相关依赖测试配置。

## 4.5.0（2025年09月22日）

#### 功能：

- `richie-component-mqtt`新增MQTT5.0实现，增强针对弱网环境的优化和监控（具体内容参考[https://docs.richie696.cn/component-mqtt5.html](https://docs.richie696.cn/component-mqtt5.html)）
- 新增`richie-component-search`组件，用以支持搜索功能，目前支持 Elasticsearch 作为搜索引擎的实现（具体内容参考[https://docs.richie696.cn/component-search.html](https://docs.richie696.cn/component-search.html)）
- 新增`richie-component-mongodb`组件，提供MongoDB数据库的连接、查询、索引和数据操作功能（具体内容参考[https://docs.richie696.cn/component-mongodb.html](https://docs.richie696.cn/component-mongodb.html)）

## 4.4.0（2025年06月16日）

#### 重大变更：

- `spring-boot` 更新版本至 3.4.5
- `spring-cloud` 更新版本至 2024.0.1
- `spring-cloud-alibaba` 更新版本至 2023.0.3.2
- `spring-cloud-azure` 引擎版本更新至 5.22.0
- `asm` 更新版本至 9.8
- `mysql-connector-j` 连接驱动更新版本至 9.3.0
- `postgresql` 连接驱动更新版本至 42.7.5
- `mybatis-plus` 更新版本至 3.5.12
- `redisson` 更新版本至 3.46.0
- `dynamic-tp` 更新版本至 1.2.1-x
- `gson` 更新版本至 1.13.1
- `hutool` 更新版本至 5.8.38
- `httpclient5` 更新版本至 5.4.4
- `springdoc` 更新版本至 2.8.8
- `commons-io` 更新版本至 2.19.0
- `modelmapper` 更新版本至 3.2.3
- `commons-collections4` 更新版本至 5.0.0
- `lettuce` 更新版本至 6.6.0.RELEASE
- `netty` 更新版本至 4.2.1.Final
- `guava` 更新版本至 33.4.8-jre
- `protobuf-java` 更新版本至 4.31.0
- 新增`richie-component-skywalking`组件，用于支持自建 skywalking 的链路追踪能力
- 新增`richie-component-pulsar`组件，用于增加对 Apache Pulsar 消息队列的支持

#### 改进：

- 框架适配最新的 spring-boot 3.4.4 及以上版本与框架兼容性的问题
- Domain 域对象全面支持国际化时间格式
- `richie-component-storage`：所有的对象存储组件均支持通过配置文件的方式，指定存储桶的存储类型，具体参考：StorageTypeEnum 枚举
- `richie-component-i18n`：支持多时区（含夏令时）和多语言的国际化
- `richie-component-cache`：优化GlobalCacheManager去除获取方法的锁，优化执行性能
- `richie-component-cache`：增加了 `spring.data.redis.enable-redisson-lock（默认：false）`参数配置，用于启用 Redisson 分布式锁的支持，不配置默认使用自由锁实现
- 改进SSO登录功能，区分PC端和移动端，细化多端登录时候的差异化处理
- 本地缓存增加 Caffeine 的支持
- 完整剔除 JDK21 预览特性中的字符串模板

## 4.3.0（2025年01月08日）

#### 重大变更：

- spring-boot 更新版本至 3.3.7
- spring-cloud 更新版本至 2023.0.4
- spring-cloud-alibaba 更新版本至 2023.0.1.2

#### 改进：

- `JsonUtils`工具类增加`configuration`方法，用于自定义配置Json序列化的默认配置信息（使用`JsonConfiguration`配置类进行重新配置）
- `richie-component-mqtt`：优化组件的在执行发消息操作时，如果连接断开，则会执行自动重连
- `poi` 更新版本至 5.3.0
- `easyexcel` 更新版本至 4.0.3
- `mybatis-plus` 更新版本至 3.5.9
- `dynamic-tp` 更新版本至 1.1.8.1-3x
- `hutool` 更新版本至 5.8.32
- `commons-lang3` 更新版本至 3.17.0
- `xxljob` 更新版本至 4.2.1（legacy）
- `powerjob` 更新版本至 5.1.0
- `mongodb` 更新版本至 5.2.0

#### 功能：

- `richie-component-http` 的 API 提供了自定义超时时长的接口
- `richie-component-http` 增加了全局超时时长配置
- `richie-component-log` 组件新增Service类的日志切面，用于记录Service类的访问日志（@LogTrace、@LogMethodTrace）
- `richie-component-cache` 增加消息队列功能的支持，可以快速方便地实现消息队列功能
- `richie-component-web` 新增 SSE 功能支持
- `richie-component-dao` 默认开启防全表更新和删除的功能
- `richie-component-dao` 默认开启非法SQL检查功能
- `richie-component-dao` 默认开启乐观锁功能（参考Mybatis-plus乐观锁插件文档）
- `richie-component-dao` 默认开启批量更新的数据变动记录功能，且默认批量更新数量不能超过1000条，否则会抛出异常阻止一个事务中存在过多的数据变动

#### BUG修复：

- `richie-component-messaging` 修复幂等去重功能的BUG
- `richie-component-messaging` 修复消息重发功能，因为消息锁不释放导致无法二次消费的BUG

## 4.2.6（2024年12月23日）

#### 功能：

- 框架新增对Microsoft Azure的支持
- `richie-component-storage-azure`：新增Azure Blob存储组件
- `richie-component-web`增加无网关模式，其包含令牌签名、校验和跨域功能

#### 改进：

- 移除自3.0.0版本开始被标识为废弃的接口和类

## 4.2.5（2024年10月19日）

#### 改进：

- `richie-component-cache`：缓存对象的接口从字符串格式替换成Hash格式
- `richie-component-http`：OkHttpClientApi的异步请求启用虚拟线程
- JsonUtils增加对象转Map的接口

## 4.2.4（2024年10月18日）

#### 重大变更：

- `richie-component-cache`：缓存对象的接口`getObjectCache`和`addObjectCache`底层存储数据结构从字符串格式替换成Hash格式
- `richie-component-http`：OkHttpClientApi的异步请求启用虚拟线程

#### 功能：

- `richie-context`：JsonUtils增加对象转Map的接口

## 4.2.3（2024年10月05日）

#### 改进：

- `richie-component-mqtt`：MQTT组件在服务端增加了自动重连机制
- `richie-component-web`：Undertow启用HTTP/2协议的支持
- `richie-component-web`：Undertow启用HTTP/2的服务段推送功能
- `richie-component-cache`：`GlobalCache`增加乐观锁和悲观锁的API：`optimisticLock` 和 `pessimisticLock`

#### BUG修复：

- `richie-component-tracing`：解决OpenTelemetry的版本冲突问题
- `richie-component-cache`：修复`LocalCache`内对象锁无效的问题
- `richie-component-logging`：修复切面日志禁用时，无法彻底关闭的问题
- `richie-component-logging`：修复切面日志发送Kafka时，返回的数据结构为`MessageEvent`对象

## 4.2.2（2024年08月23日）

#### BUG修复：

- 更新依赖组件版本，修复大量组件的CVE漏洞

#### 改进：

- `richie-context`：网关配置`IOAuthFilterConfig`接口授权过滤器配置增加`allowIps`字段

## 4.2.1（2024年08月02日）

#### BUG修复：

- `richie-component-storage-cos`：修复COS文件流上传的BUG

#### 改进：

- `richie-component-storage-cos`：排除掉`okio`旧版本依赖
- `richie-context`：网关配置`SsoConfig`配置增加`ssoLoginUrl`字段

## 4.2.0（2024年05月13日）

#### 重大变更：

- 增加全新组件`richie-component-ocr`，支持OCR识别能力
- Docker打包插件`jib-maven-plugin`更新版本至 3.4.1
- 引入全新的计划任务组件`powerjob`，用于未来替代`xxljob`
- `org.apache.oltu.oauth2.client` OAuth2客户端依赖组件废弃
- 新增`okta`OAuth2客户端依赖组件

#### 功能：

- richie-component-http 组件新增批量文件发送的功能
- richie-component-logging 组件新增扩展日志字段记录到目标数据源的功能（需要配合HeaderContextHolder类和GlobalConstants.X_RD_REQUEST_EXTRA使用）

#### 改进：

- 优化切面日志记录内容，对启用切面日志组件的项目，将会明确记录请求异常信息

#### BUG修复：

- 更新MongoDB插件，解决目标字段类型为BigDecimal时可能造成的设值失败的问题

## 4.1.0（2024年03月13日）

#### 重大变更：

- Spring Boot 更新版本至 3.2.3（全面支持虚拟线程）
- Spring Cloud 更新版本至 2023.0.0（全面支持虚拟线程）
- SpringDoc 更新版本至 2.3.0
- Apache Commons Lang 3 更新版本至 3.14.0
- Apache Commons Compress 更新版本至 1.25.0
- brave 更新版本至 5.17.0
- ber-tlv 更新版本至 1.0-11
- hutool 更新版本至 5.8.25
- modelmapper 更新版本至 3.2.0
- aliyun-sdk-oss 更新版本至 3.17.4
- 腾讯云 cos_api 更新版本至 5.6.191
- 华为云 esdk-obs-java 更新版本至 3.23.9.1
- minio 更新版本至 8.5.7
- aws-java-sdk 更新版本至 1.12.628
- ve-tos-java-sdk 更新版本至 2.6.5
- ks3-kss-java-sdk 更新版本至 1.0.6
- mybatis-plus 更新版本至 3.5.5
- mybatis-plus-join 更新版本至 1.4.8.1
- mybatis-spring-boot-starter 更新版本至 3.0.3
- mysql-connector-j 更新版本至 8.2.0
- postgresql 更新版本至 42.7.1
- jakarta.validation-api 更新版本至 3.1.0-M1
- jakarta.persistence-api 更新版本至 3.2.0-M1
- jakarta.annotation-api 更新版本至 3.0.0-M1
- easyexcel 更新版本至 3.3.3
- guava 更新版本至 33.0.0-jre
- easy-trans-spring-boot-starter 更新版本至 3.0.0
- maven-javadoc-plugin 插件更新版本至 3.6.3
- build-helper-maven-plugin 插件更新版本至 3.5.0
- maven-compiler-plugin 插件更新版本至 3.12.1
- maven-surefire-plugin 插件更新版本至 3.2.3
- 新增链路追踪组件 richie-component-tracing
- 移除 spring-cloud-sleuth 组件的依赖，全面转向 opentelemetry

#### 功能：

- 新增 richie-component-storage 存储组件（包含本地存储、FTP存储、SFTP存储、SMB存储、对象存储[AWS S3、腾讯云COS、阿里云OSS、华为云OBS、抖音火山云TOS、金山云KS3、Minio]）

#### 改进：

- richie-component-cache 支持 Epoll 方式连接 Redis 并保活（此功能仅Linux系统可用）
- 分拆 richie-component-messaging 组件，将 MQ Binder 的依赖分拆为子包，以优化打包后的程序体积
- 优化 richie-component-messaging 组件的消费者通用处理接口，防止可能出现的消息转换错误问题
- 适配升级新版 Mybatis Plus 后导致的 OrderItem 类构造函数变更导致的问题
- 适配1.1.6.1-3x版本动态线程池更新带来的API变动
- 废弃 JsonUtils 中 convertMapToBean 相关方法
- JsonUtils 中新增 convertObject 方法用于替代 convertMapToBean 方法
- 删除 redeen-component-cache 中 jetcache 的依赖
- redeen-component-messaging 中 rocketmq 的 binder 依赖 scope 改为 provide
- Redis 连接池支持使用 Epoll 方式连接并保活（此功能仅Linux系统可用）
- 优化 richie-context 包的体积，精简无用的依赖和工具类

#### BUG修复：

- 修复 TenantCodeContextHolder.getTenantCode() 为 null 的情况下，ConcurrentHashMap get null 的 npe 问题
- 修复 FeignClientRequestInterceptor 中因为 Content-Length 大小写问题导致的忽略失败的问题
- 修复 FeignClientRequestInterceptor 中因为本次请求类型为 x-form-data 导致 feign 传递时错误赋值 Content-Type 头导致的 feign 请求失败问题

## 4.0.2-RELEASE (2024年1月2日)

#### 功能：

- 增加 ehcache jsr107 标准接口的本地缓存实现
- 切面日志增加数据库持久化功能

## 4.0.1-RELEASE (2023年12月20日)

#### BUG修复：

- 修复GlobalCache中getFromHash方法的类型转换BUG

## 4.0.0-RELEASE (2023年12月19日)

#### 重大变更：

- JDK 版本升级至 21
- Spring Boot 版本升级至 3.1.6
- Spring Cloud 版本升级至 2022.0.4
- 重构全部的 component 组件
- 重构全部的 base 组件
- 新增 richie-component-tracing 链路追踪组件
- 新增 richie-component-mongodb 组件

#### 功能：

- 多语言修改，新增X-RD-Request-Language请求头
- 新增自定义数据传播线程池，用于异步线程池的 ThreadLocal 传递
- 新增国际化时区，获取映射门店和时区的关系
- i18n移除国际化，迁移到spring-cloud-component-web模块的i18n包里
- Cache 接口增加大量 ZSet、Set 数据结构的新接口

#### 改进：

- JsonUtils类增加cloneMapper方法，用于克隆一个新的JsonMapper对象
- GlobalCache的valueSerializer序列化器更换为JsonUtils的JsonMapper对象
- WebMvcAutoConfiguration增加对JavaTimeModule功能的支持

#### BUG修复：

- 修复令牌续签BUG
- 删除etcd，修改dtp版本
- 多数据源修复bug，修复无租户+使用@DS("xxx")注解的情况导致@DS注解内容使用主数据源
- 修复Long类型toString无效的BUG
- 修复JsonUtils类新改动导致的BUG
- 修改多租户bug
- 租户header修复
- 多数据源切换修复
- 多数据源切换 获取master的数据源id，有多个则取第一
- 修复GlobalCache中涉及时区问题的BUG
- JsonUtils类支持按照时间格式创建多实例，以便提供不同时间格式国际化展示时，提供正确的时间格式化

## 3.0.0-RELEASE (2023年10月14日)

#### 重大变更：

- Maven 最低版本要求上调至 Maven 3.9.x
(下载地址：[https://dlcdn.apache.org/maven/maven-3/3.9.4/binaries/apache-maven-3.9.4-bin.zip](https://dlcdn.apache.org/maven/maven-3/3.9.4/binaries/apache-maven-3.9.4-bin.zip))
- Spring Boot 升级至 3.1.3
- Spring Cloud 升级至 2022.0.0
- 升级基础组件库内的代码，以支持 Spring Boot 3.x 版本
- 移除 Druid 数据库连接池，改用 HikariCP 数据库连接池
- 新增 richie-component-http 组件，并加入全新的异步接口实现，并支持内省方式解析返回值
- 新增 richie-component-logging 组件
- 新增 richie-component-i18n 组件
- 新增 richie-component-storage 组件（Preview版本）
- 移除 richie-component-apidoc 组件
- 移除 richie-component-usb4j 组件
- 移除 richie-component-payment 组件
- 移除 richie-component-operatelog 组件

#### 功能：

- 引入 SpringDoc 作为新的 Api 文档组件
- 引入 TransmittableThreadLocal 作为新的 ThreadLocal 组件，以支持异步线程池的 ThreadLocal 传递
- 新增全局的 Request Header 传递工具类 HeaderContextHolder
- 新增全局的 LoginUser 持有对象工具类 LoginUserContextHolder
- richie-component-dao 组件增加多租户功能支持
- richie-component-dao 增加全局分布式ID算法工具类
- 融合 BOH 业务线的部分代码作为共通代码
- 增加国密算法 SM2/3/4 的支持
- GlobalCache 新增 addStringCacheIfAbsent 和 addObjectCacheIfAbsent 接口，用于在缓存不存在时新增缓存

#### 改进：

- 移除大量废弃的代码
- richie-component-cache 缓存组件支持大量新接口特性，包括：各数据类型的批量操作、过期点设置、copy、move等众多新特性
- 重构 OkhttpUtils 工具类为 richie-component-http 组件，并移除该类
- 新增 feign 接口拦截器，用于处理 token 令牌和调用地址的传递
- JWT 令牌加入自动续期功能
- 去除 MQTT 服务的内外网切换功能

#### BUG修复：

- 去除对 common-beanutils 包的依赖，避免与 Spring Boot 3.x 版本的冲突
- 修复 MQ 组件序列化导致 frozen 和 delay 字段丢失的问题
- 升级基础组件包的版本，以修复大量基础组件包的漏洞和BUG
- 修复 richie-component-mqtt 组件工作在消费者端可能出现重复订阅导致消费者相互抢占消息通道的问题、
- 修复 MQ 的幂等去重功能配置文件的错误

## 2.2.0 (2023年8月7日)

#### 功能：

- 新增动态线程池组件
- 新增 Websocket 工具类

#### 改进：

- OkHttpUtils 改造为支持动态线程池的版本
- JWTUtils完善了令牌的创建流程
- MQ 组件增加消费失败后的重试机制
- 优化缓存支持按 Key 设置过期时间
- 废弃并删除 fastjson 组件（请改用 Jackson 组件）
- 废弃 richie-component-amqp 组件（请改用 richie-component-messaging 组件）
- 移除 richie-component-service-client-core 组件
- richie-component-cache 缓存组件支持多数据源配置（Slave数据源通过缓存 Key 开头增加 数据源别名 @@ 的方式来访问 Slave 数据源）
- richie-component-cache 缓存组件增加对 Redis ZSet 数据结构的支持
- richie-component-cache 缓存组件增加对全数据类型的批量操作支持（此操作非原子性操作，需注意并发问题。）
- richie-component-cache 缓存组件支持按单个 Cache Key 设置过期时间（#时间 单位：秒），例：@Cacheable(key = "cache key#60")
- richie-component-messaging 消息队列组件的消费者支持处理失败的消息重新入队列的功能
- richie-component-mqtt MQTT 组件优化 DeviceId 生成规则，防止在单设备上跑多实例时无法生成唯一 DeviceId 的问题

#### BUG修复：

- 其它大量 SonarQube 扫描问题的修复
- 修复缓存组件的默认全局缓存过期时间重复设置导致默认1天过期时间变成30分钟的问题
- 修复部分组件的依赖传递导致不必要的依赖传递

#### 重大变更：

- 增加 Postgresql 的支持
- JDK 版本升级至 17
- 升级 Spring Boot 版本为 2.7.13
- 升级 Spring Cloud 版本为 2021.0.8
- 升级 Spring Cloud Alibaba 版本为 2021.0.5.0
- Guava 组件升级至 32.1.1-jre
- snakeyaml 组件升级到 2.0

## 2.1.3 (2023年4月27日)

#### 功能：

- 无

#### 改进：

- JsonUtils增加File、InputStream、Reader、URL、DataInput和JsonParser类型的反序列化的支持
- OperateLog组件增加操作人信息的支持接口

#### BUG修复：

- 无
- 

#### 重大变更：

- 无

## 2.1.2 (2023年4月23日)

#### 功能：

- 无

#### 改进：

- 优化 GlobalCache 组件的日志输出内容
- 优化 GlobalCache 组件API接口性能

#### BUG修复：

- 解决 GlobalCache 组件内部分接口在组合调用时可能会产生分布式锁死锁的问题

#### 重大变更：

- 无

## 2.1.1 (2023年4月8日)

#### 功能：

- OkhttpUtils 新增文件上传和下载的API

#### 改进：

- 优化 GlobalCache 组件的日志输出内容
- 优化 GlobalCache 组件API接口性能

#### BUG修复：

- 修复分布式锁可重入机制的BUG
- 修复MQTT客户端ID无法在单机上生成多个ID的BUG

#### 重大变更：

- 无

## 2.1.0 (2023年3月21日)

#### 功能：

- 无

#### 改进：

- JDK版本更新到 11 LTS
- 各组件内部实现全部从JDK8语法升级到JDK11语法

#### BUG修复：

- 修复 `security.utils.com.richie.context.HashUtils` 内，`generateMD5()`方法因为生成的 MD5 值因为首位是0导致精度丢失的 BUG

#### 重大变更：

- 无

## 2.0.2(2023年02月21日)

#### 功能:

- 无

#### 改进:

- richie-base-common: ResultVO的getSuccess()方法增加无需提供参数的方法
- richie-base-utils: 删除"com.richie.base.utils.JsonUtils"废弃类，请使用"data.utils.com.richie.context.JsonUtils"替代

#### BUG修复:

- 无
- 

#### 重大变更:

- 无

## 2.0.1(2023年02月20日)

#### 功能:

- 无

#### 改进:

- 无

#### BUG修复:

- 排除pagehelper的jsqlparser组件，避免与mybatis-plus冲突
- 替换MySQL驱动坐标

#### 重大变更:

- 无

## 2.0.0 (2023年2月16日)

#### 功能：

- 提供全局托管的项目版本管理
- 提供全局公用工具类
- 提供全局公共接口和DTO的定义

#### 改进：

- Spring Boot 版本更新到 2.7.8
- Spring Cloud 版本更新到 2021.0.5
- Spring Cloud Alibaba 版本更新到 2021.0.4.0
- Java SDK 版本更新到 11
- maven-javadoc-plugin 版本更新到 3.4.1
- maven-source-plugin 版本更新到 3.2.1
- maven-compiler-plugin 版本更新到 3.10.1
- maven-enforcer-plugin 版本更新到 3.0.0
- maven-resources-plugin 版本更新到 3.3.0
- maven-source-plugin 版本更新到 3.2.1
- maven-surefire-plugin 版本更新到 2.22.2
- build-helper-maven-plugin 版本更新到 3.3.0
- xml-maven-plugin 版本更新到 1.0.2
- Apache POI 版本更新到 5.2.3
- Jackson 版本更新到 2.14.1
- hutool 版本更新到 5.8.11
- fastjson 版本更新到 2.0.21
- pagehelper-spring-boot-starter 版本更新到 1.4.6
- okhttp 版本更新到 4.10.0
- mybatis-plus 版本更新到 3.5.3.1
- mybatis-spring-boot-starter 版本更新到 2.3.0
- mysql-connector-java 版本更新到 8.0.32
- druid-spring-boot-starter 版本更新到 1.2.13
- p6spy-spring-boot-starter 版本更新到 1.8.1
- mapstruct 版本更新到 1.5.3.Final
- guava 版本更新到 31.1-jre

#### BUG修复：

- 无

#### 重大变更：

- 无

