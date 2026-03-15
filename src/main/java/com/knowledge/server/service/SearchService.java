package com.knowledge.server.service;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.knowledge.server.config.KnowledgeServerProperties;
import com.knowledge.server.domain.SearchResult;
import com.knowledge.server.repository.LuceneIndexRepository;

public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    private final KnowledgeServerProperties properties;
    private final LuceneIndexRepository indexRepository;
    private final Analyzer analyzer;

    public SearchService(KnowledgeServerProperties properties,
                        LuceneIndexRepository indexRepository) {
        this.properties = properties;
        this.indexRepository = indexRepository;
        this.analyzer = new StandardAnalyzer();
    }

    public List<SearchResult> search(String query, Integer maxResults, Boolean highlight) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        int limit = maxResults != null ? maxResults : properties.getSearch().getMaxResults();

        try {
            return indexRepository.search(query, limit);
        } catch (ParseException e) {
            logger.error("Failed to parse query: {}", query, e);
            throw new IllegalArgumentException("Invalid query syntax: " + e.getMessage());
        } catch (IOException e) {
            logger.error("Search failed", e);
            throw new RuntimeException("Search failed: " + e.getMessage());
        }
    }

    public List<SearchResult> search(String query) {
        return search(query, null, null);
    }
}
