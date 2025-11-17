package com.gentoro.onemcp.indexing;

import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.CollectionType;
import com.arangodb.entity.EdgeDefinition;
import com.arangodb.model.CollectionCreateOptions;
import com.gentoro.onemcp.OneMcp;
import com.gentoro.onemcp.exception.ConfigException;
import com.gentoro.onemcp.exception.IoException;
import com.gentoro.onemcp.indexing.graph.GraphEdge;
import com.gentoro.onemcp.indexing.graph.nodes.GraphNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ArangoDB service for indexing knowledge base data in graph format.
 *
 * <p>This service provides features for indexing handbook data as a graph with vertices (nodes) and
 * edges (relationships) in ArangoDB. It supports multiple node types (entities, operations,
 * examples) and various relationship types.
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
  private static final String EXAMPLES_COLLECTION = "examples";
  private static final String FIELDS_COLLECTION = "fields";
  
  // Edge collections for different relationship types
  private static final String EDGES_COLLECTION = "edges";

  private final OneMcp oneMcp;
  private final String handbookName;
  private ArangoDB arangoDB;
  private ArangoDatabase database;
  private boolean initialized = false;

  /**
   * Create a new ArangoDB service bound to the provided OneMcp context.
   *
   * @param oneMcp Main entry point for OneMCP application context
   * @param handbookName Name of the handbook being indexed
   */
  public ArangoDbService(OneMcp oneMcp, String handbookName) {
    this.oneMcp = oneMcp;
    this.handbookName = handbookName;
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
      createCollectionIfNotExists(EXAMPLES_COLLECTION, CollectionType.DOCUMENT);
      createCollectionIfNotExists(FIELDS_COLLECTION, CollectionType.DOCUMENT);

      // Create edge collection for relationships
      createCollectionIfNotExists(EDGES_COLLECTION, CollectionType.EDGES);

      // Note: Named graph will be created after data is indexed (or cleared)
      // This ensures the graph is created with the correct handbook name

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
   * Create a named graph for visualization in ArangoDB UI.
   *
   * <p>Creates a General Graph that includes all vertex collections (entities, operations,
   * examples) and the edges collection. This allows visualization in the Graphs
   * section of ArangoDB UI with proper edge label support.
   */
  private void createNamedGraph() {
    try {
      // Use the handbook name from constructor parameter first
      String actualHandbookName = handbookName;
      
      // If constructor parameter is null or unknown, try to get it from knowledge base
      if (actualHandbookName == null || actualHandbookName.equals("unknown_handbook")) {
        try {
          actualHandbookName = oneMcp.knowledgeBase().getHandbookName();
          log.debug("Retrieved handbook name from knowledge base: {}", actualHandbookName);
          
          // If still unknown, try to force initialization
          if (actualHandbookName == null || actualHandbookName.equals("unknown_handbook")) {
            log.debug("Handbook name is unknown, forcing handbook path resolution");
            Path handbookPath = oneMcp.knowledgeBase().handbookPath(); // Force initialization
            // Try to extract name from path if getHandbookName still returns unknown
            actualHandbookName = oneMcp.knowledgeBase().getHandbookName();
            if ((actualHandbookName == null || actualHandbookName.equals("unknown_handbook")) && handbookPath != null) {
              // Extract name from path as fallback
              actualHandbookName = handbookPath.getFileName().toString();
              log.debug("Extracted handbook name from path: {}", actualHandbookName);
            }
          }
        } catch (Exception e) {
          log.debug("Could not retrieve handbook name from knowledge base", e);
        }
      }
      
      // Final fallback if still unknown
      if (actualHandbookName == null || actualHandbookName.equals("unknown_handbook")) {
        log.debug("Could not determine handbook name, using default");
        actualHandbookName = "onemcp_handbook";
      }
      
      String graphName = sanitizeGraphName(actualHandbookName);
      
      // Delete old graph with wrong name if it exists
      String oldGraphName = sanitizeGraphName("unknown_handbook");
      if (!graphName.equals(oldGraphName) && database.graph(oldGraphName).exists()) {
        log.info("Deleting old graph with incorrect name: {}", oldGraphName);
        try {
          database.graph(oldGraphName).drop();
        } catch (Exception e) {
          log.debug("Could not delete old graph", e);
        }
      }
      
      // Check if graph already exists
      if (database.graph(graphName).exists()) {
        log.info("Graph '{}' already exists, skipping creation", graphName);
        return;
      }
      
      // Define all vertex collections as arrays
      String[] vertexCollections = new String[] {
          ENTITIES_COLLECTION,
          OPERATIONS_COLLECTION,
          EXAMPLES_COLLECTION,
          FIELDS_COLLECTION
      };
      
      // Create edge definition - edges can connect any vertex collection to any other
      EdgeDefinition edgeDefinition = new EdgeDefinition();
      edgeDefinition.collection(EDGES_COLLECTION);
      edgeDefinition.from(vertexCollections);
      edgeDefinition.to(vertexCollections);
      
      List<EdgeDefinition> edgeDefinitions = new ArrayList<>();
      edgeDefinitions.add(edgeDefinition);
      
      // Create the graph with edge definitions
      database.createGraph(graphName, edgeDefinitions);
      log.info("Created named graph '{}' with {} vertex collections and edge collection '{}'", 
          graphName, vertexCollections.length, EDGES_COLLECTION);
    } catch (Exception e) {
      log.warn("Failed to create named graph, continuing without formal graph definition", e);
      log.info("You can still visualize using queries in the Queries tab");
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
      // Sanitize the _key field for ArangoDB (replace | with _ and <> with _to_)
      String originalKey = (String) data.get("_key");
      String sanitizedKey = null;
      if (originalKey != null) {
        sanitizedKey = sanitizeKeyForArangoDB(originalKey);
        data.put("_key", sanitizedKey);
        log.trace("Sanitized key: {} -> {}", originalKey, sanitizedKey);
      }
      
      // Use upsert to handle duplicate nodes gracefully (insert or replace if exists)
      try {
        database.collection(collection).insertDocument(data);
        log.debug("Successfully stored node: {} (type: {})", node.getKey(), node.getNodeType());
        // Log category if present for debugging
        if (data.containsKey("category") && data.get("category") != null) {
          log.trace("Stored node with category: {} = {}", node.getKey(), data.get("category"));
        }
      } catch (com.arangodb.ArangoDBException e) {
        // If node already exists (unique constraint violated), replace it to ensure all fields (including category) are updated
        if (e.getErrorNum() == 1210 && sanitizedKey != null) { // 1210 = unique constraint violated
          log.debug("Node already exists, replacing: {} (type: {})", node.getKey(), node.getNodeType());
          database.collection(collection).replaceDocument(sanitizedKey, data);
          // Log category if present for debugging
          if (data.containsKey("category") && data.get("category") != null) {
            log.trace("Replaced node with category: {} = {}", node.getKey(), data.get("category"));
          }
        } else {
          throw e;
        }
      }
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
      // Sanitize keys for ArangoDB (replace | with _ and <> with _to_)
      String sanitizedFromKey = sanitizeKeyForArangoDB(edge.getFromKey());
      String sanitizedToKey = sanitizeKeyForArangoDB(edge.getToKey());
      String edgeKey = sanitizeKeyForArangoDB(sanitizedFromKey + "<>" + sanitizedToKey);
      
      log.trace("Storing edge: {} -> {} (type: {})", 
          edge.getFromKey(), edge.getToKey(), edge.getEdgeType());
      log.trace("Sanitized edge keys: {} -> {} (from: {}, to: {})", 
          edge.getFromKey() + "<>" + edge.getToKey(), edgeKey, sanitizedFromKey, sanitizedToKey);

      Map<String, Object> edgeData = new HashMap<>(edge.toMap());
      edgeData.put("_key", edgeKey);
      
      // Determine source and target collections (using sanitized keys)
      String fromCollection = determineCollectionFromKey(sanitizedFromKey);
      String toCollection = determineCollectionFromKey(sanitizedToKey);
      
      edgeData.put("_from", fromCollection + "/" + sanitizedFromKey);
      edgeData.put("_to", toCollection + "/" + sanitizedToKey);

      // Use upsert to handle duplicate edges gracefully (insert or update if exists)
      try {
        database.collection(EDGES_COLLECTION).insertDocument(edgeData);
        log.debug("Successfully stored edge: {} -> {}", edge.getFromKey(), edge.getToKey());
      } catch (com.arangodb.ArangoDBException e) {
        // If edge already exists (unique constraint violation), update it instead
        if (e.getErrorNum() == 1210) { // 1210 = unique constraint violated
          log.debug("Edge already exists, updating: {} -> {}", edge.getFromKey(), edge.getToKey());
          database.collection(EDGES_COLLECTION).updateDocument(edgeKey, edgeData);
        } else {
          throw e;
        }
      }
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
      case "example" -> EXAMPLES_COLLECTION;
      case "field" -> FIELDS_COLLECTION;
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
    // Handle both formats: entity|... and entity_... (after sanitization)
    if (key.startsWith("entity|") || key.startsWith("entity_")) return ENTITIES_COLLECTION;
    if (key.startsWith("op|") || key.startsWith("op_")) return OPERATIONS_COLLECTION;
    if (key.startsWith("example|") || key.startsWith("example_")) return EXAMPLES_COLLECTION;
    if (key.startsWith("field|") || key.startsWith("field_")) return FIELDS_COLLECTION;
    
    // Default to entities for backward compatibility
    return ENTITIES_COLLECTION;
  }

  /**
   * Sanitize a key to be valid for ArangoDB document keys.
   * ArangoDB allows: a-z, A-Z, 0-9, _, -, :, @, .
   * This method converts our internal format (with | and <>) to ArangoDB-compatible format.
   *
   * @param key the key to sanitize (may contain | and <>)
   * @return sanitized key compatible with ArangoDB
   */
  private String sanitizeKeyForArangoDB(String key) {
    if (key == null) return "";
    // Replace | with _ and <> with _to_
    String sanitized = key.replace("|", "_").replace("<>", "_to_");
    // Remove any other invalid characters
    return sanitized.replaceAll("[^a-zA-Z0-9_\\-:@\\.]", "_");
  }

  /**
   * Sanitize a key to be valid for ArangoDB (legacy method, kept for compatibility).
   *
   * @param key the key to sanitize
   * @return sanitized key
   */
  private String sanitizeKey(String key) {
    // Replace invalid characters with underscores, but keep pipe (|) and angle brackets (<>) for separators
    return key.replaceAll("[^a-zA-Z0-9_\\-:@\\.|<>]", "_");
  }

  /**
   * Sanitize a graph name to be valid for ArangoDB.
   *
   * @param name the graph name to sanitize
   * @return sanitized graph name
   */
  private String sanitizeGraphName(String name) {
    // ArangoDB graph names: alphanumeric, underscore, hyphen
    // Must start with letter
    String sanitized = name.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
    
    // Ensure it starts with a letter
    if (!sanitized.matches("^[a-zA-Z].*")) {
      sanitized = "handbook_" + sanitized;
    }
    
    return sanitized;
  }

  /**
   * Clear all graph data from the database. Use with caution!
   *
   * <p>This method clears all collections and drops any existing graphs to ensure
   * a clean state for re-indexing.
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
      // IMPORTANT: Drop all graphs FIRST before dropping collections
      // Collections that are part of a graph cannot be dropped directly
      try {
        Collection<com.arangodb.entity.GraphEntity> graphs = database.getGraphs();
        for (com.arangodb.entity.GraphEntity graphEntity : graphs) {
          String graphName = graphEntity.getName();
          log.info("Dropping graph: {}", graphName);
          database.graph(graphName).drop();
        }
      } catch (Exception e) {
        log.debug("Could not list or drop graphs (may not exist)", e);
      }
      
      // Now drop all collections (except system collections)
      Collection<com.arangodb.entity.CollectionEntity> collections = database.getCollections();
      for (com.arangodb.entity.CollectionEntity collectionEntity : collections) {
        String collectionName = collectionEntity.getName();
        
        // Skip system collections (they start with underscore)
        if (collectionName.startsWith("_")) {
          continue;
        }
        
        log.info("Dropping collection: {}", collectionName);
        try {
          database.collection(collectionName).drop();
        } catch (Exception e) {
          log.warn("Failed to drop collection: {}", collectionName, e);
        }
      }
      
      log.info("Successfully cleared all graph data and dropped existing graphs");
      
      // Recreate the collections we need
      log.info("Recreating collections after clearing");
      createCollectionIfNotExists(ENTITIES_COLLECTION, CollectionType.DOCUMENT);
      createCollectionIfNotExists(OPERATIONS_COLLECTION, CollectionType.DOCUMENT);
      createCollectionIfNotExists(EXAMPLES_COLLECTION, CollectionType.DOCUMENT);
      createCollectionIfNotExists(FIELDS_COLLECTION, CollectionType.DOCUMENT);
      createCollectionIfNotExists(EDGES_COLLECTION, CollectionType.EDGES);
      
      log.info("Collections recreated and ready for indexing");
    } catch (Exception e) {
      throw new IoException("Failed to clear graph data", e);
    }
  }
  
  /**
   * Ensure the named graph exists. Call this after indexing is complete.
   */
  public void ensureGraphExists() {
    if (!initialized || database == null) {
      return;
    }
    createNamedGraph();
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

