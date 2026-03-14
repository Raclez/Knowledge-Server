package com.knowledge.server.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import com.knowledge.server.config.KnowledgeServerProperties;
import com.knowledge.server.domain.IndexStats;
import com.knowledge.server.domain.SearchResult;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * @author ryu
 */
@Repository
public class LuceneIndexRepository {

    private static final Logger logger = LoggerFactory.getLogger(LuceneIndexRepository.class);

    private final KnowledgeServerProperties properties;
    private final Analyzer analyzer;
    private Directory directory;
    private IndexWriter indexWriter;
    private final ReentrantLock writeLock = new ReentrantLock();

    public LuceneIndexRepository(KnowledgeServerProperties properties) {
        this.properties = properties;
        this.analyzer = new StandardAnalyzer();
    }

    @PostConstruct
    public void init() throws IOException {
        Path indexPath = Path.of(properties.getIndex().getPath());
        Files.createDirectories(indexPath);

        directory = FSDirectory.open(indexPath);
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setSimilarity(new BM25Similarity());
        indexWriter = new IndexWriter(directory, config);

        logger.info("Lucene index initialized at: {}", properties.getIndex().getPath());
    }

    @PreDestroy
    public void close() throws IOException {
        if (indexWriter != null) {
            indexWriter.close();
        }
        if (directory != null) {
            directory.close();
        }
        analyzer.close();
        logger.info("Lucene index closed");
    }

    public void addDocument(com.knowledge.server.domain.Document doc) throws IOException {
        writeLock.lock();
        try {
            org.apache.lucene.document.Document luceneDoc = new org.apache.lucene.document.Document();

            luceneDoc.add(new StringField("id", doc.getId(), Field.Store.YES));
            luceneDoc.add(new StringField("filePath", doc.getFilePath(), Field.Store.YES));
            luceneDoc.add(new StringField("fileName", doc.getFileName(), Field.Store.YES));
            luceneDoc.add(new StringField("contentType", doc.getContentType(), Field.Store.YES));
            luceneDoc.add(new TextField("content", doc.getContent(), Field.Store.YES));
            luceneDoc.add(new StringField("fileSize", String.valueOf(doc.getFileSize()), Field.Store.YES));
            luceneDoc.add(new StringField("lastModified", String.valueOf(doc.getLastModified().toEpochMilli()), Field.Store.YES));
            luceneDoc.add(new StringField("indexedAt", String.valueOf(doc.getIndexedAt().toEpochMilli()), Field.Store.YES));

            indexWriter.addDocument(luceneDoc);
            indexWriter.commit();
            logger.debug("Document added: {}", doc.getFilePath());
        } finally {
            writeLock.unlock();
        }
    }

    public void updateDocument(String id, com.knowledge.server.domain.Document doc) throws IOException {
        writeLock.lock();
        try {
            Term term = new Term("id", id);
            indexWriter.deleteDocuments(term);
            addDocument(doc);
            logger.debug("Document updated: {}", doc.getFilePath());
        } finally {
            writeLock.unlock();
        }
    }

    public void deleteDocument(String id) throws IOException {
        writeLock.lock();
        try {
            Term term = new Term("id", id);
            indexWriter.deleteDocuments(term);
            indexWriter.commit();
            logger.debug("Document deleted: {}", id);
        } finally {
            writeLock.unlock();
        }
    }

    public void deleteByFilePath(String filePath) throws IOException {
        writeLock.lock();
        try {
            Term term = new Term("filePath", filePath);
            indexWriter.deleteDocuments(term);
            indexWriter.commit();
            logger.debug("Document deleted by path: {}", filePath);
        } finally {
            writeLock.unlock();
        }
    }

    public List<SearchResult> search(String queryString, int maxResults)
            throws IOException, ParseException {
        QueryParser parser = new MultiFieldQueryParser(
            new String[] { "content", "fileName" },
            analyzer
        );
        Query query = parser.parse(queryString);

        try (IndexReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());

            Highlighter highlighter = new Highlighter(
                new SimpleHTMLFormatter("<em>", "</em>"),
                new QueryScorer(query)
            );

            TopDocs topDocs = searcher.search(query, maxResults);
            List<SearchResult> results = new ArrayList<>();

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                org.apache.lucene.document.Document luceneDoc = searcher.doc(scoreDoc.doc);

                String content = luceneDoc.get("content");
                List<String> highlights = null;
                if (content != null) {
                    try {
                        String bestFragment = highlighter.getBestFragment(analyzer, "content", content);
                        if (bestFragment != null) {
                            highlights = List.of(bestFragment);
                        } else if (content.length() > 200) {
                            highlights = List.of(content.substring(0, 200));
                        } else {
                            highlights = List.of(content);
                        }
                    } catch (Exception e) {
                        if (content.length() > 200) {
                            highlights = List.of(content.substring(0, 200));
                        } else {
                            highlights = List.of(content);
                        }
                    }
                }

                SearchResult result = new SearchResult(
                    luceneDoc.get("id"),
                    luceneDoc.get("filePath"),
                    luceneDoc.get("fileName"),
                    scoreDoc.score,
                    highlights
                );
                results.add(result);
            }

            return results;
        }
    }

    public IndexStats getStats() throws IOException {
        try (IndexReader reader = DirectoryReader.open(directory)) {
            long totalDocs = reader.numDocs();
            long totalSize = 0;
            Map<String, Long> byType = new HashMap<>();

            for (int i = 0; i < reader.maxDoc(); i++) {
                try {
                    org.apache.lucene.document.Document doc = reader.document(i);
                    String sizeStr = doc.get("fileSize");
                    if (sizeStr != null) {
                        totalSize += Long.parseLong(sizeStr);
                    }
                    String contentType = doc.get("contentType");
                    if (contentType != null) {
                        byType.merge(contentType, 1L, Long::sum);
                    }
                } catch (Exception e) {
                    logger.debug("Failed to read document {}", i, e);
                }
            }

            return new IndexStats(totalDocs, totalSize, byType, System.currentTimeMillis());
        }
    }

    public boolean hasDocument(String id) throws IOException {
        try (IndexReader reader = DirectoryReader.open(directory)) {
            Term term = new Term("id", id);
            return reader.docFreq(term) > 0;
        } catch (IndexNotFoundException e) {
            return false;
        }
    }
}
