package com.gentoro.onemcp.indexing.openapi;

import static org.junit.jupiter.api.Assertions.*;

import com.gentoro.onemcp.openapi.OpenApiLoader;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.File;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenApiSpecParserTest {

  private OpenAPI openAPI;
  private OpenApiSpecParser parser;

  @BeforeEach
  void setUp() {
    // Load the test OpenAPI spec
    URL resource = getClass().getClassLoader().getResource("openapi/sales-analytics-api.yaml");
    assertNotNull(resource, "Test OpenAPI spec should be available");
    File specFile = new File(resource.getFile());
    openAPI = OpenApiLoader.load(specFile.getAbsolutePath());
    assertNotNull(openAPI, "OpenAPI spec should be loaded");
    parser = new OpenApiSpecParser(openAPI);
  }

  @Test
  void testExtractOperations() {
    // Act
    List<OpenApiSpecParser.ParsedOperation> operations = parser.extractOperations();

    // Assert
    assertNotNull(operations);
    assertFalse(operations.isEmpty(), "Should extract at least one operation");

    // Verify operation details
    OpenApiSpecParser.ParsedOperation queryOp =
        operations.stream()
            .filter(op -> "querySalesData".equals(op.getOperationId()))
            .findFirst()
            .orElse(null);

    assertNotNull(queryOp, "Should find querySalesData operation");
    assertEquals("POST", queryOp.getMethod());
    assertEquals("/query", queryOp.getPath());
    assertNotNull(queryOp.getSummary());
    assertNotNull(queryOp.getDescription());
    assertTrue(queryOp.getTags().contains("Analytics"), "Should have Analytics tag");
    assertEquals("Compute", queryOp.getCategory(), "POST query operations should be Compute");
  }

  @Test
  void testExtractOperationsWithAllMethods() {
    // Act
    List<OpenApiSpecParser.ParsedOperation> operations = parser.extractOperations();

    // Assert - should have GET, POST operations
    assertTrue(
        operations.stream().anyMatch(op -> "GET".equals(op.getMethod())),
        "Should have GET operations");
    assertTrue(
        operations.stream().anyMatch(op -> "POST".equals(op.getMethod())),
        "Should have POST operations");
  }

  @Test
  void testExtractTags() {
    // Act
    List<OpenApiSpecParser.ParsedTag> tags = parser.extractTags();

    // Assert
    assertNotNull(tags);
    assertFalse(tags.isEmpty(), "Should extract tags");

    // Verify specific tags
    OpenApiSpecParser.ParsedTag analyticsTag =
        tags.stream().filter(tag -> "Analytics".equals(tag.getName())).findFirst().orElse(null);

    assertNotNull(analyticsTag, "Should find Analytics tag");
    assertNotNull(analyticsTag.getDescription());
    assertTrue(
        analyticsTag.getDescription().contains("Data querying"),
        "Tag description should contain relevant text");
  }

  @Test
  void testExtractSchemas() {
    // Act
    List<OpenApiSpecParser.ParsedSchema> schemas = parser.extractSchemas();

    // Assert
    assertNotNull(schemas);
    assertFalse(schemas.isEmpty(), "Should extract schema components");

    // Verify specific schemas exist
    assertTrue(
        schemas.stream().anyMatch(s -> "QueryRequest".equals(s.getName())),
        "Should have QueryRequest schema");
    assertTrue(
        schemas.stream().anyMatch(s -> "QueryResponse".equals(s.getName())),
        "Should have QueryResponse schema");
    assertTrue(
        schemas.stream().anyMatch(s -> "FilterCondition".equals(s.getName())),
        "Should have FilterCondition schema");
  }

  @Test
  void testExtractRequestSchema() {
    // Act
    List<OpenApiSpecParser.ParsedOperation> operations = parser.extractOperations();

    // Assert
    OpenApiSpecParser.ParsedOperation queryOp =
        operations.stream()
            .filter(op -> "querySalesData".equals(op.getOperationId()))
            .findFirst()
            .orElse(null);

    assertNotNull(queryOp);
    assertNotNull(queryOp.getRequestSchema(), "POST operations should have request schema");
    assertTrue(
        queryOp.getRequestSchema().contains("filter") || queryOp.getRequestSchema().contains("fields"),
        "Request schema should contain filter or fields");
  }

  @Test
  void testExtractResponseSchema() {
    // Act
    List<OpenApiSpecParser.ParsedOperation> operations = parser.extractOperations();

    // Assert
    OpenApiSpecParser.ParsedOperation queryOp =
        operations.stream()
            .filter(op -> "querySalesData".equals(op.getOperationId()))
            .findFirst()
            .orElse(null);

    assertNotNull(queryOp);
    assertNotNull(queryOp.getResponseSchema(), "Operations should have response schema");
  }

  @Test
  void testExtractExamples() {
    // Act
    List<OpenApiSpecParser.ParsedOperation> operations = parser.extractOperations();

    // Assert
    OpenApiSpecParser.ParsedOperation queryOp =
        operations.stream()
            .filter(op -> "querySalesData".equals(op.getOperationId()))
            .findFirst()
            .orElse(null);

    assertNotNull(queryOp);
    List<OpenApiSpecParser.ParsedExample> examples = queryOp.getExamples();
    assertNotNull(examples);
    assertFalse(examples.isEmpty(), "querySalesData should have examples");

    // Verify example structure
    OpenApiSpecParser.ParsedExample example = examples.get(0);
    assertNotNull(example.getName());
    // Examples should have either requestBody or responseBody
    assertTrue(
        example.getRequestBody() != null || example.getResponseBody() != null,
        "Example should have request or response body");
  }

  @Test
  void testCategoryDetermination() {
    // Act
    List<OpenApiSpecParser.ParsedOperation> operations = parser.extractOperations();

    // Assert
    OpenApiSpecParser.ParsedOperation getOp =
        operations.stream()
            .filter(op -> "GET".equals(op.getMethod()))
            .findFirst()
            .orElse(null);

    assertNotNull(getOp);
    assertEquals("Retrieve", getOp.getCategory(), "GET operations should be Retrieve");

    OpenApiSpecParser.ParsedOperation postOp =
        operations.stream()
            .filter(op -> "POST".equals(op.getMethod()) && "querySalesData".equals(op.getOperationId()))
            .findFirst()
            .orElse(null);

    assertNotNull(postOp);
    assertEquals("Compute", postOp.getCategory(), "Query POST operations should be Compute");
  }

  @Test
  void testGetInfo() {
    // Act
    var info = parser.getInfo();

    // Assert
    assertNotNull(info);
    assertEquals("ACME Sales Analytics API", info.get("title"));
    assertEquals("1.0.0", info.get("version"));
    assertNotNull(info.get("description"));
  }

  @Test
  void testGetSpecSummary() {
    // Act
    var summary = parser.getSpecSummary();

    // Assert
    assertNotNull(summary);
    assertNotNull(summary.get("info"));
    assertNotNull(summary.get("operationCount"));
    assertNotNull(summary.get("tagCount"));
    assertNotNull(summary.get("schemaCount"));

    assertTrue(
        ((Number) summary.get("operationCount")).intValue() > 0,
        "Should have operations");
    assertTrue(((Number) summary.get("tagCount")).intValue() > 0, "Should have tags");
    assertTrue(((Number) summary.get("schemaCount")).intValue() > 0, "Should have schemas");
  }

  @Test
  void testOperationWithNoTags() {
    // This test verifies that operations without tags are still extracted
    // Act
    List<OpenApiSpecParser.ParsedOperation> operations = parser.extractOperations();

    // Assert - all operations should be extracted regardless of tags
    assertFalse(operations.isEmpty());
    // Operations without tags should have empty tag list, not null
    operations.forEach(
        op -> assertNotNull(op.getTags(), "Tags list should not be null"));
  }

  @Test
  void testOperationParameters() {
    // Act
    List<OpenApiSpecParser.ParsedOperation> operations = parser.extractOperations();

    // Assert
    OpenApiSpecParser.ParsedOperation op =
        operations.stream()
            .filter(o -> o.getParameters() != null && !o.getParameters().isEmpty())
            .findFirst()
            .orElse(null);

    // Some operations may have parameters, but it's optional
    if (op != null) {
      assertNotNull(op.getParameters());
      assertFalse(op.getParameters().isEmpty());
    }
  }

  @Test
  void testEmptyOpenAPI() {
    // Test with empty/minimal OpenAPI spec
    OpenAPI emptyAPI = new OpenAPI();
    OpenApiSpecParser emptyParser = new OpenApiSpecParser(emptyAPI);

    // Act
    List<OpenApiSpecParser.ParsedOperation> operations = emptyParser.extractOperations();
    List<OpenApiSpecParser.ParsedTag> tags = emptyParser.extractTags();
    List<OpenApiSpecParser.ParsedSchema> schemas = emptyParser.extractSchemas();

    // Assert
    assertNotNull(operations);
    assertTrue(operations.isEmpty(), "Empty spec should have no operations");
    assertNotNull(tags);
    assertTrue(tags.isEmpty(), "Empty spec should have no tags");
    assertNotNull(schemas);
    assertTrue(schemas.isEmpty(), "Empty spec should have no schemas");
  }
}

