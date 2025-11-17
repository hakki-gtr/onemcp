# createAlertRule
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | POST |
| Relative Path   | /alerts   |
----------------------------------------
      
```md
Create Alert Rule

Define a threshold-based alert rule for a sensor. Supports multiple condition types and severity levels.
```

### Request Body Schema
```json5
Object({
  // (Required): Sensor to monitor
  "sensor_id": String,
  // (Required): Threshold condition
  "condition": String(Enum("greater_than", "less_than", "equals", "not_equals", "between")),
  // (Required): Threshold value (or array for 'between')
  "threshold": Number,
  // (Required): Alert severity level
  "severity": String(Enum("critical", "warning", "info")),
  // (Optional): Human-readable alert rule name
  "name": String,
  // (Optional): Whether rule is active
  "enabled": Boolean
})
```

### Response `201`
Alert rule created successfully

```json5
Object({
  // (Optional): Unique alert rule identifier
  "rule_id": String,
  // (Optional): Monitored sensor
  "sensor_id": String,
  // (Optional): Threshold condition
  "condition": String,
  // (Optional): Threshold value
  "threshold": Number,
  // (Optional): Alert severity
  "severity": String,
  // (Optional): Alert rule name
  "name": String,
  // (Optional): Whether rule is active
  "enabled": Boolean,
  // (Optional): When rule was created
  "created_at": String(date-time)
})
```

### Response `400`
Invalid alert rule data

```json5
Object({
  // (Optional): 
  "success": Boolean,
  // (Optional): 
  "error": Object({
    // (Optional): Error code
    "code": String,
    // (Optional): Human-readable error message
    "message": String,
    // (Optional): Additional error context
    "details": Object({
      additionalProperties: true
    })
  }),
  // (Optional): When the error occurred
  "timestamp": String(date-time)
})
```

### Response `500`
Internal server error

```json5
Object({
  // (Optional): 
  "success": Boolean,
  // (Optional): 
  "error": Object({
    // (Optional): Error code
    "code": String,
    // (Optional): Human-readable error message
    "message": String,
    // (Optional): Additional error context
    "details": Object({
      additionalProperties: true
    })
  }),
  // (Optional): When the error occurred
  "timestamp": String(date-time)
})
```

