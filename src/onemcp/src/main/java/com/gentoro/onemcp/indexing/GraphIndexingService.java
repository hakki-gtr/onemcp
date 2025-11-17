package com.gentoro.onemcp.indexing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gentoro.onemcp.OneMcp;
import com.gentoro.onemcp.context.KnowledgeDocument;
import com.gentoro.onemcp.context.Operation;
import com.gentoro.onemcp.context.Service;
import com.gentoro.onemcp.indexing.graph.*;
import com.gentoro.onemcp.indexing.graph.nodes.EntityNode;
import com.gentoro.onemcp.indexing.graph.nodes.OperationNode;
import com.gentoro.onemcp.indexing.graph.nodes.ExampleNode;
import com.gentoro.onemcp.indexing.graph.nodes.FieldNode;
import com.gentoro.onemcp.model.LlmClient;
import com.gentoro.onemcp.prompt.PromptTemplate;
import com.gentoro.onemcp.utility.JacksonUtility;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.tags.Tag;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for building a graph representation of the knowledge base in ArangoDB.
 *
 * <p>This service orchestrates the extraction and indexing of:
 *
 * <ul>
 *   <li>Entities from OpenAPI tags
 *   <li>Operations with signatures, examples, and documentation
 *   <li>Examples from OpenAPI specifications
 *   <li>Relationships between all these elements
 * </ul>
 *
 * <p>The resulting graph enables semantic querying and retrieval based on relationships.
 */
public class GraphIndexingService {
  private static final org.slf4j.Logger log =
      com.gentoro.onemcp.logging.LoggingService.getLogger(GraphIndexingService.class);

  private final OneMcp oneMcp;
  private final ArangoDbService arangoDbService;
  private final String handbookName;

  /**
   * Create a new graph indexing service.
   *
   * @param oneMcp the OneMcp application context
   */
  public GraphIndexingService(OneMcp oneMcp) {
    this.oneMcp = oneMcp;
    this.handbookName = oneMcp.knowledgeBase().getHandbookName();
    this.arangoDbService = new ArangoDbService(oneMcp, handbookName);
  }

  /**
   * Index the entire knowledge base into the graph database.
   *
   * <p>This is the main entry point that coordinates all indexing operations.
   */
  public void indexKnowledgeBase() {
    log.info("Starting knowledge base graph indexing");

    try {
      // Initialize ArangoDB service
      arangoDbService.initialize();

      if (!arangoDbService.isInitialized()) {
        log.info("ArangoDB not enabled or not available, graph indexing skipped");
        return;
      }

      // Clear existing data to avoid duplicate key errors on re-indexing
      boolean clearData = oneMcp.configuration().getBoolean("arangodb.clearOnStartup", true);
      if (clearData) {
        log.info("Clearing existing graph data before re-indexing");
        arangoDbService.clearAllData();
      }

      // Index services and their components using LLM-based extraction
      List<Service> services = oneMcp.knowledgeBase().services();
      log.info("Indexing {} services using LLM-based extraction", services.size());

      for (Service service : services) {
        indexServiceWithLLM(service);
      }

      // COMMENTED OUT: Index general documentation (doc chunks)
      // indexGeneralDocumentation();

      // Ensure graph exists after indexing (in case it wasn't created during clear)
      arangoDbService.ensureGraphExists();

      log.info("Knowledge base graph indexing completed successfully");
    } catch (Exception e) {
      log.error("Failed to index knowledge base graph", e);
      throw new com.gentoro.onemcp.exception.IoException("Graph indexing failed", e);
    }
  }

  /**
   * Index a single service using LLM-based extraction of entities and operations.
   *
   * @param service the service to index
   */
  private void indexServiceWithLLM(Service service) {
    log.debug("Indexing service with LLM: {}", service.getSlug());

    try {
      // Check if LLM client is available
      LlmClient llmClient = oneMcp.llmClient();
      if (llmClient == null) {
        log.warn("LLM client not available, falling back to rule-based extraction for service: {}", service.getSlug());
        indexService(service);
        return;
      }

      // Load prompt template
      PromptTemplate promptTemplate = oneMcp.promptRepository().get("graph-rag-indexing");
      PromptTemplate.PromptSession session = promptTemplate.newSession();

      // Prepare context variables for the prompt
      Map<String, Object> context = preparePromptContext(service);
      
      // Enable the activation section with context
      // All sections in the template use the same activation ID "graph-rag-indexing"
      // Enabling it once will enable all sections with that ID
      session.enable("graph-rag-indexing", context);
      
      // Render messages and verify we have content to send
      List<LlmClient.Message> messages = session.renderMessages();
      if (messages.isEmpty()) {
        log.warn("No messages rendered from prompt template, falling back to rule-based extraction");
        indexService(service);
        return;
      }
      
      log.debug("Rendered {} messages from prompt template, calling LLM for service: {}", messages.size(), service.getSlug());
      
      // Log prompt to file
      logLLMPrompt(service.getSlug(), messages);
      
      String llmResponse = llmClient.chat(messages, Collections.emptyList(), false, null);
      
      // Log response to file
      logLLMResponse(service.getSlug(), llmResponse);

      // Parse LLM response and extract entities/operations
      GraphExtractionResult result = parseLLMResponse(llmResponse, service.getSlug());
      
      // Index extracted entities
      for (EntityNode entity : result.entities()) {
        arangoDbService.storeNode(entity);
      }

      // Index extracted fields
      for (FieldNode field : result.fields()) {
        arangoDbService.storeNode(field);
      }

      // Index extracted operations
      for (OperationNode operation : result.operations()) {
        arangoDbService.storeNode(operation);
      }

      // Index extracted examples
      for (ExampleNode example : result.examples()) {
        arangoDbService.storeNode(example);
        
        // Create edge from operation to example if operationKey is provided
        // Only create if it doesn't already exist in the relationships list
        if (example.getOperationKey() != null && !example.getOperationKey().isEmpty()) {
          // Check if this edge already exists in relationships (check for common example edge types)
          boolean edgeExists = result.relationships().stream()
              .anyMatch(edge -> 
                  edge.getFromKey().equals(example.getOperationKey()) &&
                  edge.getToKey().equals(example.getKey()) &&
                  (edge.getEdgeType().equals("HAS_EXAMPLE") || 
                   edge.getEdgeType().equals("DEMONSTRATES") ||
                   edge.getEdgeType().equals("ILLUSTRATES")));
          
          if (!edgeExists) {
            GraphEdge exampleEdge = new GraphEdge(
                example.getOperationKey(),
                example.getKey(),
                "HAS_EXAMPLE",
                new HashMap<>(),
                "Operation has this example",
                "strong");
            arangoDbService.storeEdge(exampleEdge);
          }
        }
      }

      // Index relationships
      for (GraphEdge edge : result.relationships()) {
        arangoDbService.storeEdge(edge);
      }

      // Log complete graph structure
      logCompleteGraph(service.getSlug(), result);

      log.info(
          "Indexed service {} with {} entities, {} fields, {} operations, {} examples, {} relationships (LLM-based)",
          service.getSlug(),
          result.entities().size(),
          result.fields().size(),
          result.operations().size(),
          result.examples().size(),
          result.relationships().size());
    } catch (Exception e) {
      log.error("Failed to index service with LLM: {}, falling back to rule-based extraction", service.getSlug(), e);
      // Fallback to rule-based extraction
      indexService(service);
    }
  }

  /**
   * Prepare context variables for the LLM prompt.
   *
   * @param service the service to prepare context for
   * @return map of context variables
   */
  private Map<String, Object> preparePromptContext(Service service) {
    Map<String, Object> context = new HashMap<>();
    
    try {
      // Load instructions.md content
      List<KnowledgeDocument> instructions = oneMcp.knowledgeBase().findByUriPrefix("kb:///instructions.md");
      String instructionsContent = instructions.isEmpty() ? "" : instructions.get(0).content();
      context.put("instructions_content", instructionsContent);

      // Load OpenAPI files
      List<Map<String, String>> openapiFiles = new ArrayList<>();
      Path handbookPath = oneMcp.knowledgeBase().handbookPath();
      Path openapiPath = handbookPath.resolve("openapi");
      if (Files.exists(openapiPath)) {
        Files.walk(openapiPath)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
            .forEach(file -> {
              try {
                Map<String, String> fileInfo = new HashMap<>();
                fileInfo.put("name", file.getFileName().toString());
                fileInfo.put("content", Files.readString(file));
                openapiFiles.add(fileInfo);
              } catch (Exception e) {
                log.warn("Failed to read OpenAPI file: {}", file, e);
              }
            });
      }
      context.put("openapi_files", openapiFiles);

      // Prepare service information
      List<Map<String, Object>> services = new ArrayList<>();
      Map<String, Object> serviceInfo = new HashMap<>();
      serviceInfo.put("slug", service.getSlug());
      
      // Load service.yaml content
      try {
        Path serviceYamlPath = handbookPath.resolve(service.getResourcesUri()).resolve("service.yaml");
        if (Files.exists(serviceYamlPath)) {
          serviceInfo.put("config", Files.readString(serviceYamlPath));
        }
      } catch (Exception e) {
        log.debug("Could not load service.yaml for {}", service.getSlug(), e);
      }

      // Prepare operations with documentation
      List<Map<String, Object>> operations = new ArrayList<>();
      for (Operation op : service.getOperations()) {
        Map<String, Object> opInfo = new HashMap<>();
        opInfo.put("operation", op.getOperation());
        opInfo.put("method", op.getMethod());
        opInfo.put("path", op.getPath());
        
        // Load operation documentation
        try {
          String docUri = "kb:///services/" + service.getSlug() + "/operations/" + op.getOperation() + ".md";
          KnowledgeDocument doc = oneMcp.knowledgeBase().getDocument(docUri);
          opInfo.put("documentation", doc.content());
        } catch (Exception e) {
          log.debug("Could not load documentation for operation: {}", op.getOperation(), e);
          opInfo.put("documentation", "");
        }
        operations.add(opInfo);
      }
      serviceInfo.put("operations", operations);
      services.add(serviceInfo);
      context.put("services", services);

    } catch (Exception e) {
      log.warn("Error preparing prompt context", e);
    }

    return context;
  }

  /**
   * Parse LLM JSON response and extract entities, operations, and relationships.
   *
   * @param llmResponse the LLM response string (should be JSON)
   * @param serviceSlug the service slug
   * @return extraction result with entities, operations, and relationships
   */
  private GraphExtractionResult parseLLMResponse(String llmResponse, String serviceSlug) {
    List<EntityNode> entities = new ArrayList<>();
    List<FieldNode> fields = new ArrayList<>();
    List<OperationNode> operations = new ArrayList<>();
    List<ExampleNode> examples = new ArrayList<>();
    List<GraphEdge> relationships = new ArrayList<>();

    try {
      // Extract JSON from response (may be wrapped in markdown code blocks)
      String jsonStr = extractJsonFromResponse(llmResponse);
      
      if (jsonStr == null || jsonStr.trim().isEmpty()) {
        log.warn("No JSON content found in LLM response for service: {}", serviceSlug);
        logLLMParseError(serviceSlug, llmResponse, new IllegalArgumentException("No JSON content found"));
        return new GraphExtractionResult(entities, fields, operations, examples, relationships);
      }

      // Try to fix common JSON issues (truncated strings, unclosed structures)
      String fixedJson = fixTruncatedJson(jsonStr);

      // Parse JSON
      ObjectMapper mapper = JacksonUtility.getJsonMapper();
      @SuppressWarnings("unchecked")
      Map<String, Object> result = mapper.readValue(fixedJson, Map.class);

      // Extract entities
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> entitiesList = (List<Map<String, Object>>) result.get("entities");
      if (entitiesList != null) {
        for (Map<String, Object> entityData : entitiesList) {
          String key = (String) entityData.get("key");
          String name = (String) entityData.get("name");
          String description = (String) entityData.getOrDefault("description", "");
          @SuppressWarnings("unchecked")
          List<String> associatedOps = (List<String>) entityData.getOrDefault("associatedOperations", new ArrayList<>());
          String source = (String) entityData.getOrDefault("source", null);
          @SuppressWarnings("unchecked")
          List<String> attributes = (List<String>) entityData.getOrDefault("attributes", null);
          String domain = (String) entityData.getOrDefault("domain", null);
          
          EntityNode entity = new EntityNode(
              key != null ? key : "entity|" + sanitizeKey(name),
              name,
              description,
              serviceSlug,
              associatedOps,
              source,
              attributes,
              domain);
          entities.add(entity);
        }
      }

      // Extract fields
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> fieldsList = (List<Map<String, Object>>) result.get("fields");
      if (fieldsList != null) {
        for (Map<String, Object> fieldData : fieldsList) {
          String key = (String) fieldData.get("key");
          String name = (String) fieldData.get("name");
          String description = (String) fieldData.getOrDefault("description", "");
          String fieldType = (String) fieldData.getOrDefault("fieldType", "string");
          String entityKey = (String) fieldData.get("entityKey");
          String source = (String) fieldData.getOrDefault("source", null);
          
          // Generate key if not provided
          if (key == null && entityKey != null && name != null) {
            String entityName = entityKey.replace("entity|", "");
            key = "field|" + sanitizeKey(entityName + "_" + name);
          } else if (key == null) {
            log.warn("Field missing key, name, or entityKey, skipping");
            continue;
          }
          
          FieldNode field = new FieldNode(
              key,
              name,
              description,
              fieldType,
              entityKey,
              serviceSlug,
              source);
          fields.add(field);
        }
      }

      // Extract operations
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> operationsList = (List<Map<String, Object>>) result.get("operations");
      if (operationsList != null) {
        for (Map<String, Object> opData : operationsList) {
          String key = (String) opData.get("key");
          String operationId = (String) opData.get("operationId");
          String method = (String) opData.get("method");
          String path = (String) opData.get("path");
          String summary = (String) opData.getOrDefault("summary", "");
          String description = (String) opData.getOrDefault("description", summary);
          @SuppressWarnings("unchecked")
          List<String> tags = (List<String>) opData.getOrDefault("tags", new ArrayList<>());
          String signature = (String) opData.getOrDefault("signature", method + " " + path + " - " + summary);
          @SuppressWarnings("unchecked")
          List<String> exampleKeys = (List<String>) opData.getOrDefault("exampleKeys", new ArrayList<>());
          String documentationUri = (String) opData.getOrDefault("documentationUri", "");
          Object requestSchemaObj = opData.getOrDefault("requestSchema", null);
          String requestSchema = requestSchemaObj != null ? convertToString(requestSchemaObj) : null;
          Object responseSchemaObj = opData.getOrDefault("responseSchema", null);
          String responseSchema = responseSchemaObj != null ? convertToString(responseSchemaObj) : null;
          @SuppressWarnings("unchecked")
          List<String> operationExamples = (List<String>) opData.getOrDefault("examples", null);
          String category = (String) opData.getOrDefault("category", null);
          
          // Log category extraction for debugging
          if (category != null) {
            log.trace("Extracted category '{}' for operation: {}", category, operationId);
          } else {
            log.debug("No category found for operation: {}", operationId);
          }

          OperationNode operation = new OperationNode(
              key != null ? key : "op|" + sanitizeKey(operationId),
              operationId,
              method,
              path,
              summary,
              description,
              serviceSlug,
              tags,
              signature,
              exampleKeys,
              documentationUri,
              requestSchema,
              responseSchema,
              operationExamples,
              category);
          operations.add(operation);
        }
      }

      // Extract examples
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> examplesList = (List<Map<String, Object>>) result.get("examples");
      if (examplesList != null) {
        for (Map<String, Object> exData : examplesList) {
          String key = (String) exData.get("key");
          String name = (String) exData.get("name");
          String summary = (String) exData.getOrDefault("summary", "");
          String description = (String) exData.getOrDefault("description", summary);
          
          // Handle requestBody and responseBody which might be objects or strings
          Object requestBodyObj = exData.getOrDefault("requestBody", "");
          String requestBody = convertToString(requestBodyObj);
          
          Object responseBodyObj = exData.getOrDefault("responseBody", "");
          String responseBody = convertToString(responseBodyObj);
          
          // Handle responseStatus which might be a number or string
          Object responseStatusObj = exData.getOrDefault("responseStatus", "200");
          String responseStatus = responseStatusObj != null ? responseStatusObj.toString() : "200";
          
          String operationKey = (String) exData.get("operationKey");
          
          // Generate key if not provided
          if (key == null && operationKey != null && name != null) {
            key = "example|" + sanitizeKey(operationKey.replace("op|", "") + "_" + name);
          } else if (key == null) {
            log.warn("Example missing key, name, or operationKey, skipping");
            continue;
          }
          
          ExampleNode example = new ExampleNode(
              key,
              name != null ? name : "",
              summary,
              description,
              requestBody,
              responseBody,
              responseStatus,
              operationKey,
              serviceSlug);
          examples.add(example);
        }
      }

      // Extract relationships
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> relationshipsList = (List<Map<String, Object>>) result.get("relationships");
      if (relationshipsList != null) {
        for (Map<String, Object> relData : relationshipsList) {
          String fromKey = (String) relData.get("fromKey");
          String toKey = (String) relData.get("toKey");
          String edgeTypeStr = (String) relData.get("edgeType");
          String description = (String) relData.getOrDefault("description", null);
          String strength = (String) relData.getOrDefault("strength", null);
          
          // Validate that edgeType is provided
          if (edgeTypeStr == null || edgeTypeStr.trim().isEmpty()) {
            log.warn("Missing edge type for relationship {} -> {}, skipping", fromKey, toKey);
            continue;
          }

          GraphEdge edge = new GraphEdge(fromKey, toKey, edgeTypeStr, new HashMap<>(), description, strength);
          relationships.add(edge);
        }
      }

    } catch (Exception e) {
      log.error("Failed to parse LLM response for service: {}, using empty result", serviceSlug, e);
      logLLMParseError(serviceSlug, llmResponse, e);
    }

    return new GraphExtractionResult(entities, fields, operations, examples, relationships);
  }

  /**
   * Extract JSON content from LLM response, handling markdown code blocks and other wrappers.
   *
   * @param response the raw LLM response
   * @return extracted JSON string, or null if not found
   */
  private String extractJsonFromResponse(String response) {
    if (response == null || response.trim().isEmpty()) {
      return null;
    }

    String jsonStr = response.trim();
    
    // Remove markdown code block markers
    if (jsonStr.startsWith("```json")) {
      jsonStr = jsonStr.substring(7).trim();
    } else if (jsonStr.startsWith("```")) {
      jsonStr = jsonStr.substring(3).trim();
    }
    
    if (jsonStr.endsWith("```")) {
      jsonStr = jsonStr.substring(0, jsonStr.length() - 3).trim();
    }
    
    // Try to find JSON object boundaries if response contains extra text
    int firstBrace = jsonStr.indexOf('{');
    int lastBrace = jsonStr.lastIndexOf('}');
    
    if (firstBrace >= 0 && lastBrace > firstBrace) {
      jsonStr = jsonStr.substring(firstBrace, lastBrace + 1);
    } else if (firstBrace >= 0) {
      // JSON starts but doesn't end properly - might be truncated
      jsonStr = jsonStr.substring(firstBrace);
    }
    
    return jsonStr.trim();
  }

  /**
   * Attempt to fix truncated JSON by closing unclosed strings and structures.
   *
   * @param jsonStr the JSON string to fix
   * @return fixed JSON string
   */
  private String fixTruncatedJson(String jsonStr) {
    if (jsonStr == null) {
      return "{}";
    }

    StringBuilder fixed = new StringBuilder(jsonStr.length() + 200);
    boolean inString = false;
    boolean escaped = false;
    int braceDepth = 0;
    int bracketDepth = 0;
    int lastValidPos = -1;
    
    for (int i = 0; i < jsonStr.length(); i++) {
      char c = jsonStr.charAt(i);
      
      if (escaped) {
        fixed.append(c);
        escaped = false;
        continue;
      }
      
      if (c == '\\') {
        escaped = true;
        fixed.append(c);
        continue;
      }
      
      if (c == '"') {
        inString = !inString;
        fixed.append(c);
        if (!inString) {
          lastValidPos = fixed.length();
        }
        continue;
      }
      
      if (!inString) {
        if (c == '{') {
          braceDepth++;
          fixed.append(c);
          lastValidPos = fixed.length();
        } else if (c == '}') {
          braceDepth--;
          fixed.append(c);
          lastValidPos = fixed.length();
        } else if (c == '[') {
          bracketDepth++;
          fixed.append(c);
          lastValidPos = fixed.length();
        } else if (c == ']') {
          bracketDepth--;
          fixed.append(c);
          lastValidPos = fixed.length();
        } else {
          fixed.append(c);
          if (Character.isWhitespace(c) || c == ',' || c == ':') {
            lastValidPos = fixed.length();
          }
        }
      } else {
        // Inside a string - just append
        fixed.append(c);
      }
    }
    
    // If we're still in a string, close it
    if (inString) {
      fixed.append('"');
      log.warn("Fixed unclosed string in JSON by adding closing quote");
    }
    
    // Close any unclosed brackets/braces
    while (bracketDepth > 0) {
      fixed.append(']');
      bracketDepth--;
      log.warn("Fixed unclosed array bracket in JSON");
    }
    
    while (braceDepth > 0) {
      fixed.append('}');
      braceDepth--;
      log.warn("Fixed unclosed object brace in JSON");
    }
    
    // If JSON appears to be truncated mid-value, try to close the last incomplete object/array
    String result = fixed.toString();
    
    // If the last character before our fixes was a comma, remove it
    if (lastValidPos > 0 && lastValidPos < result.length()) {
      String beforeFix = result.substring(0, lastValidPos);
      String afterFix = result.substring(lastValidPos);
      
      // If we added closing braces and there's a trailing comma before them, clean it up
      if (afterFix.startsWith("}") || afterFix.startsWith("]")) {
        // Remove trailing comma if present
        beforeFix = beforeFix.replaceAll(",\\s*$", "");
        result = beforeFix + afterFix;
      }
    }
    
    return result;
  }

  /**
   * Log JSON parsing errors to a separate file for debugging.
   *
   * @param serviceSlug the service slug
   * @param llmResponse the original LLM response
   * @param error the parsing error
   */
  private void logLLMParseError(String serviceSlug, String llmResponse, Exception error) {
    try {
      File logsDir = new File("logs");
      if (!logsDir.exists()) {
        logsDir.mkdirs();
      }
      
      // Create timestamp-based filename for error log
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
      String sanitizedServiceSlug = serviceSlug != null ? serviceSlug.replaceAll("[^a-zA-Z0-9_-]", "_") : "unknown";
      String filename =
          String.format(
              "%s-llm-parse-error-%s-%s.log",
              sanitizedHandbookName(), timestamp, sanitizedServiceSlug);
      File errorLogFile = new File(logsDir, filename);
      
      try (FileWriter writer = new FileWriter(errorLogFile, false)) {
        writer.write("=".repeat(80) + "\n");
        writer.write("JSON PARSING ERROR\n");
        writer.write("=".repeat(80) + "\n");
        writer.write(String.format("TIMESTAMP: %s\n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        writer.write(String.format("SERVICE: %s\n", serviceSlug));
        writer.write(String.format("ERROR TYPE: %s\n", error.getClass().getSimpleName()));
        writer.write(String.format("ERROR MESSAGE: %s\n", error.getMessage()));
        writer.write("-".repeat(80) + "\n");
        writer.write("ORIGINAL LLM RESPONSE:\n");
        writer.write("-".repeat(80) + "\n");
        writer.write(llmResponse);
        writer.write("\n");
        writer.write("-".repeat(80) + "\n");
        writer.write("STACK TRACE:\n");
        writer.write("-".repeat(80) + "\n");
        
        java.io.PrintWriter pw = new java.io.PrintWriter(writer);
        error.printStackTrace(pw);
        pw.flush();
        
        writer.write("\n");
        writer.write("=".repeat(80) + "\n");
        writer.flush();
      }
      
      log.debug("Logged JSON parsing error to file: {}", errorLogFile.getAbsolutePath());
    } catch (Exception e) {
      log.warn("Failed to log JSON parsing error to file", e);
    }
  }

  /**
   * Convert an object to a string, handling both String and complex objects (like Map).
   * If the object is a Map or other complex type, serialize it to JSON string.
   *
   * @param obj the object to convert
   * @return string representation
   */
  private String convertToString(Object obj) {
    if (obj == null) {
      return "";
    }
    if (obj instanceof String) {
      return (String) obj;
    }
    // For complex objects (Map, List, etc.), serialize to JSON
    try {
      ObjectMapper mapper = JacksonUtility.getJsonMapper();
      return mapper.writeValueAsString(obj);
    } catch (Exception e) {
      log.warn("Failed to serialize object to JSON string, using toString()", e);
      return obj.toString();
    }
  }

  /**
   * Record to hold extraction results.
   */
  private record GraphExtractionResult(
      List<EntityNode> entities,
      List<FieldNode> fields,
      List<OperationNode> operations,
      List<ExampleNode> examples,
      List<GraphEdge> relationships) {}

  /**
   * Index a single service using rule-based extraction (fallback method).
   *
   * @param service the service to index
   */
  private void indexService(Service service) {
    log.debug("Indexing service (rule-based): {}", service.getSlug());

    try {
      // Load OpenAPI definition
      OpenAPI openAPI = service.definition(oneMcp.knowledgeBase().handbookPath());

      // Extract and index entities (tags)
      List<EntityNode> entities = extractEntities(service, openAPI);
      for (EntityNode entity : entities) {
        arangoDbService.storeNode(entity);
      }

      // Extract and index operations
      List<OperationNode> operationNodes = extractOperations(service, openAPI);
      for (OperationNode opNode : operationNodes) {
        arangoDbService.storeNode(opNode);

        // Create edges from entities to operations
        for (String tag : opNode.getTags()) {
          String entityKey = "entity|" + sanitizeKey(tag);
          arangoDbService.storeEdge(
              new GraphEdge(entityKey, opNode.getKey(), "HAS_OPERATION"));
        }
      }

      log.debug(
          "Indexed service {} with {} entities, {} operations (rule-based)",
          service.getSlug(),
          entities.size(),
          operationNodes.size());
    } catch (Exception e) {
      log.error("Failed to index service: {}", service.getSlug(), e);
    }
  }

  /**
   * Extract entity nodes from OpenAPI tags.
   *
   * @param service the service
   * @param openAPI the OpenAPI specification
   * @return list of entity nodes
   */
  private List<EntityNode> extractEntities(Service service, OpenAPI openAPI) {
    List<EntityNode> entities = new ArrayList<>();

    if (openAPI.getTags() == null || openAPI.getTags().isEmpty()) {
      log.debug("No tags found in service: {}", service.getSlug());
      return entities;
    }

    for (Tag tag : openAPI.getTags()) {
      String key = "entity|" + sanitizeKey(tag.getName());
      String description = tag.getDescription() != null ? tag.getDescription() : "";

      // Find operations associated with this tag
      List<String> associatedOps =
          service.getOperations().stream()
              .filter(op -> op.getOperation() != null)
              .map(op -> "op|" + sanitizeKey(op.getOperation()))
              .collect(Collectors.toList());

      EntityNode entity =
          new EntityNode(key, tag.getName(), description, service.getSlug(), associatedOps);
      entities.add(entity);
    }

    return entities;
  }

  /**
   * Extract operation nodes from service operations.
   *
   * @param service the service
   * @param openAPI the OpenAPI specification
   * @return list of operation nodes
   */
  private List<OperationNode> extractOperations(Service service, OpenAPI openAPI) {
    List<OperationNode> operationNodes = new ArrayList<>();

    for (Operation operation : service.getOperations()) {
      String key = "op|" + sanitizeKey(operation.getOperation());
      String signature = buildOperationSignature(operation);

      // Extract tags from operation
      List<String> tags = new ArrayList<>();
      // Note: tags would need to be extracted from OpenAPI paths, simplified for now
      if (openAPI.getTags() != null) {
        tags =
            openAPI.getTags().stream().map(Tag::getName).collect(Collectors.toList());
      }

      // Find example keys for this operation
      List<String> exampleKeys = new ArrayList<>();
      // Examples will be linked later

      OperationNode opNode =
          new OperationNode(
              key,
              operation.getOperation(),
              operation.getMethod(),
              operation.getPath(),
              operation.getSummary(),
              operation.getSummary(), // Using summary as description
              service.getSlug(),
              tags,
              signature,
              exampleKeys,
              operation.getDocumentationUri());

      operationNodes.add(opNode);
    }

    return operationNodes;
  }

  /**
   * Build a signature string for an operation.
   *
   * @param operation the operation
   * @return signature string
   */
  private String buildOperationSignature(Operation operation) {
    return String.format(
        "%s %s - %s",
        operation.getMethod().toUpperCase(), operation.getPath(), operation.getSummary());
  }

  /**
   * Log LLM prompt messages to a file.
   *
   * <p>Creates a separate file for each prompt using a timestamp in the filename.
   *
   * @param serviceSlug the service slug
   * @param messages the messages sent to LLM
   */
  private void logLLMPrompt(String serviceSlug, List<LlmClient.Message> messages) {
    try {
      File logsDir = new File("logs");
      if (!logsDir.exists()) {
        logsDir.mkdirs();
      }
      
      // Create timestamp-based filename (filesystem-safe format: yyyyMMdd-HHmmss-SSS)
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
      String sanitizedServiceSlug = serviceSlug != null ? serviceSlug.replaceAll("[^a-zA-Z0-9_-]", "_") : "unknown";
      String filename =
          String.format(
              "%s-llm-prompt-%s-%s.log", sanitizedHandbookName(), timestamp, sanitizedServiceSlug);
      File logFile = new File(logsDir, filename);
      
      try (FileWriter writer = new FileWriter(logFile, false)) {
        writer.write("=".repeat(80) + "\n");
        writer.write(String.format("TIMESTAMP: %s\n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        writer.write(String.format("SERVICE: %s\n", serviceSlug));
        writer.write("=".repeat(80) + "\n");
        writer.write("PROMPT MESSAGES:\n");
        writer.write("-".repeat(80) + "\n");
        
        for (int i = 0; i < messages.size(); i++) {
          LlmClient.Message msg = messages.get(i);
          writer.write(String.format("\n[Message %d/%d - Role: %s]\n", i + 1, messages.size(), msg.role()));
          writer.write("-".repeat(80) + "\n");
          writer.write(msg.content());
          writer.write("\n");
        }
        
        writer.write("=".repeat(80) + "\n");
        writer.flush();
      }
      
      log.debug("Logged LLM prompt to file: {}", logFile.getAbsolutePath());
    } catch (IOException e) {
      log.warn("Failed to log LLM prompt to file", e);
    }
  }

  /**
   * Log complete graph structure to a file.
   *
   * <p>Creates a separate file for each graph using a timestamp in the filename.
   * Logs all entities, operations, examples, and relationships in JSON format.
   *
   * @param serviceSlug the service slug
   * @param result the graph extraction result containing all nodes and edges
   */
  private void logCompleteGraph(String serviceSlug, GraphExtractionResult result) {
    try {
      File logsDir = new File("logs");
      if (!logsDir.exists()) {
        logsDir.mkdirs();
      }
      
      // Create timestamp-based filename (filesystem-safe format: yyyyMMdd-HHmmss-SSS)
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
      String sanitizedServiceSlug = serviceSlug != null ? serviceSlug.replaceAll("[^a-zA-Z0-9_-]", "_") : "unknown";
      String filename =
          String.format(
              "%s-llm-graph-%s-%s.log", sanitizedHandbookName(), timestamp, sanitizedServiceSlug);
      File logFile = new File(logsDir, filename);
      
      try (FileWriter writer = new FileWriter(logFile, false)) {
        writer.write("=".repeat(80) + "\n");
        writer.write(String.format("TIMESTAMP: %s\n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        writer.write(String.format("SERVICE: %s\n", serviceSlug));
        writer.write("=".repeat(80) + "\n");
        writer.write("COMPLETE GRAPH STRUCTURE:\n");
        writer.write("-".repeat(80) + "\n");
        
        // Build complete graph structure as JSON
        Map<String, Object> graphStructure = new HashMap<>();
        graphStructure.put("serviceSlug", serviceSlug);
        graphStructure.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        // Convert entities to maps
        List<Map<String, Object>> entitiesList = new ArrayList<>();
        for (EntityNode entity : result.entities()) {
          entitiesList.add(entity.toMap());
        }
        graphStructure.put("entities", entitiesList);
        
        // Convert fields to maps
        List<Map<String, Object>> fieldsList = new ArrayList<>();
        for (FieldNode field : result.fields()) {
          fieldsList.add(field.toMap());
        }
        graphStructure.put("fields", fieldsList);
        
        // Convert operations to maps
        List<Map<String, Object>> operationsList = new ArrayList<>();
        for (OperationNode operation : result.operations()) {
          operationsList.add(operation.toMap());
        }
        graphStructure.put("operations", operationsList);
        
        // Convert examples to maps
        List<Map<String, Object>> examplesList = new ArrayList<>();
        for (ExampleNode example : result.examples()) {
          examplesList.add(example.toMap());
        }
        graphStructure.put("examples", examplesList);
        
        // Convert relationships to maps
        List<Map<String, Object>> relationshipsList = new ArrayList<>();
        for (GraphEdge edge : result.relationships()) {
          Map<String, Object> edgeMap = edge.toMap();
          edgeMap.put("fromKey", edge.getFromKey());
          edgeMap.put("toKey", edge.getToKey());
          relationshipsList.add(edgeMap);
        }
        graphStructure.put("relationships", relationshipsList);
        
        // Add summary statistics
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalEntities", result.entities().size());
        summary.put("totalFields", result.fields().size());
        summary.put("totalOperations", result.operations().size());
        summary.put("totalExamples", result.examples().size());
        summary.put("totalRelationships", result.relationships().size());
        graphStructure.put("summary", summary);
        
        // Write as formatted JSON
        ObjectMapper mapper = JacksonUtility.getJsonMapper();
        String jsonOutput = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(graphStructure);
        writer.write(jsonOutput);
        writer.write("\n");
        writer.write("=".repeat(80) + "\n");
        writer.flush();
      }
      
      log.debug("Logged complete graph structure to file: {}", logFile.getAbsolutePath());
    } catch (Exception e) {
      log.warn("Failed to log complete graph structure to file", e);
    }
  }

  /**
   * Log LLM response to a file.
   *
   * <p>Creates a separate file for each response using a timestamp in the filename.
   *
   * @param serviceSlug the service slug
   * @param response the response from LLM
   */
  private void logLLMResponse(String serviceSlug, String response) {
    try {
      File logsDir = new File("logs");
      if (!logsDir.exists()) {
        logsDir.mkdirs();
      }
      
      // Create timestamp-based filename (filesystem-safe format: yyyyMMdd-HHmmss-SSS)
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
      String sanitizedServiceSlug = serviceSlug != null ? serviceSlug.replaceAll("[^a-zA-Z0-9_-]", "_") : "unknown";
      String filename =
          String.format(
              "%s-llm-response-%s-%s.log", sanitizedHandbookName(), timestamp, sanitizedServiceSlug);
      File logFile = new File(logsDir, filename);
      
      try (FileWriter writer = new FileWriter(logFile, false)) {
        writer.write("=".repeat(80) + "\n");
        writer.write(String.format("TIMESTAMP: %s\n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        writer.write(String.format("SERVICE: %s\n", serviceSlug));
        writer.write("=".repeat(80) + "\n");
        writer.write("LLM RESPONSE:\n");
        writer.write("-".repeat(80) + "\n");
        writer.write(response);
        writer.write("\n");
        writer.write("=".repeat(80) + "\n");
        writer.flush();
      }
      
      log.debug("Logged LLM response to file: {}", logFile.getAbsolutePath());
    } catch (IOException e) {
      log.warn("Failed to log LLM response to file", e);
    }
  }

  /**
   * Sanitize a string to be used as a key in ArangoDB.
   *
   * @param input the input string
   * @return sanitized string
   */
  private String sanitizeKey(String input) {
    if (input == null) return "";
    return input.replaceAll("[^a-zA-Z0-9_\\-|]", "_").toLowerCase();
  }

  /**
   * Shutdown the indexing service and release resources.
   */
  public void shutdown() {
    if (arangoDbService != null) {
      arangoDbService.shutdown();
    }
  }

  private String sanitizedHandbookName() {
    String name = handbookName;
    if (name == null || name.isBlank()) {
      try {
        name = oneMcp.knowledgeBase().getHandbookName();
      } catch (Exception ignored) {
        // ignore
      }
    }
    if (name == null || name.isBlank()) {
      name = "unknown_handbook";
    }
    return name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
  }

}