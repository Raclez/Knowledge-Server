package com.knowledge.server.domain;

import java.time.Instant;
import java.util.List;

public class Document {

    private String id;
    private String filePath;
    private String fileName;
    private String contentType;
    private String content;
    private long fileSize;
    private Instant lastModified;
    private Instant indexedAt;
    private List<String> highlights;

    public Document() {
    }

    public Document(String id, String filePath, String fileName, String contentType, String content,
                    long fileSize, Instant lastModified, Instant indexedAt) {
        this.id = id;
        this.filePath = filePath;
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
        this.fileSize = fileSize;
        this.lastModified = lastModified;
        this.indexedAt = indexedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(Instant indexedAt) {
        this.indexedAt = indexedAt;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public void setHighlights(List<String> highlights) {
        this.highlights = highlights;
    }
}
