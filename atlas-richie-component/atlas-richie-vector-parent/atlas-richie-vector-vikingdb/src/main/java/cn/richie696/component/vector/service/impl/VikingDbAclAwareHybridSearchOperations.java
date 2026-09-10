package cn.richie696.component.vector.service.impl;

import cn.richie696.ai.vectorstore.vikingdb.api.VikingDbSearchOperations;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbResourceRef;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbSearchCommonOptions;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbVectorSearchRequest;
import cn.richie696.component.vector.filter.VikingDbVectorFilterAdapter;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.HybridStoreOptions;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.HybridSearchExecutionMode;
import cn.richie696.component.vector.service.SparseVectorizer;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import java.util.List;
import java.util.Map;

/** Native VikingDB HYBRID request: mandatoryFilter is applied by the provider before ranking. */
public final class VikingDbAclAwareHybridSearchOperations implements VectorAclAwareHybridSearchOperations, HybridSearchExecutionMode {
    private final VikingDbSearchOperations search; private final org.springframework.ai.embedding.EmbeddingModel embedding;
    private final SparseVectorizer sparse; private final HybridStoreOptions options; private final Map<String,VikingDbResourceRef> targets;
    public VikingDbAclAwareHybridSearchOperations(VikingDbSearchOperations search, org.springframework.ai.embedding.EmbeddingModel embedding, SparseVectorizer sparse, HybridStoreOptions options, Map<String,VikingDbResourceRef> targets){this.search=search;this.embedding=embedding;this.sparse=sparse;this.options=options;this.targets=Map.copyOf(targets);}
    @Override public String hybridExecutionMode(){return "native";}
    @Override public List<VectorSearchResult> hybridSearch(String i,String text,String keyword,int limit,HybridSearchOptions o){throw new UnsupportedOperationException("VikingDB ACL-safe hybrid search requires an explicit structured ACL filter");}
    @Override public List<VectorSearchResult> hybridSearch(String indexName,String text,String keyword,int limit,HybridSearchOptions query,VectorFilter filter){
        if(filter==null)throw new IllegalArgumentException("ACL filter must not be null");if(limit<=0)throw new IllegalArgumentException("hybrid search limit must be greater than zero");if((text==null||text.isBlank())&&(keyword==null||keyword.isBlank()))throw new IllegalArgumentException("text and keywordQuery must not both be blank");
        SearchOptions searchOptions=query==null||query.getSearchOptions()==null?SearchOptions.builder().build():query.getSearchOptions();if(searchOptions.getFilter()!=null&&!searchOptions.getFilter().equals(filter))throw new IllegalArgumentException("ACL filter conflicts with hybrid search options filter");
        String denseText=text==null||text.isBlank()?keyword:text;String sparseText=keyword==null||keyword.isBlank()?denseText:keyword;
        var response=search.search(VikingDbVectorSearchRequest.builder().target(target(indexName)).mode(VikingDbVectorSearchRequest.Mode.HYBRID).denseVector(embedding.embed(denseText)).sparseVector(sparse.encode(sparseText).coordinates().entrySet().stream().collect(java.util.stream.Collectors.toMap(e->String.valueOf(e.getKey()), java.util.Map.Entry::getValue))).mandatoryFilter(VikingDbVectorFilterAdapter.toSpring(filter)).common(VikingDbSearchCommonOptions.builder().limit(Math.max(limit,options.candidateLimit())).build()).build());
        return response.hits().stream().limit(limit).map(hit->VectorSearchResult.of(hit.id(),String.valueOf(hit.fields().getOrDefault("content","")),hit.score()==null?0D:hit.score())).toList();
    }
    private VikingDbResourceRef target(String index){var target=targets.get(index);if(target==null)throw new IllegalArgumentException("index is not declared by this VikingDB Store: "+index);return target;}
}
