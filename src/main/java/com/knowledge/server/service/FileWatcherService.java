package com.knowledge.server.service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.knowledge.server.config.KnowledgeServerProperties;

public class FileWatcherService {

    private static final Logger logger = LoggerFactory.getLogger(FileWatcherService.class);

    private final KnowledgeServerProperties properties;
    private final IndexService indexService;

    private final Map<Path, WatchService> watchers = new ConcurrentHashMap<>();
    private final Map<Path, Long> lastModified = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService virtualThreadExecutor;

    public FileWatcherService(KnowledgeServerProperties properties,
                              IndexService indexService) {
        this.properties = properties;
        this.indexService = indexService;
        this.virtualThreadExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
    }

    public void start() {
        if (properties.getIndex().getWatchPaths().isEmpty()) {
            logger.warn("No watch paths configured");
            return;
        }

        running.set(true);
        for (String pathStr : properties.getIndex().getWatchPaths()) {
            Path path = Path.of(pathStr);
            startWatching(path);
        }
        logger.info("File watcher started for paths: {}", properties.getIndex().getWatchPaths());
    }

    public void stop() {
        running.set(false);
        watchers.values().forEach(this::closeQuietly);
        watchers.clear();
        try {
            virtualThreadExecutor.shutdown();
            if (!virtualThreadExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualThreadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("File watcher stopped");
    }

    private void startWatching(Path path) {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            if (properties.getWatcher().isRecursive()) {
                registerRecursive(path, watchService);
            } else {
                path.register(watchService,
                    java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                    java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
                    java.nio.file.StandardWatchEventKinds.ENTRY_DELETE);
            }
            watchers.put(path, watchService);

            virtualThreadExecutor.submit(() -> processEvents(watchService, path));
            logger.info("Watching path: {}", path);
        } catch (IOException e) {
            logger.error("Failed to start watching path: {}", path, e);
        }
    }

    private void registerRecursive(Path dir, WatchService watchService) throws IOException {
        if (dir.toFile().isDirectory()) {
            dir.register(watchService,
                java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
                java.nio.file.StandardWatchEventKinds.ENTRY_DELETE);

            try (var stream = java.nio.file.Files.list(dir)) {
                stream.filter(p -> p.toFile().isDirectory())
                    .forEach(p -> {
                        try {
                            registerRecursive(p, watchService);
                        } catch (IOException e) {
                            logger.error("Failed to register directory: {}", p, e);
                        }
                    });
            }
        }
    }

    private void processEvents(WatchService watchService, Path rootPath) {
        while (running.get()) {
            try {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == java.nio.file.StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path filePath = rootPath.resolve(pathEvent.context());

                    if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_DELETE) {
                        handleFileDelete(filePath);
                    } else {
                        handleFileChange(filePath);
                    }
                }
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error processing watch events", e);
            }
        }
    }

    private void handleFileChange(Path filePath) {
        if (!isSupportedFile(filePath)) {
            return;
        }

        long currentModified = 0;
        try {
            currentModified = java.nio.file.Files.getLastModifiedTime(filePath).toMillis();
        } catch (IOException e) {
            logger.debug("Could not get last modified time for: {}", filePath);
        }

        Long lastMod = lastModified.get(filePath);
        if (lastMod != null && lastMod == currentModified) {
            return;
        }

        lastModified.put(filePath, currentModified);

        long debounceMs = properties.getWatcher().getDebounceMs();
        virtualThreadExecutor.submit(() -> {
            try {
                Thread.sleep(debounceMs);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                logger.info("Indexing file: {}", filePath);
                indexService.indexFile(filePath);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Failed to index file: {}", filePath, e);
            }
        });
    }

    private void handleFileDelete(Path filePath) {
        lastModified.remove(filePath);
        try {
            indexService.removeDocument(filePath.toString());
            logger.info("Removed document from index: {}", filePath);
        } catch (Exception e) {
            logger.error("Failed to remove document from index: {}", filePath, e);
        }
    }

    private boolean isSupportedFile(Path filePath) {
        String fileName = filePath.getFileName().toString();
        return properties.getIndex().getSupportedExtensions().stream()
            .anyMatch(ext -> fileName.toLowerCase().endsWith(ext));
    }

    private void closeQuietly(WatchService watchService) {
        try {
            watchService.close();
        } catch (IOException e) {
            logger.debug("Error closing watch service", e);
        }
    }

    public void triggerReindex(Path path) {
        if (isSupportedFile(path)) {
            handleFileChange(path);
        }
    }
}
