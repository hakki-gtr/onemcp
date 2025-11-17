package com.gentoro.onemcp;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class StartupParameters {

  final Map<String, Object> parameters = new HashMap<>();

  {
    parameters.put("config-file", "classpath:application.yaml");
    parameters.put("mode", "interactive"); // interactive, dry-run, server, help
    parameters.put("skip-kb-reindex", "false");
  }

  public StartupParameters(String[] arguments) {
    this.parameters.putAll(parseArguments(arguments));
    this.validate();
  }

  private Map<String, Object> parseArguments(String[] arguments) {
    Map<String, Object> result = new HashMap<>();
    for (int p = 0; p < arguments.length; p++) {

      if (!arguments[p].startsWith("--")) {
        continue;
      }

      String paramName = arguments[p].substring(2);
      String paramValue = "true";
      if (p < arguments.length - 1) {
        String next = arguments[p + 1];
        if (!next.startsWith("--")) {
          paramValue = next;
          p++;
        }
      }

      result.put(paramName, paramValue);
    }
    return result;
  }

  private void validate() {
    if (!parameters.containsKey("mode")
        || (parameters.containsKey("mode")
            && !parameters.get("mode").equals("help")
            && !parameters.get("mode").equals("interactive")
            && !parameters.get("mode").equals("dry-run")
            && !parameters.get("mode").equals("server"))) {
      throw new IllegalArgumentException("Invalid mode: " + parameters.get("mode"));
    }

    if (!parameters.containsKey("config-file")
        || parameters.get("config-file") == null
        || parameters.get("config-file").toString().isBlank()) {
      throw new IllegalArgumentException("Missing config file location");
    }
  }

  /**
   * Returns the configuration location string. Examples: "classpath:application.yaml",
   * "/etc/onemcp.yaml", "config/local.yaml".
   */
  public String configFile() {
    return getOptionalParameter("config-file", String.class).orElse("classpath:application.yaml");
  }

  public <T> T getParameter(String name, Class<T> type) {
    return type.cast(parameters.get(name));
  }

  public <T> Optional<T> getOptionalParameter(String name, Class<T> type) {
    return Optional.ofNullable(type.cast(parameters.get(name)));
  }

  public boolean isParameterPresent(String name) {
    return parameters.containsKey(name);
  }

  public boolean getBooleanParameter(String name, boolean defaultValue) {
    return getOptionalParameter(name, String.class)
        .map(String::trim)
        .map(String::toLowerCase)
        .map(
            value -> {
              if (value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("y")) {
                return true;
              }
              if (value.equals("false") || value.equals("0") || value.equals("no") || value.equals("n")) {
                return false;
              }
              return defaultValue;
            })
        .orElse(defaultValue);
  }
}
