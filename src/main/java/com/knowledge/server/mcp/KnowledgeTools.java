package com.knowledge.server.mcp;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.github.thought2code.mcp.annotated.annotation.McpTool;
import com.github.thought2code.mcp.annotated.annotation.McpToolParam;

import com.knowledge.server.config.KnowledgeServerProperties;
import com.knowledge.server.domain.IndexStats;
import com.knowledge.server.domain.SearchResult;
import com.knowledge.server.service.IndexService;
import com.knowledge.server.service.SearchService;

import lombok.extern.slf4j.Slf4j;

/**
 * @author ryu
 */
@Slf4j
public class KnowledgeTools {

    private static SearchService searchService;
    private static IndexService indexService;
    private static KnowledgeServerProperties properties;

    public KnowledgeTools() {
    }

    public static void setSearchService(SearchService searchService) {
        KnowledgeTools.searchService = searchService;
    }

    public static void setIndexService(IndexService indexService) {
        KnowledgeTools.indexService = indexService;
    }

    public static void setProperties(KnowledgeServerProperties properties) {
        KnowledgeTools.properties = properties;
    }

    private static boolean isPathAllowed(Path path) {
        if (properties == null || properties.getIndex().getWatchPaths().isEmpty()) {
            return true;
        }
        try {
            Path absolutePath = path.toAbsolutePath().normalize();
            for (String watchPath : properties.getIndex().getWatchPaths()) {
                Path watchDir = Path.of(watchPath).toAbsolutePath().normalize();
                if (absolutePath.startsWith(watchDir)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to validate path: {}", path, e);
            return false;
        }
    }

    @McpTool(name = "knowledge_search", description = "Search indexed documents using full-text search. Returns relevant documents with their file paths and scores.")
    public List<SearchResult> search(
            @McpToolParam(name = "query", description = "The search query string") String query,
            @McpToolParam(name = "maxResults", description = "Maximum results to return") int maxResults) {
        log.info("MCP tool called: knowledge_search with query={}, maxResults={}", query, maxResults);
        return searchService.search(query, maxResults, false);
    }

    @McpTool(name = "knowledge_index", description = "Index a file or directory for search")
    public Map<String, Object> index(
            @McpToolParam(name = "path", description = "Path to file or directory to index") String path) {
        log.info("MCP tool called: knowledge_index with path={}", path);
        try {
            Path filePath = Path.of(path);
            if (!isPathAllowed(filePath)) {
                throw new SecurityException("Path is not within allowed watch paths: " + path);
            }
            indexService.indexFile(filePath);
            return Map.of("status", "indexed", "path", path);
        } catch (SecurityException e) {
            log.warn("Path validation failed: {}", path);
            throw e;
        } catch (Exception e) {
            log.error("Failed to index: {}", path, e);
            throw new RuntimeException("Failed to index: " + e.getMessage(), e);
        }
    }

    @McpTool(name = "knowledge_status", description = "Get indexing status and statistics")
    public IndexStats status() {
        log.info("MCP tool called: knowledge_status");
        return indexService.getStats();
    }

    @McpTool(name = "knowledge_reindex", description = "Re-index all watched directories")
    public Map<String, Object> reindex() {
        log.info("MCP tool called: knowledge_reindex");
        indexService.reindex();
        return Map.of("status", "reindex started");
    }
}
