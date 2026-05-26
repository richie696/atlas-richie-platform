package com.richie.component.mfa.core.config.properties;

import com.richie.component.mfa.core.constant.CloudKmsProviderEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 云 KMS 配置属性
 * <p>
 * 配置前缀：{@code platform.component.mfa.security.key-management.kms}
 * <p>
 * 用于配置云 KMS 服务商的连接信息和密钥参数
 * <p>
 * 此配置类位于 core 模块，供 management 和 validation 模块共同使用
 *
 * @author richie696
 * @since 5.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mfa.security.key-management.kms")
public class MfaCloudKmsProperties {

    /**
     * 云 KMS 服务商
     * <p>
     * 支持的云服务商：
     * <ul>
     *   <li>AWS：AWS KMS</li>
     *   <li>ALIYUN：阿里云 KMS</li>
     *   <li>TENCENT：腾讯云 KMS</li>
     *   <li>VOLCENGINE：火山引擎 KMS</li>
     *   <li>HUAWEI：华为云 KMS</li>
     * </ul>
     * <p>
     * 默认值：AWS
     */
    private CloudKmsProviderEnum provider = CloudKmsProviderEnum.AWS;

    /**
     * 区域（Region）
     * <p>
     * 云 KMS 服务所在的区域
     * <p>
     * 示例：
     * <ul>
     *   <li>AWS：us-east-1、ap-southeast-1</li>
     *   <li>阿里云：cn-hangzhou、cn-beijing</li>
     *   <li>腾讯云：ap-guangzhou、ap-shanghai</li>
     *   <li>火山引擎：cn-north-1</li>
     *   <li>华为云：cn-north-4</li>
     * </ul>
     */
    private String region;

    /**
     * 访问密钥 ID（Access Key ID）
     * <p>
     * 云服务商的访问密钥 ID，用于身份认证
     * <p>
     * 注意：生产环境应使用环境变量或密钥管理服务存储，不要硬编码在配置文件中
     */
    private String accessKeyId;

    /**
     * 访问密钥（Secret Access Key）
     * <p>
     * 云服务商的访问密钥，用于身份认证
     * <p>
     * 注意：生产环境应使用环境变量或密钥管理服务存储，不要硬编码在配置文件中
     */
    private String accessKeySecret;

    /**
     * 密钥 ID（Key ID）
     * <p>
     * 在云 KMS 中创建的加密密钥标识，用于加密/解密操作
     */
    private String keyId;

    /**
     * 端点（Endpoint，可选）
     * <p>
     * 云 KMS 服务的自定义端点地址
     * <p>
     * 如果不配置，将根据 provider 和 region 自动生成标准端点
     */
    private String endpoint;
}
