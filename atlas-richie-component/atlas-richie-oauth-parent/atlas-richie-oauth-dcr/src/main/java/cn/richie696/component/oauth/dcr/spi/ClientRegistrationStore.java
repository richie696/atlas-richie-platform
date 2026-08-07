package cn.richie696.component.oauth.dcr.spi;

import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.dcr.model.ClientIdMetadataDocument;

/** DCR 持久化端口，OAuth Service 可替换为数据库事务实现。 */
public interface ClientRegistrationStore {

    void save(ClientIdMetadataDocument metadata, ClientConfig client, String registrationAccessToken, long ttlMillis);

    void update(ClientIdMetadataDocument metadata, long ttlMillis);

    boolean exists(String clientId);
}
