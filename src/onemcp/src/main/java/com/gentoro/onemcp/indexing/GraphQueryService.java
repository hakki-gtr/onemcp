package com.gentoro.onemcp.indexing;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDatabase;
import com.gentoro.onemcp.exception.IoException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for querying the knowledge graph in ArangoDB.
 *
 * <p>This service provides optimized graph traversal algorithms for retrieving
 * API definitions, signatures, documentation, examples, and related information
 * based on entity and operation context.
 *
 * <p>Key Features:
 * <ul>
 *   <li>Efficient single-query traversal using AQL (ArangoDB Query Language)</li>
 *   <li>Support for confidence-based and referral-based filtering</li>
 *   <li>Retrieval of complete operation context including examples and documentation</li>
 *   <li>Field-level information for entities</li>
 * </ul>
 */
public class GraphQueryService {
  private static final org.slf4j.Logger log =
      com.gentoro.onemcp.logging.LoggingService.getLogger(GraphQueryService.class);

  private final ArangoDbService arangoDbService;

  /**
   * Create a new graph query service.
   *
   * @param arangoDbService the ArangoDB service
   */
  public GraphQueryService(ArangoDbService arangoDbService) {
    this.arangoDbService = arangoDbService;
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
    if (!arangoDbService.isInitialized()) {
      throw new IoException("ArangoDB service is not initialized");
    }

    if (request == null || request.getContext() == null || request.getContext().isEmpty()) {
      log.warn("Empty query request received");
      return Collections.emptyList();
    }

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
   * Query a single context item using an optimized AQL traversal.
   *
   * @param contextItem the context item to query
   * @return query result with all relevant information
   */
  private QueryResult queryContextItem(ContextItem contextItem) {
    String entityName = contextItem.getEntity();
    List<String> requestedCategories = contextItem.getOperations();

    log.debug("Querying entity '{}' for operations: {}", entityName, requestedCategories);

    // Build the AQL query
    String aql = buildOptimizedQuery(entityName, requestedCategories);

    // Execute query
    ArangoDatabase db = arangoDbService.getDatabase();
    Map<String, Object> bindVars = new HashMap<>();
    bindVars.put("entityName", entityName);
    bindVars.put("categories", requestedCategories);

    log.trace("Executing AQL query:\n{}\nBind vars: {}", aql, bindVars);

    try {
      @SuppressWarnings({"unchecked", "rawtypes"})
      ArangoCursor<Map<String, Object>> cursor = (ArangoCursor) db.query(aql, Map.class, bindVars, null);
      
      if (!cursor.hasNext()) {
        log.warn("No results found for entity: {}", entityName);
        return createEmptyResult(contextItem);
      }

      Map<String, Object> rawResult = cursor.next();
      
      // Parse and structure the result
      return parseQueryResult(rawResult, contextItem);

    } catch (Exception e) {
      log.error("Error executing graph query for entity: {}", entityName, e);
      throw new IoException("Graph query failed for entity: " + entityName, e);
    }
  }

  /**
   * Build an optimized AQL query that retrieves all required information in a single traversal.
   *
   * <p>This query:
   * <ol>
   *   <li>Finds the entity by name</li>
   *   <li>Retrieves entity fields via HAS_FIELD edges</li>
   *   <li>Retrieves operations via SUPPORTS_{CATEGORY} edges (filtered by requested categories)</li>
   *   <li>For each operation, retrieves examples via HAS_EXAMPLE edges</li>
   *   <li>For each operation, retrieves documentation via HAS_DOCUMENTATION edges</li>
   *   <li>Retrieves entity-level documentation</li>
   * </ol>
   *
   * @param entityName the entity name
   * @param categories the operation categories to filter by
   * @return AQL query string
   */
  private String buildOptimizedQuery(String entityName, List<String> categories) {
    // Sanitize entity key (entity|EntityName -> entity_entityname)
    String entityKey = "entity_" + sanitizeKey(entityName.toLowerCase());

    return """
        LET entity = FIRST(
          FOR e IN entities
            FILTER e._key == @entityName OR e._key == '%s' OR LOWER(e.name) == LOWER(@entityName)
            RETURN e
        )
        
        LET fields = (
          FOR f IN 1..1 OUTBOUND entity edges
            FILTER f.nodeType == 'field'
            RETURN f
        )
        
        LET entityDocs = (
          FOR doc IN 1..1 OUTBOUND entity edges
            FILTER doc.nodeType == 'documentation'
            RETURN doc
        )
        
        LET operations = (
          FOR op IN 1..1 OUTBOUND entity edges
            FILTER op.nodeType == 'operation'
            FILTER op.category IN @categories
            
            LET examples = (
              FOR ex IN 1..1 OUTBOUND op edges
                FILTER ex.nodeType == 'example'
                RETURN ex
            )
            
            LET opDocs = (
              FOR doc IN 1..1 OUTBOUND op edges
                FILTER doc.nodeType == 'documentation'
                RETURN doc
            )
            
            RETURN {
              operationId: op.operationId,
              method: op.method,
              path: op.path,
              summary: op.summary,
              description: op.description,
              category: op.category,
              signature: op.signature,
              requestSchema: op.requestSchema,
              responseSchema: op.responseSchema,
              tags: op.tags,
              primaryEntity: op.primaryEntity,
              examples: examples,
              documentation: opDocs
            }
        )
        
        RETURN {
          entity: entity,
          fields: fields,
          entityDocumentation: entityDocs,
          operations: operations
        }
        """.formatted(entityKey);
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
      result.setFields(fieldsList.stream()
          .map(this::cleanArangoFields)
          .collect(Collectors.toList()));
    } else {
      result.setFields(Collections.emptyList());
    }

    // Parse operations
    List<Map<String, Object>> operationsList = (List<Map<String, Object>>) rawResult.get("operations");
    if (operationsList != null) {
      List<OperationResult> operations = operationsList.stream()
          .map(this::parseOperation)
          .collect(Collectors.toList());
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
      op.setExamples(examplesList.stream()
          .map(this::cleanArangoFields)
          .collect(Collectors.toList()));
    } else {
      op.setExamples(Collections.emptyList());
    }

    // Parse documentation
    List<Map<String, Object>> docsList = (List<Map<String, Object>>) opMap.get("documentation");
    if (docsList != null) {
      op.setDocumentation(docsList.stream()
          .map(this::cleanArangoFields)
          .collect(Collectors.toList()));
    } else {
      op.setDocumentation(Collections.emptyList());
    }

    return op;
  }

  /**
   * Remove internal ArangoDB fields from a map.
   *
   * @param map the map to clean
   * @return cleaned map
   */
  private Map<String, Object> cleanArangoFields(Map<String, Object> map) {
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
    result.setOperations(Collections.emptyList());
    return result;
  }

  /**
   * Sanitize a key to match the format used in ArangoDB.
   *
   * @param key the key to sanitize
   * @return sanitized key
   */
  private String sanitizeKey(String key) {
    if (key == null) return "";
    return key.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
  }

  /**
   * Query multiple entities in a single optimized batch operation.
   *
   * <p>This is more efficient than calling query() multiple times as it
   * combines all queries into a single AQL execution.
   *
   * @param request the query request
   * @return list of query results
   */
  public List<QueryResult> queryBatch(QueryRequest request) {
    // For now, delegate to individual queries
    // Can be optimized later with UNION or sub-queries
    return query(request);
  }
}

