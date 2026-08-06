package cn.richie696.component.vector.config;

import com.volcengine.vikingdb.model.FieldForCreateVikingdbCollectionInput;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * VikingDB 连接与 collection 配置。
 */
@Data
@ConfigurationProperties(prefix = "platform.component.vector.vikingdb")
public class VikingDbConfig {

    private String host;
    private String controlEndpoint;
    private String region = "cn-beijing";
    private String accessKey;
    private String secretKey;
    private String scheme = "HTTPS";
    private String collectionName = "documents";
    private String indexName = "documents";
    private int embeddingDimension = 1536;
    private boolean initializeSchema;
    private String projectName;
    private String description;
    private Integer shardCount;
    private Map<String, FieldForCreateVikingdbCollectionInput.FieldTypeEnum> metadataFields = Map.of();
}
