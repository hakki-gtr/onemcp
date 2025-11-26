package com.gentoro.onemcp.indexing.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gentoro.onemcp.utility.JacksonUtility;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Chunks large OpenAPI specifications into manageable pieces for LLM processing.
 *
 * <p>This class intelligently groups operations by tags/paths to create coherent chunks that fit
 * within LLM context windows while maintaining semantic relationships.
 */
public class OpenApiChunker {
  private static final org.slf4j.Logger log =
      com.gentoro.onemcp.logging.LoggingService.getLogger(OpenApiChunker.class);

  private final OpenApiSpecParser parser;
  private final int maxOperationsPerChunk;
  private final int maxChunkSizeBytes;

  /**
   * Create a new chunker with default settings.
   *
   * @param parser the OpenAPI parser
   */
  public OpenApiChunker(OpenApiSpecParser parser) {
    this(parser, 20, 50000); // Default: 20 operations or ~50KB per chunk
  }

  /**
   * Create a new chunker with custom settings.
   *
   * @param parser the OpenAPI parser
   * @param maxOperationsPerChunk maximum number of operations per chunk
   * @param maxChunkSizeBytes maximum chunk size in bytes (approximate)
   */
  public OpenApiChunker(OpenApiSpecParser parser, int maxOperationsPerChunk, int maxChunkSizeBytes) {
    this.parser = parser;
    this.maxOperationsPerChunk = maxOperationsPerChunk;
    this.maxChunkSizeBytes = maxChunkSizeBytes;
  }

  /**
   * Represents a chunk of operations to be processed together.
   */
  public static class OperationChunk {
    private final String chunkId;
    private final List<OpenApiSpecParser.ParsedOperation> operations;
    private final List<String> tags;
    private final Map<String, Object> metadata;

    public OperationChunk(
        String chunkId,
        List<OpenApiSpecParser.ParsedOperation> operations,
        List<String> tags,
        Map<String, Object> metadata) {
      this.chunkId = chunkId;
      this.operations = operations;
      this.tags = tags;
      this.metadata = metadata;
    }

    public String getChunkId() {
      return chunkId;
    }

    public List<OpenApiSpecParser.ParsedOperation> getOperations() {
      return operations;
    }

    public List<String> getTags() {
      return tags;
    }

    public Map<String, Object> getMetadata() {
      return metadata;
    }
  }

  /**
   * Chunk operations into groups that can be processed independently.
   *
   * <p>Operations are grouped by tags first, then by path prefixes if needed. This ensures
   * semantically related operations are processed together.
   *
   * @return list of operation chunks
   */
  public List<OperationChunk> chunkOperations() {
    List<OpenApiSpecParser.ParsedOperation> allOperations = parser.extractOperations();
    List<OpenApiSpecParser.ParsedTag> allTags = parser.extractTags();

    if (allOperations.isEmpty()) {
      log.warn("No operations found in OpenAPI spec");
      return Collections.emptyList();
    }

    log.info(
        "Chunking {} operations into manageable pieces (max {} per chunk)",
        allOperations.size(),
        maxOperationsPerChunk);

    // Group operations by primary tag
    Map<String, List<OpenApiSpecParser.ParsedOperation>> operationsByTag =
        groupOperationsByTag(allOperations);

    List<OperationChunk> chunks = new ArrayList<>();
    int chunkIndex = 0;

    // Create chunks from tag groups
    for (Map.Entry<String, List<OpenApiSpecParser.ParsedOperation>> tagEntry :
        operationsByTag.entrySet()) {
      String tag = tagEntry.getKey();
      List<OpenApiSpecParser.ParsedOperation> tagOperations = tagEntry.getValue();

      // If tag group fits in one chunk, add it
      if (tagOperations.size() <= maxOperationsPerChunk) {
        chunks.add(createChunk(chunkIndex++, tagOperations, Collections.singletonList(tag)));
      } else {
        // Split large tag groups into multiple chunks
        List<List<OpenApiSpecParser.ParsedOperation>> subChunks =
            splitOperations(tagOperations);
        for (List<OpenApiSpecParser.ParsedOperation> subChunk : subChunks) {
          chunks.add(createChunk(chunkIndex++, subChunk, Collections.singletonList(tag)));
        }
      }
    }

    // Handle operations without tags
    List<OpenApiSpecParser.ParsedOperation> untaggedOperations =
        allOperations.stream()
            .filter(op -> op.getTags().isEmpty())
            .collect(Collectors.toList());

    if (!untaggedOperations.isEmpty()) {
      List<List<OpenApiSpecParser.ParsedOperation>> untaggedChunks =
          splitOperations(untaggedOperations);
      for (List<OpenApiSpecParser.ParsedOperation> untaggedChunk : untaggedChunks) {
        chunks.add(createChunk(chunkIndex++, untaggedChunk, Collections.emptyList()));
      }
    }

    log.info("Created {} chunks from {} operations", chunks.size(), allOperations.size());
    return chunks;
  }

  /**
   * Group operations by their primary tag.
   */
  private Map<String, List<OpenApiSpecParser.ParsedOperation>> groupOperationsByTag(
      List<OpenApiSpecParser.ParsedOperation> operations) {
    Map<String, List<OpenApiSpecParser.ParsedOperation>> grouped = new HashMap<>();

    for (OpenApiSpecParser.ParsedOperation operation : operations) {
      if (operation.getTags().isEmpty()) {
        // Will be handled separately
        continue;
      }

      // Use first tag as primary tag
      String primaryTag = operation.getTags().get(0);
      grouped.computeIfAbsent(primaryTag, k -> new ArrayList<>()).add(operation);
    }

    return grouped;
  }

  /**
   * Split a list of operations into smaller chunks.
   */
  private List<List<OpenApiSpecParser.ParsedOperation>> splitOperations(
      List<OpenApiSpecParser.ParsedOperation> operations) {
    List<List<OpenApiSpecParser.ParsedOperation>> chunks = new ArrayList<>();

    // Group by path prefix first to keep related operations together
    Map<String, List<OpenApiSpecParser.ParsedOperation>> byPathPrefix =
        new HashMap<>();
    for (OpenApiSpecParser.ParsedOperation op : operations) {
      String pathPrefix = extractPathPrefix(op.getPath());
      byPathPrefix.computeIfAbsent(pathPrefix, k -> new ArrayList<>()).add(op);
    }

    // Create chunks from path groups
    List<OpenApiSpecParser.ParsedOperation> currentChunk = new ArrayList<>();
    int currentSize = 0;

    for (Map.Entry<String, List<OpenApiSpecParser.ParsedOperation>> pathEntry :
        byPathPrefix.entrySet()) {
      List<OpenApiSpecParser.ParsedOperation> pathOps = pathEntry.getValue();

      // If adding this path group would exceed limit, start new chunk
      if (!currentChunk.isEmpty()
          && currentChunk.size() + pathOps.size() > maxOperationsPerChunk) {
        chunks.add(new ArrayList<>(currentChunk));
        currentChunk.clear();
        currentSize = 0;
      }

      // Add path operations to current chunk
      currentChunk.addAll(pathOps);
      currentSize += pathOps.size();

      // If chunk is full, start new one
      if (currentSize >= maxOperationsPerChunk) {
        chunks.add(new ArrayList<>(currentChunk));
        currentChunk.clear();
        currentSize = 0;
      }
    }

    // Add remaining operations
    if (!currentChunk.isEmpty()) {
      chunks.add(currentChunk);
    }

    return chunks;
  }

  /**
   * Extract path prefix (first segment) from a path.
   */
  private String extractPathPrefix(String path) {
    if (path == null || path.isEmpty()) {
      return "/";
    }
    String[] segments = path.split("/");
    if (segments.length > 1) {
      return "/" + segments[1];
    }
    return path;
  }

  /**
   * Create a chunk from a list of operations.
   */
  private OperationChunk createChunk(
      int index,
      List<OpenApiSpecParser.ParsedOperation> operations,
      List<String> tags) {
    String chunkId = "chunk-" + index;

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("operationCount", operations.size());
    metadata.put("tags", tags);
    metadata.put("chunkIndex", index);

    // Estimate chunk size
    int estimatedSize = estimateChunkSize(operations);
    metadata.put("estimatedSizeBytes", estimatedSize);

    return new OperationChunk(chunkId, operations, tags, metadata);
  }

  /**
   * Estimate the size of a chunk in bytes.
   */
  private int estimateChunkSize(List<OpenApiSpecParser.ParsedOperation> operations) {
    int size = 0;
    ObjectMapper mapper = JacksonUtility.getJsonMapper();
    try {
      for (OpenApiSpecParser.ParsedOperation op : operations) {
        String json = mapper.writeValueAsString(op);
        size += json.length();
      }
    } catch (Exception e) {
      log.warn("Failed to estimate chunk size", e);
      // Fallback: rough estimate
      size = operations.size() * 2000; // ~2KB per operation
    }
    return size;
  }

  /**
   * Convert a chunk to a JSON-serializable format for LLM processing.
   */
  public Map<String, Object> chunkToMap(OperationChunk chunk) {
    Map<String, Object> map = new HashMap<>();
    map.put("chunkId", chunk.getChunkId());
    map.put("metadata", chunk.getMetadata());

    List<Map<String, Object>> operationsList = new ArrayList<>();
    for (OpenApiSpecParser.ParsedOperation op : chunk.getOperations()) {
      Map<String, Object> opMap = new HashMap<>();
      opMap.put("operationId", op.getOperationId());
      opMap.put("method", op.getMethod());
      opMap.put("path", op.getPath());
      opMap.put("summary", op.getSummary());
      opMap.put("description", op.getDescription());
      opMap.put("tags", op.getTags());
      opMap.put("category", op.getCategory());
      if (op.getRequestSchema() != null) {
        opMap.put("requestSchema", op.getRequestSchema());
      }
      if (op.getResponseSchema() != null) {
        opMap.put("responseSchema", op.getResponseSchema());
      }

      // Add examples
      List<Map<String, Object>> examplesList = new ArrayList<>();
      for (OpenApiSpecParser.ParsedExample example : op.getExamples()) {
        Map<String, Object> exMap = new HashMap<>();
        exMap.put("name", example.getName());
        if (example.getSummary() != null) {
          exMap.put("summary", example.getSummary());
        }
        if (example.getDescription() != null) {
          exMap.put("description", example.getDescription());
        }
        if (example.getRequestBody() != null) {
          exMap.put("requestBody", example.getRequestBody());
        }
        if (example.getResponseBody() != null) {
          exMap.put("responseBody", example.getResponseBody());
        }
        if (example.getResponseStatus() != null) {
          exMap.put("responseStatus", example.getResponseStatus());
        }
        examplesList.add(exMap);
      }
      if (!examplesList.isEmpty()) {
        opMap.put("examples", examplesList);
      }

      operationsList.add(opMap);
    }
    map.put("operations", operationsList);

    return map;
  }
}

