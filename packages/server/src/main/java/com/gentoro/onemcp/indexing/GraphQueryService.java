package com.gentoro.onemcp.indexing;

import com.gentoro.onemcp.OneMcp;
import com.gentoro.onemcp.exception.IoException;
import com.gentoro.onemcp.indexing.driver.GraphQueryDriver;
import com.gentoro.onemcp.indexing.driver.arangodb.ArangoQueryDriver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.configuration2.Configuration;

/**
 * Service for querying the knowledge graph independent of the underlying driver implementation.
 *
 * <p>This service orchestrates optimized traversals for retrieving entity details, operations,
 * examples, and documentation using the configured {@link GraphQueryDriver}.
 */
public class GraphQueryService implements AutoCloseable {
  private static final org.slf4j.Logger log =
      com.gentoro.onemcp.logging.LoggingService.getLogger(GraphQueryService.class);

  private final OneMcp oneMcp;
  private final String driverName;
  private final String handbookName;
  private final GraphQueryDriver queryDriver;
  private boolean driverInitialized;

  /**
   * Create a new graph query service using the provided OneMcp context and configured driver.
   *
   * @param oneMcp the OneMcp application context
   */
  public GraphQueryService(OneMcp oneMcp) {
    this(oneMcp, null);
  }

  /**
   * Create a new graph query service with an explicit driver override.
   *
   * @param oneMcp the application context
   * @param customDriver optional custom driver (when {@code null}, driver determined via config)
   */
  public GraphQueryService(OneMcp oneMcp, GraphQueryDriver customDriver) {
    this.oneMcp = Objects.requireNonNull(oneMcp, "oneMcp must not be null");
    this.handbookName = oneMcp.knowledgeBase().getHandbookName();
    this.driverName = resolveDriverName(oneMcp);
    this.queryDriver = customDriver != null ? customDriver : createDriver(this.driverName);
    this.driverInitialized = queryDriver != null && queryDriver.isInitialized();
  }

  /**
   * Create a query service from an already constructed driver (useful for tests).
   *
   * @param queryDriver the driver to use
   */
  public GraphQueryService(GraphQueryDriver queryDriver) {
    this.oneMcp = null;
    this.handbookName = queryDriver != null ? queryDriver.getHandbookName() : null;
    this.driverName = queryDriver != null ? queryDriver.getDriverName() : "unknown";
    this.queryDriver = queryDriver;
    this.driverInitialized = queryDriver != null && queryDriver.isInitialized();
  }

  /**
   * Query context item representing an entity with operations to retrieve.
   */
  public static class ContextItem {
    private String entity;
    private List<String> operations;
    private Integer confidence;
    private String referral;

    public ContextItem() {}

    public ContextItem(String entity, List<String> operations, Integer confidence, String referral) {
      this.entity = entity;
      this.operations = operations;
      this.confidence = confidence;
      this.referral = referral;
    }

    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }
    public List<String> getOperations() { return operations; }
    public void setOperations(List<String> operations) { this.operations = operations; }
    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }
    public String getReferral() { return referral; }
    public void setReferral(String referral) { this.referral = referral; }
  }

  /**
   * Query request containing context items.
   */
  public static class QueryRequest {
    private List<ContextItem> context;

    public QueryRequest() {}

    public QueryRequest(List<ContextItem> context) {
      this.context = context;
    }

    public List<ContextItem> getContext() { return context; }
    public void setContext(List<ContextItem> context) { this.context = context; }
  }

  /**
   * Query result containing all retrieved information for each context item.
   */
  public static class QueryResult {
    private String entity;
    private List<String> requestedOperations;
    private Integer confidence;
    private String referral;
    private Map<String, Object> entityInfo;
    private List<Map<String, Object>> fields;
    private List<Map<String, Object>> entityDocumentation;
    private List<Map<String, Object>> entityRelationships;
    private List<OperationResult> operations;

    public QueryResult() {}

    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }
    public List<String> getRequestedOperations() { return requestedOperations; }
    public void setRequestedOperations(List<String> requestedOperations) { 
      this.requestedOperations = requestedOperations; 
    }
    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }
    public String getReferral() { return referral; }
    public void setReferral(String referral) { this.referral = referral; }
    public Map<String, Object> getEntityInfo() { return entityInfo; }
    public void setEntityInfo(Map<String, Object> entityInfo) { this.entityInfo = entityInfo; }
    public List<Map<String, Object>> getFields() { return fields; }
    public void setFields(List<Map<String, Object>> fields) { this.fields = fields; }
    public List<Map<String, Object>> getEntityDocumentation() { return entityDocumentation; }
    public void setEntityDocumentation(List<Map<String, Object>> entityDocumentation) { 
      this.entityDocumentation = entityDocumentation; 
    }
    public List<Map<String, Object>> getEntityRelationships() { return entityRelationships; }
    public void setEntityRelationships(List<Map<String, Object>> entityRelationships) {
      this.entityRelationships = entityRelationships;
    }
    public List<OperationResult> getOperations() { return operations; }
    public void setOperations(List<OperationResult> operations) { this.operations = operations; }
  }

  /**
   * Operation result containing complete operation information.
   */
  public static class OperationResult {
    private String operationId;
    private String method;
    private String path;
    private String summary;
    private String description;
    private String category;
    private String signature;
    private String requestSchema;
    private String responseSchema;
    private List<String> tags;
    private List<Map<String, Object>> examples;
    private List<Map<String, Object>> documentation;

    public OperationResult() {}

    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getRequestSchema() { return requestSchema; }
    public void setRequestSchema(String requestSchema) { this.requestSchema = requestSchema; }
    public String getResponseSchema() { return responseSchema; }
    public void setResponseSchema(String responseSchema) { this.responseSchema = responseSchema; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public List<Map<String, Object>> getExamples() { return examples; }
    public void setExamples(List<Map<String, Object>> examples) { this.examples = examples; }
    public List<Map<String, Object>> getDocumentation() { return documentation; }
    public void setDocumentation(List<Map<String, Object>> documentation) { 
      this.documentation = documentation; 
    }
  }

  /**
   * Operation-oriented result item with kind, content, and reference.
   */
  public static class OperationOrientedItem {
    private String kind;
    private String content;
    private String ref;

    public OperationOrientedItem() {}

    public OperationOrientedItem(String kind, String content, String ref) {
      this.kind = kind;
      this.content = content;
      this.ref = ref;
    }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
  }

  /**
   * Operation-oriented result grouped by operation.
   */
  public static class OperationOrientedGroup {
    private String operation;
    private List<OperationOrientedItem> items;

    public OperationOrientedGroup() {}

    public OperationOrientedGroup(String operation, List<OperationOrientedItem> items) {
      this.operation = operation;
      this.items = items;
    }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public List<OperationOrientedItem> getItems() { return items; }
    public void setItems(List<OperationOrientedItem> items) { this.items = items; }
  }

  /**
   * Execute an optimized graph query to retrieve all relevant information for the given context.
   *
   * <p>This method uses a single optimized AQL query to traverse the graph and retrieve:
   * <ul>
   *   <li>Entity information</li>
   *   <li>Entity fields</li>
   *   <li>Operations matching the requested categories</li>
   *   <li>Examples for each operation</li>
   *   <li>Documentation for operations and entities</li>
   * </ul>
   *
   * @param request the query request containing context items
   * @return list of query results, one per context item
   */
  public List<QueryResult> query(QueryRequest request) {
    if (request == null || request.getContext() == null || request.getContext().isEmpty()) {
      log.warn("Empty query request received");
      return Collections.emptyList();
    }

    ensureDriverReady();

    log.info("Processing graph query with {} context items", request.getContext().size());

    List<QueryResult> results = new ArrayList<>();

    for (ContextItem contextItem : request.getContext()) {
      try {
        QueryResult result = queryContextItem(contextItem);
        results.add(result);
      } catch (Exception e) {
        log.error("Failed to query context item: {}", contextItem.getEntity(), e);
        // Add empty result for this context item to maintain order
        QueryResult errorResult = new QueryResult();
        errorResult.setEntity(contextItem.getEntity());
        errorResult.setRequestedOperations(contextItem.getOperations());
        errorResult.setConfidence(contextItem.getConfidence());
        errorResult.setReferral(contextItem.getReferral());
        errorResult.setOperations(Collections.emptyList());
        results.add(errorResult);
      }
    }

    return results;
  }

  /**
   * Execute a graph query and return operation-oriented results grouped by operation.
   *
   * <p>This method returns results focused on operations, grouped by operation name.
   * Each group contains items with:
   * <ul>
   *   <li>kind: "doc", "signature", or "example"</li>
   *   <li>content: markdown-formatted content</li>
   *   <li>ref: reference URL or source file</li>
   * </ul>
   *
   * @param request the query request containing context items
   * @return list of operation-oriented groups
   */
  public List<OperationOrientedGroup> queryOperationOriented(QueryRequest request) {
    List<QueryResult> results = query(request);
    return flattenToOperationOriented(results);
  }

  /**
   * Query operations based on context items and return prompt-ready results.
   *
   * <p>This method queries operations matching the context items (entity + categories) and returns
   * full prompt-ready data for each operation. All data is gathered from the context query results,
   * ensuring we only use context items (entity + categories) and never query operations directly.
   *
   * @param request the query request containing context items
   * @return list of operation prompt results, one per operation found
   */
  public List<OperationPromptResult> queryOperationsForPrompt(QueryRequest request) {
    ensureDriverReady();

    if (request == null || request.getContext() == null || request.getContext().isEmpty()) {
      log.warn("Empty query request received");
      return Collections.emptyList();
    }

    log.info("Querying operations for prompt generation with {} context items", request.getContext().size());

    List<OperationPromptResult> results = new ArrayList<>();
    Set<String> seenOperationKeys = new HashSet<>();

    // Query operations using context items (entity + categories)
    List<QueryResult> queryResults = query(request);

    // Build OperationPromptResult from QueryResult data (no direct operation queries)
    for (QueryResult queryResult : queryResults) {
      if (queryResult.getOperations() == null || queryResult.getOperations().isEmpty()) {
        continue;
      }

      // Get entity info and fields from the context query result
      Map<String, Object> entityInfo = queryResult.getEntityInfo();
      List<Map<String, Object>> entityFields = queryResult.getFields();

      for (OperationResult op : queryResult.getOperations()) {
        String operationId = op.getOperationId();
        if (operationId == null || operationId.isBlank()) {
          continue;
        }

        // Build operation key for deduplication
        String operationKey = "op|" + operationId;
        
        // Skip if we've already processed this operation
        if (seenOperationKeys.contains(operationKey) || seenOperationKeys.contains(operationId)) {
          continue;
        }

        try {
          // Use direct operation query to get all related entities and fields
          // This ensures we get fields from all entities related to the operation, not just the context entity
          OperationPromptResult promptResult = queryOperationForPrompt(operationId);
          if (promptResult == null) {
            // Fallback to context-based building if direct query fails
            promptResult = buildOperationPromptResultFromContext(op, queryResult);
          }
          if (promptResult != null) {
            results.add(promptResult);
            seenOperationKeys.add(operationKey);
            seenOperationKeys.add(operationId);
          }
        } catch (Exception e) {
          log.warn("Failed to query operation {} for prompt, trying context-based fallback", operationId, e);
          try {
            // Fallback to context-based building
            OperationPromptResult promptResult = buildOperationPromptResultFromContext(op, queryResult);
            if (promptResult != null) {
              results.add(promptResult);
              seenOperationKeys.add(operationKey);
              seenOperationKeys.add(operationId);
            }
          } catch (Exception e2) {
            log.warn("Failed to build prompt result for operation {} from context, skipping", operationId, e2);
          }
        }
      }
    }

    log.info("Found {} operations for prompt generation from context items", results.size());
    return results;
  }

  /**
   * Query graph diagnostics for a specific operation to inspect the graph structure.
   *
   * <p>This method helps verify:
   * <ul>
   *   <li>What entities are connected to the operation</li>
   *   <li>What edge types connect entities to operations</li>
   *   <li>How many fields each entity has</li>
   *   <li>What entity-to-entity relationships exist</li>
   * </ul>
   *
   * @param operationKey the operation key (e.g., "op|querySalesData") or operationId
   * @return diagnostic information about the graph structure, or null if operation not found
   */
  public Map<String, Object> queryGraphDiagnostics(String operationKey) {
    ensureDriverReady();

    log.debug("Querying graph diagnostics for operation: {}", operationKey);
    try {
      return queryDriver.queryGraphDiagnostics(operationKey);
    } catch (Exception e) {
      log.error("Failed to query graph diagnostics for operation: {}", operationKey, e);
      throw new IoException("Graph diagnostic query failed for operation: " + operationKey, e);
    }
  }

  /**
   * Query a specific operation to gather all data needed for prompt generation.
   *
   * <p>This method retrieves:
   * <ul>
   *   <li>Operation basic information (name, description, signature)</li>
   *   <li>Operation documentation</li>
   *   <li>Operation relationships/connections (entity relationships)</li>
   *   <li>Operation examples</li>
   *   <li>Operation input (fields organized by entity, operators, aggregation functions)</li>
   *   <li>Operation output (structure and examples)</li>
   * </ul>
   *
   * @param operationKey the operation key (e.g., "op|querySalesData") or operationId
   * @return operation prompt result with all necessary data, or null if operation not found
   */
  public OperationPromptResult queryOperationForPrompt(String operationKey) {
    ensureDriverReady();

    log.debug("Querying operation for prompt generation: {}", operationKey);

    try {
      Map<String, Object> rawResult = queryDriver.queryOperationForPrompt(operationKey);

      if (rawResult == null) {
        log.warn("No results found for operation: {}", operationKey);
        return null;
      }

      return parseOperationPromptResult(rawResult);

    } catch (Exception e) {
      log.error("Error executing graph query for operation: {}", operationKey, e);
      throw new IoException("Graph query failed for operation: " + operationKey, e);
    }
  }

  /**
   * Flatten query results into operation-oriented groups.
   *
   * <p>Items are grouped by operation, with only doc, signature, and example kinds.
   *
   * @param results the query results to flatten
   * @return list of operation-oriented groups
   */
  @SuppressWarnings("unchecked")
  public List<OperationOrientedGroup> flattenToOperationOriented(List<QueryResult> results) {
    Map<String, List<OperationOrientedItem>> operationGroups = new HashMap<>();
    Set<String> seenDocKeys = new HashSet<>();

    for (QueryResult result : results) {
      if (result.getOperations() == null) {
        continue;
      }

      // Process each operation
      for (OperationResult op : result.getOperations()) {
        String operationName = op.getOperationId();
        if (operationName == null || operationName.isBlank()) {
          operationName = buildOperationName(op.getMethod(), op.getPath());
        }

        // Get or create group for this operation
        List<OperationOrientedItem> groupItems = operationGroups.computeIfAbsent(operationName, k -> new ArrayList<>());

        // Add operation signature
        if (op.getSignature() != null && !op.getSignature().isBlank()) {
          String ref = buildOperationRef(op.getMethod(), op.getPath());
          groupItems.add(new OperationOrientedItem("signature", op.getSignature(), ref));
        }

        // Add examples
        if (op.getExamples() != null) {
          for (Map<String, Object> example : op.getExamples()) {
            String ref = buildOperationRef(op.getMethod(), op.getPath());

            // Build example content from request/response
            StringBuilder exampleContent = new StringBuilder();
            String name = (String) example.get("name");
            if (name != null && !name.isBlank()) {
              exampleContent.append("**").append(name).append("**\n\n");
            }

            String description = (String) example.get("description");
            if (description != null && !description.isBlank()) {
              exampleContent.append(description).append("\n\n");
            }

            String requestBody = (String) example.get("requestBody");
            if (requestBody != null && !requestBody.isBlank()) {
              exampleContent.append("**Request:**\n```json\n").append(requestBody).append("\n```\n\n");
            }

            String responseBody = (String) example.get("responseBody");
            if (responseBody != null && !responseBody.isBlank()) {
              exampleContent.append("**Response:**\n```json\n").append(responseBody).append("\n```\n");
            }

            if (exampleContent.length() > 0) {
              groupItems.add(new OperationOrientedItem("example", exampleContent.toString(), ref));
            }
          }
        }

        // Add operation documentation (deduplicated by key)
        if (op.getDocumentation() != null) {
          for (Map<String, Object> doc : op.getDocumentation()) {
            String docKey = (String) doc.get("_key");
            if (docKey == null) {
              docKey = (String) doc.get("key");
            }

            // Skip if we've already seen this documentation node
            if (docKey != null && seenDocKeys.contains(docKey)) {
              continue;
            }

            String content = (String) doc.get("content");
            if (content != null && !content.isBlank()) {
              if (docKey != null) {
                seenDocKeys.add(docKey);
              }
              String ref = (String) doc.get("sourceFile");
              if (ref == null || ref.isBlank()) {
                ref = buildOperationRef(op.getMethod(), op.getPath());
              }
              groupItems.add(new OperationOrientedItem("doc", content, ref));
            }
          }
        }
      }
    }

    // Convert to list of groups
    return operationGroups.entrySet().stream()
        .map(entry -> new OperationOrientedGroup(entry.getKey(), entry.getValue()))
        .collect(Collectors.toList());
  }

  /**
   * Build an operation name from method and path.
   */
  private String buildOperationName(String method, String path) {
    if (method != null && !method.isBlank() && path != null && !path.isBlank()) {
      return method.toUpperCase() + " " + path;
    }
    if (path != null && !path.isBlank()) {
      return path;
    }
    if (method != null && !method.isBlank()) {
      return method.toUpperCase();
    }
    return "unknown";
  }

  /**
   * Build a reference URL for an operation.
   */
  private String buildOperationRef(String method, String path) {
    if (path != null && !path.isBlank()) {
      return path.startsWith("/") ? path : "/" + path;
    }
    if (method != null && !method.isBlank()) {
      return String.format("/%s", method.toLowerCase());
    }
    return "/";
  }

  /**
   * Query a single context item using an optimized AQL traversal.
   *
   * @param contextItem the context item to query
   * @return query result with all relevant information
   */
  private QueryResult queryContextItem(ContextItem contextItem) {
    String entityName = contextItem.getEntity();
    List<String> requestedCategories = contextItem.getOperations();

    log.debug("Querying entity '{}' for operations: {}", entityName, requestedCategories);

    try {
      Map<String, Object> rawResult = queryDriver.queryContext(entityName, requestedCategories);

      if (rawResult == null) {
        log.warn("No results found for entity: {}", entityName);
        return createEmptyResult(contextItem);
      }

      return parseQueryResult(rawResult, contextItem);

    } catch (Exception e) {
      log.error("Error executing graph query for entity: {}", entityName, e);
      throw new IoException("Graph query failed for entity: " + entityName, e);
    }
  }

  /**
   * Parse the raw query result into a structured QueryResult object.
   *
   * @param rawResult the raw result from ArangoDB
   * @param contextItem the original context item
   * @return structured query result
   */
  @SuppressWarnings("unchecked")
  private QueryResult parseQueryResult(Map<String, Object> rawResult, ContextItem contextItem) {
    QueryResult result = new QueryResult();
    
    result.setEntity(contextItem.getEntity());
    result.setRequestedOperations(contextItem.getOperations());
    result.setConfidence(contextItem.getConfidence());
    result.setReferral(contextItem.getReferral());

    // Parse entity info
    Map<String, Object> entityMap = (Map<String, Object>) rawResult.get("entity");
    if (entityMap != null) {
      // Remove internal ArangoDB fields
      entityMap = new HashMap<>(entityMap);
      entityMap.remove("_id");
      entityMap.remove("_rev");
      result.setEntityInfo(entityMap);
    }

    // Parse fields
    List<Map<String, Object>> fieldsList = (List<Map<String, Object>>) rawResult.get("fields");
    if (fieldsList != null) {
      result.setFields(
          fieldsList.stream().map(this::cleanInternalFields).collect(Collectors.toList()));
    } else {
      result.setFields(Collections.emptyList());
    }

    // Parse entity documentation
    List<Map<String, Object>> entityDocsList = (List<Map<String, Object>>) rawResult.get("entityDocumentation");
    if (entityDocsList != null) {
      result.setEntityDocumentation(
          entityDocsList.stream().map(this::cleanInternalFields).collect(Collectors.toList()));
    } else {
      result.setEntityDocumentation(Collections.emptyList());
    }

    // Parse entity relationships
    List<Map<String, Object>> entityRelationshipsList =
        (List<Map<String, Object>>) rawResult.get("entityRelationships");
    if (entityRelationshipsList != null) {
      result.setEntityRelationships(entityRelationshipsList);
    } else {
      result.setEntityRelationships(Collections.emptyList());
    }

    // Parse operations
    List<Map<String, Object>> operationsList =
        (List<Map<String, Object>>) rawResult.get("operations");
    if (operationsList != null) {
      List<OperationResult> operations =
          operationsList.stream().map(this::parseOperation).collect(Collectors.toList());
      result.setOperations(operations);
    } else {
      result.setOperations(Collections.emptyList());
    }

    return result;
  }

  /**
   * Parse an operation map into an OperationResult object.
   *
   * @param opMap the operation map from query result
   * @return structured operation result
   */
  @SuppressWarnings("unchecked")
  private OperationResult parseOperation(Map<String, Object> opMap) {
    OperationResult op = new OperationResult();
    
    op.setOperationId((String) opMap.get("operationId"));
    op.setMethod((String) opMap.get("method"));
    op.setPath((String) opMap.get("path"));
    op.setSummary((String) opMap.get("summary"));
    op.setDescription((String) opMap.get("description"));
    op.setCategory((String) opMap.get("category"));
    op.setSignature((String) opMap.get("signature"));
    op.setRequestSchema((String) opMap.get("requestSchema"));
    op.setResponseSchema((String) opMap.get("responseSchema"));
    op.setTags((List<String>) opMap.get("tags"));

    // Parse examples
    List<Map<String, Object>> examplesList = (List<Map<String, Object>>) opMap.get("examples");
    if (examplesList != null) {
      op.setExamples(
          examplesList.stream().map(this::cleanInternalFields).collect(Collectors.toList()));
    } else {
      op.setExamples(Collections.emptyList());
    }

    // Parse documentation
    List<Map<String, Object>> docsList = (List<Map<String, Object>>) opMap.get("documentation");
    if (docsList != null) {
      op.setDocumentation(
          docsList.stream().map(this::cleanInternalFields).collect(Collectors.toList()));
    } else {
      op.setDocumentation(Collections.emptyList());
    }

    return op;
  }

  /**
   * Remove internal driver-specific fields from a map (e.g., ArangoDB metadata).
   *
   * @param map the map to clean
   * @return cleaned map
   */
  private Map<String, Object> cleanInternalFields(Map<String, Object> map) {
    Map<String, Object> cleaned = new HashMap<>(map);
    cleaned.remove("_id");
    cleaned.remove("_rev");
    cleaned.remove("_key");
    return cleaned;
  }

  /**
   * Create an empty result for a context item when no data is found.
   *
   * @param contextItem the context item
   * @return empty query result
   */
  private QueryResult createEmptyResult(ContextItem contextItem) {
    QueryResult result = new QueryResult();
    result.setEntity(contextItem.getEntity());
    result.setRequestedOperations(contextItem.getOperations());
    result.setConfidence(contextItem.getConfidence());
    result.setReferral(contextItem.getReferral());
    result.setFields(Collections.emptyList());
    result.setEntityDocumentation(Collections.emptyList());
    result.setEntityRelationships(Collections.emptyList());
    result.setOperations(Collections.emptyList());
    return result;
  }

  /**
   * Build OperationPromptResult from context query data (OperationResult + QueryResult).
   *
   * <p>This method constructs prompt-ready data from the context query results without making
   * additional direct operation queries. It uses the entity and operation data already retrieved
   * from the context query.
   *
   * @param op the operation result from context query
   * @param queryResult the full query result containing entity and field data
   * @return operation prompt result built from context data
   */
  @SuppressWarnings("unchecked")
  private OperationPromptResult buildOperationPromptResultFromContext(
      OperationResult op, QueryResult queryResult) {
    OperationPromptResult result = new OperationPromptResult();

    // Set operation basic info from OperationResult
    String operationId = op.getOperationId();
    result.setOperationId(operationId);
    result.setOperationName(operationId != null ? operationId : "unknown");
    result.setDescription(op.getDescription());
    result.setSignature(op.getSignature());

    // Set documentation from OperationResult
    if (op.getDocumentation() != null) {
      result.setDocumentation(op.getDocumentation());
    }

    // Set examples from OperationResult
    if (op.getExamples() != null) {
      result.setExamples(op.getExamples());
    }

    // Build relationships from entity relationships in query result
    if (queryResult.getEntityRelationships() != null) {
      result.setRelationships(queryResult.getEntityRelationships());
    } else {
      result.setRelationships(Collections.emptyList());
    }

    // Build input from entity fields
    OperationPromptResult.OperationInput input = new OperationPromptResult.OperationInput();
    
    // Group fields by entity (using the entity from queryResult)
    List<OperationPromptResult.EntityFieldGroup> fieldGroups = new ArrayList<>();
    if (queryResult.getFields() != null && !queryResult.getFields().isEmpty()) {
      OperationPromptResult.EntityFieldGroup group = new OperationPromptResult.EntityFieldGroup();
      String entityName = queryResult.getEntity();
      group.setEntityName(entityName != null ? entityName : "unknown");
      
      if (queryResult.getEntityInfo() != null) {
        String entityDesc = (String) queryResult.getEntityInfo().get("description");
        group.setEntityDescription(entityDesc != null ? entityDesc : "");
      }
      
      List<OperationPromptResult.FieldInfo> fields = new ArrayList<>();
      for (Map<String, Object> fieldMap : queryResult.getFields()) {
        OperationPromptResult.FieldInfo fieldInfo = new OperationPromptResult.FieldInfo();
        fieldInfo.setName((String) fieldMap.get("name"));
        fieldInfo.setType((String) fieldMap.get("fieldType"));
        fieldInfo.setDescription((String) fieldMap.get("description"));
        fieldInfo.setExample((String) fieldMap.get("example"));
        List<String> possibleValues = (List<String>) fieldMap.get("possibleValues");
        if (possibleValues != null) {
          fieldInfo.setPossibleValues(possibleValues);
        }
        fields.add(fieldInfo);
      }
      group.setFields(fields);
      fieldGroups.add(group);
    }
    input.setFields(fieldGroups);

    // Extract operators and aggregation functions from operation documentation
    List<String> operators = Collections.emptyList();
    List<String> aggregationFunctions = Collections.emptyList();
    
    if (op.getDocumentation() != null) {
      for (Map<String, Object> doc : op.getDocumentation()) {
        String docType = (String) doc.get("docType");
        String content = (String) doc.get("content");
        
        if (content != null && !content.isBlank()) {
          if ("operators_reference".equals(docType) || 
              content.toLowerCase().contains("operator")) {
            operators = extractOperatorsFromDoc(doc);
            if (!operators.isEmpty()) {
              break;
            }
          }
          if ("aggregation_reference".equals(docType) || 
              "functions_reference".equals(docType) ||
              content.toLowerCase().contains("aggregation")) {
            aggregationFunctions = extractAggregationFunctionsFromDoc(doc);
            if (!aggregationFunctions.isEmpty()) {
              break;
            }
          }
        }
      }
    }
    input.setOperators(operators);
    input.setAggregationFunctions(aggregationFunctions);

    result.setInput(input);

    // Build output from OperationResult
    OperationPromptResult.OperationOutput output = new OperationPromptResult.OperationOutput();
    if (op.getResponseSchema() != null) {
      output.setStructure(op.getResponseSchema());
    }
    if (op.getExamples() != null) {
      output.setExamples(op.getExamples());
    }
    result.setOutput(output);

    return result;
  }

  /**
   * Parse the raw query result into a structured OperationPromptResult object.
   *
   * <p>This method is used when querying operations directly (e.g., via operationId).
   * For context-based queries, use {@link #buildOperationPromptResultFromContext}.
   *
   * @param rawResult the raw result from ArangoDB
   * @return structured operation prompt result
   */
  @SuppressWarnings("unchecked")
  private OperationPromptResult parseOperationPromptResult(Map<String, Object> rawResult) {
    OperationPromptResult result = new OperationPromptResult();

    // Parse operation
    Map<String, Object> operationMap = (Map<String, Object>) rawResult.get("operation");
    if (operationMap != null) {
      String operationId = (String) operationMap.get("operationId");
      result.setOperationId(operationId);
      // Use operationId as name, or fallback to summary/description
      String operationName = operationId;
      if (operationName == null || operationName.isBlank()) {
        operationName = (String) operationMap.get("summary");
        if (operationName == null || operationName.isBlank()) {
          operationName = (String) operationMap.get("description");
        }
      }
      result.setOperationName(operationName != null ? operationName : "unknown");
      result.setDescription((String) operationMap.get("description"));
      result.setSignature((String) operationMap.get("signature"));
    }

    // Parse documentation
    List<Map<String, Object>> docsList =
        (List<Map<String, Object>>) rawResult.get("documentation");
    if (docsList != null) {
      result.setDocumentation(
          docsList.stream().map(this::cleanInternalFields).collect(Collectors.toList()));
    }

    // Parse relationships
    List<Map<String, Object>> relationshipsList =
        (List<Map<String, Object>>) rawResult.get("relationships");
    if (relationshipsList != null) {
      result.setRelationships(
          relationshipsList.stream().map(this::cleanInternalFields).collect(Collectors.toList()));
    }

    // Parse examples
    List<Map<String, Object>> examplesList =
        (List<Map<String, Object>>) rawResult.get("examples");
    if (examplesList != null) {
      result.setExamples(
          examplesList.stream().map(this::cleanInternalFields).collect(Collectors.toList()));
    }

    // Parse input
    OperationPromptResult.OperationInput input = new OperationPromptResult.OperationInput();
    
    // Parse entity fields
    List<Map<String, Object>> entityFieldsList =
        (List<Map<String, Object>>) rawResult.get("entityFields");
    if (entityFieldsList != null) {
      List<OperationPromptResult.EntityFieldGroup> fieldGroups = new ArrayList<>();
      for (Map<String, Object> entityFieldGroup : entityFieldsList) {
        OperationPromptResult.EntityFieldGroup group =
            new OperationPromptResult.EntityFieldGroup();
        group.setEntityName((String) entityFieldGroup.get("entityName"));
        group.setEntityDescription((String) entityFieldGroup.get("entityDescription"));
        
        List<Map<String, Object>> fieldsList =
            (List<Map<String, Object>>) entityFieldGroup.get("fields");
        if (fieldsList != null) {
          List<OperationPromptResult.FieldInfo> fields = new ArrayList<>();
          for (Map<String, Object> fieldMap : fieldsList) {
            OperationPromptResult.FieldInfo fieldInfo =
                new OperationPromptResult.FieldInfo();
            fieldInfo.setName((String) fieldMap.get("name"));
            fieldInfo.setType((String) fieldMap.get("type"));
            fieldInfo.setDescription((String) fieldMap.get("description"));
            fieldInfo.setExample((String) fieldMap.get("example"));
            List<String> possibleValues = (List<String>) fieldMap.get("possibleValues");
            if (possibleValues != null) {
              fieldInfo.setPossibleValues(possibleValues);
            }
            fields.add(fieldInfo);
          }
          group.setFields(fields);
        }
        fieldGroups.add(group);
      }
      input.setFields(fieldGroups);
    }

    // Parse operators and aggregation functions from documentation
    Map<String, Object> operatorsDoc = (Map<String, Object>) rawResult.get("operatorsDoc");
    Map<String, Object> aggregationDoc =
        (Map<String, Object>) rawResult.get("aggregationDoc");
    
    // Extract operators from documentation content
    List<String> operators = extractOperatorsFromDoc(operatorsDoc);
    if (operators.isEmpty()) {
      // Fallback: try to extract from all documentation
      List<Map<String, Object>> allDocs =
          (List<Map<String, Object>>) rawResult.get("documentation");
      if (allDocs != null) {
        for (Map<String, Object> doc : allDocs) {
          String docType = (String) doc.get("docType");
          if ("operators_reference".equals(docType) || 
              (doc.get("content") != null && 
               doc.get("content").toString().toLowerCase().contains("operator"))) {
            operators = extractOperatorsFromDoc(doc);
            if (!operators.isEmpty()) {
              break;
            }
          }
        }
      }
    }
    input.setOperators(operators);
    
    // Extract aggregation functions from documentation content
    List<String> aggregationFunctions = extractAggregationFunctionsFromDoc(aggregationDoc);
    if (aggregationFunctions.isEmpty()) {
      // Fallback: try to extract from all documentation
      List<Map<String, Object>> allDocs =
          (List<Map<String, Object>>) rawResult.get("documentation");
      if (allDocs != null) {
        for (Map<String, Object> doc : allDocs) {
          String docType = (String) doc.get("docType");
          if ("aggregation_reference".equals(docType) || 
              "functions_reference".equals(docType) ||
              (doc.get("content") != null && 
               doc.get("content").toString().toLowerCase().contains("aggregation"))) {
            aggregationFunctions = extractAggregationFunctionsFromDoc(doc);
            if (!aggregationFunctions.isEmpty()) {
              break;
            }
          }
        }
      }
    }
    input.setAggregationFunctions(aggregationFunctions);

    result.setInput(input);

    // Parse output
    OperationPromptResult.OperationOutput output =
        new OperationPromptResult.OperationOutput();
    String outputSchema = (String) rawResult.get("outputSchema");
    if (outputSchema != null) {
      output.setStructure(outputSchema);
    }
    
    // Output examples are the same as operation examples
    if (examplesList != null) {
      output.setExamples(
          examplesList.stream().map(this::cleanInternalFields).collect(Collectors.toList()));
    }

    result.setOutput(output);

    return result;
  }

  /**
   * Shutdown the underlying driver and release resources.
   */
  public void shutdown() {
    if (queryDriver != null) {
      queryDriver.shutdown();
      driverInitialized = false;
    }
  }

  @Override
  public void close() {
    shutdown();
  }

  /** @return configured driver name (lowercase). */
  public String getDriverName() {
    return driverName;
  }

  private void ensureDriverReady() {
    if (queryDriver == null) {
      throw new IoException(
          "Graph query driver '%s' is not available or not configured".formatted(driverName));
    }

    if (!driverInitialized) {
      queryDriver.initialize();
      driverInitialized = queryDriver.isInitialized();
    }

    if (!driverInitialized) {
      throw new IoException("Failed to initialize graph query driver: " + driverName);
    }
  }

  private GraphQueryDriver createDriver(String name) {
    if (name == null) {
      log.warn("Graph query driver name is null; queries are disabled");
      return null;
    }

    return switch (name) {
      case "arangodb" -> new ArangoQueryDriver(oneMcp, handbookName);
      default -> {
        log.warn("Graph query driver '{}' not supported yet; queries are disabled", name);
        yield null;
      }
    };
  }

  private String resolveDriverName(OneMcp oneMcp) {
    Configuration config = oneMcp.configuration();
    String queryKey = "graph.query.driver";
    String indexingKey = "graph.indexing.driver";

    String driver =
        config.containsKey(queryKey)
            ? config.getString(queryKey, "arangodb")
            : config.getString(indexingKey, "arangodb");

    return driver != null ? driver.toLowerCase(Locale.ROOT) : "arangodb";
  }

  /**
   * Extract operator names from documentation content.
   *
   * @param doc the documentation map
   * @return list of operator names
   */
  @SuppressWarnings("unchecked")
  private List<String> extractOperatorsFromDoc(Map<String, Object> doc) {
    if (doc == null) {
      return Collections.emptyList();
    }

    String content = (String) doc.get("content");
    if (content == null || content.isBlank()) {
      return Collections.emptyList();
    }

    // Unescape content (handle \\n, \\t, etc.)
    content = content.replace("\\n", "\n").replace("\\t", "\t");

    List<String> operators = new ArrayList<>();
    String lowerContent = content.toLowerCase();

    // Common operators list
    String[] commonOperators = {
      "equals", "not_equals", "greater_than", "greater_than_or_equal",
      "less_than", "less_than_or_equal", "contains", "not_contains",
      "starts_with", "ends_with", "in", "not_in", "between",
      "is_null", "is_not_null"
    };

    // Check if content mentions these operators (with various formats)
    for (String op : commonOperators) {
      if (lowerContent.contains("\"" + op + "\"") || 
          lowerContent.contains("`" + op + "`") ||
          lowerContent.contains("- `" + op + "`") ||
          lowerContent.contains("- \"" + op + "\"") ||
          lowerContent.contains("- " + op) ||
          lowerContent.contains(op + " -") ||
          lowerContent.contains(op + ":") ||
          lowerContent.contains(op + " -")) {
        if (!operators.contains(op)) {
          operators.add(op);
        }
      }
    }

    // Try to extract from markdown lists (e.g., "- `equals`: Exact match")
    // Pattern matches: - `operator_name`: or - "operator_name": or - operator_name:
    java.util.regex.Pattern pattern = 
        java.util.regex.Pattern.compile("-\\s*[`\"]([\\w_]+)[`\"]\\s*:");
    java.util.regex.Matcher matcher = pattern.matcher(content);
    while (matcher.find()) {
      String match = matcher.group(1);
      if (!operators.contains(match)) {
        operators.add(match);
      }
    }

    // Also try simpler pattern for operators in quotes or backticks
    pattern = java.util.regex.Pattern.compile("[`\"](equals|not_equals|greater_than|greater_than_or_equal|less_than|less_than_or_equal|contains|not_contains|starts_with|ends_with|in|not_in|between|is_null|is_not_null)[`\"]");
    matcher = pattern.matcher(content);
    while (matcher.find()) {
      String match = matcher.group(1);
      if (!operators.contains(match)) {
        operators.add(match);
      }
    }

    // If we found operators, return them; otherwise return all common operators as fallback
    if (!operators.isEmpty()) {
      return operators;
    }

    // Fallback: if documentation mentions operators but we didn't extract them, return all common ones
    if (lowerContent.contains("operator") || lowerContent.contains("filter")) {
      return java.util.Arrays.asList(commonOperators);
    }

    return Collections.emptyList();
  }

  /**
   * Extract aggregation function names from documentation content.
   *
   * @param doc the documentation map
   * @return list of aggregation function names
   */
  @SuppressWarnings("unchecked")
  private List<String> extractAggregationFunctionsFromDoc(Map<String, Object> doc) {
    if (doc == null) {
      return Collections.emptyList();
    }

    String content = (String) doc.get("content");
    if (content == null || content.isBlank()) {
      return Collections.emptyList();
    }

    // Unescape content (handle \\n, \\t, etc.)
    content = content.replace("\\n", "\n").replace("\\t", "\t");

    List<String> functions = new ArrayList<>();
    String lowerContent = content.toLowerCase();

    // Common aggregation functions
    String[] commonFunctions = {
      "sum", "avg", "count", "min", "max", "median", "stddev", "variance"
    };

    // Check if content mentions these functions (with various formats)
    for (String func : commonFunctions) {
      if (lowerContent.contains("\"" + func + "\"") || 
          lowerContent.contains("`" + func + "`") ||
          lowerContent.contains("- `" + func + "`") ||
          lowerContent.contains("- \"" + func + "\"") ||
          lowerContent.contains("- " + func) ||
          lowerContent.contains(func + " -") ||
          lowerContent.contains(func + ":")) {
        if (!functions.contains(func)) {
          functions.add(func);
        }
      }
    }

    // Try to extract from markdown lists (e.g., "- `sum`: Calculates the total")
    java.util.regex.Pattern pattern = 
        java.util.regex.Pattern.compile("-\\s*[`\"](sum|avg|count|min|max|median|stddev|variance)[`\"]\\s*:");
    java.util.regex.Matcher matcher = pattern.matcher(content);
    while (matcher.find()) {
      String match = matcher.group(1);
      if (!functions.contains(match)) {
        functions.add(match);
      }
    }

    // Also try simpler pattern for functions in quotes or backticks
    pattern = java.util.regex.Pattern.compile("[`\"](sum|avg|count|min|max|median|stddev|variance)[`\"]");
    matcher = pattern.matcher(content);
    while (matcher.find()) {
      String match = matcher.group(1);
      if (!functions.contains(match)) {
        functions.add(match);
      }
    }

    // If we found functions, return them; otherwise return all common functions as fallback
    if (!functions.isEmpty()) {
      return functions;
    }

    // Fallback: if documentation mentions aggregation but we didn't extract them, return all common ones
    if (lowerContent.contains("aggregation") || lowerContent.contains("function")) {
      return java.util.Arrays.asList(commonFunctions);
    }

    return Collections.emptyList();
  }
}
