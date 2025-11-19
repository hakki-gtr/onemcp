package com.gentoro.onemcp.indexing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gentoro.onemcp.OneMcp;
import com.gentoro.onemcp.utility.JacksonUtility;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * Example usage of the GraphQueryService.
 *
 * <p>This class demonstrates how to query the knowledge graph for entity-specific
 * operations, examples, and documentation.
 */
public class GraphQueryExample {
  private static final org.slf4j.Logger log =
      com.gentoro.onemcp.logging.LoggingService.getLogger(GraphQueryExample.class);

  /**
   * Convert a value to a compact JSON string if it's a JSON object/array, otherwise return as string.
   *
   * @param value the value to stringify
   * @return compact JSON string or original string representation
   */
  private static String stringifyJsonValue(Object value) {
    if (value == null) {
      return "N/A";
    }
    
    // If it's already a string, try to parse and re-stringify as compact JSON
    if (value instanceof String) {
      String str = (String) value;
      if (str.trim().isEmpty()) {
        return "N/A";
      }
      // Try to parse as JSON and re-stringify compactly
      try {
        ObjectMapper mapper = JacksonUtility.getJsonMapper();
        Object parsed = mapper.readValue(str, Object.class);
        return mapper.writeValueAsString(parsed);
      } catch (Exception e) {
        // Not valid JSON, return as-is
        return str;
      }
    }
    
    // If it's a Map or Collection, convert to compact JSON
    if (value instanceof Map || value instanceof Collection) {
      try {
        ObjectMapper mapper = JacksonUtility.getJsonMapper();
        return mapper.writeValueAsString(value);
      } catch (Exception e) {
        return value.toString();
      }
    }
    
    // For other types, return string representation
    return value.toString();
  }

  /**
   * Example: Query for Sale entity with Retrieve and Compute operations.
   *
   * <p>This method queries the existing ArangoDB graph data (no reindexing).
   * It expects the graph to already be indexed in ArangoDB.
   *
   * @param oneMcp the OneMcp instance (should be initialized with --skip-kb-reindex)
   */
  public static void querySaleEntity(OneMcp oneMcp) {
    ArangoDbService arangoDbService = new ArangoDbService(oneMcp, oneMcp.knowledgeBase().getHandbookName());
    arangoDbService.initialize();

    if (!arangoDbService.isInitialized()) {
      log.error("ArangoDB is not initialized. Cannot execute query.");
      return;
    }

    GraphQueryService queryService = new GraphQueryService(arangoDbService);

    GraphQueryService.QueryRequest request = new GraphQueryService.QueryRequest();
    
    List<GraphQueryService.ContextItem> context = Arrays.asList(
        // new GraphQueryService.ContextItem("Sale", Arrays.asList("Retrieve", "Compute"), 100, "direct")
        //new GraphQueryService.ContextItem("Category", Arrays.asList("Retrieve"), 100, "direct")
        new GraphQueryService.ContextItem("Product", Arrays.asList("Retrieve"), 100, "indirect")
    );
    
    request.setContext(context);

    // Execute query
    log.info("Executing graph query for context: {} items", context.size());
    List<GraphQueryService.QueryResult> results = queryService.query(request);

    // Display results in flattened format
    System.out.println("\n========================================");
    System.out.println("QUERY RESULTS (FLATTENED)");
    System.out.println("========================================");
    log.info("Query completed. Found {} results", results.size());
    System.out.println("Found " + results.size() + " results\n");
    
    for (GraphQueryService.QueryResult result : results) {
      try {
        // Entity Information
        System.out.println("ENTITY: " + result.getEntity());
        if (result.getEntityInfo() != null) {
          System.out.println("  Description: " + (result.getEntityInfo().get("description") != null ? result.getEntityInfo().get("description") : "N/A"));
          System.out.println("  Domain: " + (result.getEntityInfo().get("domain") != null ? result.getEntityInfo().get("domain") : "N/A"));
        }
        System.out.println("  Confidence: " + result.getConfidence());
        System.out.println("  Referral: " + result.getReferral());
        System.out.println();
        
        // Fields (flattened)
        if (result.getFields() != null && !result.getFields().isEmpty()) {
          System.out.println("FIELDS (" + result.getFields().size() + "):");
          for (Map<String, Object> field : result.getFields()) {
            System.out.println("  Field: " + field.get("name"));
            System.out.println("    Type: " + field.get("fieldType"));
            System.out.println("    Description: " + (field.get("description") != null ? field.get("description") : "N/A"));
            System.out.println();
          }
        }
        
        // Operations (flattened with all details)
        System.out.println("OPERATIONS (" + result.getOperations().size() + "):");
        for (GraphQueryService.OperationResult op : result.getOperations()) {
          System.out.println("  Operation: " + op.getOperationId());
          System.out.println("    Method: " + op.getMethod());
          System.out.println("    Path: " + op.getPath());
          System.out.println("    Category: " + op.getCategory());
          System.out.println("    Summary: " + (op.getSummary() != null ? op.getSummary() : "N/A"));
          System.out.println("    Description: " + (op.getDescription() != null ? op.getDescription() : "N/A"));
          System.out.println("    Signature: " + (op.getSignature() != null ? op.getSignature() : "N/A"));
          System.out.println("    Request Schema: " + stringifyJsonValue(op.getRequestSchema()));
          System.out.println("    Response Schema: " + stringifyJsonValue(op.getResponseSchema()));
          if (op.getTags() != null && !op.getTags().isEmpty()) {
            System.out.println("    Tags: " + String.join(", ", op.getTags()));
          }
          
          // Examples (flattened)
          if (op.getExamples() != null && !op.getExamples().isEmpty()) {
            System.out.println("    Examples (" + op.getExamples().size() + "):");
            for (Map<String, Object> example : op.getExamples()) {
              System.out.println("      Example: " + example.get("name"));
              System.out.println("        Summary: " + (example.get("summary") != null ? example.get("summary") : "N/A"));
              System.out.println("        Description: " + (example.get("description") != null ? example.get("description") : "N/A"));
              System.out.println("        Request Body: " + stringifyJsonValue(example.get("requestBody")));
              System.out.println("        Response Body: " + stringifyJsonValue(example.get("responseBody")));
              System.out.println("        Response Status: " + (example.get("responseStatus") != null ? example.get("responseStatus") : "N/A"));
              System.out.println();
            }
          }
          
          // Documentation (flattened)
          if (op.getDocumentation() != null && !op.getDocumentation().isEmpty()) {
            System.out.println("    Documentation (" + op.getDocumentation().size() + "):");
            for (Map<String, Object> doc : op.getDocumentation()) {
              System.out.println("      Doc: " + doc.get("title"));
              System.out.println("        Type: " + doc.get("docType"));
              System.out.println("        Source File: " + (doc.get("sourceFile") != null ? doc.get("sourceFile") : "N/A"));
              System.out.println("        Content: " + (doc.get("content") != null ? 
                  (doc.get("content").toString().length() > 200 ? 
                   doc.get("content").toString().substring(0, 200) + "..." : 
                   doc.get("content")) : "N/A"));
              System.out.println();
            }
          }
          
          System.out.println();
        }
        
        // Entity-level Documentation
        if (result.getEntityInfo() != null && result.getEntityInfo().containsKey("documentation")) {
          System.out.println("ENTITY DOCUMENTATION:");
          // This would need to be added to the result structure
        }
        
        System.out.println("----------------------------------------\n");
        
        log.info("Entity '{}': {} operations, {} fields", 
            result.getEntity(), 
            result.getOperations().size(),
            result.getFields() != null ? result.getFields().size() : 0);
            
      } catch (Exception e) {
        System.err.println("ERROR: Failed to process result for " + result.getEntity() + ": " + e.getMessage());
        e.printStackTrace();
        log.error("Failed to serialize result", e);
      }
    }
    System.out.println("========================================\n");
    
    // Optionally output JSON representation
    System.out.println("JSON REPRESENTATION:");
    System.out.println("========================================");
    String jsonOutput = resultsToJson(results);
    System.out.println(jsonOutput);
    System.out.println("========================================\n");

    // Cleanup
    arangoDbService.shutdown();
  }

  /**
   * Convert query results to JSON representation.
   *
   * @param results the query results to convert
   * @return JSON string representation of the results
   */
  public static String resultsToJson(List<GraphQueryService.QueryResult> results) {
    try {
      ObjectMapper mapper = JacksonUtility.getJsonMapper();
      return mapper.writeValueAsString(results);
    } catch (Exception e) {
      log.error("Failed to convert results to JSON", e);
      return "{\"error\": \"Failed to serialize results: " + e.getMessage() + "\"}";
    }
  }

  /**
   * Convert query results to pretty-printed JSON representation.
   *
   * @param results the query results to convert
   * @return Pretty-printed JSON string representation of the results
   */
  public static String resultsToPrettyJson(List<GraphQueryService.QueryResult> results) {
    try {
      ObjectMapper mapper = JacksonUtility.getJsonMapper();
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(results);
    } catch (Exception e) {
      log.error("Failed to convert results to JSON", e);
      return "{\"error\": \"Failed to serialize results: " + e.getMessage() + "\"}";
    }
  }

  /**
   * Example: Query with JSON input (as from HTTP request).
   *
   * <p>This method queries existing ArangoDB graph data (no reindexing).
   *
   * @param oneMcp the OneMcp instance (should be initialized with --skip-kb-reindex)
   * @param jsonRequest JSON string of the request
   */
  public static void queryFromJson(OneMcp oneMcp, String jsonRequest) {
    ArangoDbService arangoDbService = new ArangoDbService(oneMcp, oneMcp.knowledgeBase().getHandbookName());
    arangoDbService.initialize();

    if (!arangoDbService.isInitialized()) {
      log.error("ArangoDB is not initialized. Cannot execute query.");
      return;
    }

    GraphQueryService queryService = new GraphQueryService(arangoDbService);
    ObjectMapper mapper = JacksonUtility.getJsonMapper();

    try {
      // Parse JSON request
      GraphQueryService.QueryRequest request = mapper.readValue(jsonRequest, GraphQueryService.QueryRequest.class);
      
      // Execute query
      List<GraphQueryService.QueryResult> results = queryService.query(request);
      
      // Serialize results back to JSON
      String jsonResponse = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(results);
      log.info("Query results:\n{}", jsonResponse);
      
    } catch (Exception e) {
      log.error("Failed to process JSON query", e);
    }

    arangoDbService.shutdown();
  }

  /**
   * Main method for standalone testing.
   *
   * <p>This example uses existing ArangoDB data for acme-handbook without reindexing.
   * It connects to the existing graph database and queries it.
   *
   * <p>Usage:
   * <pre>
   *   # Using default handbook (acme-handbook) - uses existing ArangoDB data
   *   java -cp ... GraphQueryExample
   *
   *   # Using specific handbook
   *   java -cp ... GraphQueryExample --handbook /path/to/handbook
   *
   *   # With ArangoDB configuration
   *   ARANGODB_ENABLED=true ARANGODB_HOST=localhost ARANGODB_PORT=8529 \
   *   ARANGODB_USER=root ARANGODB_PASSWORD=test123 \
   *   java -cp ... GraphQueryExample
   * </pre>
   *
   * <p>Note: This example skips knowledge base reindexing (--skip-kb-reindex)
   * to use existing ArangoDB data. Make sure ArangoDB is running and contains
   * indexed data for the handbook.
   *
   * @param args command line arguments (passed to OneMcp initialization)
   */
  public static void main(String[] args) {
    log.info("========================================");
    log.info("GraphQueryService Example");
    log.info("Using existing ArangoDB data (no reindexing)");
    log.info("========================================");
    
    OneMcp oneMcp = null;
    
    try {
      // Add --skip-kb-reindex to prevent reindexing and use existing ArangoDB data
      String[] enhancedArgs = new String[args.length + 1];
      System.arraycopy(args, 0, enhancedArgs, 0, args.length);
      enhancedArgs[args.length] = "--skip-kb-reindex";
      
      // Initialize OneMcp with skip reindex flag
      log.info("Initializing OneMcp (skipping reindex to use existing ArangoDB data)...");
      oneMcp = new OneMcp(enhancedArgs);
      oneMcp.initialize();
      
      log.info("OneMcp initialized successfully");
      log.info("Handbook: {}", oneMcp.knowledgeBase().getHandbookName());
      log.info("Using existing ArangoDB data (reindex skipped)");
      
      // Check if ArangoDB is configured
      boolean arangoEnabled = oneMcp.configuration().getBoolean("arangodb.enabled", false);
      
      if (!arangoEnabled) {
        log.warn("ArangoDB is disabled in configuration.");
        log.warn("To enable, set ARANGODB_ENABLED=true or configure in application.yaml");
        log.warn("Example: ARANGODB_ENABLED=true ARANGODB_HOST=localhost ARANGODB_PORT=8529");
        return;
      }
      
      // Verify ArangoDB connection
      String handbookName = oneMcp.knowledgeBase().getHandbookName();
      ArangoDbService arangoDbService = new ArangoDbService(oneMcp, handbookName);
      arangoDbService.initialize();
      
      if (!arangoDbService.isInitialized()) {
        log.error("Failed to connect to ArangoDB. Please ensure:");
        log.error("  1. ArangoDB is running");
        log.error("  2. Connection settings are correct");
        log.error("  3. Database exists for handbook: {}", handbookName);
        arangoDbService.shutdown();
        return;
      }
      
      log.info("Connected to ArangoDB database: {}", arangoDbService.getDatabaseName());
      arangoDbService.shutdown();
      
      // Run the example query
      log.info("Running example query for Sale, Category, and Product entities...");
      querySaleEntity(oneMcp);
      
      log.info("========================================");
      log.info("Example completed successfully!");
      log.info("========================================");
      
    } catch (Exception e) {
      log.error("Failed to run GraphQueryExample", e);
      System.err.println("\nError: " + e.getMessage());
      System.err.println("\nUsage:");
      System.err.println("  java -cp ... GraphQueryExample [--handbook /path/to/handbook]");
      System.err.println("\nNote: This example uses existing ArangoDB data (no reindexing)");
      System.err.println("\nEnvironment variables:");
      System.err.println("  ARANGODB_ENABLED=true|false");
      System.err.println("  ARANGODB_HOST=localhost");
      System.err.println("  ARANGODB_PORT=8529");
      System.err.println("  ARANGODB_USER=root");
      System.err.println("  ARANGODB_PASSWORD=your_password");
      System.err.println("  HANDBOOK_DIR=/path/to/handbook");
      System.exit(1);
    } finally {
      // Cleanup
      if (oneMcp != null) {
        try {
          oneMcp.shutdown();
        } catch (Exception e) {
          log.warn("Error during shutdown", e);
        }
      }
    }
  }
  
  /**
   * Example: Query from JSON string (useful for testing).
   *
   * <p>This method queries existing ArangoDB graph data (no reindexing).
   *
   * @param oneMcp the OneMcp instance (should be initialized with --skip-kb-reindex)
   * @param jsonRequest JSON string of the request
   * @return the JSON response as string
   */
  public static String queryFromJsonString(OneMcp oneMcp, String jsonRequest) {
    ArangoDbService arangoDbService = new ArangoDbService(oneMcp, oneMcp.knowledgeBase().getHandbookName());
    arangoDbService.initialize();

    if (!arangoDbService.isInitialized()) {
      log.error("ArangoDB is not initialized. Cannot execute query.");
      return "{\"error\": \"ArangoDB not initialized\"}";
    }

    GraphQueryService queryService = new GraphQueryService(arangoDbService);
    ObjectMapper mapper = JacksonUtility.getJsonMapper();

    try {
      // Parse JSON request
      GraphQueryService.QueryRequest request = mapper.readValue(jsonRequest, GraphQueryService.QueryRequest.class);
      
      // Execute query
      List<GraphQueryService.QueryResult> results = queryService.query(request);
      
      // Serialize results back to JSON
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(results);
      
    } catch (Exception e) {
      log.error("Failed to process JSON query", e);
      return "{\"error\": \"" + e.getMessage() + "\"}";
    } finally {
      arangoDbService.shutdown();
    }
  }
}

