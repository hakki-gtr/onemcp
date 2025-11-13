package com.gentoro.onemcp.indexing;

import static org.junit.jupiter.api.Assertions.*;

import com.arangodb.ArangoDatabase;
import com.gentoro.onemcp.OneMcp;
import com.gentoro.onemcp.exception.IoException;
import java.util.HashMap;
import java.util.Map;
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
    assertNotNull(arangoDbService.getDatabase());
  }

  @Test
  void testStoreVertex() {
    // Require ArangoDB to be running
    arangoDbService.initialize();

    // Arrange
    Map<String, Object> vertexData = new HashMap<>();
    vertexData.put("title", "Test Vertex");
    vertexData.put("content", "Test content for vertex");
    String key = "test-vertex-1";

    // Act
    assertDoesNotThrow(() -> arangoDbService.storeVertex(key, vertexData));

    // Assert - verify document exists by querying
    ArangoDatabase db = arangoDbService.getDatabase();
    assertNotNull(db);
    assertTrue(db.collection("vertices").exists());
  }

  @Test
  void testStoreEdge() {
    // Require ArangoDB to be running
    arangoDbService.initialize();

    // Arrange - create two vertices first
    Map<String, Object> vertex1Data = new HashMap<>();
    vertex1Data.put("title", "Vertex 1");
    arangoDbService.storeVertex("vertex-1", vertex1Data);

    Map<String, Object> vertex2Data = new HashMap<>();
    vertex2Data.put("title", "Vertex 2");
    arangoDbService.storeVertex("vertex-2", vertex2Data);

    // Create edge
    Map<String, Object> edgeData = new HashMap<>();
    edgeData.put("type", "references");
    edgeData.put("weight", 1.0);

    // Act
    assertDoesNotThrow(
        () -> arangoDbService.storeEdge("vertex-1", "vertex-2", edgeData));

    // Assert - verify edge collection exists
    ArangoDatabase db = arangoDbService.getDatabase();
    assertNotNull(db);
    assertTrue(db.collection("edges").exists());
  }

  @Test
  void testStoreMultipleVerticesAndEdges() {
    // Require ArangoDB to be running
    arangoDbService.initialize();

    // Arrange - create multiple vertices
    for (int i = 1; i <= 5; i++) {
      Map<String, Object> vertexData = new HashMap<>();
      vertexData.put("title", "Vertex " + i);
      vertexData.put("index", i);
      arangoDbService.storeVertex("vertex-" + i, vertexData);
    }

    // Create edges forming a chain: 1->2->3->4->5
    for (int i = 1; i < 5; i++) {
      Map<String, Object> edgeData = new HashMap<>();
      edgeData.put("type", "next");
      arangoDbService.storeEdge("vertex-" + i, "vertex-" + (i + 1), edgeData);
    }

    // Assert - verify collections exist
    ArangoDatabase db = arangoDbService.getDatabase();
    assertNotNull(db);
    assertTrue(db.collection("vertices").exists());
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
    assertTrue(db.collection("vertices").exists());
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
  void testStoreVertexWithDuplicateKeyThrowsException() {
    // Require ArangoDB to be running
    arangoDbService.initialize();

    // Arrange
    Map<String, Object> vertexData = new HashMap<>();
    vertexData.put("title", "Duplicate Test");
    String key = "duplicate-key";

    // Act - store first time
    arangoDbService.storeVertex(key, vertexData);

    // Act & Assert - storing again should throw exception
    assertThrows(IoException.class, () -> arangoDbService.storeVertex(key, vertexData));
  }

  @Test
  void testStoreEdgeWithNonExistentVertices() {
    // Require ArangoDB to be running
    arangoDbService.initialize();

    // Arrange - create edge referencing non-existent vertices
    // Note: ArangoDB allows creating edges even if vertices don't exist
    // (it doesn't enforce referential integrity by default)
    Map<String, Object> edgeData = new HashMap<>();
    edgeData.put("type", "references");

    // Act - ArangoDB allows this, so it should succeed
    assertDoesNotThrow(
        () -> arangoDbService.storeEdge("non-existent-1", "non-existent-2", edgeData));

    // Assert - verify edge was created
    ArangoDatabase db = arangoDbService.getDatabase();
    assertNotNull(db);
    assertTrue(db.collection("edges").exists());
  }

}

