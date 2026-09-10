package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.filter.PostgresqlAclFilterCompiler;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.HybridStoreOptions;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.HybridSearchExecutionMode;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.support.AclSafeHybridSearchFallback;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** pgvector KNN + PostgreSQL tsvector recall, with the same ACL WHERE clause in both SQL queries. */
public final class PostgresqlAclAwareHybridSearchOperations
        implements VectorAclAwareHybridSearchOperations, HybridSearchExecutionMode {
    private final JdbcTemplate jdbc;
    private final EmbeddingModel embeddingModel;
    private final HybridStoreOptions options;
    private final String schema;
    private final Map<String, String> tables;
    private final PostgresqlAclFilterCompiler filters = new PostgresqlAclFilterCompiler();

    public PostgresqlAclAwareHybridSearchOperations(JdbcTemplate jdbc, EmbeddingModel embeddingModel,
                                                     HybridStoreOptions options, String schema,
                                                     Map<String, String> tables) {
        this.jdbc = jdbc; this.embeddingModel = embeddingModel; this.options = options;
        this.schema = schema; this.tables = Map.copyOf(tables);
    }

    @Override public String hybridExecutionMode() { return "core-rrf"; }
    @Override public List<VectorSearchResult> hybridSearch(String indexName, String text, String keyword, int limit, HybridSearchOptions queryOptions) {
        throw new UnsupportedOperationException("PostgreSQL ACL-safe hybrid search requires an explicit structured ACL filter");
    }
    @Override public List<VectorSearchResult> hybridSearch(String indexName, String text, String keyword, int limit,
                                                             HybridSearchOptions queryOptions, VectorFilter filter) {
        if (filter == null) throw new IllegalArgumentException("ACL filter must not be null");
        if (limit <= 0) throw new IllegalArgumentException("hybrid search limit must be greater than zero");
        if ((text == null || text.isBlank()) && (keyword == null || keyword.isBlank())) throw new IllegalArgumentException("text and keywordQuery must not both be blank");
        SearchOptions search = queryOptions == null || queryOptions.getSearchOptions() == null ? SearchOptions.builder().build() : queryOptions.getSearchOptions();
        if (search.getFilter() != null && !search.getFilter().equals(filter)) throw new IllegalArgumentException("ACL filter conflicts with hybrid search options filter");
        String table = table(indexName);
        PostgresqlAclFilterCompiler.Compiled acl = filters.compile(filter);
        int candidates = Math.max(limit, options.candidateLimit());
        List<VectorSearchResult> dense = text == null || text.isBlank() ? List.of() : dense(table, text, candidates, acl);
        List<VectorSearchResult> lexical = keyword == null || keyword.isBlank() ? List.of() : lexical(table, keyword, candidates, acl);
        return AclSafeHybridSearchFallback.fuse(dense, lexical, queryOptions, limit);
    }

    private List<VectorSearchResult> dense(String table, String text, int limit, PostgresqlAclFilterCompiler.Compiled acl) {
        String vector = vector(embeddingModel.embed(text));
        String sql = "SELECT id::text, content, GREATEST(0, 1 - (embedding <=> ?::vector)) AS score FROM " + table
                + " WHERE " + acl.sql() + " ORDER BY embedding <=> ?::vector LIMIT ?";
        List<Object> args = new ArrayList<>(); args.add(vector); args.addAll(acl.parameters()); args.add(vector); args.add(limit);
        return jdbc.query(sql, (rs, row) -> VectorSearchResult.of(rs.getString(1), rs.getString(2), rs.getDouble(3)), args.toArray());
    }

    private List<VectorSearchResult> lexical(String table, String keyword, int limit, PostgresqlAclFilterCompiler.Compiled acl) {
        String query = "websearch_to_tsquery('simple', ?)";
        String sql = "SELECT id::text, content, ts_rank_cd(search_tsv, " + query + ") AS score FROM " + table
                + " WHERE search_tsv @@ " + query + " AND " + acl.sql() + " ORDER BY score DESC, id LIMIT ?";
        List<Object> args = new ArrayList<>(); args.add(keyword); args.add(keyword); args.addAll(acl.parameters()); args.add(limit);
        return jdbc.query(sql, (rs, row) -> VectorSearchResult.of(rs.getString(1), rs.getString(2), rs.getDouble(3)), args.toArray());
    }

    private String table(String logical) {
        String physical = tables.get(logical);
        if (physical == null) throw new IllegalArgumentException("index is not declared by this PostgreSQL Store: " + logical);
        return schema + "." + physical;
    }
    private static String vector(float[] values) {
        if (values == null || values.length == 0) throw new IllegalStateException("PostgreSQL dense embedding must not be empty");
        StringBuilder result = new StringBuilder("["); for (int i=0;i<values.length;i++) { if(i>0) result.append(','); result.append(values[i]); } return result.append(']').toString();
    }
}
