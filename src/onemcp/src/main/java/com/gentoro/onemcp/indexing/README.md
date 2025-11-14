# Graph RAG Implementation for OneMCP

## Overview

This package implements a graph-based Retrieval-Augmented Generation (RAG) mechanism using ArangoDB for indexing OneMCP handbooks. The graph representation enables semantic querying and retrieval based on relationships between different knowledge base elements.

## Architecture

### Components

1. **Graph Nodes** (`com.gentoro.onemcp.indexing.graph.*`)
   - `EntityNode`: Represents OpenAPI tags (business concepts/service categories)
   - `OperationNode`: Represents API operations/endpoints
   - `DocChunkNode`: Represents semantic chunks of documentation
   - `ExampleNode`: Represents API examples (request/response pairs)

2. **Graph Edges** (`GraphEdge`)
   - Defines relationships between nodes (HAS_OPERATION, HAS_EXAMPLE, HAS_DOCUMENTATION, etc.)
   - Enables traversal and semantic queries

3. **Services**
   - `ArangoDbService`: Manages ArangoDB connection and CRUD operations
   - `GraphIndexingService`: Orchestrates graph building after handbook ingestion
   - `DocumentChunker`: Splits markdown into semantic chunks

## Graph Structure

### Node Types

#### 1. Entity Nodes
- **Source**: OpenAPI tags
- **Key Format**: `entity_{serviceSlug}_{tagName}`
- **Properties**:
  - name: Tag name
  - description: Tag description
  - serviceSlug: Associated service
  - associatedOperations: List of operation keys

#### 2. Operation Nodes
- **Source**: OpenAPI operations
- **Key Format**: `op_{serviceSlug}_{operationId}`
- **Properties**:
  - operationId, method, path
  - summary, description
  - signature: Human-readable operation signature
  - tags: Associated entity tags
  - exampleKeys: Associated examples
  - documentationUri: Link to generated docs

#### 3. Doc Chunk Nodes
- **Source**: Markdown files and OpenAPI descriptions
- **Key Format**: `chunk_{identifier}_{index}`
- **Properties**:
  - content: Chunk text content
  - sourceUri: Original document URI
  - sourceType: markdown, openapi_description, etc.
  - chunkIndex, startOffset, endOffset
  - title: Section heading or inferred title
  - parentKey: Parent node reference

#### 4. Example Nodes
- **Source**: OpenAPI request/response examples
- **Key Format**: `example_{serviceSlug}_{operationId}_{exampleName}`
- **Properties**:
  - name, summary, description
  - requestBody, responseBody
  - responseStatus
  - operationKey: Associated operation

### Edge Types

- `HAS_OPERATION`: Entity → Operation
- `HAS_EXAMPLE`: Operation → Example
- `HAS_DOCUMENTATION`: Operation/Entity → DocChunk
- `FOLLOWS_CHUNK`: DocChunk → DocChunk (sequential)
- `PART_OF`: DocChunk → Parent Document
- `RELATES_TO`: Operation ↔ Operation (similar functionality)
- `DESCRIBES`: DocChunk → Entity/Operation
- `HAS_FEEDBACK`: Node → Feedback (future implementation)

## Configuration

### application.yaml

```yaml
arangodb:
  enabled: ${env:ARANGODB_ENABLED:-false}  # Enable/disable graph indexing
  host: ${env:ARANGODB_HOST:-localhost}
  port: ${env:ARANGODB_PORT:-8529}
  user: ${env:ARANGODB_USER:-root}
  password: ${env:ARANGODB_PASSWORD:-}
  database: ${env:ARANGODB_DATABASE:-onemcp_kb}
```

### Environment Variables

- `ARANGODB_ENABLED`: Set to `true` to enable graph indexing
- `ARANGODB_HOST`: ArangoDB server hostname
- `ARANGODB_PORT`: ArangoDB server port
- `ARANGODB_USER`: Database user
- `ARANGODB_PASSWORD`: Database password
- `ARANGODB_DATABASE`: Database name

## Collections

The implementation creates the following ArangoDB collections:

- `entities`: Document collection for entity nodes
- `operations`: Document collection for operation nodes
- `doc_chunks`: Document collection for documentation chunk nodes
- `examples`: Document collection for example nodes
- `edges`: Edge collection for all relationship types

## Usage

### Automatic Indexing

Graph indexing happens automatically during handbook ingestion:

```java
KnowledgeBase kb = new KnowledgeBase(oneMcp);
kb.ingestHandbook(); // Triggers graph indexing
```

### Manual Indexing

```java
GraphIndexingService indexer = new GraphIndexingService(oneMcp);
indexer.indexKnowledgeBase();
```

### Querying the Graph (Future)

While the current implementation focuses on graph building, future queries might look like:

```aql
// Find all operations in an entity
FOR entity IN entities
  FILTER entity._key == "entity_sales_analytics_Analytics"
  FOR op IN 1..1 OUTBOUND entity edges
    RETURN op

// Find documentation for an operation
FOR op IN operations
  FILTER op._key == "op_sales_analytics_querySalesData"
  FOR doc IN 1..1 OUTBOUND op edges
    FILTER doc.nodeType == "doc_chunk"
    RETURN doc

// Find related operations
FOR op IN operations
  FOR related IN 1..2 ANY op edges
    FILTER related.nodeType == "operation"
    RETURN DISTINCT related
```

## Document Chunking

The `DocumentChunker` service intelligently splits markdown documents:

### Chunking Strategy

1. **Section-based**: Splits on markdown headers (#, ##, ###, etc.)
2. **Size-based**: Target chunk size of 800 characters (min: 300, max: 1500)
3. **Context preservation**: 10% overlap between chunks for context
4. **Code block protection**: Keeps code blocks intact
5. **Natural breaks**: Prefers paragraph/sentence boundaries

### Chunk Properties

- Content with semantic context
- Source URI and type
- Position offsets (startOffset, endOffset)
- Inferred or explicit titles
- Sequential ordering (via FOLLOWS_CHUNK edges)

## Future: User Feedback Integration

The design includes placeholders for user feedback integration:

### Feedback Node Design

```java
class FeedbackNode implements GraphNode {
  String userId;
  Timestamp timestamp;
  String feedbackType; // positive, negative, correction, suggestion
  String content;
  String contextKey; // What the user was interacting with
}
```

### Integration Points

1. Collect feedback during user interactions
2. Store as `FeedbackNode` instances
3. Link to relevant nodes via `HAS_FEEDBACK` edges
4. Use for:
   - Quality metrics
   - Relevance ranking
   - Content improvement
   - Personalized recommendations

### Example Usage

```java
// Store feedback
FeedbackNode feedback = new FeedbackNode(
    "feedback_12345",
    userId,
    timestamp,
    "positive",
    "This example was very helpful!",
    exampleKey
);
arangoDbService.storeNode(feedback);
arangoDbService.storeEdge(new GraphEdge(
    exampleKey,
    feedback.getKey(),
    GraphEdge.EdgeType.HAS_FEEDBACK
));

// Query feedback
// Find operations with most positive feedback
// Find content that needs improvement
// Track feedback trends over time
```

## Performance Considerations

### Indexing Performance

- Indexing happens once during handbook ingestion
- Failed graph indexing doesn't fail overall ingestion
- Collections are created lazily
- Batch operations for efficiency

### Query Performance

- Primary key (_key) is automatically indexed
- Additional indexes created on-demand
- Consider compound indexes for common query patterns
- Use AQL query optimization features

## Troubleshooting

### Common Issues

1. **ArangoDB Connection Failed**
   - Check if ArangoDB is running
   - Verify connection parameters
   - Check network connectivity

2. **Graph Indexing Disabled**
   - Set `arangodb.enabled=true` in configuration
   - Provide valid connection parameters

3. **Large Handbooks**
   - Indexing may take time for large handbooks
   - Monitor ArangoDB memory usage
   - Consider chunking parameters adjustment

### Logging

Enable debug logging for graph operations:

```yaml
logging:
  level:
    com.gentoro.onemcp.indexing: DEBUG
```

## Development

### Adding New Node Types

1. Create a class implementing `GraphNode`
2. Define properties and `toMap()` method
3. Add collection to `ArangoDbService`
4. Update `GraphIndexingService` to extract and index

### Adding New Edge Types

1. Add to `GraphEdge.EdgeType` enum
2. Create edges in `GraphIndexingService`
3. Document the relationship semantics

### Testing

```bash
# Set up test ArangoDB instance
docker run -e ARANGO_ROOT_PASSWORD=test -p 8529:8529 arangodb:latest

# Enable in tests
export ARANGODB_ENABLED=true
export ARANGODB_PASSWORD=test

# Run OneMCP
mvn clean package
java -jar target/onemcp-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## References

- [ArangoDB Java Driver](https://www.arangodb.com/docs/stable/drivers/java.html)
- [OpenAPI Specification](https://swagger.io/specification/)
- [Graph RAG Patterns](https://arxiv.org/abs/2404.16130)

