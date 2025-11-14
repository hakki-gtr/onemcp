package com.gentoro.onemcp.indexing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gentoro.onemcp.indexing.graph.GraphEdge;
import com.gentoro.onemcp.indexing.graph.nodes.EntityNode;
import java.util.ArrayList;
import org.apache.commons.configuration2.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArangoDbServiceTest {

  @Mock private com.gentoro.onemcp.OneMcp oneMcp;
  @Mock private Configuration configuration;

  private ArangoDbService arangoDbService;

  @BeforeEach
  void setUp() {
    // Use lenient mocking to avoid issues with OneMcp class
    lenient().when(oneMcp.configuration()).thenReturn(configuration);
    arangoDbService = new ArangoDbService(oneMcp);
  }

  @Test
  void testConstructor() {
    ArangoDbService service = new ArangoDbService(oneMcp);
    assertNotNull(service);
  }

  @Test
  void testInitializeReadsConfiguration() {
    // Arrange - verify configuration is read with correct defaults
    when(configuration.getString("arangodb.host", "localhost")).thenReturn("localhost");
    when(configuration.getInteger("arangodb.port", 8529)).thenReturn(8529);
    when(configuration.getString("arangodb.user", "root")).thenReturn("root");
    when(configuration.getString("arangodb.password", "")).thenReturn("");
    when(configuration.getString("arangodb.database", "onemcp_kb")).thenReturn("test-db");
    when(configuration.getBoolean("arangodb.enabled", false)).thenReturn(false);

    // Act - initialization should skip when disabled
    arangoDbService.initialize();

    // Assert - service should not be initialized
    assertFalse(arangoDbService.isInitialized());
  }

  @Test
  void testStoreNodeSkipsWhenNotInitialized() {
    // Arrange
    EntityNode node = new EntityNode("test-key", "Test Entity", "Description", "test-service", new ArrayList<>());

    // Act & Assert - should not throw, just log warning
    assertDoesNotThrow(() -> arangoDbService.storeNode(node));
  }

  @Test
  void testStoreEdgeSkipsWhenNotInitialized() {
    // Arrange
    GraphEdge edge = new GraphEdge("from-key", "to-key", GraphEdge.EdgeType.HAS_OPERATION);

    // Act & Assert - should not throw, just log warning
    assertDoesNotThrow(() -> arangoDbService.storeEdge(edge));
  }

  @Test
  void testShutdownWhenNotInitialized() {
    // Act & Assert - should not throw
    assertDoesNotThrow(() -> arangoDbService.shutdown());
  }

  @Test
  void testGetDatabaseReturnsNullWhenNotInitialized() {
    // Act
    var result = arangoDbService.getDatabase();

    // Assert
    assertNull(result);
  }
}

