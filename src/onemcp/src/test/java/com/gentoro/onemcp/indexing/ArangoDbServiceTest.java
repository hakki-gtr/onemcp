package com.gentoro.onemcp.indexing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gentoro.onemcp.exception.IoException;
import java.util.HashMap;
import java.util.Map;
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

    // Act & Assert - initialization will fail without real ArangoDB, but we verify
    // that configuration is being read correctly by checking it throws IoException
    // (which means it tried to connect)
    try {
      arangoDbService.initialize();
      // If we get here, ArangoDB might be available - that's okay for unit test
    } catch (Exception e) {
      // Expected - ArangoDB not available or connection failed
      // This confirms configuration reading is working
      assertTrue(e instanceof IoException || e.getCause() != null);
    }

    // Verify configuration was accessed
    verify(configuration, atLeastOnce()).getString(eq("arangodb.host"), anyString());
    verify(configuration, atLeastOnce()).getInteger(eq("arangodb.port"), anyInt());
  }

  @Test
  void testStoreVertexThrowsWhenNotInitialized() {
    // Arrange
    Map<String, Object> data = new HashMap<>();
    data.put("content", "test");

    // Act & Assert
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> arangoDbService.storeVertex("test-key", data));
    assertTrue(exception.getMessage().contains("not initialized"));
  }

  @Test
  void testStoreEdgeThrowsWhenNotInitialized() {
    // Arrange
    Map<String, Object> data = new HashMap<>();
    data.put("type", "references");

    // Act & Assert
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> arangoDbService.storeEdge("from-key", "to-key", data));
    assertTrue(exception.getMessage().contains("not initialized"));
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

