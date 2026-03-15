# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Knowledge-Server** - Industrial-grade local knowledge engine with IDE-level file indexing

## Tech Stack
- Java 21 (Virtual Threads)
- Spring Boot 3.4
- Spring AI MCP 1.1.0-M1-PLATFORM-2
- Apache Lucene 9.x
- Apache Tika 2.x

## Commands

```bash
# Build (requires Java 21)
 mvn clean install

# Run
mvn spring-boot:run

# Run tests
mvn test

# Run single test
mvn test -Dtest=KnowledgeServerIntegrationTest
```

## Architecture

```
knowledge-server/
├── controller/KnowledgeServerController.java  # REST API endpoints
├── mcp/StdioMcpServer.java                 # stdio MCP Server (JSON-RPC)
├── repository/LuceneIndexRepository.java   # Lucene index operations
├── service/
│   ├── FileWatcherService.java            # NIO WatchService with Virtual Threads
│   ├── DocumentParserService.java        # Apache Tika parser (streaming + memory monitor)
│   ├── IndexService.java                 # Index management
│   └── SearchService.java                # Full-text search
├── domain/                               # Document, SearchResult, IndexStats
└── config/                               # Properties
```

## Communication

### REST API (optional, for web integration)
- `GET /api/search?query=...` - Full-text search
- `POST /api/index` - Manual file/directory indexing
- `GET /api/status` - Get index statistics
- `POST /api/reindex` - Rebuild entire index

### MCP stdio (primary - for AI assistant integration)
The server communicates via JSON-RPC 2.0 over stdio:
```bash
# Example: Initialize
echo '{"jsonrpc":"2.0","id":"1","method":"initialize"}' | java -jar knowledge-server.jar

# Example: List tools
echo '{"jsonrpc":"2.0","id":"2","method":"tools/list"}' | java -jar knowledge-server.jar

# Example: Search
echo '{"jsonrpc":"2.0","id":"3","method":"tools/call","params":{"name":"knowledge_search","arguments":{"query":"test"}}}' | java -jar knowledge-server.jar
```

### MCP Tools
- `knowledge_search` - Full-text search
- `knowledge_index` - Manual file/directory indexing
- `knowledge_status` - Get index statistics
- `knowledge_reindex` - Rebuild entire index

## Design Principles

- **Privacy-first**: Zero external network calls, all local
- **O(1) memory**: Streaming parser with memory delta logging for large files (100MB+)
- **Reactive**: Auto-indexing via WatchService with debouncing
- **Virtual Threads**: Uses Java 21 Virtual Threads for concurrent file watching

## Maven Configuration

Maven settings.xml 配置 (绕过阿里云镜像直连 Spring 仓库):
```xml
<mirrors>
  <mirror>
    <id>nexus-aliyun</id>
    <mirrorOf>central</mirrorOf>
    <url>http://maven.aliyun.com/nexus/content/groups/public</url>
  </mirror>
</mirrors>
```
