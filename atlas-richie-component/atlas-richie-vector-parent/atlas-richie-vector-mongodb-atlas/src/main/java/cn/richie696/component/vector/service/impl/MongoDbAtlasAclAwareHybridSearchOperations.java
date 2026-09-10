package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.filter.MongoDbAtlasAclFilterCompiler;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.HybridStoreOptions;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.HybridSearchExecutionMode;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.support.AclSafeHybridSearchFallback;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Atlas Vector Search plus Atlas Search, both with an identical ACL predicate before candidate output. */
public final class MongoDbAtlasAclAwareHybridSearchOperations implements VectorAclAwareHybridSearchOperations, HybridSearchExecutionMode {
    private final MongoTemplate template; private final EmbeddingModel embedding; private final HybridStoreOptions options;
    private final Map<String,String> collections, vectorIndexes, textIndexes; private final MongoDbAtlasAclFilterCompiler filters=new MongoDbAtlasAclFilterCompiler();
    public MongoDbAtlasAclAwareHybridSearchOperations(MongoTemplate template, EmbeddingModel embedding, HybridStoreOptions options, Map<String,String> collections, Map<String,String> vectorIndexes, Map<String,String> textIndexes){this.template=template;this.embedding=embedding;this.options=options;this.collections=Map.copyOf(collections);this.vectorIndexes=Map.copyOf(vectorIndexes);this.textIndexes=Map.copyOf(textIndexes);}
    @Override public String hybridExecutionMode(){return "core-rrf";}
    @Override public List<VectorSearchResult> hybridSearch(String i,String text,String keyword,int limit,HybridSearchOptions o){throw new UnsupportedOperationException("MongoDB Atlas ACL-safe hybrid search requires an explicit structured ACL filter");}
    @Override public List<VectorSearchResult> hybridSearch(String indexName,String text,String keyword,int limit,HybridSearchOptions queryOptions,VectorFilter filter){
        if(filter==null)throw new IllegalArgumentException("ACL filter must not be null");if(limit<=0)throw new IllegalArgumentException("hybrid search limit must be greater than zero");if((text==null||text.isBlank())&&(keyword==null||keyword.isBlank()))throw new IllegalArgumentException("text and keywordQuery must not both be blank");
        SearchOptions search=queryOptions==null||queryOptions.getSearchOptions()==null?SearchOptions.builder().build():queryOptions.getSearchOptions();if(search.getFilter()!=null&&!search.getFilter().equals(filter))throw new IllegalArgumentException("ACL filter conflicts with hybrid search options filter");
        Document acl=filters.vectorFilter(filter);int candidates=Math.max(limit,options.candidateLimit());
        List<VectorSearchResult> dense=text==null||text.isBlank()?List.of():dense(indexName,text,candidates,acl);List<VectorSearchResult> lexical=keyword==null||keyword.isBlank()?List.of():lexical(indexName,keyword,candidates,filter);
        return AclSafeHybridSearchFallback.fuse(dense,lexical,queryOptions,limit);
    }
    private List<VectorSearchResult> dense(String logical,String text,int limit,Document acl){ Document stage=new Document("$vectorSearch",new Document("index",vector(logical)).append("path","embedding").append("queryVector",floats(embedding.embed(text))).append("numCandidates",Math.max(limit,options.candidateLimit())).append("limit",limit).append("filter",acl));return execute(collection(logical),List.of(stage,new Document("$project",new Document("content",1).append("score",new Document("$meta","vectorSearchScore"))))); }
    private List<VectorSearchResult> lexical(String logical,String keyword,int limit,VectorFilter acl){
        Document compound=new Document("must",List.of(new Document("text",new Document("query",keyword).append("path","content"))))
                .append("filter",List.of(filters.searchFilter(acl)));
        Document stage=new Document("$search",new Document("index",text(logical)).append("compound",compound));
        return execute(collection(logical),List.of(stage,new Document("$limit",limit),new Document("$project",new Document("content",1).append("score",new Document("$meta","searchScore")))));
    }
    private List<VectorSearchResult> execute(String collection,List<Document> pipeline){ MongoCollection<Document> c=template.getCollection(collection); List<VectorSearchResult> result=new ArrayList<>(); for(Document d:c.aggregate(pipeline)){Object id=d.get("_id");result.add(VectorSearchResult.of(String.valueOf(id),d.getString("content"),d.get("score",Number.class).doubleValue()));}return result; }
    private String collection(String logical){return require(collections,logical,"collection");} private String vector(String logical){return require(vectorIndexes,logical,"vector index");} private String text(String logical){return require(textIndexes,logical,"text index");} private static String require(Map<String,String> values,String key,String name){String v=values.get(key);if(v==null)throw new IllegalArgumentException("index is not declared by this MongoDB Atlas Store: "+key);return v;} private static List<Float> floats(float[] v){if(v==null||v.length==0)throw new IllegalStateException("MongoDB dense embedding must not be empty");List<Float> r=new ArrayList<>(v.length);for(float x:v)r.add(x);return r;}
}
