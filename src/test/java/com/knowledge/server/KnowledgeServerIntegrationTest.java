package com.knowledge.server;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.knowledge.server.config.KnowledgeServerProperties;
import com.knowledge.server.domain.SearchResult;
import com.knowledge.server.repository.LuceneIndexRepository;
import com.knowledge.server.service.DocumentParserService;
import com.knowledge.server.service.IndexService;
import com.knowledge.server.service.SearchService;

class KnowledgeServerIntegrationTest {

    @TempDir
    Path tempDir;

    private KnowledgeServerProperties properties;
    private LuceneIndexRepository indexRepository;
    private DocumentParserService documentParserService;
    private IndexService indexService;
    private SearchService searchService;

    @BeforeEach
    void setUp() throws Exception {
        properties = new KnowledgeServerProperties();
        properties.getIndex().setPath(tempDir.resolve("index").toString());
        properties.getIndex().setSupportedExtensions(List.of(".txt", ".md"));
        properties.getSearch().setMaxResults(10);

        Files.createDirectories(Path.of(properties.getIndex().getPath()));

        indexRepository = new LuceneIndexRepository(properties);
        indexRepository.init();

        documentParserService = new DocumentParserService(properties);
        indexService = new IndexService(properties, indexRepository, documentParserService);
        searchService = new SearchService(properties, indexRepository);
    }

    @Test
    void testIndexAndSearch() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "This is a test document with some searchable content.");

        indexService.indexFile(testFile);

        List<SearchResult> results = searchService.search("test document");
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getScore() > 0);
    }

    @Test
    void testSearchNoResults() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Some content");

        indexService.indexFile(testFile);

        List<SearchResult> results = searchService.search("nonexistent query xyz");
        assertTrue(results.isEmpty());
    }

    @Test
    void testRemoveDocument() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Content to remove");

        indexService.indexFile(testFile);
        List<SearchResult> before = searchService.search("remove");
        assertFalse(before.isEmpty());

        indexService.removeDocument(testFile.toString());
        List<SearchResult> after = searchService.search("remove");
        assertTrue(after.isEmpty());
    }
}
