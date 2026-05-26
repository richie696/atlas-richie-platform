package com.richie.component.search.controller;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.search.model.PageResult;
import com.richie.component.search.model.SearchQueryWrapper;
import com.richie.component.search.service.ElasticsearchService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/search")
public class SearchSampleController {

    private final ElasticsearchService elasticsearchService;

    @PostMapping(path = "/index/{index}/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> createIndex(@PathVariable("index") String index,
                                           @RequestBody(required = false) Map<String, Object> mapping) {
        String mappingJson = mapping == null ? defaultMappingJson() : JsonUtils.getInstance().serialize(mapping);
        boolean ok = elasticsearchService.createIndex(index, mappingJson);
        return Map.of("index", index, "created", ok);
    }

    @DeleteMapping(path = "/index/{index}")
    public Map<String, Object> deleteIndex(@PathVariable("index") String index) {
        boolean ok = elasticsearchService.deleteIndex(index);
        return Map.of("index", index, "deleted", ok);
    }

    @PostMapping(path = "/{index}/docs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<SimpleDoc> saveDocs(@PathVariable("index") String index,
                                    @RequestBody List<SimpleDoc> docs) {
        docs.forEach(d -> {
            if (!StringUtils.hasText(d.getId())) {
                d.setId(String.valueOf(System.nanoTime()));
            }
            if (d.getCreatedAt() == null) {
                d.setCreatedAt(Instant.now());
            }
        });
        return elasticsearchService.saveBatch(index, docs);
    }

    @GetMapping(path = "/{index}/simple-search", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<SimpleDoc> simpleSearch(@PathVariable("index") String index,
                                              @RequestParam("q") String keyword,
                                              @RequestParam(value = "page", defaultValue = "0") int page,
                                              @RequestParam(value = "size", defaultValue = "10") int size) {
        var wrapper = SearchQueryWrapper
                .create(SimpleDoc.class, index)
                .like(SimpleDoc::getTitle, keyword)
                .orderByDesc(SimpleDoc::getCreatedAt)
                .page(page, size);
        return elasticsearchService.search(wrapper);
    }

    private String defaultMappingJson() {
        return """
                {
                  "mappings": {
                    "properties": {
                      "id": { "type": "keyword" },
                      "title": { "type": "text", "analyzer": "standard" },
                      "content": { "type": "text", "analyzer": "standard" },
                      "createdAt": { "type": "date" }
                    }
                  }
                }""";
    }

    @Data
    public static class SimpleDoc {
        private String id;
        private String title;
        private String content;
        private Instant createdAt;
    }

}


