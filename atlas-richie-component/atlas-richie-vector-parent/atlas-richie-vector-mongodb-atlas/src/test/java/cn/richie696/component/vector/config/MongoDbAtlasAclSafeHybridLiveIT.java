package cn.richie696.component.vector.config;

import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.HybridStoreOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.service.impl.MongoDbAtlasAclAwareHybridSearchOperations;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Atlas Local E2E for filter-before-candidate ACL safety. */
@EnabledIfEnvironmentVariable(named = "VECTOR_MONGODB_IT_RUN", matches = "true")
class MongoDbAtlasAclSafeHybridLiveIT {
    @Test void excludesDeniedTextWinner() {
        String collection="acl_hybrid_"+UUID.randomUUID().toString().replace("-",""); String vector="vec_"+collection; String text="txt_"+collection;
        try(MongoClient client=MongoClients.create(required("VECTOR_MONGODB_URI"))){
            MongoTemplate template=new MongoTemplate(client,required("VECTOR_MONGODB_DATABASE"));
            try {
                template.getCollection(collection).insertMany(List.of(
                        new Document("_id","allowed").append("content","ordinary permitted").append("metadata",new Document("tenantId","tenant-a")).append("embedding",List.of(1F,0F,0F,0F)),
                        new Document("_id","denied").append("content","rare restricted").append("metadata",new Document("tenantId","tenant-b")).append("embedding",List.of(0F,1F,0F,0F))));
                template.getDb().runCommand(new Document("createSearchIndexes",collection).append("indexes",List.of(
                        new Document("name",vector).append("type","vectorSearch").append("definition",new Document("fields",List.of(
                                new Document("type","vector").append("path","embedding").append("numDimensions",4).append("similarity","cosine"),new Document("type","filter").append("path","metadata.tenantId")))),
                        MongoDbAtlasVectorProviderFactory.textSearchIndexDefinition(text,List.of("tenantId")))));
                MongoDbAtlasAclAwareHybridSearchOperations hybrid=new MongoDbAtlasAclAwareHybridSearchOperations(template,embedding(),new HybridStoreOptions(true,"embedding","content",null,10,HybridStoreOptions.FallbackMode.CORE_RRF),Map.of("documents",collection),Map.of("documents",vector),Map.of("documents",text));
                var results=eventuallyNonEmpty(() -> hybrid.hybridSearch("documents","ordinary","rare",5,HybridSearchOptions.builder().vectorWeight(.3D).keywordWeight(.7D).build(),VectorFilter.eq("tenantId","tenant-a")));
                assertThat(results).extracting(result->result.getId()).containsExactly("allowed"); assertThat(results).noneMatch(result->"denied".equals(result.getId()));
                template.getCollection(collection).deleteOne(new Document("_id","allowed"));
                assertThat(eventually(() -> hybrid.hybridSearch("documents","ordinary","ordinary",5,null,VectorFilter.eq("tenantId","tenant-a")))).isEmpty();
            } finally { template.getCollection(collection).drop(); }
        }
    }
    private static java.util.List<cn.richie696.component.vector.model.VectorSearchResult> eventually(java.util.concurrent.Callable<java.util.List<cn.richie696.component.vector.model.VectorSearchResult>> action){ RuntimeException latest=null; for(int i=0;i<30;i++){try{return action.call();}catch(RuntimeException e){latest=e;try{Thread.sleep(1000);}catch(InterruptedException x){Thread.currentThread().interrupt();throw e;}}catch(Exception e){throw new RuntimeException(e);}}throw latest; }
    private static java.util.List<cn.richie696.component.vector.model.VectorSearchResult> eventuallyNonEmpty(java.util.concurrent.Callable<java.util.List<cn.richie696.component.vector.model.VectorSearchResult>> action){ RuntimeException latest=null; for(int i=0;i<30;i++){try{var results=action.call();if(!results.isEmpty())return results;}catch(RuntimeException e){latest=e;}catch(Exception e){throw new RuntimeException(e);}try{Thread.sleep(1000);}catch(InterruptedException x){Thread.currentThread().interrupt();throw new IllegalStateException(x);}}if(latest!=null)throw latest;throw new IllegalStateException("Atlas search indexes did not become ready"); }
    private static String required(String key){String v=System.getenv(key);if(v==null||v.isBlank())throw new IllegalStateException("missing integration-test environment variable: "+key);return v;}
    private static EmbeddingModel embedding(){return EmbeddingModel.class.cast(Proxy.newProxyInstance(EmbeddingModel.class.getClassLoader(),new Class<?>[]{EmbeddingModel.class},(p,m,a)->"embed".equals(m.getName())?new float[]{1F,0F,0F,0F}:"dimensions".equals(m.getName())?4:null));}
}
