package com.gentoro.onemcp.indexing.graph;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a directed edge (relationship) between two nodes in the knowledge graph.
 *
 * <p>Edges connect nodes and define semantic relationships such as "HAS_OPERATION",
 * "HAS_DOCUMENTATION", "RELATES_TO", etc.
 */
public class GraphEdge {
  private final String fromKey;
  private final String toKey;
  private final EdgeType edgeType;
  private final Map<String, Object> properties;

  public GraphEdge(String fromKey, String toKey, EdgeType edgeType) {
    this(fromKey, toKey, edgeType, new HashMap<>());
  }

  public GraphEdge(
      String fromKey, String toKey, EdgeType edgeType, Map<String, Object> properties) {
    this.fromKey = fromKey;
    this.toKey = toKey;
    this.edgeType = edgeType;
    this.properties = properties;
  }

  public String getFromKey() {
    return fromKey;
  }

  public String getToKey() {
    return toKey;
  }

  public EdgeType getEdgeType() {
    return edgeType;
  }

  public Map<String, Object> getProperties() {
    return properties;
  }

  /**
   * Convert this edge to a map suitable for storing in ArangoDB.
   *
   * @return map of edge properties
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>(properties);
    map.put("edgeType", edgeType.name());
    return map;
  }

  /**
   * Types of relationships between nodes in the knowledge graph.
   *
   * <p>These edge types define the semantic relationships that connect different types of nodes.
   */
  public enum EdgeType {
    /** Entity has an operation */
    HAS_OPERATION,

    /** Operation has an example */
    HAS_EXAMPLE,

    /** Operation or entity has documentation chunk */
    HAS_DOCUMENTATION,

    /** Documentation chunk follows another chunk (sequential) */
    FOLLOWS_CHUNK,

    /** Documentation chunk is part of a parent document/section */
    PART_OF,

    /** Operation is related to another operation (similar functionality) */
    RELATES_TO,

    /** Entity relates to another entity */
    RELATES_TO_ENTITY,

    /** Example demonstrates an operation */
    DEMONSTRATES,

    /** Documentation chunk describes an entity or operation */
    DESCRIBES,

    /** Operation depends on or references another operation */
    DEPENDS_ON,

    /**
     * User feedback edge (future implementation). Links user feedback to operations, examples, or
     * doc chunks.
     */
    HAS_FEEDBACK
  }
}

