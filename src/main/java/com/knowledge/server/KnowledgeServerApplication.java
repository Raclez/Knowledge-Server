package com.knowledge.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.knowledge.server.config.ConfigLoader;
import com.knowledge.server.config.KnowledgeServerProperties;
import com.knowledge.server.config.McpServerConfig;
import com.knowledge.server.repository.LuceneIndexRepository;
import com.knowledge.server.service.DocumentParserService;
import com.knowledge.server.service.FileWatcherService;
import com.knowledge.server.service.IndexService;
import com.knowledge.server.service.SearchService;

public class KnowledgeServerApplication {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeServerApplication.class);

    private static LuceneIndexRepository indexRepository;
    private static FileWatcherService fileWatcherService;
    private static IndexService indexService;

    public static void main(String[] args) {
        System.setProperty("org.apache.lucene.store.MMapDirectory.enableMemorySegments", "false");
        logger.info("Starting Knowledge-Server...");

        KnowledgeServerProperties properties = ConfigLoader.load();
        logger.info("Configuration loaded");

        indexRepository = new LuceneIndexRepository(properties);
        try {
            indexRepository.init();
        } catch (Exception e) {
            logger.error("Failed to initialize Lucene index: {}", e.getMessage(), e);
            System.exit(1);
            return;
        }
        logger.info("Lucene index repository initialized");

        DocumentParserService documentParserService = new DocumentParserService(properties);
        logger.info("Document parser service initialized");

        indexService = new IndexService(properties, indexRepository, documentParserService);
        indexService.init();
        logger.info("Index service initialized");

        SearchService searchService = new SearchService(properties, indexRepository);
        logger.info("Search service initialized");

        fileWatcherService = new FileWatcherService(properties, indexService);
        fileWatcherService.start();
        logger.info("File watcher service started");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Knowledge-Server...");
            if (fileWatcherService != null) {
                fileWatcherService.stop();
            }
            if (indexService != null) {
                indexService.shutdown();
            }
            if (indexRepository != null) {
                try {
                    indexRepository.close();
                } catch (Exception e) {
                    logger.error("Error closing index repository", e);
                }
            }
            logger.info("Shutdown complete");
        }));

        McpServerConfig mcpServerConfig = new McpServerConfig(searchService, indexService, properties);
        mcpServerConfig.startMcpServer();
    }
}
