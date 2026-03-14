# Knowledge-Server 实施计划

## 项目概述

**项目名称**: Knowledge-Server
**项目类型**: Java MCP (Model Context Protocol) Server
**核心功能**: 工业级本地知识引擎，支持 IDE 级别的文件索引能力
**目标用户**: AI 助手开发者、本地文档搜索需求者

---

## 一、技术规格与目标

### 1.1 技术栈要求

| 组件 | 要求版本 | 当前状态 | 优先级 |
|------|----------|----------|--------|
| Java | 21 (Virtual Threads) | 17 (普通线程池) | 🔴 高 |
| Spring Boot | 3.4.x | 3.4.0 | ✅ 完成 |
| Spring AI MCP | 1.0.x | 未集成 | 🔴 高 |
| Apache Lucene | 10.x | 9.12.0 | 🟡 中 |
| Apache Tika | 2.x | 2.9.1 | ✅ 完成 |
| Maven | 3.9.x | 3.9.x | ✅ 完成 |

### 1.2 核心功能目标

- [ ] **隐私至上**: 所有计算和索引必须在本地完成，不得调用外网 Embedding 接口
- [ ] **性能优化**: 支持 100MB+ 大文件的流式索引，内存占用需控制在 O(1) 复杂度
- [ ] **响应式**: 监听本地文件变化并自动增量更新索引

---

## 二、阶段一：基础设施升级

### 2.1 Java 21 虚拟线程改造

**目标**: 利用虚拟线程替代传统线程池，实现高效并发

**改造内容**:

```
src/main/java/com/knowledge/server/service/
├── FileWatcherService.java       # 虚拟线程改造
└── IndexService.java             # 虚拟线程改造
```

**实施步骤**:

1. **检查 Java 环境**
   ```bash
   # 检查可用的 Java 版本
   java -version
   # 列出所有已安装的 JDK
   /usr/libexec/java_home -V
   ```

2. **安装 Java 21**
   - macOS: `brew install openjdk@21` 或从 Azul Zulu 下载
   - 配置 JAVA_HOME 指向 Java 21

3. **修改 pom.xml**
   ```xml
   <properties>
       <java.version>21</java.version>
       <maven.compiler.source>21</maven.compiler.source>
       <maven.compiler.target>21</maven.compiler.target>
   </properties>
   ```

4. **改造线程池为虚拟线程**

   **原代码 (FileWatcherService.java)**:
   ```java
   this.virtualThreadExecutor = Executors.newFixedThreadPool(4, r -> {
       Thread t = new Thread(r);
       t.setDaemon(true);
       return t;
   });
   ```

   **改造后**:
   ```java
   this.virtualThreadExecutor = Executors.newThreadPerTaskExecutor(
       Thread.ofVirtual().factory()
   );
   ```

   **关键点**:
   - 虚拟线程默认就是守护线程，无需手动设置
   - 虚拟线程适合 IO 密集型任务（文件监听、索引）
   - 无需手动管理线程池大小

**验收标准**:
- [ ] `java -version` 显示 21 或更高版本
- [ ] 代码中使用 `Thread.ofVirtual()` 创建虚拟线程
- [ ] 启动日志可见虚拟线程相关配置

---

### 2.2 Apache Lucene 10.x 升级

**目标**: 使用最新 Lucene 版本，获得更好的性能和功能

**挑战**:
- Lucene 10.x 尚未正式发布（当前最新稳定版为 9.x）
- 需要关注官方发布动态

**替代方案**:

若 Lucene 10.x 不可用，保持 Lucene 9.x 并进行优化：

```
Lucene 9.x 优化清单:
├── 使用更高效的 Analyzer (ICU Analyzer 处理国际化)
├── 启用索引压缩 (FST Compressor)
├── 优化查询缓存
└── 批量索引提交
```

**当前实现评估**:
- ✅ 已使用 BM25Similarity (Lucene 9.x+)
- ✅ 已使用 StandardAnalyzer
- 🟡 可考虑添加 Query Cache

---

## 二、阶段二：MCP 协议集成

### 2.1 Spring AI MCP 集成

**目标**: 实现完整的 MCP 协议支持，使 Knowledge-Server 成为标准的 MCP Server

**当前问题**:
- Spring AI MCP 依赖无法从阿里云镜像下载
- 需要配置正确的 Maven 仓库

**实施步骤**:

1. **配置 Maven 仓库**

   ```xml
   <!-- pom.xml -->
   <repositories>
       <!-- 阿里云镜像 (如果需要) -->
       <repository>
           <id>aliyun</id>
           <url>https://maven.aliyun.com/repository/public</url>
       </repository>
       <!-- Spring Milestones -->
       <repository>
           <id>spring-milestones</id>
           <url>https://repo.spring.io/milestone</url>
       </repository>
   </repositories>
   ```

2. **添加 MCP 依赖**

   ```xml
   <dependency>
       <groupId>org.springframework.ai</groupId>
       <artifactId>spring-ai-mcp</artifactId>
       <version>1.0.0-M4</version>
   </dependency>
   ```

3. **实现 MCP Handler**

   ```java
   // src/main/java/com/knowledge/server/mcp/KnowledgeServerMcpHandler.java
   @Service
   public class KnowledgeServerMcpHandler implements McpAsyncServer {

       @Override
       public List<ToolDefinition> listTools() {
           return List.of(
               ToolDefinition.builder()
                   .name("knowledge_search")
                   .description("Search indexed documents")
                   .inputSchema("...")
                   .build(),
               ToolDefinition.builder()
                   .name("knowledge_index")
                   .description("Index a file or directory")
                   .build(),
               ToolDefinition.builder()
                   .name("knowledge_status")
                   .description("Get index statistics")
                   .build()
           );
       }

       @Override
       public Object callTool(String toolName, Map<String, Object> arguments) {
           // 根据 toolName 调用对应服务
       }
   }
   ```

4. **配置 MCP Server**

   ```yaml
   # application.yml
   spring:
     ai:
       mcp:
         enabled: true
         server:
           name: knowledge-server
           version: 1.0.0
   ```

### 2.2 MCP 工具定义

| 工具名称 | 描述 | 参数 |
|----------|------|------|
| `knowledge_search` | 全文搜索 | `{ query: string, maxResults?: number }` |
| `knowledge_index` | 手动索引 | `{ path: string, force?: boolean }` |
| `knowledge_status` | 获取状态 | `{}` |
| `knowledge_reindex` | 重建索引 | `{}` |
| `knowledge_watch` | 监听目录 | `{ path: string }` |

---

## 三、阶段三：性能优化

### 3.1 大文件流式索引 (O(1) 内存)

**当前实现评估**:

```java
// DocumentParserService.java - 当前实现
private String parseLargeFile(Path filePath) throws IOException {
    // 问题: 仍然将内容加载到 StringBuilder
    StringBuilder content = new StringBuilder();
    // ...
}
```

**问题**:
- 当前实现仍会将整个文件内容加载到内存
- 对于 100MB+ 文件，内存占用仍然是 O(n)

**优化方案**:

```java
// 方案一: 使用 TokenStream 流式处理
public class StreamingDocumentParser {

    private final IndexWriter indexWriter;

    public void indexFileStreaming(Path filePath) throws Exception {
        try (InputStream is = Files.newInputStream(filePath)) {
            TokenStream tokenStream = new StandardTokenizer(
                new TikaInputStreamAdapter(is)
            );

            // 直接写入索引，不加载到内存
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                // 逐词索引
            }
            tokenStream.end();
        }
    }
}

// 方案二: 分块处理 (Chunk-based)
public class ChunkedDocumentParser {

    private static final int CHUNK_SIZE = 64 * 1024; // 64KB chunks

    public void indexFileInChunks(Path filePath, IndexWriter writer) throws IOException {
        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int chunkIndex = 0;

            while ((bytesRead = is.read(buffer)) != -1) {
                String chunk = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                addChunkToIndex(chunk, chunkIndex++, writer);
            }
        }
    }
}
```

**实现步骤**:

1. **创建流式索引器**
   ```
   src/main/java/com/knowledge/server/service/
   └── StreamingIndexService.java  (新建)
   ```

2. **修改 DocumentParserService**
   - 添加 `parseFileStreaming(Path)` 方法
   - 使用 Tika 的 `ParseContext` 进行流式解析

3. **添加内存监控**
   ```java
   // 监控内存使用
   Runtime runtime = Runtime.getRuntime();
   long usedMemory = runtime.totalMemory() - runtime.freeMemory();
   log.info("Memory usage: {} MB", usedMemory / 1024 / 1024);
   ```

### 3.2 索引优化配置

```java
// LuceneIndexRepository.java 优化
IndexWriterConfig config = new IndexWriterConfig(analyzer);

// 1. 使用更快的合并策略
config.setMergePolicy(new TieredMergePolicy());

// 2. 批量提交，减少 IO
config.setMaxBufferedDocs(1000);
config.setRAMBufferSizeMB(64);

// 3. 启用索引压缩
config.setUseCompoundFile(true);
```

---

## 四、阶段四：功能增强

### 4.1 支持的文件格式

当前支持:
- ✅ PDF
- ✅ Word (.doc, .docx)
- ✅ Excel (.xls, .xlsx)
- ✅ Text (.txt)
- ✅ Markdown (.md)
- ✅ HTML (.html)

**扩展计划**:
- [ ] 添加 XML/JSON 解析
- [ ] 添加源代码文件支持 (.java, .py, .js 等)
- [ ] 添加代码语义索引 (基于 AST)

### 4.2 高级搜索功能

**待实现**:

1. **向量搜索** (可选，本地计算)
   ```java
   // 本地 Embedding (不使用外网)
   // 使用轻量级模型如 TF-IDF 或 BM25
   ```

2. **模糊搜索**
   ```java
   // 支持通配符、正则表达式
   Query query = parser.parse("test~0.8"); // 模糊匹配
   ```

3. **分面搜索** (Facet Search)
   ```java
   // 按文件类型、时间、目录分组
   ```

### 4.3 文件监控增强

**当前实现**:
- ✅ NIO WatchService 基础实现
- ✅ 去抖动 (debounce) 配置

**优化项**:

1. **跨平台支持**
   - Windows: 使用 JNotify 或 WatchService
   - Linux: 使用 inotify (已支持)
   - macOS: 使用 FSEvents (WatchService 已支持)

2. **增量索引**
   ```java
   // 检测文件变化，只重新索引变化的部分
   public boolean needsReindex(Path filePath) {
       long currentModified = Files.getLastModifiedTime(filePath).toMillis();
       Long lastIndexed = lastModified.get(filePath);
       return lastIndexed == null || currentModified > lastIndexed;
   }
   ```

---

## 五、实施时间表

### Week 1: 基础设施

| 任务 | 状态 | 备注 |
|------|------|------|
| Java 21 环境配置 | 🔴 待开始 | 安装 JDK 21 |
| 虚拟线程改造 | 🔴 待开始 | 替换线程池 |
| Maven 仓库配置 | 🔴 待开始 | 解决依赖问题 |

### Week 2: MCP 集成

| 任务 | 状态 | 备注 |
|------|------|------|
| Spring AI MCP 依赖集成 | 🔴 待开始 | |
| MCP Handler 实现 | 🔴 待开始 | |
| MCP 协议测试 | 🔴 待开始 | |

### Week 3: 性能优化

| 任务 | 状态 | 备注 |
|------|------|------|
| 流式索引实现 | 🔴 待开始 | O(1) 内存 |
| 内存使用监控 | 🔴 待开始 | |
| 索引配置优化 | 🔴 待开始 | |

### Week 4: 功能增强与测试

| 任务 | 状态 | 备注 |
|------|------|------|
| 扩展文件格式支持 | 🔴 待开始 | |
| 高级搜索功能 | 🔴 待开始 | |
| 集成测试 | 🔴 待开始 | |
| 性能测试 | 🔴 待开始 | 100MB+ 文件测试 |

---

## 六、关键里程碑

1. **M1**: Java 21 + 虚拟线程运行正常
2. **M2**: MCP 协议完整支持，AI 助手可调用
3. **M3**: 100MB 文件索引内存占用稳定
4. **M4**: 完整功能可用，测试通过

---

## 七、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Java 21 不可用 | 高 | 保持 Java 17 回退方案 |
| Lucene 10.x 未发布 | 中 | 使用 Lucene 9.x 优化版 |
| MCP 依赖问题 | 高 | 配置多个 Maven 仓库 |
| 内存溢出 | 高 | 流式处理 + 监控 |

---

## 八、验证清单

### 功能验证

- [ ] 索引 PDF 文件成功
- [ ] 索引 Word 文档成功
- [ ] 索引 Excel 文件成功
- [ ] 搜索返回正确结果
- [ ] 文件变化自动检测
- [ ] MCP 工具可被调用

### 性能验证

- [ ] 10MB 文件索引 < 5秒
- [ ] 100MB 文件索引内存 < 200MB
- [ ] 搜索响应 < 100ms
- [ ] 并发索引 10 个文件正常

### 隐私验证

- [ ] 无外部网络调用 (抓包验证)
- [ ] 索引数据存储在本地
