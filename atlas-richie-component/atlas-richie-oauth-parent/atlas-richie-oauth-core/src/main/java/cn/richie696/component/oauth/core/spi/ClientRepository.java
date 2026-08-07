package cn.richie696.component.oauth.core.spi;

import cn.richie696.component.oauth.core.model.ClientConfig;

/** Client 权威数据仓储端口，OAuth Service 可替换为 JDBC/LDAP/多租户实现。 */
public interface ClientRepository {

    ClientConfig find(String clientId);

    void save(ClientConfig client);

    default void delete(String clientId) {
        throw new UnsupportedOperationException("当前 ClientRepository 不支持删除");
    }
}
