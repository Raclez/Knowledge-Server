package com.knowledge.server.config;

import com.github.thought2code.mcp.annotated.McpServers;
import com.github.thought2code.mcp.annotated.configuration.McpServerCapabilities;
import com.github.thought2code.mcp.annotated.configuration.McpServerConfiguration;
import com.github.thought2code.mcp.annotated.enums.ServerMode;
import com.github.thought2code.mcp.annotated.enums.ServerType;
import com.knowledge.server.mcp.KnowledgeTools;
import com.knowledge.server.service.IndexService;
import com.knowledge.server.service.SearchService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings("deprecation")
public class McpServerConfig {

    private final SearchService searchService;
    private final IndexService indexService;
    private final KnowledgeServerProperties properties;

    public McpServerConfig(SearchService searchService, IndexService indexService, KnowledgeServerProperties properties) {
        this.searchService = searchService;
        this.indexService = indexService;
        this.properties = properties;
    }

    @SuppressWarnings("deprecation")
    public void startMcpServer() {
        log.info("Starting MCP Server with native Java SDK...");

        KnowledgeTools.setSearchService(searchService);
        KnowledgeTools.setIndexService(indexService);
        KnowledgeTools.setProperties(properties);

        McpServerCapabilities capabilities = McpServerCapabilities.builder()
            .tool(true)
            .build();

        McpServerConfiguration.Builder configBuilder = McpServerConfiguration.builder()
            .enabled(true)
            .mode(ServerMode.STDIO)
            .name("knowledge-server")
            .version("1.0.0")
            .type(ServerType.SYNC)
            .requestTimeout(20000L)
            .capabilities(capabilities);

        McpServers.run(KnowledgeTools.class, new String[]{})
            .startStdioServer(configBuilder);
    }
}
