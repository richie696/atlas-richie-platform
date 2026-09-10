package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.filter.Neo4jAclFilterCompiler;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.HybridStoreOptions;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.HybridSearchExecutionMode;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.support.AclSafeHybridSearchFallback;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ACL-first Cypher hybrid search. It intentionally scans the labelled set so ACL is applied before candidate ranking. */
public final class Neo4jAclAwareHybridSearchOperations implements VectorAclAwareHybridSearchOperations, HybridSearchExecutionMode {
    private final Driver driver; private final SessionConfig sessionConfig; private final EmbeddingModel embedding;
    private final HybridStoreOptions options; private final Map<String, String> indexes; private final String metric;
    private final Neo4jAclFilterCompiler filters = new Neo4jAclFilterCompiler();
    public Neo4jAclAwareHybridSearchOperations(Driver driver, SessionConfig sessionConfig, EmbeddingModel embedding,
                                                HybridStoreOptions options, Map<String, String> indexes, String metric) {
        this.driver=driver; this.sessionConfig=sessionConfig; this.embedding=embedding; this.options=options; this.indexes=Map.copyOf(indexes); this.metric=metric;
    }
    @Override public String hybridExecutionMode() { return "core-rrf"; }
    @Override public List<VectorSearchResult> hybridSearch(String i,String text,String keyword,int limit,HybridSearchOptions o) { throw new UnsupportedOperationException("Neo4j ACL-safe hybrid search requires an explicit structured ACL filter"); }
    @Override public List<VectorSearchResult> hybridSearch(String indexName,String text,String keyword,int limit,HybridSearchOptions queryOptions,VectorFilter filter) {
        if(filter==null) throw new IllegalArgumentException("ACL filter must not be null"); if(limit<=0) throw new IllegalArgumentException("hybrid search limit must be greater than zero");
        if((text==null||text.isBlank())&&(keyword==null||keyword.isBlank())) throw new IllegalArgumentException("text and keywordQuery must not both be blank");
        SearchOptions search=queryOptions==null||queryOptions.getSearchOptions()==null?SearchOptions.builder().build():queryOptions.getSearchOptions();
        if(search.getFilter()!=null&&!search.getFilter().equals(filter)) throw new IllegalArgumentException("ACL filter conflicts with hybrid search options filter");
        String label="VectorDocument_"+require(indexName); Neo4jAclFilterCompiler.Compiled acl=filters.compile(filter); int candidates=Math.max(limit, options.candidateLimit());
        List<VectorSearchResult> dense=text==null||text.isBlank()?List.of():dense(label,text,candidates,acl);
        List<VectorSearchResult> lexical=keyword==null||keyword.isBlank()?List.of():lexical(label,keyword,candidates,acl);
        return AclSafeHybridSearchFallback.fuse(dense,lexical,queryOptions,limit);
    }
    private List<VectorSearchResult> dense(String label,String text,int limit,Neo4jAclFilterCompiler.Compiled acl) {
        String function="cosine".equalsIgnoreCase(metric)?"vector.similarity.cosine":"vector.similarity.euclidean";
        Map<String,Object> p=new HashMap<>(acl.parameters()); p.put("embedding", floats(embedding.embed(text))); p.put("limit",limit);
        return query("MATCH (n:"+label+") WHERE "+acl.cypher()+" WITH n, "+function+"(n.embedding, $embedding) AS score WHERE score IS NOT NULL RETURN n.id AS id, n.content AS content, score ORDER BY score DESC, id LIMIT $limit",p);
    }
    private List<VectorSearchResult> lexical(String label,String keyword,int limit,Neo4jAclFilterCompiler.Compiled acl) {
        Map<String,Object> p=new HashMap<>(acl.parameters()); p.put("keyword",keyword.toLowerCase(java.util.Locale.ROOT)); p.put("limit",limit);
        return query("MATCH (n:"+label+") WHERE "+acl.cypher()+" AND toLower(n.content) CONTAINS $keyword RETURN n.id AS id, n.content AS content, 1.0 AS score ORDER BY id LIMIT $limit",p);
    }
    private List<VectorSearchResult> query(String cypher,Map<String,Object> params) { try(var session=driver.session(sessionConfig)) { return session.run(cypher,params).list(record->VectorSearchResult.of(record.get("id").asString(),record.get("content").asString(""),record.get("score").asDouble())); } }
    private String require(String logical){ String physical=indexes.get(logical); if(physical==null) throw new IllegalArgumentException("index is not declared by this Neo4j Store: "+logical); return physical; }
    private static List<Float> floats(float[] values){ if(values==null||values.length==0) throw new IllegalStateException("Neo4j dense embedding must not be empty"); List<Float> result=new ArrayList<>(values.length); for(float value:values) result.add(value); return result; }
}
