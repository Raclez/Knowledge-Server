package com.knowledge.server.domain;

import java.util.Map;

public class IndexStats {

    private long totalDocuments;
    private long totalSizeBytes;
    private Map<String, Long> documentsByType;
    private long lastUpdated;

    public IndexStats() {
    }

    public IndexStats(long totalDocuments, long totalSizeBytes, Map<String, Long> documentsByType, long lastUpdated) {
        this.totalDocuments = totalDocuments;
        this.totalSizeBytes = totalSizeBytes;
        this.documentsByType = documentsByType;
        this.lastUpdated = lastUpdated;
    }

    public long getTotalDocuments() {
        return totalDocuments;
    }

    public void setTotalDocuments(long totalDocuments) {
        this.totalDocuments = totalDocuments;
    }

    public long getTotalSizeBytes() {
        return totalSizeBytes;
    }

    public void setTotalSizeBytes(long totalSizeBytes) {
        this.totalSizeBytes = totalSizeBytes;
    }

    public Map<String, Long> getDocumentsByType() {
        return documentsByType;
    }

    public void setDocumentsByType(Map<String, Long> documentsByType) {
        this.documentsByType = documentsByType;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    @Override
    public String toString() {
        return "IndexStats{" +
                "totalDocuments=" + totalDocuments +
                ", totalSizeBytes=" + totalSizeBytes +
                ", totalSizeMB=" + String.format("%.2f", totalSizeBytes / 1024.0 / 1024.0) +
                ", documentsByType=" + documentsByType +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
