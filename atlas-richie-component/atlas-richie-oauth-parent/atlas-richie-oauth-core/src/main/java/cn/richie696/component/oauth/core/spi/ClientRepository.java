package cn.richie696.component.oauth.core.spi;

import cn.richie696.component.oauth.core.model.ClientConfig;

/**
 * OAuth 客户端权威数据的仓储端口。
 * <p>
 * 把"如何存/取 ClientConfig"从 {@link ClientRegistry} 中拆出:默认走 Redis(见
 * {@link cn.richie696.component.oauth.core.support.CacheBackedClientRepository}),
 * OAuth Service 可替换为 JDBC / LDAP / 多租户分区等实现,而不必修改任何协议层代码。
 * </p>
 * <p>
 * 处于 oauth-core 的客户端数据接入位置:由 {@link ClientRegistry} 调用,对上层隐藏存储细节;
 * 注册(动态注册/静态配置)与读取(client_credentials、授权码、PKCE)共用同一接口。
 * </p>
 * <p>
 * 解决的问题:让客户端元数据的存储后端独立可换,业务方可以无缝对接企业 LDAP、CMDB 或多租户分区
 * 存储,同时保留 OAuth 协议层的零侵入。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public interface ClientRepository {

    ClientConfig find(String clientId);

    void save(ClientConfig client);

    default void delete(String clientId) {
        throw new UnsupportedOperationException("当前 ClientRepository 不支持删除");
    }
}
