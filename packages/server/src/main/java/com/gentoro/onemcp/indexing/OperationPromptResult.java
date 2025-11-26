package com.gentoro.onemcp.indexing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Query result schema for generating operation prompts.
 *
 * <p>This class contains all the data necessary to generate prompts for operations, including:
 * <ul>
 *   <li>Operation basic information (name, description)</li>
 *   <li>Operation documentation</li>
 *   <li>Operation relationships/connections (entity relationships)</li>
 *   <li>Operation examples</li>
 *   <li>Operation signature</li>
 *   <li>Operation input (fields organized by entity, with types, descriptions, operators, aggregation functions)</li>
 *   <li>Operation output (with examples)</li>
 * </ul>
 */
public class OperationPromptResult {
  private String operationId;
  private String operationName;
  private String description;
  private String signature;
  private List<Map<String, Object>> documentation;
  private List<Map<String, Object>> relationships;
  private List<Map<String, Object>> examples;
  private OperationInput input;
  private OperationOutput output;

  public OperationPromptResult() {
    this.documentation = new ArrayList<>();
    this.relationships = new ArrayList<>();
    this.examples = new ArrayList<>();
    this.input = new OperationInput();
    this.output = new OperationOutput();
  }

  public String getOperationId() {
    return operationId;
  }

  public void setOperationId(String operationId) {
    this.operationId = operationId;
  }

  public String getOperationName() {
    return operationName;
  }

  public void setOperationName(String operationName) {
    this.operationName = operationName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getSignature() {
    return signature;
  }

  public void setSignature(String signature) {
    this.signature = signature;
  }

  public List<Map<String, Object>> getDocumentation() {
    return documentation;
  }

  public void setDocumentation(List<Map<String, Object>> documentation) {
    this.documentation = documentation != null ? documentation : new ArrayList<>();
  }

  public List<Map<String, Object>> getRelationships() {
    return relationships;
  }

  public void setRelationships(List<Map<String, Object>> relationships) {
    this.relationships = relationships != null ? relationships : new ArrayList<>();
  }

  public List<Map<String, Object>> getExamples() {
    return examples;
  }

  public void setExamples(List<Map<String, Object>> examples) {
    this.examples = examples != null ? examples : new ArrayList<>();
  }

  public OperationInput getInput() {
    return input;
  }

  public void setInput(OperationInput input) {
    this.input = input != null ? input : new OperationInput();
  }

  public OperationOutput getOutput() {
    return output;
  }

  public void setOutput(OperationOutput output) {
    this.output = output != null ? output : new OperationOutput();
  }

  /**
   * Operation input schema containing fields, operators, and aggregation functions.
   */
  public static class OperationInput {
    private List<EntityFieldGroup> fields;
    private List<String> operators;
    private List<String> aggregationFunctions;

    public OperationInput() {
      this.fields = new ArrayList<>();
      this.operators = new ArrayList<>();
      this.aggregationFunctions = new ArrayList<>();
    }

    public List<EntityFieldGroup> getFields() {
      return fields;
    }

    public void setFields(List<EntityFieldGroup> fields) {
      this.fields = fields != null ? fields : new ArrayList<>();
    }

    public List<String> getOperators() {
      return operators;
    }

    public void setOperators(List<String> operators) {
      this.operators = operators != null ? operators : new ArrayList<>();
    }

    public List<String> getAggregationFunctions() {
      return aggregationFunctions;
    }

    public void setAggregationFunctions(List<String> aggregationFunctions) {
      this.aggregationFunctions =
          aggregationFunctions != null ? aggregationFunctions : new ArrayList<>();
    }
  }

  /**
   * Operation output schema containing structure and examples.
   */
  public static class OperationOutput {
    private String structure;
    private List<Map<String, Object>> examples;

    public OperationOutput() {
      this.examples = new ArrayList<>();
    }

    public String getStructure() {
      return structure;
    }

    public void setStructure(String structure) {
      this.structure = structure;
    }

    public List<Map<String, Object>> getExamples() {
      return examples;
    }

    public void setExamples(List<Map<String, Object>> examples) {
      this.examples = examples != null ? examples : new ArrayList<>();
    }
  }

  /**
   * Fields grouped by entity with their properties.
   */
  public static class EntityFieldGroup {
    private String entityName;
    private String entityDescription;
    private List<FieldInfo> fields;

    public EntityFieldGroup() {
      this.fields = new ArrayList<>();
    }

    public String getEntityName() {
      return entityName;
    }

    public void setEntityName(String entityName) {
      this.entityName = entityName;
    }

    public String getEntityDescription() {
      return entityDescription;
    }

    public void setEntityDescription(String entityDescription) {
      this.entityDescription = entityDescription;
    }

    public List<FieldInfo> getFields() {
      return fields;
    }

    public void setFields(List<FieldInfo> fields) {
      this.fields = fields != null ? fields : new ArrayList<>();
    }
  }

  /**
   * Field information with type, description, examples, and possible values.
   */
  public static class FieldInfo {
    private String name;
    private String type;
    private String description;
    private String example;
    private List<String> possibleValues;

    public FieldInfo() {
      this.possibleValues = new ArrayList<>();
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getExample() {
      return example;
    }

    public void setExample(String example) {
      this.example = example;
    }

    public List<String> getPossibleValues() {
      return possibleValues;
    }

    public void setPossibleValues(List<String> possibleValues) {
      this.possibleValues = possibleValues != null ? possibleValues : new ArrayList<>();
    }
  }
}

