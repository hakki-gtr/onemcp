# Graph RAG Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          OneMCP Application                          │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         Knowledge Base                               │
│  - Processes OpenAPI definitions                                     │
│  - Loads markdown documents                                          │
│  - Triggers graph indexing                                          │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Graph Indexing Service                            │
│  - Orchestrates graph building                                       │
│  - Extracts entities, operations, examples                          │
│  - Chunks documentation                                              │
│  - Creates relationships                                             │
└─────────────────────────────────────────────────────────────────────┘
                    │                              │
                    ▼                              ▼
    ┌──────────────────────────┐    ┌──────────────────────────┐
    │   DocumentChunker        │    │   ArangoDB Service       │
    │  - Semantic chunking     │    │  - Node storage          │
    │  - Size optimization     │    │  - Edge storage          │
    │  - Context preservation  │    │  - Collection mgmt       │
    └──────────────────────────┘    └──────────────────────────┘
                                                 │
                                                 ▼
                                    ┌──────────────────────────┐
                                    │      ArangoDB            │
                                    │  - entities              │
                                    │  - operations            │
                                    │  - doc_chunks            │
                                    │  - examples              │
                                    │  - edges                 │
                                    └──────────────────────────┘
```

## Data Flow

```
Handbook Ingestion
       │
       ├─→ Load OpenAPI specs ────→ Extract Tags ────→ Entity Nodes
       │                                                     │
       │                                                     ▼
       ├─→ Load Operations ────────→ Build Signatures ──→ Operation Nodes
       │                                 │                   │
       │                                 │                   ▼
       │                                 └────→ Extract ──→ Example Nodes
       │
       └─→ Load Markdown docs ────→ Chunk ─────────────→ DocChunk Nodes
       
       
Create Relationships
       │
       ├─→ Entity ──[HAS_OPERATION]──→ Operation
       ├─→ Operation ──[HAS_EXAMPLE]──→ Example
       ├─→ Operation ──[HAS_DOCUMENTATION]──→ DocChunk
       └─→ DocChunk ──[FOLLOWS_CHUNK]──→ DocChunk
```

## Node Types and Properties

### EntityNode
```
Key: entity_{serviceSlug}_{tagName}
─────────────────────────────────
├─ name: string
├─ description: string
├─ serviceSlug: string
└─ associatedOperations: List<string>
```

### OperationNode
```
Key: op_{serviceSlug}_{operationId}
─────────────────────────────────
├─ operationId: string
├─ method: string
├─ path: string
├─ summary: string
├─ description: string
├─ serviceSlug: string
├─ tags: List<string>
├─ signature: string
├─ exampleKeys: List<string>
└─ documentationUri: string
```

### DocChunkNode
```
Key: chunk_{identifier}_{index}
─────────────────────────────────
├─ content: string
├─ sourceUri: string
├─ sourceType: string
├─ chunkIndex: int
├─ startOffset: int
├─ endOffset: int
├─ title: string
└─ parentKey: string
```

### ExampleNode
```
Key: example_{serviceSlug}_{operationId}_{name}
─────────────────────────────────
├─ name: string
├─ summary: string
├─ description: string
├─ requestBody: string (JSON)
├─ responseBody: string (JSON)
├─ responseStatus: string
├─ operationKey: string
└─ serviceSlug: string
```

## Edge Relationships

```
Entity ──[HAS_OPERATION]──────→ Operation
                                    │
                                    ├──[HAS_EXAMPLE]──────→ Example
                                    │
                                    └──[HAS_DOCUMENTATION]──→ DocChunk
                                                               │
                                                               └──[FOLLOWS_CHUNK]──→ DocChunk
```

## Query Patterns

### Pattern 1: Find Operations by Entity
```aql
FOR entity IN entities
  FILTER entity.name == "Analytics"
  FOR op IN 1..1 OUTBOUND entity edges
    RETURN op
```

### Pattern 2: Find Documentation for Operation
```aql
FOR op IN operations
  FILTER op.operationId == "querySalesData"
  FOR doc IN 1..1 OUTBOUND op edges
    FILTER doc.nodeType == "doc_chunk"
    SORT doc.chunkIndex
    RETURN doc
```

### Pattern 3: Find Examples for Operation
```aql
FOR op IN operations
  FILTER op.operationId == "querySalesData"
  FOR example IN 1..1 OUTBOUND op edges
    FILTER example.nodeType == "example"
    RETURN example
```

### Pattern 4: Find Related Operations
```aql
FOR op IN operations
  FILTER op.operationId == "querySalesData"
  FOR entity IN 1..1 INBOUND op edges
    FOR related IN 1..1 OUTBOUND entity edges
      FILTER related._key != op._key
      RETURN DISTINCT related
```

### Pattern 5: Full Context Retrieval
```aql
LET operation = DOCUMENT("operations/op_sales_analytics_querySalesData")

LET documentation = (
  FOR doc IN 1..1 OUTBOUND operation edges
    FILTER doc.nodeType == "doc_chunk"
    SORT doc.chunkIndex
    RETURN doc
)

LET examples = (
  FOR ex IN 1..1 OUTBOUND operation edges
    FILTER ex.nodeType == "example"
    RETURN ex
)

LET entities = (
  FOR entity IN 1..1 INBOUND operation edges
    FILTER entity.nodeType == "entity"
    RETURN entity
)

RETURN {
  operation: operation,
  documentation: documentation,
  examples: examples,
  entities: entities
}
```

## Document Chunking Strategy

```
Original Document (instructions.md)
│
├─ Header: "# LLM Instructions"
│  └─ Chunk 1: [0:245] "# LLM Instructions\n\n## Purpose\n..."
│     ├─ title: "LLM Instructions"
│     └─ sourceType: "general_doc"
│
├─ Header: "## Purpose"
│  └─ Chunk 2: [246:520] "## Purpose\n\nYou are an AI assistant..."
│     ├─ title: "Purpose"
│     └─ [FOLLOWS_CHUNK] → Chunk 1
│
├─ Header: "## Scope"
│  └─ Chunk 3: [521:780] "## Scope\n\nYou can help with:..."
│     ├─ title: "Scope"
│     └─ [FOLLOWS_CHUNK] → Chunk 2
│
└─ Header: "## Core Entities"
   └─ Chunk 4: [781:1050] "## Core Entities\n\n- **Sales**:..."
      ├─ title: "Core Entities"
      └─ [FOLLOWS_CHUNK] → Chunk 3
```

## Future: User Feedback Integration

```
┌─────────────────────────────────────────────────────────────────────┐
│                         User Interaction                             │
│  - Query execution                                                   │
│  - Example usage                                                     │
│  - Documentation reading                                             │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Feedback Collection                             │
│  - Positive/Negative rating                                          │
│  - Corrections                                                       │
│  - Suggestions                                                       │
│  - Context capture                                                   │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       FeedbackNode                                   │
│  Key: feedback_{uuid}                                                │
│  ─────────────────────────────────                                  │
│  ├─ userId: string                                                   │
│  ├─ timestamp: datetime                                              │
│  ├─ feedbackType: string                                             │
│  ├─ content: string                                                  │
│  └─ contextKey: string                                               │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
                    Operation/Example/DocChunk
                          ▲
                          │
                    [HAS_FEEDBACK]
```

### Feedback Query Patterns

```aql
-- Find most helpful operations
FOR op IN operations
  LET feedback = (
    FOR f IN 1..1 INBOUND op edges
      FILTER f.nodeType == "feedback" AND f.feedbackType == "positive"
      RETURN f
  )
  SORT LENGTH(feedback) DESC
  LIMIT 10
  RETURN {
    operation: op,
    positiveCount: LENGTH(feedback)
  }

-- Find content needing improvement
FOR doc IN doc_chunks
  LET negativeFeedback = (
    FOR f IN 1..1 INBOUND doc edges
      FILTER f.nodeType == "feedback" AND f.feedbackType == "negative"
      RETURN f
  )
  FILTER LENGTH(negativeFeedback) > 3
  RETURN {
    chunk: doc,
    negativeCount: LENGTH(negativeFeedback),
    feedbackDetails: negativeFeedback
  }

-- Track user feedback history
FOR f IN feedback
  FILTER f.userId == @userId
  SORT f.timestamp DESC
  LIMIT 50
  LET target = (
    FOR node IN 1..1 OUTBOUND f edges
      RETURN node
  )
  RETURN {
    feedback: f,
    target: FIRST(target)
  }
```

## Scalability Considerations

### Indexing Performance
- **Batch operations**: Store nodes/edges in batches
- **Lazy initialization**: Create collections on-demand
- **Parallel processing**: Chunk documents in parallel (future)
- **Incremental updates**: Re-index only changed content (future)

### Query Performance
- **Primary key index**: Automatic on `_key`
- **Edge indexes**: Automatic on `_from` and `_to`
- **Custom indexes**: Create on frequently queried fields
- **Graph traversal optimization**: Use PRUNE and path depth limits

### Storage Optimization
- **Chunk size tuning**: Balance between context and storage
- **Compression**: Enable ArangoDB compression
- **TTL policies**: Archive old feedback (future)
- **Selective indexing**: Skip non-essential content (future)

## Configuration Tuning

### Document Chunking
```java
// In DocumentChunker.java
private static final int MIN_CHUNK_SIZE = 300;      // Adjustable
private static final int TARGET_CHUNK_SIZE = 800;   // Adjustable
private static final int MAX_CHUNK_SIZE = 1500;     // Adjustable
```

### ArangoDB Connection
```yaml
arangodb:
  enabled: true
  host: localhost
  port: 8529
  # Connection pooling (future)
  maxConnections: 10
  connectionTimeout: 5000
  # Query limits (future)
  maxTraversalDepth: 5
  maxResultSize: 1000
```

## Monitoring and Observability

### Key Metrics
- Indexing duration
- Number of nodes by type
- Number of edges by type
- Failed indexing operations
- Query response times
- Graph traversal depths

### Logging
```
INFO  Starting graph indexing of handbook
DEBUG Indexing service: sales-analytics-api
DEBUG Created 3 entity nodes
DEBUG Created 12 operation nodes
DEBUG Created 45 doc chunks
DEBUG Created 8 example nodes
DEBUG Created 68 edges
INFO  Graph indexing completed in 2.3s
```

## Error Handling

### Graceful Degradation
```
ArangoDB unavailable
       │
       ├─→ Log warning
       ├─→ Continue handbook ingestion
       └─→ OneMCP operates without graph features
```

### Retry Logic (Future)
- Connection failures: Retry with exponential backoff
- Transaction failures: Retry transient errors
- Partial failures: Log and continue with remaining items

## Security Considerations

- **Authentication**: Use ArangoDB user credentials
- **Authorization**: Set up role-based access
- **Network**: Use TLS for production deployments
- **Data sanitization**: Sanitize keys and prevent injection
- **Audit logging**: Track all graph modifications (future)

