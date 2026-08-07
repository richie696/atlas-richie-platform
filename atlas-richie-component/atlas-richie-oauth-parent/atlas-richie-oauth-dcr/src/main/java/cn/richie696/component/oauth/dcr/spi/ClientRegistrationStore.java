package cn.richie696.component.oauth.dcr.spi;

import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.dcr.model.ClientIdMetadataDocument;

/**
 * 动态客户端注册的持久化端口。
 * <p>
 * 把"如何保存/更新/查询已注册的客户端"从 {@link DynamicClientRegistrationEndpoint} 中拆出:默认
 * 走 Redis(见 {@link RedisClientRegistrationStore}),OAuth Service 可替换为带事务的数据库实现,
 * 让 DCR 元数据与业务库在同一事务内提交。
 * </p>
 * <p>
 * 处于 oauth-dcr 的元数据接入位置:由 {@link DynamicClientRegistrationEndpoint} 直接调用;写入
 * 同时涉及 {@code OAUTH2_CLIENT_META} 与 {@code OAUTH2_CLIENT_CONFIG} 两类 Key,确保
 * {@link cn.richie696.component.oauth.core.ClientRegistry} 能读到一致数据。
 * </p>
 * <p>
 * 解决的问题:让 DCR 元数据可以和企业内部的客户端管理、审批流程共用同一持久层,避免注册数据散落在
 * Redis;同时通过 SPI 边界让"替换为数据库事务"这种需求无需修改协议层。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public interface ClientRegistrationStore {

    void save(ClientIdMetadataDocument metadata, ClientConfig client, String registrationAccessToken, long ttlMillis);

    void update(ClientIdMetadataDocument metadata, long ttlMillis);

    boolean exists(String clientId);
}
