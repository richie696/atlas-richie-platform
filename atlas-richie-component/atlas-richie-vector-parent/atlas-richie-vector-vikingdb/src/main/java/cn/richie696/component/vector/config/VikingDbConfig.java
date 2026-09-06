package cn.richie696.component.vector.config;

import com.volcengine.vikingdb.model.FieldForCreateVikingdbCollectionInput;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbFilterValidationMode;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbIndexVectorOptions;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbPostProcessOperation;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbSearchAdvanceOptions;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbSearchCommonOptions;
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
    private java.util.List<String> scalarIndex = java.util.List.of();
    private Map<String, FieldForCreateVikingdbCollectionInput.FieldTypeEnum> metadataFields = Map.of();
    private VikingDbFilterValidationMode filterValidationMode = VikingDbFilterValidationMode.DECLARED_FIELDS;
    private SearchDefaultsProperties searchDefaults = new SearchDefaultsProperties();
    private SearchAdvanceDefaultsProperties searchAdvanceDefaults = new SearchAdvanceDefaultsProperties();
    private IndexVectorProperties indexVectorOptions = new IndexVectorProperties();

    public VikingDbSearchCommonOptions searchDefaultsModel() {
        return VikingDbSearchCommonOptions.builder()
                .limit(searchDefaults.limit).offset(searchDefaults.offset).partition(searchDefaults.partition)
                .outputFields(searchDefaults.outputFields).returnSchema(searchDefaults.returnSchema)
                .returnDownloadUrl(searchDefaults.returnDownloadUrl)
                .returnAnalyzedResult(searchDefaults.returnAnalyzedResult)
                .returnDetailInfo(searchDefaults.returnDetailInfo).build();
    }

    public VikingDbSearchAdvanceOptions searchAdvanceDefaultsModel() {
        return VikingDbSearchAdvanceOptions.builder()
                .denseWeight(searchAdvanceDefaults.denseWeight).idsIn(searchAdvanceDefaults.idsIn)
                .idsNotIn(searchAdvanceDefaults.idsNotIn)
                .postProcessOperations(searchAdvanceDefaults.postProcessOperations.stream()
                        .map(item -> VikingDbPostProcessOperation.of(item.type, item.parameters)).toList())
                .postProcessInputLimit(searchAdvanceDefaults.postProcessInputLimit)
                .scaleK(searchAdvanceDefaults.scaleK).filterPreAnnLimit(searchAdvanceDefaults.filterPreAnnLimit)
                .filterPreAnnRatio(searchAdvanceDefaults.filterPreAnnRatio).build();
    }

    public VikingDbIndexVectorOptions indexVectorOptionsModel() {
        return VikingDbIndexVectorOptions.builder()
                .type(indexVectorOptions.type).distance(indexVectorOptions.distance)
                .quantization(indexVectorOptions.quantization).hnswM(indexVectorOptions.hnswM)
                .hnswCef(indexVectorOptions.hnswCef).hnswSef(indexVectorOptions.hnswSef)
                .diskannM(indexVectorOptions.diskannM).diskannCef(indexVectorOptions.diskannCef)
                .cacheRatio(indexVectorOptions.cacheRatio).pqCodeRatio(indexVectorOptions.pqCodeRatio).build();
    }

    @Data
    public static class SearchDefaultsProperties {
        private Integer limit;
        private Integer offset;
        private String partition;
        private java.util.List<String> outputFields;
        private Boolean returnSchema;
        private Boolean returnDownloadUrl;
        private Boolean returnAnalyzedResult;
        private Boolean returnDetailInfo;
    }

    @Data
    public static class SearchAdvanceDefaultsProperties {
        private Double denseWeight;
        private java.util.List<Object> idsIn = java.util.List.of();
        private java.util.List<Object> idsNotIn = java.util.List.of();
        private java.util.List<PostProcessProperties> postProcessOperations = java.util.List.of();
        private Integer postProcessInputLimit;
        private Double scaleK;
        private Integer filterPreAnnLimit;
        private Double filterPreAnnRatio;
    }

    @Data
    public static class PostProcessProperties {
        private VikingDbPostProcessOperation.Type type;
        private Map<String, Object> parameters = Map.of();
    }

    @Data
    public static class IndexVectorProperties {
        private VikingDbIndexVectorOptions.Type type = VikingDbIndexVectorOptions.Type.HNSW;
        private VikingDbIndexVectorOptions.Distance distance = VikingDbIndexVectorOptions.Distance.COSINE;
        private VikingDbIndexVectorOptions.Quantization quantization = VikingDbIndexVectorOptions.Quantization.FLOAT;
        private Integer hnswM;
        private Integer hnswCef;
        private Integer hnswSef;
        private Integer diskannM;
        private Integer diskannCef;
        private Float cacheRatio;
        private Float pqCodeRatio;
    }
}
