/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.oauth.dcr.spi;

import cn.richie696.component.oauth.dcr.model.ClientIdMetadataDocument;

/**
 * 客户端元数据文档解析器接口。
 * <p>
 * 把"按 clientId(以及可选的外部 metadataUri)解析出已注册元数据"从 DCR 协议层中拆出:默认走 Redis
 * (见 {@link DefaultClientIdMetadataDocumentResolver}),OAuth Service 可替换为从企业 IdP / CMDB
 * 拉取的实现;外部 metadataUri 在解析前必经 {@link SSRFProtection} 校验。
 * </p>
 * <p>
 * 处于 oauth-dcr 的元数据接入位置:由 {@link DynamicClientRegistrationEndpoint} 在更新与读取
 * 客户端时调用;SPI 暴露让业务方可以接入内部审批/标签/部门归属等扩展属性,而无需改协议层。
 * </p>
 * <p>
 * 解决的问题:把元数据解析从协议层剥离,允许业务方把"客户端属于哪个部门/审批人是谁"这类企业内部
 * 属性融入到 DCR 流程;同时 SSRF 校验被封装到默认实现里,业务方自己写解析器时不会漏掉安全检查。
 * </p>
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
