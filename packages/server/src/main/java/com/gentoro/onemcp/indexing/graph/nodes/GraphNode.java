package com.gentoro.onemcp.indexing.graph.nodes;

import java.util.Map;

/**
 * Base interface for all graph nodes in the knowledge base.
 *
 * <p>Graph nodes represent entities that are indexed in ArangoDB as vertices. Each node type has a
 * unique key and additional properties that describe the entity.
 */
public interface GraphNode {
  /**
   * Get the unique key for this node. This will be used as the ArangoDB document _key.
   *
   * @return unique identifier for the node
   */
  String getKey();

  /**
   * Get the node type identifier (e.g., "entity", "operation", "example").
   *
   * @return node type string
   */
  String getNodeType();

  /**
   * Convert this node to a map suitable for storing in ArangoDB.
   *
   * @return map of node properties
   */
  Map<String, Object> toMap();
}
