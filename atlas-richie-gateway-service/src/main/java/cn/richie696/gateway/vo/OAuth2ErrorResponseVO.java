package cn.com.rydeen.gateway.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2.0 错误响应 VO（符合 RFC 6749 §5.2）
 * <p>
 * 包含 {@code error} 与 {@code error_description} 两个必选字段，以及
 * 可选的 {@code error_uri} 字段。 {@code error_uri} 由配置
 * {@code platform.gateway.oauth2-error-doc-base-uri} 控制：
 * <ul>
 *   <li>配置了有效值 → 返回 {@code error_uri}，值为 {@code {baseUri}{error}}（如
 *       {@code https://docs.example.com/errors#invalid_client}）</li>
 *   <li>未配置或为空 → 不返回 {@code error_uri}，避免向外泄露系统支持的错误码白名单</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OAuth2ErrorResponseVO {

    /**
     * 错误码，例如：invalid_request / invalid_client / invalid_grant ...
     */
    @JsonProperty("error")
    private String error;

    /**
     * 错误描述（可读文本）
     */
    @JsonProperty("error_description")
    private String errorDescription;

    /**
     * 错误说明文档链接（可选，由配置 {@code platform.gateway.oauth2-error-doc-base-uri} 控制）。
     * <p>
     * 配置后返回 {@code {baseUri}{error}}，未配置或为空时不返回此字段（{@code null} 被 Jackson
     * 自动忽略）。
     */
    @JsonProperty("error_uri")
    private String errorUri;
}
