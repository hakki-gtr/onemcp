# Graph RAG Quick Start Guide

## Prerequisites

- Java 21+
- Maven 3.8+
- Docker (for ArangoDB)

## Step 1: Start ArangoDB

```bash
# Start ArangoDB in Docker
docker run -d \
  --name arangodb \
  -p 8529:8529 \
  -e ARANGO_ROOT_PASSWORD=test123 \
  arangodb:latest

# Verify it's running
curl http://localhost:8529/_api/version
```

## Step 2: Configure OneMCP

Set environment variables (optional - defaults are already configured):

```bash
export ARANGODB_ENABLED=true       # Already enabled by default
export ARANGODB_HOST=localhost     # Default
export ARANGODB_PORT=8529          # Default
export ARANGODB_USER=root          # Default
export ARANGODB_PASSWORD=test123   # Default
export ARANGODB_DATABASE=onemcp_kb # Default
```

Or edit `src/onemcp/src/main/resources/application.yaml`:

```yaml
arangodb:
  enabled: true           # Already enabled by default
  host: localhost
  port: 8529
  user: root
  password: test123       # Default password
  database: onemcp_kb
```

## Step 3: Build and Run OneMCP

```bash
cd src/onemcp
mvn clean package
java -jar target/onemcp-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Look for these log messages:

```
INFO  Starting graph indexing of handbook
INFO  Initializing ArangoDB connection: localhost:8529
INFO  Creating ArangoDB database: onemcp_kb
INFO  Creating collection: entities (type: DOCUMENT)
INFO  Creating collection: operations (type: DOCUMENT)
INFO  Creating collection: doc_chunks (type: DOCUMENT)
INFO  Creating collection: examples (type: DOCUMENT)
INFO  Creating collection: edges (type: EDGES)
INFO  ArangoDB service initialized successfully
INFO  Indexing 1 services
DEBUG Indexing service: sales-analytics-api
DEBUG Indexed service sales-analytics-api with 3 entities, 3 operations, 6 examples
INFO  Graph indexing completed
```

## Step 4: Explore the Graph

### Access ArangoDB Web UI

Open http://localhost:8529 in your browser:
- Username: `root`
- Password: `test123`

### Query Examples

Go to the "Queries" tab and try these:

#### List all entities
```aql
FOR entity IN entities
  RETURN entity
```

#### List all operations
```aql
FOR op IN operations
  RETURN {
    id: op.operationId,
    method: op.method,
    path: op.path,
    summary: op.summary
  }
```

#### Find operations for "Analytics" entity
```aql
FOR entity IN entities
  FILTER entity.name == "Analytics"
  FOR op IN 1..1 OUTBOUND entity edges
    RETURN {
      entity: entity.name,
      operation: op.operationId,
      method: op.method,
      path: op.path
    }
```

#### Find documentation for an operation
```aql
FOR op IN operations
  FILTER op.operationId == "querySalesData"
  FOR doc IN 1..1 OUTBOUND op edges
    FILTER doc.nodeType == "doc_chunk"
    SORT doc.chunkIndex
    RETURN {
      chunk: doc.chunkIndex,
      title: doc.title,
      content: SUBSTRING(doc.content, 0, 100)
    }
```

#### Find examples for an operation
```aql
FOR op IN operations
  FILTER op.operationId == "querySalesData"
  FOR example IN 1..1 OUTBOUND op edges
    FILTER example.nodeType == "example"
    RETURN {
      name: example.name,
      summary: example.summary,
      hasRequest: example.requestBody != "",
      hasResponse: example.responseBody != ""
    }
```

#### Get full context for an operation
```aql
LET operation = FIRST(
  FOR op IN operations
    FILTER op.operationId == "querySalesData"
    RETURN op
)

LET documentation = (
  FOR doc IN 1..1 OUTBOUND operation edges
    FILTER doc.nodeType == "doc_chunk"
    SORT doc.chunkIndex
    RETURN {
      title: doc.title,
      content: doc.content
    }
)

LET examples = (
  FOR ex IN 1..1 OUTBOUND operation edges
    FILTER ex.nodeType == "example"
    RETURN {
      name: ex.name,
      summary: ex.summary
    }
)

LET entities = (
  FOR entity IN 1..1 INBOUND operation edges
    FILTER entity.nodeType == "entity"
    RETURN entity.name
)

RETURN {
  operation: {
    id: operation.operationId,
    method: operation.method,
    path: operation.path,
    summary: operation.summary
  },
  entities: entities,
  exampleCount: LENGTH(examples),
  docChunkCount: LENGTH(documentation)
}
```

## Step 5: Verify Graph Structure

### Count nodes by type
```aql
RETURN {
  entities: LENGTH(entities),
  operations: LENGTH(operations),
  docChunks: LENGTH(doc_chunks),
  examples: LENGTH(examples),
  edges: LENGTH(edges)
}
```

### Visualize graph
```aql
FOR entity IN entities
  LIMIT 1
  FOR v, e, p IN 1..2 OUTBOUND entity edges
    RETURN p
```

Click "Graph" view to see visual representation.

## Step 6: Test Different Scenarios

### Scenario 1: Find all operations in a service
```aql
FOR op IN operations
  FILTER op.serviceSlug == "sales-analytics-api"
  RETURN {
    operation: op.operationId,
    method: op.method,
    path: op.path
  }
```

### Scenario 2: Find related operations (via shared entity)
```aql
LET targetOp = FIRST(
  FOR op IN operations
    FILTER op.operationId == "querySalesData"
    RETURN op
)

FOR entity IN 1..1 INBOUND targetOp edges
  FILTER entity.nodeType == "entity"
  FOR relatedOp IN 1..1 OUTBOUND entity edges
    FILTER relatedOp._key != targetOp._key
    RETURN DISTINCT {
      entity: entity.name,
      operation: relatedOp.operationId
    }
```

### Scenario 3: Find documentation chunks by content
```aql
FOR doc IN doc_chunks
  FILTER CONTAINS(LOWER(doc.content), "filter")
  RETURN {
    title: doc.title,
    snippet: SUBSTRING(doc.content, 0, 150)
  }
```

### Scenario 4: Analyze chunk sizes
```aql
FOR doc IN doc_chunks
  LET size = doc.endOffset - doc.startOffset
  COLLECT sizeRange = FLOOR(size / 100) * 100
  AGGREGATE count = COUNT(*)
  SORT sizeRange
  RETURN {
    sizeRange: CONCAT(sizeRange, "-", sizeRange + 99, " chars"),
    count: count
  }
```

## Troubleshooting

### Issue: ArangoDB connection failed

**Check if ArangoDB is running:**
```bash
docker ps | grep arangodb
```

**Check logs:**
```bash
docker logs arangodb
```

**Restart if needed:**
```bash
docker restart arangodb
```

### Issue: Graph indexing disabled

**Check configuration:**
```bash
cat src/onemcp/src/main/resources/application.yaml | grep -A 6 arangodb
```

**Verify environment variables:**
```bash
echo $ARANGODB_ENABLED
echo $ARANGODB_HOST
```

### Issue: Collections not created

**Check logs for errors:**
```bash
grep -i "arangodb" logs/onemcp.log
```

**Manually create database:**
```bash
curl -X POST \
  http://localhost:8529/_db/_system/_api/database \
  -H 'Content-Type: application/json' \
  -u root:test123 \
  -d '{"name":"onemcp_kb"}'
```

### Issue: No data in collections

**Verify handbook was ingested:**
```bash
grep -i "ingest" logs/onemcp.log
```

**Check for indexing errors:**
```bash
grep -i "graph indexing" logs/onemcp.log
```

**Manually trigger indexing (future enhancement):**
```java
// Add endpoint to re-trigger indexing
POST /admin/reindex-graph
```

## Performance Tuning

### For Large Handbooks

Adjust chunk sizes in `DocumentChunker.java`:
```java
private static final int TARGET_CHUNK_SIZE = 500;  // Smaller chunks
private static final int MAX_CHUNK_SIZE = 1000;
```

### For Many Operations

Increase ArangoDB resources:
```bash
docker run -d \
  --name arangodb \
  -p 8529:8529 \
  -e ARANGO_ROOT_PASSWORD=test123 \
  -m 4g \  # 4GB memory limit
  --cpus=2 \  # 2 CPU cores
  arangodb:latest
```

### For Query Performance

Create additional indexes:
```aql
-- Index on operation method
FOR op IN operations
  COLLECT method = op.method WITH COUNT INTO count
  RETURN {method, count}

-- Create index if needed
db._collection("operations").ensureIndex({
  type: "persistent",
  fields: ["method"]
})
```

## Next Steps

1. **Explore Graph Queries**: Try more complex graph traversals
2. **Add Custom Relationships**: Extend GraphEdge.EdgeType
3. **Implement Retrieval**: Build API for graph-based retrieval
4. **Add Feedback**: Implement user feedback collection
5. **Enable Embeddings**: Add vector search layer (future)

## Resources

- [ArangoDB Documentation](https://www.arangodb.com/docs/)
- [AQL Tutorial](https://www.arangodb.com/docs/stable/aql/tutorial.html)
- [Graph Queries](https://www.arangodb.com/docs/stable/aql/graphs.html)
- [OneMCP Graph RAG README](src/onemcp/src/main/java/com/gentoro/onemcp/indexing/README.md)
- [Architecture Documentation](docs/graph-rag-architecture.md)

## Support

For issues or questions:
1. Check logs: `logs/onemcp.log`
2. Review implementation: `GRAPH_RAG_IMPLEMENTATION.md`
3. Consult architecture: `docs/graph-rag-architecture.md`

