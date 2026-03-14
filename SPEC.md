# Knowledge-Server Specification

## 1. Project Overview

**Project Name**: Knowledge-Server
**Type**: Java MCP (Model Context Protocol) Server
**Core Functionality**: Industrial-grade local knowledge engine with IDE-level file indexing capabilities
**Target Users**: AI assistants, developers needing local document search without cloud dependencies

## 2. Technical Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Java | 17 |
| Framework | Spring Boot | 3.4.x |
| Search Engine | Apache Lucene | 9.x |
| Document Parser | Apache Tika | 2.x |
| Build Tool | Maven | 3.9.x |

## 3. Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Knowledge-Server                          │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │   MCP        │  │   File       │  │   Lucene         │  │
│  │   Protocol   │  │   Watcher    │  │   Index          │  │
│  │   Handler    │  │   (Reactive) │  │   Engine         │  │
│  └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘  │
│         │                 │                    │             │
│         └─────────────────┼────────────────────┘             │
│                           ▼                                  │
│              ┌────────────────────────┐                      │
│              │   Document Parser      │                      │
│              │   (Apache Tika)        │                      │
│              │   Streaming Support   │                      │
│              └────────────────────────┘                      │
└─────────────────────────────────────────────────────────────┘
```

## 4. Functionality Specification

### 4.1 Core Features

#### 4.1.1 File Indexing
- **Supported Formats**: PDF, Word (.doc, .docx), Excel (.xls, .xlsx), Plain Text, Markdown, HTML
- **Streaming Index**: O(1) memory complexity for files up to 100MB+
- **Incremental Update**: Automatic re-indexing on file change detection

#### 4.1.2 File Watching (Reactive)
- Monitor specified directories for file changes (create, modify, delete)
- Use Java NIO WatchService with Virtual Threads for efficient monitoring
- Debounce rapid file changes (configurable, default 500ms)

#### 4.1.3 Search Capabilities
- Full-text search across indexed documents
- Ranking by relevance (BM25 algorithm)
- Search result highlighting
- Support for complex queries (AND, OR, NOT, phrase matching)

#### 4.1.4 MCP Protocol Endpoints
- `tools/search`: Search indexed documents
- `tools/index`: Manually trigger indexing
- `tools/status`: Get indexing status and statistics
- `resources/list`: List indexed documents

### 4.2 Privacy Design
- **Zero Network Calls**: No external embedding APIs
- **Local Computation**: All indexing and search done locally
- **Data Isolation**: Index stored in configurable local directory

### 4.3 Performance Requirements
- Support 100MB+ single file indexing
- Memory usage constant regardless of file size (streaming)
- Concurrent file watching with Virtual Threads
- Index optimization for fast queries

## 5. Configuration

```yaml
knowledge-server:
  index:
    path: "./index"              # Lucene index directory
    watch-paths:                 # Directories to watch
      - "./docs"
    supported-extensions:       # File types to index
      - ".pdf"
      - ".doc"
      - ".docx"
      - ".xls"
      - ".xlsx"
      - ".txt"
      - ".md"
      - ".html"
    max-file-size: 104857600   # 100MB
    batch-size: 100             # Documents per batch commit

  watcher:
    debounce-ms: 500            # Debounce delay
    recursive: true             # Watch subdirectories

  search:
    max-results: 100            # Maximum results per query
    highlight-fragments: 3      # Highlight snippets
```

## 6. API Specification

### 6.1 REST API Endpoints

#### GET /api/search
Query parameters:
- `query` (string, required): Search query
- `maxResults` (int, optional, default: 10): Maximum results
- `highlight` (boolean, optional, default: true): Enable highlighting

#### POST /api/index
Request body:
```json
{
  "path": "string",
  "force": false
}
```

#### GET /api/status
Returns index statistics

#### POST /api/reindex
Triggers full reindex of all watched directories

## 7. Acceptance Criteria

1. **Indexing**: Successfully index PDF, Word, Excel, and text files
2. **Memory**: Memory usage stays constant during large file indexing
3. **Watching**: Auto-detect and index new/modified files within 1 second
4. **Search**: Return relevant results within 100ms for typical queries
5. **MCP**: Fully compliant MCP protocol implementation
6. **Privacy**: Zero network calls during operation
7. **Virtual Threads**: Utilize Java 21 Virtual Threads for concurrent operations

## 8. Project Structure

```
knowledge-server/
├── pom.xml
├── src/main/java/com/knowledge/server/
│   ├── KnowledgeServerApplication.java
│   ├── config/
│   │   └── KnowledgeServerProperties.java
│   ├── domain/
│   │   ├── Document.java
│   │   ├── SearchResult.java
│   │   └── IndexStats.java
│   ├── service/
│   │   ├── IndexService.java
│   │   ├── SearchService.java
│   │   ├── FileWatcherService.java
│   │   └── DocumentParserService.java
│   ├── repository/
│   │   └── LuceneIndexRepository.java
│   ├── mcp/
│   │   ├── KnowledgeServerMcpHandler.java
│   │   └── KnowledgeServerToolsProvider.java
│   └── util/
│       └── FileUtils.java
├── src/main/resources/
│   └── application.yml
└── src/test/java/
    └── ...
```
