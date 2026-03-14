package com.knowledge.server.mcp;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.knowledge.server.domain.IndexStats;
import com.knowledge.server.domain.SearchResult;
import com.knowledge.server.service.IndexService;
import com.knowledge.server.service.SearchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author ryu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeTools {

    private final SearchService searchService;
    private final IndexService indexService;

    @Tool(name = "knowledge_search", description = "Search indexed documents using full-text search. Returns relevant documents with their file paths and scores.")
    public List<SearchResult> search(
            @ToolParam(description = "The search query string") String query,
            @ToolParam(description = "Maximum results to return") int maxResults) {
        log.info("MCP tool called: knowledge_search with query={}, maxResults={}", query, maxResults);
        return searchService.search(query, maxResults, false);
    }

    @Tool(name = "knowledge_index", description = "Index a file or directory for search")
    public Map<String, Object> index(
            @ToolParam(description = "Path to file or directory to index") String path) {
        log.info("MCP tool called: knowledge_index with path={}", path);
        try {
            indexService.indexFile(java.nio.file.Path.of(path));
            return Map.of("status", "indexed", "path", path);
        } catch (Exception e) {
            log.error("Failed to index: {}", path, e);
            throw new RuntimeException("Failed to index: " + e.getMessage(), e);
        }
    }

    @Tool(name = "knowledge_status", description = "Get indexing status and statistics")
    public IndexStats status() {
        log.info("MCP tool called: knowledge_status");
        return indexService.getStats();
    }

    @Tool(name = "knowledge_reindex", description = "Re-index all watched directories")
    public Map<String, Object> reindex() {
        log.info("MCP tool called: knowledge_reindex");
        indexService.reindex();
        return Map.of("status", "reindex started");
    }
}
