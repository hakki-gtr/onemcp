package com.gentoro.onemcp.indexing;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for chunking configuration logic in GraphIndexingService.
 *
 * <p>These tests verify the configuration hierarchy and fallback behavior for chunking settings
 * using reflection to test the private isChunkingEnabled method. The tests use a real Configuration
 * object to ensure reliable behavior.
 *
 * <p>Note: These tests require proper OneMcp setup. In a real environment, consider using
 * integration tests or a test harness that provides a fully configured OneMcp instance.
 */
@DisplayName("GraphIndexingService Chunking Configuration Tests")
class GraphIndexingServiceChunkingConfigTest {

  private Configuration configuration;
  private GraphIndexingService service;

  @BeforeEach
  void setUp() throws Exception {
    // Use real Configuration implementation for reliable testing
    configuration = new BaseConfiguration();
    configuration.setProperty("graph.indexing.driver", "arangodb");

    // Create a minimal test OneMcp instance
    // Note: This is a workaround for testing - in production, OneMcp is properly initialized
    com.gentoro.onemcp.OneMcp testOneMcp = createTestOneMcp(configuration);
    service = new GraphIndexingService(testOneMcp);
  }

  /**
   * Creates a minimal OneMcp instance for testing purposes. This is a test helper that provides
   * only the necessary methods for configuration testing.
   */
  private com.gentoro.onemcp.OneMcp createTestOneMcp(Configuration config) {
    // Create OneMcp with empty args (won't be fully initialized, but sufficient for config tests)
    return new com.gentoro.onemcp.OneMcp(new String[0]) {
      @Override
      public Configuration configuration() {
        return config;
      }

      @Override
      public com.gentoro.onemcp.context.KnowledgeBase knowledgeBase() {
        // Return a minimal KnowledgeBase that provides the required methods
        return new com.gentoro.onemcp.context.KnowledgeBase(this) {
          @Override
          public String getHandbookName() {
            return "test-handbook";
          }
        };
      }
    };
  }

  @Test
  @DisplayName("Global chunking enabled returns true when configured")
  void testGlobalChunkingEnabled() throws Exception {
    // Arrange
    configuration.setProperty("graph.indexing.chunking.enabled", true);

    // Act
    boolean result = invokeIsChunkingEnabled("openapi");

    // Assert
    assertTrue(result, "Should return true when global chunking is enabled");
  }

  @Test
  @DisplayName("Global chunking disabled returns false")
  void testGlobalChunkingDisabled() throws Exception {
    // Arrange
    configuration.setProperty("graph.indexing.chunking.enabled", false);

    // Act
    boolean result = invokeIsChunkingEnabled("openapi");

    // Assert
    assertFalse(result, "Should return false when global chunking is disabled");
  }

  @Test
  @DisplayName("Document-type-specific config overrides global config when set")
  void testDocumentTypeSpecificOverridesGlobal() throws Exception {
    // Arrange - type-specific enabled, global disabled
    configuration.setProperty("graph.indexing.chunking.enabled", false);
    configuration.setProperty("graph.indexing.chunking.openapi.enabled", true);

    // Act
    boolean result = invokeIsChunkingEnabled("openapi");

    // Assert
    assertTrue(result, "Should use type-specific config when set, even if global is different");
  }

  @Test
  @DisplayName("Document-type-specific config disabled overrides global enabled")
  void testDocumentTypeSpecificDisabledOverridesGlobalEnabled() throws Exception {
    // Arrange - type-specific disabled, global enabled
    configuration.setProperty("graph.indexing.chunking.enabled", true);
    configuration.setProperty("graph.indexing.chunking.openapi.enabled", false);

    // Act
    boolean result = invokeIsChunkingEnabled("openapi");

    // Assert
    assertFalse(result, "Should use type-specific disabled config even if global is enabled");
  }

  @Test
  @DisplayName("Empty type-specific config falls back to global")
  void testEmptyTypeSpecificFallsBackToGlobal() throws Exception {
    // Arrange - type-specific key exists but is empty
    configuration.setProperty("graph.indexing.chunking.enabled", true);
    configuration.setProperty("graph.indexing.chunking.openapi.enabled", "");

    // Act
    boolean result = invokeIsChunkingEnabled("openapi");

    // Assert
    assertTrue(result, "Should fall back to global config when type-specific is empty");
  }

  @Test
  @DisplayName("Whitespace-only type-specific config falls back to global")
  void testWhitespaceTypeSpecificFallsBackToGlobal() throws Exception {
    // Arrange - type-specific key exists but is whitespace only
    configuration.setProperty("graph.indexing.chunking.enabled", false);
    configuration.setProperty("graph.indexing.chunking.openapi.enabled", "   ");

    // Act
    boolean result = invokeIsChunkingEnabled("openapi");

    // Assert
    assertFalse(result, "Should fall back to global config when type-specific is whitespace");
  }

  @Test
  @DisplayName("Default is false when no config is set")
  void testDefaultIsFalse() throws Exception {
    // Arrange - no config keys set (using defaults)

    // Act
    boolean result = invokeIsChunkingEnabled("openapi");

    // Assert
    assertFalse(result, "Should default to false when no config is set");
  }

  @Test
  @DisplayName("Different document types can have different configs")
  void testDifferentDocumentTypes() throws Exception {
    // Arrange - openapi enabled, markdown disabled
    configuration.setProperty("graph.indexing.chunking.openapi.enabled", true);
    configuration.setProperty("graph.indexing.chunking.markdown.enabled", false);

    // Act
    boolean openapiResult = invokeIsChunkingEnabled("openapi");
    boolean markdownResult = invokeIsChunkingEnabled("markdown");

    // Assert
    assertTrue(openapiResult, "OpenAPI chunking should be enabled");
    assertFalse(markdownResult, "Markdown chunking should be disabled");
  }

  /**
   * Helper method to invoke the private isChunkingEnabled method using reflection.
   *
   * @param documentType the document type to check
   * @return the result of isChunkingEnabled
   */
  private boolean invokeIsChunkingEnabled(String documentType) throws Exception {
    Method method =
        GraphIndexingService.class.getDeclaredMethod("isChunkingEnabled", String.class);
    method.setAccessible(true);
    return (boolean) method.invoke(service, documentType);
  }
}
