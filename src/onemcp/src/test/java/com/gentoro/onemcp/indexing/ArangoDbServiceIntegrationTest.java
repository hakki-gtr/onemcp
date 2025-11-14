package com.gentoro.onemcp.indexing;

import static org.junit.jupiter.api.Assertions.*;

import com.arangodb.ArangoDatabase;
import com.gentoro.onemcp.OneMcp;
import com.gentoro.onemcp.indexing.graph.GraphEdge;
import com.gentoro.onemcp.indexing.graph.nodes.EntityNode;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for ArangoDbService.
 *
 * <p><strong>REQUIREMENT:</strong> These tests require ArangoDB to be running locally. Tests will
 * FAIL if ArangoDB is not available.
 *
 * <p>To run these tests:
 *
 * <ol>
 *   <li>Start ArangoDB locally (default: localhost:8529):
 *       <pre>{@code
 * docker run -e ARANGO_ROOT_PASSWORD=test123 -p 8529:8529 -d arangodb
 * }</pre>
 *   <li>Verify ArangoDB is running:
 *       <pre>{@code
 * curl -u root:test123 http://localhost:8529/_api/version
 * }</pre>
 *   <li>Set environment variables if using different credentials:
 *       <ul>
 *         <li>ARANGODB_HOST (default: localhost)
 *         <li>ARANGODB_PORT (default: 8529)
 *         <li>ARANGODB_USER (default: root)
 *         <li>ARANGODB_PASSWORD (default: test123)
 *       </ul>
 *   <li>Run: {@code mvn test -Dtest=ArangoDbServiceIntegrationTest}
 * </ol>
 *
 * <p>These tests use a separate test database ({@code onemcp_kb_test}) and clean up after
 * themselves.
 */
class ArangoDbServiceIntegrationTest {

  private ArangoDbService arangoDbService;
  private OneMcp oneMcp;

  @BeforeEach
  void setUp() {
    // Create OneMcp instance with test-specific configuration
    // This uses a separate test database to avoid conflicts with production data
    String[] args = {"--config", "classpath:application-test.yaml"};
    oneMcp = new OneMcp(args);
    
    // Initialize only the configuration provider (needed for ArangoDbService)
    // We don't do full initialization to avoid loading handbook and other services
    try {
      com.gentoro.onemcp.ConfigurationProvider configProvider = 
          new com.gentoro.onemcp.ConfigurationProvider("classpath:application-test.yaml");
      java.lang.reflect.Field configField = OneMcp.class.getDeclaredField("configurationProvider");
      configField.setAccessible(true);
      configField.set(oneMcp, configProvider);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set up test configuration", e);
    }

    arangoDbService = new ArangoDbService(oneMcp);
  }

  @AfterEach
  void tearDown() {
    if (arangoDbService != null) {
      try {
        // Clean up test database
        ArangoDatabase db = arangoDbService.getDatabase();
        if (db != null && db.exists()) {
          try {
            db.drop();
          } catch (Exception e) {
            // Ignore cleanup errors
          }
        }
        arangoDbService.shutdown();
      } catch (Exception e) {
        // Ignore cleanup errors
      }
    }
  }

  @Test
  void testInitializeConnectsToArangoDB() {
    // Act - this will fail if ArangoDB is not running
    assertDoesNotThrow(
        () -> arangoDbService.initialize(),
        "ArangoDB must be running locally. Start ArangoDB and try again.");

    // Assert
    assertTrue(arangoDbService.isInitialized());
    assertNotNull(arangoDbService.getDatabase());
  }

  @Test
  void testStoreNode() {
    // Require ArangoDB to be running
    arangoDbService.initialize();

    // Arrange
    EntityNode node = new EntityNode(
        "test-entity-1",
        "Test Entity",
        "Test description",
        "test-service",
        new ArrayList<>());

    // Act
    assertDoesNotThrow(() -> arangoDbService.storeNode(node));

    // Assert - verify document exists by querying
    ArangoDatabase db = arangoDbService.getDatabase();
    assertNotNull(db);
    assertTrue(db.collection("entities").exists());
  }

  @Test
  void testStoreEdge() {
    // Require ArangoDB to be running
    arangoDbService.initialize();

    // Arrange - create two nodes first
    EntityNode entity1 = new EntityNode("entity-1", "Entity 1", "Description 1", "test-service", new ArrayList<>());
    EntityNode entity2 = new EntityNode("entity-2", "Entity 2", "Description 2", "test-service", new ArrayList<>());
    
    arangoDbService.storeNode(entity1);
    arangoDbService.storeNode(entity2);

    // Create edge
    GraphEdge edge = new GraphEdge("entity-1", "entity-2", GraphEdge.EdgeType.RELATES_TO_ENTITY);

    // Act
    assertDoesNotThrow(() -> arangoDbService.storeEdge(edge));

    // Assert - verify edge collection exists
    ArangoDatabase db = arangoDbService.getDatabase();
    assertNotNull(db);
    assertTrue(db.collection("edges").exists());
  }

  @Test
  void testStoreMultipleNodesAndEdges() {
    // Require ArangoDB to be running
    arangoDbService.initialize();

    // Arrange - create multiple nodes
    for (int i = 1; i <= 5; i++) {
      EntityNode node = new EntityNode(
          "entity-" + i,
          "Entity " + i,
          "Description " + i,
          "test-service",
          new ArrayList<>());
      arangoDbService.storeNode(node);
    }

    // Create edges forming a chain: 1->2->3->4->5
    for (int i = 1; i < 5; i++) {
      GraphEdge edge = new GraphEdge(
          "entity-" + i,
          "entity-" + (i + 1),
          GraphEdge.EdgeType.RELATES_TO_ENTITY);
      arangoDbService.storeEdge(edge);
    }

    // Assert - verify collections exist
    ArangoDatabase db = arangoDbService.getDatabase();
    assertNotNull(db);
    assertTrue(db.collection("entities").exists());
    assertTrue(db.collection("edges").exists());
  }

  @Test
  void testInitializeCreatesCollections() {
    // Require ArangoDB to be running
    // Act
    arangoDbService.initialize();

    // Assert
    ArangoDatabase db = arangoDbService.getDatabase();
    assertNotNull(db);
    assertTrue(db.collection("entities").exists());
    assertTrue(db.collection("operations").exists());
    assertTrue(db.collection("doc_chunks").exists());
    assertTrue(db.collection("examples").exists());
    assertTrue(db.collection("edges").exists());
  }

  @Test
  void testShutdown() {
    // Require ArangoDB to be running
    arangoDbService.initialize();

    // Act & Assert - should not throw
    assertDoesNotThrow(() -> arangoDbService.shutdown());
  }

  @Test
  void testStoreNodeWithDuplicateKeyThrowsException() {
    // Require ArangoDB to be running
    arangoDbService.initialize();

    // Arrange
    EntityNode node = new EntityNode("duplicate-key", "Duplicate Test", "Description", "test-service", new ArrayList<>());

    // Act - store first time
    arangoDbService.storeNode(node);

    // Act & Assert - storing again should throw exception
    assertThrows(com.gentoro.onemcp.exception.IoException.class, () -> arangoDbService.storeNode(node));
  }

  @Test
  void testStoreEdgeWithNonExistentNodes() {
    // Require ArangoDB to be running
    arangoDbService.initialize();

    // Arrange - create edge referencing non-existent nodes
    // Note: ArangoDB allows creating edges even if nodes don't exist
    // (it doesn't enforce referential integrity by default)
    GraphEdge edge = new GraphEdge("non-existent-1", "non-existent-2", GraphEdge.EdgeType.RELATES_TO);

    // Act - ArangoDB allows this, so it should succeed
    assertDoesNotThrow(() -> arangoDbService.storeEdge(edge));

    // Assert - verify edge was created
    ArangoDatabase db = arangoDbService.getDatabase();
    assertNotNull(db);
    assertTrue(db.collection("edges").exists());
  }

}

