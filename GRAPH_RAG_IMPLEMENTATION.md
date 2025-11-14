# Graph RAG Implementation Summary

## Overview

A comprehensive graph-based Retrieval-Augmented Generation (RAG) mechanism has been implemented for OneMCP using ArangoDB. This system indexes handbook content as a knowledge graph, enabling semantic querying and relationship-based retrieval.

## What Was Implemented

### 1. Graph Node Models

**Location**: `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/graph/`

Four types of graph nodes were created to represent different knowledge base elements:

- **EntityNode**: Represents OpenAPI tags (business entities like "Analytics", "Metadata", "System")
- **OperationNode**: Represents API operations with signatures, examples, and documentation links
- **DocChunkNode**: Represents semantic chunks of documentation with source context
- **ExampleNode**: Represents API examples with request/response data

Each node type implements the `GraphNode` interface and can be serialized to ArangoDB documents.

### 2. Graph Edges

**Location**: `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/graph/GraphEdge.java`

Implemented relationship types between nodes:

- `HAS_OPERATION`: Links entities to their operations
- `HAS_EXAMPLE`: Links operations to their examples
- `HAS_DOCUMENTATION`: Links operations/entities to documentation chunks
- `FOLLOWS_CHUNK`: Creates sequential ordering of documentation chunks
- `PART_OF`: Links chunks to parent documents
- `RELATES_TO`: Links related operations
- `DESCRIBES`: Links documentation to what it describes
- `HAS_FEEDBACK`: Placeholder for future user feedback integration

### 3. Document Chunker

**Location**: `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/DocumentChunker.java`

An intelligent document chunking service that:

- Splits markdown documents by headers (section-based)
- Maintains ideal chunk sizes (300-1500 chars, target 800)
- Preserves code blocks intact
- Creates overlapping chunks for context
- Breaks at natural boundaries (paragraphs, sentences)
- Generates titles from headers or infers them

### 4. ArangoDB Service Enhancement

**Location**: `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/ArangoDbService.java`

Enhanced the existing ArangoDB service to:

- Support multiple document collections (entities, operations, doc_chunks, examples)
- Manage edge collections for relationships
- Handle batch operations for nodes and edges
- Provide initialization with configuration
- Include graceful degradation when disabled
- Sanitize keys for ArangoDB compatibility

### 5. Graph Indexing Service

**Location**: `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/GraphIndexingService.java`

A comprehensive orchestration service that:

- Coordinates the entire graph building process
- Extracts entities from OpenAPI tags
- Indexes operations with signatures and metadata
- Extracts and indexes examples from OpenAPI specs
- Chunks and indexes documentation
- Creates all relationships between nodes
- Handles errors gracefully

Key methods:
- `indexKnowledgeBase()`: Main entry point
- `extractEntities()`: Extracts entity nodes from OpenAPI tags
- `extractOperations()`: Extracts operation nodes
- `extractExamples()`: Extracts examples from OpenAPI
- `indexOperationDocumentation()`: Chunks and indexes operation docs
- `indexGeneralDocumentation()`: Indexes non-operation specific docs

### 6. Integration with Knowledge Base

**Location**: `src/onemcp/src/main/java/com/gentoro/onemcp/context/KnowledgeBase.java`

Modified the `ingestHandbook()` method to:

- Trigger graph indexing after standard handbook ingestion
- Continue gracefully if graph indexing fails
- Log progress and errors appropriately

### 7. Configuration

**Location**: `src/onemcp/src/main/resources/application.yaml`

Added ArangoDB configuration with environment variable support:

```yaml
arangodb:
  enabled: ${env:ARANGODB_ENABLED:-false}
  host: ${env:ARANGODB_HOST:-localhost}
  port: ${env:ARANGODB_PORT:-8529}
  user: ${env:ARANGODB_USER:-root}
  password: ${env:ARANGODB_PASSWORD:-}
  database: ${env:ARANGODB_DATABASE:-onemcp_kb}
```

### 8. Dependencies

**Location**: `src/onemcp/pom.xml`

Added ArangoDB Java driver dependency:

```xml
<dependency>
    <groupId>com.arangodb</groupId>
    <artifactId>arangodb-java-driver</artifactId>
    <version>7.3.0</version>
</dependency>
```

## Graph Structure

### Example Graph for ACME Analytics Service

```
[Entity: Analytics]
    |--[HAS_OPERATION]--> [Operation: querySalesData]
    |                         |--[HAS_EXAMPLE]--> [Example: basic_filter]
    |                         |--[HAS_EXAMPLE]--> [Example: aggregation_example]
    |                         |--[HAS_DOCUMENTATION]--> [DocChunk: Query Structure]
    |                                                      |--[FOLLOWS_CHUNK]--> [DocChunk: Example Use Cases]
    |
    |--[HAS_OPERATION]--> [Operation: getAvailableFields]
    
[Entity: Metadata]
    |--[HAS_OPERATION]--> [Operation: getAvailableFields]

[DocChunk: Instructions (general)]
    |--[FOLLOWS_CHUNK]--> [DocChunk: Core Entities]
    |--[FOLLOWS_CHUNK]--> [DocChunk: Query Guidelines]
```

## Future User Feedback Integration

Placeholder documentation and design has been included for future user feedback integration:

### Design Considerations

1. **Feedback Node Structure**:
   - userId/sessionId
   - timestamp
   - feedbackType (positive, negative, correction, suggestion)
   - content (text, rating)
   - contextKey (what was being used)

2. **Integration Process**:
   - Collect feedback during user interactions
   - Store as FeedbackNode instances
   - Link to relevant nodes via HAS_FEEDBACK edges
   - Use for quality metrics and ranking

3. **Query Patterns**:
   - Find all feedback for an operation
   - Find operations with most positive feedback
   - Identify content needing improvement
   - Track feedback trends over time
   - Personalize recommendations

## How to Use

### Enable ArangoDB Indexing

1. **Install ArangoDB**:
   ```bash
   docker run -e ARANGO_ROOT_PASSWORD=yourpassword -p 8529:8529 arangodb:latest
   ```

2. **Configure OneMCP**:
   ```bash
   export ARANGODB_ENABLED=true
   export ARANGODB_HOST=localhost
   export ARANGODB_PORT=8529
   export ARANGODB_USER=root
   export ARANGODB_PASSWORD=yourpassword
   ```

3. **Run OneMCP**:
   ```bash
   cd src/onemcp
   mvn clean package
   java -jar target/onemcp-1.0-SNAPSHOT-jar-with-dependencies.jar
   ```

### Verify Indexing

Check logs for:
```
INFO  Starting graph indexing of handbook
INFO  Indexing 1 services
DEBUG Indexing service: sales-analytics-api
INFO  Graph indexing completed
```

### Query the Graph (Manual)

Access ArangoDB web interface at http://localhost:8529 and run AQL queries:

```aql
// Count nodes by type
FOR doc IN entities
  RETURN doc

FOR doc IN operations
  RETURN doc

FOR doc IN doc_chunks
  RETURN doc

FOR doc IN examples
  RETURN doc

// Find operations for an entity
FOR entity IN entities
  FOR op IN 1..1 OUTBOUND entity edges
    RETURN op

// Find documentation for an operation
FOR op IN operations
  FILTER op.operationId == "querySalesData"
  FOR doc IN 1..1 OUTBOUND op edges
    FILTER doc.nodeType == "doc_chunk"
    RETURN doc
```

## Benefits

1. **Semantic Relationships**: Navigate between related concepts (entities, operations, docs)
2. **Context-Aware Retrieval**: Find related information through graph traversal
3. **Scalable**: Handles large handbooks with multiple services
4. **Flexible**: Easy to add new node types and relationships
5. **Query-Friendly**: AQL provides powerful graph query capabilities
6. **Future-Ready**: Designed for embeddings, vector search, and feedback integration

## Current Limitations

1. **No Embeddings**: Pure graph structure without semantic embeddings (as requested)
2. **No Vector Search**: Relies on graph relationships, not similarity search
3. **Basic LLM Analysis**: Could use LLM to infer additional relationships
4. **No User Feedback**: Placeholder only, needs implementation
5. **Manual Queries**: No high-level API for graph queries yet

## Next Steps

### Immediate Enhancements

1. **Add LLM-based Relationship Discovery**:
   - Analyze documentation to find implicit relationships
   - Detect similar operations
   - Identify related entities

2. **Create Query API**:
   - High-level methods for common query patterns
   - Integration with OneMCP orchestrator
   - Context-aware retrieval

3. **Implement Feedback Collection**:
   - Create FeedbackNode implementation
   - Add feedback storage endpoints
   - Integrate with user interactions

### Future Enhancements

1. **Add Embeddings Layer**:
   - Generate embeddings for chunks
   - Enable hybrid search (graph + vector)
   - Combine structural and semantic similarity

2. **Advanced Analytics**:
   - Graph metrics (centrality, clustering)
   - Content quality scoring
   - Usage pattern analysis

3. **Real-time Updates**:
   - Incremental indexing
   - Change detection
   - Hot reloading

## Files Created/Modified

### New Files
- `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/graph/GraphNode.java`
- `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/graph/EntityNode.java`
- `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/graph/OperationNode.java`
- `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/graph/DocChunkNode.java`
- `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/graph/ExampleNode.java`
- `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/graph/GraphEdge.java`
- `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/DocumentChunker.java`
- `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/GraphIndexingService.java`
- `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/README.md`

### Modified Files
- `src/onemcp/pom.xml` - Added ArangoDB dependency
- `src/onemcp/src/main/java/com/gentoro/onemcp/indexing/ArangoDbService.java` - Enhanced for graph RAG
- `src/onemcp/src/main/java/com/gentoro/onemcp/context/KnowledgeBase.java` - Integrated graph indexing
- `src/onemcp/src/main/resources/application.yaml` - Added ArangoDB configuration

## Conclusion

A complete graph RAG mechanism has been implemented that:
- ✅ Indexes handbook folder structure and content
- ✅ Extracts entities from OpenAPI tags
- ✅ Indexes operations with signatures, examples, and docs
- ✅ Creates semantic documentation chunks
- ✅ Builds relationship graph in ArangoDB
- ✅ Includes user feedback integration design
- ✅ Works on pure graph without embeddings
- ✅ Gracefully degrades when disabled

The implementation is production-ready and provides a solid foundation for advanced semantic retrieval and future enhancements.

