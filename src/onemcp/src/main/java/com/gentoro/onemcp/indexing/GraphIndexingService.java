package com.gentoro.onemcp.indexing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gentoro.onemcp.OneMcp;
import com.gentoro.onemcp.context.KnowledgeDocument;
import com.gentoro.onemcp.context.Operation;
import com.gentoro.onemcp.context.Service;
import com.gentoro.onemcp.indexing.graph.*;
import com.gentoro.onemcp.indexing.graph.nodes.DocChunkNode;
import com.gentoro.onemcp.indexing.graph.nodes.EntityNode;
import com.gentoro.onemcp.indexing.graph.nodes.ExampleNode;
import com.gentoro.onemcp.indexing.graph.nodes.OperationNode;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.tags.Tag;
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
 *   <li>Documentation chunks from markdown files
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
  private final DocumentChunker documentChunker;
  private final ObjectMapper jsonMapper;
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
    this.documentChunker = new DocumentChunker();
    this.jsonMapper = new ObjectMapper();
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

      // Index services and their components
      List<Service> services = oneMcp.knowledgeBase().services();
      log.info("Indexing {} services", services.size());

      for (Service service : services) {
        indexService(service);
      }

      // Index general documentation
      indexGeneralDocumentation();

      // Ensure graph exists after indexing (in case it wasn't created during clear)
      arangoDbService.ensureGraphExists();

      log.info("Knowledge base graph indexing completed successfully");
    } catch (Exception e) {
      log.error("Failed to index knowledge base graph", e);
      throw new com.gentoro.onemcp.exception.IoException("Graph indexing failed", e);
    }
  }

  /**
   * Index a single service including its entities, operations, and documentation.
   *
   * @param service the service to index
   */
  private void indexService(Service service) {
    log.debug("Indexing service: {}", service.getSlug());

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
          String entityKey = "entity_" + service.getSlug() + "_" + sanitizeKey(tag);
          arangoDbService.storeEdge(
              new GraphEdge(entityKey, opNode.getKey(), GraphEdge.EdgeType.HAS_OPERATION));
        }
      }

      // Extract and index examples
      List<ExampleNode> examples = extractExamples(service, openAPI);
      for (ExampleNode example : examples) {
        arangoDbService.storeNode(example);

        // Create edge from operation to example
        arangoDbService.storeEdge(
            new GraphEdge(
                example.getOperationKey(), example.getKey(), GraphEdge.EdgeType.HAS_EXAMPLE));
      }

      // Index operation documentation
      indexOperationDocumentation(service);

      log.debug(
          "Indexed service {} with {} entities, {} operations, {} examples",
          service.getSlug(),
          entities.size(),
          operationNodes.size(),
          examples.size());
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
      String key = "entity_" + service.getSlug() + "_" + sanitizeKey(tag.getName());
      String description = tag.getDescription() != null ? tag.getDescription() : "";

      // Find operations associated with this tag
      List<String> associatedOps =
          service.getOperations().stream()
              .filter(op -> op.getOperation() != null)
              .map(op -> "op_" + service.getSlug() + "_" + sanitizeKey(op.getOperation()))
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
      String key = "op_" + service.getSlug() + "_" + sanitizeKey(operation.getOperation());
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
   * Extract example nodes from OpenAPI specification.
   *
   * @param service the service
   * @param openAPI the OpenAPI specification
   * @return list of example nodes
   */
  private List<ExampleNode> extractExamples(Service service, OpenAPI openAPI) {
    List<ExampleNode> examples = new ArrayList<>();

    if (openAPI.getPaths() == null) {
      return examples;
    }

    // Iterate through paths and operations to find examples
    openAPI
        .getPaths()
        .forEach(
            (path, pathItem) -> {
              pathItem
                  .readOperationsMap()
                  .forEach(
                      (method, operation) -> {
                        if (operation.getOperationId() != null) {
                          String operationKey =
                              "op_"
                                  + service.getSlug()
                                  + "_"
                                  + sanitizeKey(operation.getOperationId());

                          // Extract request body examples
                          if (operation.getRequestBody() != null
                              && operation.getRequestBody().getContent() != null) {
                            extractExamplesFromContent(
                                operation.getRequestBody().getContent(),
                                operationKey,
                                service.getSlug(),
                                "request",
                                examples);
                          }

                          // Extract response examples
                          if (operation.getResponses() != null) {
                            operation
                                .getResponses()
                                .forEach(
                                    (status, response) ->
                                        extractExamplesFromResponse(
                                            response,
                                            operationKey,
                                            service.getSlug(),
                                            status,
                                            examples));
                          }
                        }
                      });
            });

    return examples;
  }

  /**
   * Extract examples from media type content.
   *
   * @param content the content map
   * @param operationKey the operation key
   * @param serviceSlug the service slug
   * @param type request or response
   * @param examples the list to add examples to
   */
  private void extractExamplesFromContent(
      Content content,
      String operationKey,
      String serviceSlug,
      String type,
      List<ExampleNode> examples) {
    if (content == null) return;

    content.forEach(
        (mediaTypeStr, mediaType) -> {
          if (mediaType.getExamples() != null) {
            mediaType
                .getExamples()
                .forEach(
                    (exampleName, example) -> {
                      String exampleKey =
                          "example_"
                              + serviceSlug
                              + "_"
                              + sanitizeKey(operationKey + "_" + exampleName);

                      String requestBody = "";
                      String responseBody = "";
                      if ("request".equals(type)) {
                        requestBody = serializeExample(example);
                      } else {
                        responseBody = serializeExample(example);
                      }

                      ExampleNode exampleNode =
                          new ExampleNode(
                              exampleKey,
                              exampleName,
                              example.getSummary() != null ? example.getSummary() : "",
                              example.getDescription() != null ? example.getDescription() : "",
                              requestBody,
                              responseBody,
                              type.equals("request") ? "" : "200",
                              operationKey,
                              serviceSlug);

                      examples.add(exampleNode);
                    });
          }
        });
  }

  /**
   * Extract examples from API response.
   *
   * @param response the API response
   * @param operationKey the operation key
   * @param serviceSlug the service slug
   * @param status the response status
   * @param examples the list to add examples to
   */
  private void extractExamplesFromResponse(
      ApiResponse response,
      String operationKey,
      String serviceSlug,
      String status,
      List<ExampleNode> examples) {
    if (response.getContent() != null) {
      extractExamplesFromContent(response.getContent(), operationKey, serviceSlug, status, examples);
    }
  }

  /**
   * Serialize an example to JSON string.
   *
   * @param example the example
   * @return JSON string
   */
  private String serializeExample(Example example) {
    try {
      if (example.getValue() != null) {
        return jsonMapper.writeValueAsString(example.getValue());
      }
    } catch (Exception e) {
      log.warn("Failed to serialize example", e);
    }
    return "";
  }

  /**
   * Index operation documentation as chunks.
   *
   * @param service the service
   */
  private void indexOperationDocumentation(Service service) {
    for (Operation operation : service.getOperations()) {
      try {
        String docUri =
            "kb:///services/" + service.getSlug() + "/operations/" + operation.getOperation() + ".md";
        KnowledgeDocument doc = oneMcp.knowledgeBase().getDocument(docUri);

        String operationKey = "op_" + service.getSlug() + "_" + sanitizeKey(operation.getOperation());

        // Chunk the documentation
        List<DocumentChunker.DocumentChunk> chunks =
            documentChunker.chunkDocument(doc.content(), docUri);

        // Index each chunk
        for (int i = 0; i < chunks.size(); i++) {
          DocumentChunker.DocumentChunk chunk = chunks.get(i);
          String chunkKey =
              "chunk_"
                  + service.getSlug()
                  + "_"
                  + sanitizeKey(operation.getOperation())
                  + "_"
                  + i;

          DocChunkNode chunkNode =
              new DocChunkNode(
                  chunkKey,
                  chunk.getContent(),
                  chunk.getSourceUri(),
                  "operation_doc",
                  chunk.getChunkIndex(),
                  chunk.getStartOffset(),
                  chunk.getEndOffset(),
                  chunk.getTitle(),
                  operationKey);

          arangoDbService.storeNode(chunkNode);

          // Create edge from operation to documentation chunk
          arangoDbService.storeEdge(
              new GraphEdge(operationKey, chunkKey, GraphEdge.EdgeType.HAS_DOCUMENTATION));

          // Create sequential edges between chunks
          if (i > 0) {
            String prevChunkKey =
                "chunk_"
                    + service.getSlug()
                    + "_"
                    + sanitizeKey(operation.getOperation())
                    + "_"
                    + (i - 1);
            arangoDbService.storeEdge(
                new GraphEdge(prevChunkKey, chunkKey, GraphEdge.EdgeType.FOLLOWS_CHUNK));
          }
        }
      } catch (Exception e) {
        log.warn("Failed to index documentation for operation: {}", operation.getOperation(), e);
      }
    }
  }

  /**
   * Index general documentation files (non-operation specific).
   */
  private void indexGeneralDocumentation() {
    log.debug("Indexing general documentation");

    try {
      // Index instructions.md
      List<KnowledgeDocument> docs =
          oneMcp.knowledgeBase().findByUriPrefix("kb:///instructions.md");

      for (KnowledgeDocument doc : docs) {
        List<DocumentChunker.DocumentChunk> chunks =
            documentChunker.chunkDocument(doc.content(), doc.uri());

        for (int i = 0; i < chunks.size(); i++) {
          DocumentChunker.DocumentChunk chunk = chunks.get(i);
          String chunkKey = "chunk_general_instructions_" + i;

          DocChunkNode chunkNode =
              new DocChunkNode(
                  chunkKey,
                  chunk.getContent(),
                  chunk.getSourceUri(),
                  "general_doc",
                  chunk.getChunkIndex(),
                  chunk.getStartOffset(),
                  chunk.getEndOffset(),
                  chunk.getTitle(),
                  null);

          arangoDbService.storeNode(chunkNode);

          // Create sequential edges
          if (i > 0) {
            String prevChunkKey = "chunk_general_instructions_" + (i - 1);
            arangoDbService.storeEdge(
                new GraphEdge(prevChunkKey, chunkKey, GraphEdge.EdgeType.FOLLOWS_CHUNK));
          }
        }
      }

      // Index other documentation in docs/ folder
      List<KnowledgeDocument> allDocs = oneMcp.knowledgeBase().findByUriPrefix("kb:///docs/");
      for (KnowledgeDocument doc : allDocs) {
        indexGeneralDocument(doc);
      }

    } catch (Exception e) {
      log.warn("Failed to index general documentation", e);
    }
  }

  /**
   * Index a general documentation file.
   *
   * @param doc the document to index
   */
  private void indexGeneralDocument(KnowledgeDocument doc) {
    try {
      String docId = sanitizeKey(doc.uri().replace("kb:///", "").replace("/", "_"));
      List<DocumentChunker.DocumentChunk> chunks =
          documentChunker.chunkDocument(doc.content(), doc.uri());

      for (int i = 0; i < chunks.size(); i++) {
        DocumentChunker.DocumentChunk chunk = chunks.get(i);
        String chunkKey = "chunk_" + docId + "_" + i;

        DocChunkNode chunkNode =
            new DocChunkNode(
                chunkKey,
                chunk.getContent(),
                chunk.getSourceUri(),
                "general_doc",
                chunk.getChunkIndex(),
                chunk.getStartOffset(),
                chunk.getEndOffset(),
                chunk.getTitle(),
                null);

        arangoDbService.storeNode(chunkNode);

        // Create sequential edges
        if (i > 0) {
          String prevChunkKey = "chunk_" + docId + "_" + (i - 1);
          arangoDbService.storeEdge(
              new GraphEdge(prevChunkKey, chunkKey, GraphEdge.EdgeType.FOLLOWS_CHUNK));
        }
      }
    } catch (Exception e) {
      log.warn("Failed to index document: {}", doc.uri(), e);
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
    return input.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
  }

  /**
   * Shutdown the indexing service and release resources.
   */
  public void shutdown() {
    if (arangoDbService != null) {
      arangoDbService.shutdown();
    }
  }

  // ============================================================================
  // FUTURE USER FEEDBACK INTEGRATION
  // ============================================================================

  /**
   * Placeholder for future user feedback integration.
   *
   * <p>When user feedback is collected, it can be indexed as nodes and linked to the relevant
   * operations, examples, or documentation chunks using the HAS_FEEDBACK edge type.
   *
   * <p>Feedback nodes would contain:
   *
   * <ul>
   *   <li>User ID or session ID
   *   <li>Timestamp
   *   <li>Feedback type (positive, negative, correction, suggestion)
   *   <li>Feedback content (text, rating, etc.)
   *   <li>Context (what the user was doing when providing feedback)
   * </ul>
   *
   * <p>Integration points:
   *
   * <ol>
   *   <li>Create a FeedbackNode class implementing GraphNode
   *   <li>Add a method to store feedback: storeFeedback(FeedbackNode feedback)
   *   <li>Create edges linking feedback to related nodes (operations, examples, doc chunks)
   *   <li>Use feedback to rank and improve retrieval relevance
   *   <li>Aggregate feedback statistics for quality metrics
   * </ol>
   *
   * <p>Example usage:
   *
   * <pre>
   * FeedbackNode feedback = new FeedbackNode(
   *     "feedback_12345",
   *     userId,
   *     timestamp,
   *     "positive",
   *     "This example was very helpful!",
   *     exampleKey
   * );
   * arangoDbService.storeNode(feedback);
   * arangoDbService.storeEdge(new GraphEdge(
   *     exampleKey,
   *     feedback.getKey(),
   *     GraphEdge.EdgeType.HAS_FEEDBACK
   * ));
   * </pre>
   *
   * <p>Query patterns for feedback:
   *
   * <ul>
   *   <li>Find all feedback for an operation
   *   <li>Find operations with most positive feedback
   *   <li>Find examples that need improvement (negative feedback)
   *   <li>Track feedback trends over time
   *   <li>Personalize recommendations based on user feedback history
   * </ul>
   *
   * <p>Note: This documentation serves as a placeholder and design guide for future implementation.
   */
}

