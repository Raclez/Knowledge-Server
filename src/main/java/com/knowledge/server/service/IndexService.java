package com.knowledge.server.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.knowledge.server.config.KnowledgeServerProperties;
import com.knowledge.server.domain.Document;
import com.knowledge.server.domain.IndexStats;
import com.knowledge.server.repository.LuceneIndexRepository;

public class IndexService {

    private static final Logger logger = LoggerFactory.getLogger(IndexService.class);

    private final KnowledgeServerProperties properties;
    private final LuceneIndexRepository indexRepository;
    private final DocumentParserService documentParserService;
    private final ExecutorService indexingExecutor;

    public IndexService(KnowledgeServerProperties properties,
                        LuceneIndexRepository indexRepository,
                        DocumentParserService documentParserService) {
        this.properties = properties;
        this.indexRepository = indexRepository;
        this.documentParserService = documentParserService;
        this.indexingExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
    }

    public void init() {
        if (!properties.getIndex().getWatchPaths().isEmpty()) {
            initialIndex();
        }
    }

    public void shutdown() {
        try {
            indexingExecutor.shutdown();
            if (!indexingExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                indexingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            indexingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void initialIndex() {
        properties.getIndex().getWatchPaths().forEach(pathStr -> {
            try {
                indexingExecutor.submit(() -> {
                    Path basePath = Path.of(pathStr);
                    if (Files.exists(basePath) && Files.isDirectory(basePath)) {
                        try {
                            indexDirectory(basePath);
                        } catch (Exception e) {
                            logger.error("Failed to initial index directory: {}", basePath, e);
                        }
                    }
                }).get();
            } catch (Exception e) {
                logger.error("Failed to index path: {}", pathStr, e);
            }
        });
    }

    public void indexDirectory(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();
        collectFiles(directory, files);

        logger.info("Found {} files to index in {}", files.size(), directory);

        int batchSize = properties.getIndex().getBatchSize();
        List<List<Path>> batches = new ArrayList<>();
        for (int i = 0; i < files.size(); i += batchSize) {
            batches.add(files.subList(i, Math.min(i + batchSize, files.size())));
        }

        for (List<Path> batch : batches) {
            var futures = batch.stream()
                .map(file -> indexingExecutor.submit(() -> indexFile(file)))
                .toList();
            for (var future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    logger.error("Failed to index file", e);
                }
            }
        }
    }

    private void collectFiles(Path directory, List<Path> files) throws IOException {
        try (var stream = Files.list(directory)) {
            stream.forEach(path -> {
                if (Files.isDirectory(path) && properties.getWatcher().isRecursive()) {
                    try {
                        collectFiles(path, files);
                    } catch (IOException e) {
                        logger.error("Failed to collect files from: {}", path, e);
                    }
                } else if (Files.isRegularFile(path) && isSupportedFile(path)) {
                    files.add(path);
                }
            });
        }
    }

    public Document indexFile(Path filePath) throws Exception {
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }

        if (!isSupportedFile(filePath)) {
            logger.debug("Skipping unsupported file: {}", filePath);
            return null;
        }

        Document doc = documentParserService.parseFile(filePath);

        if (indexRepository.hasDocument(doc.getId())) {
            indexRepository.updateDocument(doc.getId(), doc);
            logger.info("Updated document: {}", filePath);
        } else {
            indexRepository.addDocument(doc);
            logger.info("Indexed document: {}", filePath);
        }

        return doc;
    }

    public void removeDocument(String filePath) {
        try {
            indexRepository.deleteByFilePath(filePath);
            logger.info("Removed document: {}", filePath);
        } catch (Exception e) {
            logger.error("Failed to remove document: {}", filePath, e);
        }
    }

    public IndexStats getStats() {
        try {
            return indexRepository.getStats();
        } catch (Exception e) {
            logger.error("Failed to get index stats", e);
            return new IndexStats(0, 0, null, System.currentTimeMillis());
        }
    }

    public void reindex() {
        initialIndex();
    }

    private boolean isSupportedFile(Path filePath) {
        String fileName = filePath.getFileName().toString();
        return properties.getIndex().getSupportedExtensions().stream()
            .anyMatch(ext -> fileName.toLowerCase().endsWith(ext));
    }
}
