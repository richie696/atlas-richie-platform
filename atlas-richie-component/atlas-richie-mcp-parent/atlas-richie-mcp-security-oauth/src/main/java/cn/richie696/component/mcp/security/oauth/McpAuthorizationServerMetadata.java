package cn.richie696.component.mcp.security.oauth;

import java.net.URI;
import java.util.List;

/**
 * RFC 8414/OIDC Authorization Server Metadata 的最小内部模型。
 *
 * <p>该 record 是 MCP 客户端在 OAuth 发现阶段（{@code /.well-known/oauth-authorization-server}）
 * 解析授权服务器元数据后的归一化视图，只保留 MCP 流程必需的字段（issuer、authorization_endpoint、
 * token_endpoint、registration_endpoint 与相关能力集），其余字段被有意舍弃以保持模型紧凑。
 *
 * <p>设计要点：仅持有 MCP 运行时真正消费的能力字段，避免引入与 OAuth2.1 / OIDC 全量规范耦合的
 * 大对象；同时通过紧凑构造器对集合做不可变拷贝，确保元数据一旦装载便不会被外部篡改，配合
 * {@link McpOAuthMetadataClient} 的 URI 校验保证 issuer/endpoint 的来源安全。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpAuthorizationServerMetadata(
        URI issuer,
        URI authorizationEndpoint,
        URI tokenEndpoint,
        URI registrationEndpoint,
        List<String> responseTypesSupported,
        List<String> grantTypesSupported,
        List<String> codeChallengeMethodsSupported) {

    /**
     * 紧凑构造器：对集合字段做不可变防御性拷贝，并对 issuer 做非空校验。
     *
     * <p>为什么强制不可变：metadata 在 OAuth 流程中会被多线程复用（PKCE 生成、token 刷新、
     * registration 等），不可变结构能避免共享状态导致的并发修改问题；issuer 非空是后续
     * token 校验与 {@code iss} 字段比对的前置条件，必须在创建阶段即保证。</p>
     *
     * @param issuer 授权服务器标识符（必填）
     * @param authorizationEndpoint 授权端点，可为 null（部分 AS 不暴露）
     * @param tokenEndpoint 令牌端点，可为 null
     * @param registrationEndpoint 动态注册端点，可为 null
     * @param responseTypesSupported 支持的 response_type 列表，null 视为空集
     * @param grantTypesSupported 支持的 grant_type 列表，null 视为空集
     * @param codeChallengeMethodsSupported 支持的 PKCE 算法列表，null 视为空集
     * @throws NullPointerException 当 issuer 为 null 时
     */
    public McpAuthorizationServerMetadata {
        issuer = java.util.Objects.requireNonNull(issuer, "issuer");
        responseTypesSupported = responseTypesSupported == null ? List.of() : List.copyOf(responseTypesSupported);
        grantTypesSupported = grantTypesSupported == null ? List.of() : List.copyOf(grantTypesSupported);
        codeChallengeMethodsSupported = codeChallengeMethodsSupported == null
                ? List.of() : List.copyOf(codeChallengeMethodsSupported);
    }
}
