package com.knowledge.server.config;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ryu
 */
public class KnowledgeServerProperties {

    private IndexConfig index = new IndexConfig();
    private WatcherConfig watcher = new WatcherConfig();
    private SearchConfig search = new SearchConfig();

    public IndexConfig getIndex() {
        return index;
    }

    public void setIndex(IndexConfig index) {
        this.index = index;
    }

    public WatcherConfig getWatcher() {
        return watcher;
    }

    public void setWatcher(WatcherConfig watcher) {
        this.watcher = watcher;
    }

    public SearchConfig getSearch() {
        return search;
    }

    public void setSearch(SearchConfig search) {
        this.search = search;
    }

    public static class IndexConfig {
        private String path = "./index";
        private List<String> watchPaths = new ArrayList<>();
        private List<String> supportedExtensions = List.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".txt", ".md", ".html"
        );
        private long maxFileSize = 104857600;
        private long streamingThreshold = 10485760;
        private int batchSize = 100;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public List<String> getWatchPaths() {
            return watchPaths;
        }

        public void setWatchPaths(List<String> watchPaths) {
            this.watchPaths = watchPaths;
        }

        public List<String> getSupportedExtensions() {
            return supportedExtensions;
        }

        public void setSupportedExtensions(List<String> supportedExtensions) {
            this.supportedExtensions = supportedExtensions;
        }

        public long getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(long maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        public long getStreamingThreshold() {
            return streamingThreshold;
        }

        public void setStreamingThreshold(long streamingThreshold) {
            this.streamingThreshold = streamingThreshold;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    public static class WatcherConfig {
        private long debounceMs = 500;
        private boolean recursive = true;

        public long getDebounceMs() {
            return debounceMs;
        }

        public void setDebounceMs(long debounceMs) {
            this.debounceMs = debounceMs;
        }

        public boolean isRecursive() {
            return recursive;
        }

        public void setRecursive(boolean recursive) {
            this.recursive = recursive;
        }
    }

    public static class SearchConfig {
        private int maxResults = 100;
        private int highlightFragments = 3;

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }

        public int getHighlightFragments() {
            return highlightFragments;
        }

        public void setHighlightFragments(int highlightFragments) {
            this.highlightFragments = highlightFragments;
        }
    }
}
