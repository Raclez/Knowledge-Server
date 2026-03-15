package com.knowledge.server.domain;

import java.util.List;

public class SearchResult {

    private String documentId;
    private String filePath;
    private String fileName;
    private float score;
    private List<String> highlights;

    public SearchResult() {
    }

    public SearchResult(String documentId, String filePath, String fileName, float score, List<String> highlights) {
        this.documentId = documentId;
        this.filePath = filePath;
        this.fileName = fileName;
        this.score = score;
        this.highlights = highlights;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
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

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public void setHighlights(List<String> highlights) {
        this.highlights = highlights;
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "fileName='" + fileName + '\'' +
                ", filePath='" + filePath + '\'' +
                ", score=" + score +
                ", highlights=" + highlights +
                '}';
    }
}
