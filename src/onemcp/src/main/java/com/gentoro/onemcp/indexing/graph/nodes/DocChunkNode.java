package com.gentoro.onemcp.indexing.graph.nodes;

import java.util.HashMap;
import java.util.Map;

/**
 * Graph node representing a chunk of documentation.
 *
 * <p>Documentation chunks are extracted from markdown files and OpenAPI descriptions. Each chunk
 * contains a semantic unit of information with context about its source and position.
 */
public class DocChunkNode implements GraphNode {
  private final String key;
  private final String content;
  private final String sourceUri;
  private final String sourceType; // "markdown", "openapi_description", "openapi_summary"
  private final int chunkIndex;
  private final int startOffset;
  private final int endOffset;
  private final String title; // Section heading or inferred title
  private final String parentKey; // Key of parent node (operation, service, etc.)

  public DocChunkNode(
      String key,
      String content,
      String sourceUri,
      String sourceType,
      int chunkIndex,
      int startOffset,
      int endOffset,
      String title,
      String parentKey) {
    this.key = key;
    this.content = content;
    this.sourceUri = sourceUri;
    this.sourceType = sourceType;
    this.chunkIndex = chunkIndex;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.title = title;
    this.parentKey = parentKey;
  }

  @Override
  public String getKey() {
    return key;
  }

  @Override
  public String getNodeType() {
    return "doc_chunk";
  }

  public String getContent() {
    return content;
  }

  public String getSourceUri() {
    return sourceUri;
  }

  public String getSourceType() {
    return sourceType;
  }

  public int getChunkIndex() {
    return chunkIndex;
  }

  public int getStartOffset() {
    return startOffset;
  }

  public int getEndOffset() {
    return endOffset;
  }

  public String getTitle() {
    return title;
  }

  public String getParentKey() {
    return parentKey;
  }

  @Override
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("_key", key);
    map.put("nodeType", getNodeType());
    map.put("content", content);
    map.put("sourceUri", sourceUri);
    map.put("sourceType", sourceType);
    map.put("chunkIndex", chunkIndex);
    map.put("startOffset", startOffset);
    map.put("endOffset", endOffset);
    map.put("title", title);
    map.put("parentKey", parentKey);
    return map;
  }
}

