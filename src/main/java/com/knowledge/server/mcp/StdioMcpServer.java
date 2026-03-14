package com.knowledge.server.mcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.server.domain.IndexStats;
import com.knowledge.server.domain.SearchResult;
import com.knowledge.server.service.IndexService;
import com.knowledge.server.service.SearchService;

@Component
public class StdioMcpServer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(StdioMcpServer.class);

    private final SearchService searchService;
    private final IndexService indexService;
    private final ObjectMapper objectMapper;

    private final Map<String, JsonNode> pendingRequests = new ConcurrentHashMap<>();

    public StdioMcpServer(SearchService searchService, IndexService indexService) {
        this.searchService = searchService;
        this.indexService = indexService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Starting MCP stdio server...");

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter stdout = new PrintWriter(System.out, true);

        String line;
        while ((line = stdin.readLine()) != null) {
            try {
                JsonNode request = objectMapper.readTree(line);
                JsonNode response = handleRequest(request);
                if (response != null) {
                    stdout.println(response.toString());
                }
            } catch (Exception e) {
                logger.error("Error processing request", e);
                JsonNode error = createErrorResponse(null, -32603, "Internal error: " + e.getMessage());
                stdout.println(error.toString());
            }
        }
    }

    private JsonNode handleRequest(JsonNode request) {
        String jsonrpc = request.has("jsonrpc") ? request.get("jsonrpc").asText() : "2.0";
        String id = request.has("id") ? request.get("id").asText() : UUID.randomUUID().toString();

        if (!request.has("method")) {
            return createErrorResponse(id, -32600, "Invalid Request: method is required");
        }

        String method = request.get("method").asText();
        JsonNode params = request.has("params") ? request.get("params") : objectMapper.createObjectNode();

        logger.debug("Received MCP request: method={}", method);

        try {
            Object result = switch (method) {
                case "initialize" -> handleInitialize(params);
                case "tools/list" -> handleToolsList();
                case "tools/call" -> handleToolsCall(params);
                case "resources/list" -> handleResourcesList();
                case "resources/read" -> handleResourcesRead(params);
                default -> throw new IllegalArgumentException("Unknown method: " + method);
            };

            return createSuccessResponse(id, result);

        } catch (Exception e) {
            logger.error("Error handling method: {}", method, e);
            return createErrorResponse(id, -32603, e.getMessage());
        }
    }

    private Map<String, Object> handleInitialize(JsonNode params) {
        return Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", Map.of(
                "tools", Map.of(),
                "resources", Map.of()
            ),
            "serverInfo", Map.of(
                "name", "knowledge-server",
                "version", "1.0.0"
            )
        );
    }

    private Map<String, Object> handleToolsList() {
        return Map.of(
            "tools", List.of(
                Map.of(
                    "name", "knowledge_search",
                    "description", "Search indexed documents using full-text search. Returns relevant documents.",
                    "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "query", Map.of("type", "string", "description", "The search query string"),
                            "maxResults", Map.of("type", "integer", "description", "Maximum results to return", "default", 10)
                        ),
                        "required", List.of("query")
                    )
                ),
                Map.of(
                    "name", "knowledge_index",
                    "description", "Index a file or directory for search",
                    "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "path", Map.of("type", "string", "description", "Path to file or directory")
                        ),
                        "required", List.of("path")
                    )
                ),
                Map.of(
                    "name", "knowledge_status",
                    "description", "Get indexing status and statistics",
                    "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of()
                    )
                ),
                Map.of(
                    "name", "knowledge_reindex",
                    "description", "Re-index all watched directories",
                    "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of()
                    )
                )
            )
        );
    }

    private Object handleToolsCall(JsonNode params) {
        String name = params.get("name").asText();
        JsonNode args = params.has("arguments") ? params.get("arguments") : objectMapper.createObjectNode();

        logger.info("Calling tool: {} with args: {}", name, args);

        return switch (name) {
            case "knowledge_search" -> {
                String query = args.has("query") ? args.get("query").asText() : "";
                Integer maxResults = args.has("maxResults") ? args.get("maxResults").asInt() : 10;
                List<SearchResult> results = searchService.search(query, maxResults, false);
                yield results.stream()
                    .map(r -> Map.of(
                        "documentId", r.getDocumentId(),
                        "filePath", r.getFilePath(),
                        "fileName", r.getFileName(),
                        "score", r.getScore()
                    ))
                    .toList();
            }
            case "knowledge_index" -> {
                String path = args.get("path").asText();
                try {
                    indexService.indexFile(java.nio.file.Path.of(path));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to index: " + e.getMessage(), e);
                }
                yield Map.of("status", "indexed", "path", path);
            }
            case "knowledge_status" -> {
                IndexStats stats = indexService.getStats();
                yield Map.of(
                    "totalDocuments", stats.getTotalDocuments(),
                    "totalSizeBytes", stats.getTotalSizeBytes(),
                    "lastUpdated", stats.getLastUpdated()
                );
            }
            case "knowledge_reindex" -> {
                indexService.reindex();
                yield Map.of("status", "reindex started");
            }
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
    }

    private Map<String, Object> handleResourcesList() {
        IndexStats stats = indexService.getStats();
        return Map.of(
            "resources", List.of(
                Map.of(
                    "uri", "index://stats",
                    "name", "Index Statistics",
                    "description", "Current index statistics",
                    "mimeType", "application/json"
                )
            )
        );
    }

    private Map<String, Object> handleResourcesRead(JsonNode params) {
        String uri = params.get("uri").asText();
        if ("index://stats".equals(uri)) {
            IndexStats stats = indexService.getStats();
            try {
                return Map.of(
                    "contents", List.of(Map.of(
                        "uri", uri,
                        "mimeType", "application/json",
                        "text", objectMapper.writeValueAsString(stats)
                    ))
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize stats", e);
            }
        }
        throw new IllegalArgumentException("Unknown resource: " + uri);
    }

    private JsonNode createSuccessResponse(String id, Object result) {
        try {
            return objectMapper.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .set("result", objectMapper.valueToTree(result));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode createErrorResponse(String id, int code, String message) {
        try {
            return objectMapper.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .set("error", objectMapper.createObjectNode()
                    .put("code", code)
                    .put("message", message));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
