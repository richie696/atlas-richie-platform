package com.richie.component.oauth.dcr.spi;

import com.richie.component.oauth.dcr.model.ClientIdMetadataDocument;

/**
 * Client ID Metadata Document 解析器接口
 * <p>
 * 支持 RFC 7591 扩展，允许客户端通过外部文档声明元数据。
 *
 * @author richie696
 * @since 2026-06-12
 */
public interface ClientIdMetadataDocumentResolver {

    /**
     * 解析 Client ID Metadata Document
     *
     * @param clientId    客户端 ID
     * @param metadataUri Metadata Document URI（可为 null）
     * @return 解析后的 Metadata Document
     */
    ClientIdMetadataDocument resolve(String clientId, String metadataUri);

    /**
     * 获取客户端的默认 Metadata Document URI
     *
     * @param clientId 客户端 ID
     * @return Metadata Document URI，若无则返回 null
     */
    String getMetadataUri(String clientId);
}
