package com.gentoro.onemcp.indexing;

import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.CollectionType;
import com.arangodb.model.CollectionCreateOptions;
import com.gentoro.onemcp.OneMcp;
import com.gentoro.onemcp.exception.ConfigException;
import com.gentoro.onemcp.exception.IoException;
import com.gentoro.onemcp.indexing.graph.GraphEdge;
import com.gentoro.onemcp.indexing.graph.nodes.GraphNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ArangoDB service for indexing knowledge base data in graph format.
 *
 * <p>This service provides features for indexing handbook data as a graph with vertices (nodes) and
 * edges (relationships) in ArangoDB. It supports multiple node types (entities, operations, doc
 * chunks, examples) and various relationship types.
 *
 * <p>The graph structure enables semantic querying and retrieval based on relationships between
 * different knowledge base elements.
 */
public class ArangoDbService {
  private static final org.slf4j.Logger log =
      com.gentoro.onemcp.logging.LoggingService.getLogger(ArangoDbService.class);

  private static final String DEFAULT_DATABASE = "onemcp_kb";
  
  // Collection names for different node types
  private static final String ENTITIES_COLLECTION = "entities";
  private static final String OPERATIONS_COLLECTION = "operations";
  private static final String DOC_CHUNKS_COLLECTION = "doc_chunks";
  private static final String EXAMPLES_COLLECTION = "examples";
  
  // Edge collections for different relationship types
  private static final String EDGES_COLLECTION = "edges";

  private final OneMcp oneMcp;
  private ArangoDB arangoDB;
  private ArangoDatabase database;
  private boolean initialized = false;

  /**
   * Create a new ArangoDB service bound to the provided OneMcp context.
   *
   * @param oneMcp Main entry point for OneMCP application context
   */
  public ArangoDbService(OneMcp oneMcp) {
    this.oneMcp = oneMcp;
  }

  /**
   * Initialize the ArangoDB connection and create necessary collections.
   *
   * @throws ConfigException if configuration is missing or invalid
   * @throws IoException if connection or initialization fails
   */
  public void initialize() {
    if (initialized) {
      log.debug("ArangoDB service already initialized");
      return;
    }

    String host = oneMcp.configuration().getString("arangodb.host", "localhost");
    Integer port = oneMcp.configuration().getInteger("arangodb.port", 8529);
    String user = oneMcp.configuration().getString("arangodb.user", "root");
    String password = oneMcp.configuration().getString("arangodb.password", "");
    String databaseName = oneMcp.configuration().getString("arangodb.database", DEFAULT_DATABASE);
    Boolean enabled = oneMcp.configuration().getBoolean("arangodb.enabled", false);

    if (!enabled) {
      log.info("ArangoDB indexing is disabled in configuration");
      return;
    }

    log.info("Initializing ArangoDB connection: {}:{}", host, port);

    try {
      arangoDB = new ArangoDB.Builder().host(host, port).user(user).password(password).build();

      // Create database if it doesn't exist
      if (!arangoDB.getDatabases().contains(databaseName)) {
        log.info("Creating ArangoDB database: {}", databaseName);
        arangoDB.createDatabase(databaseName);
      }

      database = arangoDB.db(databaseName);

      // Create document collections for different node types
      createCollectionIfNotExists(ENTITIES_COLLECTION, CollectionType.DOCUMENT);
      createCollectionIfNotExists(OPERATIONS_COLLECTION, CollectionType.DOCUMENT);
      createCollectionIfNotExists(DOC_CHUNKS_COLLECTION, CollectionType.DOCUMENT);
      createCollectionIfNotExists(EXAMPLES_COLLECTION, CollectionType.DOCUMENT);

      // Create edge collection for relationships
      createCollectionIfNotExists(EDGES_COLLECTION, CollectionType.EDGES);

      // Create indexes for better query performance
      createIndexes();

      initialized = true;
      log.info("ArangoDB service initialized successfully");
    } catch (Exception e) {
      throw new IoException("Failed to initialize ArangoDB service", e);
    }
  }

  /**
   * Create a collection if it doesn't exist.
   *
   * @param name collection name
   * @param type collection type (DOCUMENT or EDGES)
   */
  private void createCollectionIfNotExists(String name, CollectionType type) {
    try {
      if (!database.collection(name).exists()) {
        log.info("Creating collection: {} (type: {})", name, type);
        CollectionCreateOptions options = new CollectionCreateOptions().type(type);
        database.createCollection(name, options);
      }
    } catch (Exception e) {
      throw new IoException("Failed to create collection: " + name, e);
    }
  }

  /**
   * Create indexes on collections for better query performance.
   */
  private void createIndexes() {
    try {
      // Index on nodeType for all document collections
      log.debug("Creating indexes for graph collections");
      
      // These indexes will be created on-demand by ArangoDB based on query patterns
      // For now, we rely on the _key index which is automatic
      
    } catch (Exception e) {
      log.warn("Failed to create some indexes, continuing anyway", e);
    }
  }

  /**
   * Store a graph node in the appropriate collection based on its type.
   *
   * @param node the graph node to store
   * @throws IoException if indexing operation fails
   */
  public void storeNode(GraphNode node) {
    if (!initialized || database == null) {
      log.warn("ArangoDB service not initialized, skipping node storage: {}", node.getKey());
      return;
    }

    try {
      String collection = getCollectionForNodeType(node.getNodeType());
      log.trace("Storing node: {} in collection: {}", node.getKey(), collection);
      
      Map<String, Object> data = node.toMap();
      database.collection(collection).insertDocument(data);
      
      log.debug("Successfully stored node: {} (type: {})", node.getKey(), node.getNodeType());
    } catch (Exception e) {
      throw new IoException("Failed to store node: " + node.getKey(), e);
    }
  }

  /**
   * Store multiple graph nodes in a batch operation.
   *
   * @param nodes list of nodes to store
   * @throws IoException if batch operation fails
   */
  public void storeNodes(List<GraphNode> nodes) {
    if (!initialized || database == null) {
      log.warn("ArangoDB service not initialized, skipping batch node storage");
      return;
    }

    log.debug("Storing {} nodes in batch", nodes.size());
    for (GraphNode node : nodes) {
      storeNode(node);
    }
  }

  /**
   * Store an edge (relationship) between two nodes in the graph.
   *
   * @param edge the edge to store
   * @throws IoException if indexing operation fails
   */
  public void storeEdge(GraphEdge edge) {
    if (!initialized || database == null) {
      log.warn("ArangoDB service not initialized, skipping edge storage");
      return;
    }

    try {
      String edgeKey = sanitizeKey(edge.getFromKey() + "_to_" + edge.getToKey());
      log.trace("Storing edge: {} -> {} (type: {})", 
          edge.getFromKey(), edge.getToKey(), edge.getEdgeType());

      Map<String, Object> edgeData = new HashMap<>(edge.toMap());
      edgeData.put("_key", edgeKey);
      
      // Determine source and target collections
      String fromCollection = determineCollectionFromKey(edge.getFromKey());
      String toCollection = determineCollectionFromKey(edge.getToKey());
      
      edgeData.put("_from", fromCollection + "/" + edge.getFromKey());
      edgeData.put("_to", toCollection + "/" + edge.getToKey());

      database.collection(EDGES_COLLECTION).insertDocument(edgeData);
      log.debug("Successfully stored edge: {} -> {}", edge.getFromKey(), edge.getToKey());
    } catch (Exception e) {
      throw new IoException(
          "Failed to store edge: " + edge.getFromKey() + " -> " + edge.getToKey(), e);
    }
  }

  /**
   * Store multiple edges in a batch operation.
   *
   * @param edges list of edges to store
   * @throws IoException if batch operation fails
   */
  public void storeEdges(List<GraphEdge> edges) {
    if (!initialized || database == null) {
      log.warn("ArangoDB service not initialized, skipping batch edge storage");
      return;
    }

    log.debug("Storing {} edges in batch", edges.size());
    for (GraphEdge edge : edges) {
      storeEdge(edge);
    }
  }

  /**
   * Get the collection name for a given node type.
   *
   * @param nodeType the node type
   * @return collection name
   */
  private String getCollectionForNodeType(String nodeType) {
    return switch (nodeType) {
      case "entity" -> ENTITIES_COLLECTION;
      case "operation" -> OPERATIONS_COLLECTION;
      case "doc_chunk" -> DOC_CHUNKS_COLLECTION;
      case "example" -> EXAMPLES_COLLECTION;
      default -> throw new IllegalArgumentException("Unknown node type: " + nodeType);
    };
  }

  /**
   * Determine the collection from a node key prefix.
   *
   * @param key the node key
   * @return collection name
   */
  private String determineCollectionFromKey(String key) {
    if (key.startsWith("entity_")) return ENTITIES_COLLECTION;
    if (key.startsWith("op_")) return OPERATIONS_COLLECTION;
    if (key.startsWith("chunk_")) return DOC_CHUNKS_COLLECTION;
    if (key.startsWith("example_")) return EXAMPLES_COLLECTION;
    
    // Default to entities for backward compatibility
    return ENTITIES_COLLECTION;
  }

  /**
   * Sanitize a key to be valid for ArangoDB.
   *
   * @param key the key to sanitize
   * @return sanitized key
   */
  private String sanitizeKey(String key) {
    // Replace invalid characters with underscores
    return key.replaceAll("[^a-zA-Z0-9_\\-:@\\.]", "_");
  }

  /**
   * Clear all graph data from the database. Use with caution!
   *
   * @throws IoException if clear operation fails
   */
  public void clearAllData() {
    if (!initialized || database == null) {
      log.warn("ArangoDB service not initialized, nothing to clear");
      return;
    }

    log.warn("Clearing all graph data from ArangoDB");
    try {
      database.collection(ENTITIES_COLLECTION).truncate();
      database.collection(OPERATIONS_COLLECTION).truncate();
      database.collection(DOC_CHUNKS_COLLECTION).truncate();
      database.collection(EXAMPLES_COLLECTION).truncate();
      database.collection(EDGES_COLLECTION).truncate();
      log.info("Successfully cleared all graph data");
    } catch (Exception e) {
      throw new IoException("Failed to clear graph data", e);
    }
  }

  /**
   * Close the ArangoDB connection and release resources.
   */
  public void shutdown() {
    if (arangoDB != null) {
      try {
        arangoDB.shutdown();
        log.info("ArangoDB connection closed");
      } catch (Exception e) {
        log.warn("Error closing ArangoDB connection", e);
      }
    }
  }

  /**
   * Get the ArangoDB database instance.
   *
   * @return the ArangoDatabase instance
   */
  public ArangoDatabase getDatabase() {
    return database;
  }

  /**
   * Check if the service is initialized and ready to use.
   *
   * @return true if initialized, false otherwise
   */
  public boolean isInitialized() {
    return initialized;
  }
}

