package com.knowledge.server.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import com.knowledge.server.config.KnowledgeServerProperties;
import com.knowledge.server.domain.Document;

@Service
public class DocumentParserService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentParserService.class);

    private final KnowledgeServerProperties properties;
    private final Tika tika;
    private final Parser parser;

    public DocumentParserService(KnowledgeServerProperties properties) {
        this.properties = properties;
        this.tika = new Tika();
        this.parser = new AutoDetectParser();
    }

    public Document parseFile(Path filePath) throws IOException, SAXException, TikaException {
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }

        long fileSize = Files.size(filePath);
        if (fileSize > properties.getIndex().getMaxFileSize()) {
            throw new IOException("File too large: " + fileSize + " bytes (max: " + properties.getIndex().getMaxFileSize() + ")");
        }

        String contentType = tika.detect(filePath);
        String content;

        if (fileSize > 10 * 1024 * 1024) {
            content = parseLargeFile(filePath);
        } else {
            content = parseSmallFile(filePath);
        }

        String id = generateDocumentId(filePath);

        Document doc = new Document();
        doc.setId(id);
        doc.setFilePath(filePath.toAbsolutePath().toString());
        doc.setFileName(filePath.getFileName().toString());
        doc.setContentType(contentType);
        doc.setContent(content);
        doc.setFileSize(fileSize);
        doc.setLastModified(Files.getLastModifiedTime(filePath).toInstant());
        doc.setIndexedAt(Instant.now());

        return doc;
    }

    private String parseSmallFile(Path filePath) throws IOException, SAXException, TikaException {
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream stream = Files.newInputStream(filePath)) {
            parser.parse(stream, handler, metadata, context);
        }

        return handler.toString();
    }

    private String parseLargeFile(Path filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        long processedBytes = 0;
        long chunkSize = 1024 * 1024;

        try (InputStream stream = Files.newInputStream(filePath);
             java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream))) {

            char[] buffer = new char[(int) Math.min(chunkSize, 1024 * 64)];
            int read;

            while ((read = reader.read(buffer)) != -1) {
                content.append(buffer, 0, read);
                processedBytes += read;

                if (processedBytes >= chunkSize) {
                    break;
                }
            }

            if (processedBytes >= chunkSize && processedBytes < Files.size(filePath)) {
                content.append("\n\n[File truncated - content exceeds streaming limit]");
            }
        }

        return content.toString();
    }

    public void parseFileStreaming(Path filePath, IndexWriter indexWriter) throws Exception {
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }

        long fileSize = Files.size(filePath);
        if (fileSize > properties.getIndex().getMaxFileSize()) {
            throw new IOException("File too large: " + fileSize);
        }

        String contentType = tika.detect(filePath);
        String id = generateDocumentId(filePath);

        Runtime runtime = Runtime.getRuntime();
        long startMemory = runtime.totalMemory() - runtime.freeMemory();

        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream stream = Files.newInputStream(filePath)) {
            parser.parse(stream, handler, metadata, context);
        }

        long endMemory = runtime.totalMemory() - runtime.freeMemory();
        logger.info("Memory delta for {}: {} MB", filePath.getFileName(), (endMemory - startMemory) / 1024 / 1024);

        org.apache.lucene.document.Document luceneDoc = new org.apache.lucene.document.Document();
        luceneDoc.add(new StringField("id", id, Field.Store.YES));
        luceneDoc.add(new StringField("filePath", filePath.toAbsolutePath().toString(), Field.Store.YES));
        luceneDoc.add(new StringField("fileName", filePath.getFileName().toString(), Field.Store.YES));
        luceneDoc.add(new StringField("contentType", contentType, Field.Store.YES));
        luceneDoc.add(new TextField("content", handler.toString(), Field.Store.YES));
        luceneDoc.add(new StringField("fileSize", String.valueOf(fileSize), Field.Store.YES));
        luceneDoc.add(new StringField("lastModified", String.valueOf(Files.getLastModifiedTime(filePath).toMillis()), Field.Store.YES));
        luceneDoc.add(new StringField("indexedAt", String.valueOf(Instant.now().toEpochMilli()), Field.Store.YES));

        indexWriter.addDocument(luceneDoc);
        indexWriter.commit();

        logger.info("Streamed indexed file: {} ({} MB)", filePath.getFileName(), fileSize / 1024 / 1024);
    }

    public String parseFileContent(Path filePath) throws IOException, SAXException, TikaException {
        return parseFile(filePath).getContent();
    }

    private String generateDocumentId(Path filePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String pathStr = filePath.toAbsolutePath().toString();
            byte[] hash = md.digest(pathStr.getBytes());
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return filePath.toAbsolutePath().toString().hashCode() + "";
        }
    }
}
