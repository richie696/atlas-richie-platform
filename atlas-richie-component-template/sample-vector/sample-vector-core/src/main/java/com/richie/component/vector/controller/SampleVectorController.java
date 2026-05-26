package com.richie.component.vector.controller;

import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.SampleVectorService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/vector")
public class SampleVectorController {

    private final SampleVectorService sampleVectorService;

    @PostMapping(path = "/doc", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> upsert(@RequestBody UpsertRequest req) {
        String id = sampleVectorService.upsertText(req.getId(), req.getContent(), req.getType(), req.getTags(), req.getNamespace(), req.getMetadata());
        return Map.of("id", id);
    }

    @PostMapping(path = "/docs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> upsertBatch(@RequestBody UpsertBatchRequest req) {
        List<String> ids = sampleVectorService.upsertBatch(req.getItems());
        return Map.of("count", ids.size(), "ids", ids);
    }

    @PostMapping(path = "/search/text", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<VectorSearchResult> searchByText(@RequestBody SearchTextRequest req) {
        return sampleVectorService.searchByText(req.getText(), req.getLimit(), req.getMinScore(), req.getType(), req.getTags(), req.getNamespace());
    }

    @PostMapping(path = "/search/vector", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<VectorSearchResult> searchByVector(@RequestBody SearchVectorRequest req) {
        return sampleVectorService.searchByVector(req.getVector(), req.getLimit(), req.getMinScore(), req.getType(), req.getTags(), req.getNamespace());
    }

    @PostMapping(path = "/index/{name}")
    public Map<String, Object> createIndex(@PathVariable("name") String name, @RequestParam(value = "dimension", required = false) Integer dimension, @RequestParam(value = "metric", required = false) String metric) {
        sampleVectorService.createIndex(name, dimension, metric);
        return Map.of("index", name, "created", true);
    }

    @GetMapping(path = "/index/{name}/exists")
    public Map<String, Object> indexExists(@PathVariable("name") String name) {
        return Map.of("index", name, "exists", sampleVectorService.indexExists(name));
    }

    @DeleteMapping(path = "/index/{name}")
    public Map<String, Object> deleteIndex(@PathVariable("name") String name) {
        sampleVectorService.deleteIndex(name);
        return Map.of("index", name, "deleted", true);
    }

    @GetMapping(path = "/{index}/count")
    public Map<String, Object> count(@PathVariable("index") String index) {
        return Map.of("index", index, "count", sampleVectorService.count(index));
    }

    @Data
    public static class UpsertRequest {
        private String id;
        private String content;
        private String type;
        private List<String> tags;
        private String namespace;
        private Map<String, Object> metadata;
    }

    @Data
    public static class UpsertBatchRequest {
        private List<SampleVectorService.UpsertItem> items;
    }

    @Data
    public static class SearchTextRequest {
        private String text;
        private Integer limit;
        private Double minScore;
        private String type;
        private List<String> tags;
        private String namespace;
    }

    @Data
    public static class SearchVectorRequest {
        private float[] vector;
        private Integer limit;
        private Double minScore;
        private String type;
        private List<String> tags;
        private String namespace;
    }
}


