package com.richie.component.mongodb.controller;

import com.richie.component.mongodb.service.MongodbService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/mongodb")
public class MongodbSampleController {

    private final MongodbService mongodbService;

    @PostMapping(path = "/{collection}/doc", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> insert(@PathVariable("collection") String collection,
                                      @RequestBody SimpleDoc doc) {
        if (!StringUtils.hasText(doc.getId())) {
            doc.setId(new ObjectId().toHexString());
        }
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(Instant.now());
        }
        var saved = mongodbService.insert(collection, doc);
        return Map.of("insertedId", saved.getId());
    }

    @GetMapping(path = "/{collection}/doc/{id}")
    public SimpleDoc findById(@PathVariable("collection") String collection,
                              @PathVariable("id") String id) {
        return mongodbService.findById(collection, id, SimpleDoc.class);
    }

    @GetMapping(path = "/{collection}/docs")
    public List<SimpleDoc> findList(@PathVariable("collection") String collection,
                                    @RequestParam(value = "title", required = false) String titleLike) {
        Query query = new Query();
        if (StringUtils.hasText(titleLike)) {
            query.addCriteria(Criteria.where("title").regex(titleLike));
        }
        return mongodbService.find(query, collection, SimpleDoc.class);
    }

    @PatchMapping(path = "/{collection}/doc/{id}")
    public SimpleDoc updateTitle(@PathVariable("collection") String collection,
                                 @PathVariable("id") String id,
                                 @RequestParam("title") String newTitle) {
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update().set("title", newTitle);
        return mongodbService.updateOne(query, update, collection, SimpleDoc.class);
    }

    @DeleteMapping(path = "/{collection}/doc/{id}")
    public Map<String, Object> deleteById(@PathVariable("collection") String collection,
                                          @PathVariable("id") String id) {
        Query query = new Query(Criteria.where("_id").is(id));
        long deleted = mongodbService.delete(query, collection, SimpleDoc.class);
        return Map.of("deleted", deleted);
    }

    @Data
    public static class SimpleDoc {
        private String id;
        private String title;
        private String content;
        private Instant createdAt;
    }
}


