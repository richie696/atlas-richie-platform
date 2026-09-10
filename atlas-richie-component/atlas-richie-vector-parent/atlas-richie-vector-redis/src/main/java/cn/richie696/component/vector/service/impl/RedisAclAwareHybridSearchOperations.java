package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.filter.RedisSearchAclFilterCompiler;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.HybridStoreOptions;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.HybridSearchExecutionMode;
import cn.richie696.component.vector.service.support.AclSafeHybridSearchFallback;
import org.springframework.ai.embedding.EmbeddingModel;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.RediSearchUtil;

import java.util.List;
import java.util.Map;

/** Redis Stack KNN + full-text ACL-safe hybrid search with shared RediSearch ACL syntax. */
public final class RedisAclAwareHybridSearchOperations implements VectorAclAwareHybridSearchOperations, HybridSearchExecutionMode {
    private final RedisClient client;
    private final EmbeddingModel embeddingModel;
    private final HybridStoreOptions options;
    private final RedisSearchAclFilterCompiler filterCompiler;
    private final Map<String, String> indexes;

    public RedisAclAwareHybridSearchOperations(RedisClient client, EmbeddingModel embeddingModel,
                                               HybridStoreOptions options, RedisSearchAclFilterCompiler filterCompiler,
                                               Map<String, String> indexes) {
        this.client = client; this.embeddingModel = embeddingModel; this.options = options;
        this.filterCompiler = filterCompiler; this.indexes = Map.copyOf(indexes);
    }
    @Override public String hybridExecutionMode() { return "core-rrf"; }
    @Override public List<VectorSearchResult> hybridSearch(String indexName, String text, String keywordQuery, int limit, HybridSearchOptions options) {
        throw new UnsupportedOperationException("Redis ACL-safe hybrid search requires an explicit structured ACL filter");
    }
    @Override public List<VectorSearchResult> hybridSearch(String indexName, String text, String keywordQuery, int limit,
                                                            HybridSearchOptions queryOptions, VectorFilter filter) {
        if (filter == null) throw new IllegalArgumentException("ACL filter must not be null");
        if (limit <= 0) throw new IllegalArgumentException("hybrid search limit must be greater than zero");
        if ((text == null || text.isBlank()) && (keywordQuery == null || keywordQuery.isBlank())) throw new IllegalArgumentException("text and keywordQuery must not both be blank");
        SearchOptions search = queryOptions == null || queryOptions.getSearchOptions() == null ? SearchOptions.builder().build() : queryOptions.getSearchOptions();
        if (search.getFilter() != null && !search.getFilter().equals(filter)) throw new IllegalArgumentException("ACL filter conflicts with hybrid search options filter");
        String index = indexes.getOrDefault(indexName, indexName);
        String acl = filterCompiler.compile(filter);
        int candidates = Math.max(limit, options.candidateLimit());
        List<VectorSearchResult> dense = text == null || text.isBlank() ? List.of() : dense(index, acl, text, candidates);
        List<VectorSearchResult> sparse = keywordQuery == null || keywordQuery.isBlank() ? List.of() : text(index, acl, keywordQuery, candidates);
        return AclSafeHybridSearchFallback.fuse(dense, sparse, queryOptions, limit);
    }
    private List<VectorSearchResult> dense(String index, String acl, String text, int limit) {
        float[] vector = embeddingModel.embed(text); if (vector == null || vector.length == 0) throw new IllegalStateException("Redis dense embedding must not be empty");
        vector = normalize(vector);
        Query query = new Query("(" + acl + ")=>[KNN " + limit + " @embedding $BLOB AS distance]")
                .addParam("BLOB", RediSearchUtil.toByteArray(vector)).returnFields("content", "distance").limit(0, limit).dialect(2);
        return results(index, query, true);
    }
    private List<VectorSearchResult> text(String index, String acl, String text, int limit) {
        Query query = new Query("(" + acl + ") @content:(" + escapeText(text) + ")").returnFields("content", "$score").limit(0, limit).dialect(2);
        return results(index, query, false);
    }
    private List<VectorSearchResult> results(String index, Query query, boolean distance) {
        try { SearchResult result = client.ftSearch(index, query); return result.getDocuments().stream().map(document -> toResult(document, index, distance)).toList(); }
        catch (RuntimeException error) { throw new IllegalStateException("Redis ACL-filtered hybrid candidate recall failed", error); }
    }
    private static VectorSearchResult toResult(Document doc, String index, boolean distance) {
        String id = doc.getId().startsWith(index + ":") ? doc.getId().substring(index.length() + 1) : doc.getId();
        String content = doc.hasProperty("content") ? doc.getString("content") : "";
        double score = distance && doc.hasProperty("distance") ? 1D - Double.parseDouble(doc.getString("distance")) : 1D;
        return VectorSearchResult.of(id, content, Math.max(0D, score));
    }
    private static float[] normalize(float[] vector) { double norm = 0D; for(float v:vector) norm += v*v; if(norm == 0D) return vector; float[] copy=vector.clone(); double root=Math.sqrt(norm); for(int i=0;i<copy.length;i++) copy[i]/=root; return copy; }
    private static String escapeText(String text) { return text.replaceAll("([\\\\(){}\\[\\]|@~*\\\"'\\-])", "\\\\$1"); }
}
