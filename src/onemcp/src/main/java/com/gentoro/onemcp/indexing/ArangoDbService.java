package com.gentoro.onemcp.indexing;

import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.model.CollectionCreateOptions;
import com.gentoro.onemcp.OneMcp;
import com.gentoro.onemcp.exception.ConfigException;
import com.gentoro.onemcp.exception.IoException;
import java.util.Map;

/**
 * ArangoDB service for indexing knowledge base data in DAG (Directed Acyclic Graph) format.
 *
 * <p>This service provides features for indexing data as vertices (nodes) and edges
 * (relationships) in ArangoDB. It initializes the necessary collections and provides basic indexing
 * operations.
 */
public class ArangoDbService {
  private static final org.slf4j.Logger log =
      com.gentoro.onemcp.logging.LoggingService.getLogger(ArangoDbService.class);

  private static final String DEFAULT_DATABASE = "onemcp_kb";
  private static final String VERTICES_COLLECTION = "vertices";
  private static final String EDGES_COLLECTION = "edges";

  private final OneMcp oneMcp;
  private ArangoDB arangoDB;
  private ArangoDatabase database;

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
    String host = oneMcp.configuration().getString("arangodb.host", "localhost");
    Integer port = oneMcp.configuration().getInteger("arangodb.port", 8529);
    String user = oneMcp.configuration().getString("arangodb.user", "root");
    String password = oneMcp.configuration().getString("arangodb.password", "");
    String databaseName = oneMcp.configuration().getString("arangodb.database", DEFAULT_DATABASE);

    log.info("Initializing ArangoDB connection: {}:{}", host, port);

    try {
      arangoDB = new ArangoDB.Builder().host(host, port).user(user).password(password).build();

      // Create database if it doesn't exist
      if (!arangoDB.getDatabases().contains(databaseName)) {
        log.info("Creating ArangoDB database: {}", databaseName);
        arangoDB.createDatabase(databaseName);
      }

      database = arangoDB.db(databaseName);

      // Create vertices collection if it doesn't exist
      if (!database.collection(VERTICES_COLLECTION).exists()) {
        log.info("Creating vertices collection: {}", VERTICES_COLLECTION);
        database.createCollection(VERTICES_COLLECTION);
      }

      // Create edges collection if it doesn't exist
      if (!database.collection(EDGES_COLLECTION).exists()) {
        log.info("Creating edges collection: {}", EDGES_COLLECTION);
        CollectionCreateOptions options = new CollectionCreateOptions().type(com.arangodb.entity.CollectionType.EDGES);
        database.createCollection(EDGES_COLLECTION, options);
      }

      log.info("ArangoDB service initialized successfully");
    } catch (Exception e) {
      throw new IoException("Failed to initialize ArangoDB service", e);
    }
  }

  /**
   * Index a vertex (node) in the knowledge base DAG.
   *
   * @param key unique identifier for the vertex
   * @param data data to index in the vertex
   * @throws IoException if indexing operation fails
   */
  public void storeVertex(String key, Map<String, Object> data) {
    if (database == null) {
      throw new IllegalStateException("ArangoDB service not initialized. Call initialize() first.");
    }

    try {
      log.trace("Indexing vertex: {}", key);
      Map<String, Object> vertexData = new java.util.HashMap<>(data);
      vertexData.put("_key", key);
      database.collection(VERTICES_COLLECTION).insertDocument(vertexData);
      log.debug("Successfully indexed vertex: {}", key);
    } catch (Exception e) {
      throw new IoException("Failed to index vertex: " + key, e);
    }
  }

  /**
   * Index an edge (relationship) between two vertices in the knowledge base DAG.
   *
   * @param fromKey source vertex key
   * @param toKey target vertex key
   * @param data additional data to index with the edge
   * @throws IoException if indexing operation fails
   */
  public void storeEdge(String fromKey, String toKey, Map<String, Object> data) {
    if (database == null) {
      throw new IllegalStateException("ArangoDB service not initialized. Call initialize() first.");
    }

    try {
      String edgeKey = fromKey + "_" + toKey;
      log.trace("Indexing edge: {} -> {}", fromKey, toKey);

      Map<String, Object> edgeData = new java.util.HashMap<>(data);
      edgeData.put("_key", edgeKey);
      edgeData.put("_from", VERTICES_COLLECTION + "/" + fromKey);
      edgeData.put("_to", VERTICES_COLLECTION + "/" + toKey);

      database.collection(EDGES_COLLECTION).insertDocument(edgeData);
      log.debug("Successfully indexed edge: {} -> {}", fromKey, toKey);
    } catch (Exception e) {
      throw new IoException("Failed to index edge: " + fromKey + " -> " + toKey, e);
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
}

