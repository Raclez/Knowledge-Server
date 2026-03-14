package com.knowledge.server.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.knowledge.server.domain.IndexStats;
import com.knowledge.server.domain.SearchResult;
import com.knowledge.server.service.IndexService;
import com.knowledge.server.service.SearchService;

@RestController
@RequestMapping("/api")
public class KnowledgeServerController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeServerController.class);

    private final SearchService searchService;
    private final IndexService indexService;

    public KnowledgeServerController(SearchService searchService, IndexService indexService) {
        this.searchService = searchService;
        this.indexService = indexService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<SearchResult>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") Integer maxResults,
            @RequestParam(defaultValue = "true") Boolean highlight) {
        logger.info("Search: query={}, maxResults={}", query, maxResults);
        List<SearchResult> results = searchService.search(query, maxResults, highlight);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/index")
    public ResponseEntity<String> index(@RequestBody IndexRequest request) {
        logger.info("Index: path={}, force={}", request.path(), request.force());
        try {
            indexService.indexFile(java.nio.file.Path.of(request.path()));
            return ResponseEntity.ok("Indexed: " + request.path());
        } catch (Exception e) {
            logger.error("Index failed", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<IndexStats> status() {
        return ResponseEntity.ok(indexService.getStats());
    }

    @PostMapping("/reindex")
    public ResponseEntity<String> reindex() {
        logger.info("Reindex requested");
        indexService.reindex();
        return ResponseEntity.ok("Reindex started");
    }

    public record IndexRequest(String path, Boolean force) {}
}
