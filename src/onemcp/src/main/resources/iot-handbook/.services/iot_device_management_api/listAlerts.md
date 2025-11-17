# listAlerts
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | GET |
| Relative Path   | /alerts   |
----------------------------------------
      
```md
List Alerts

Retrieve all active alerts with optional filtering by device, severity, or status.
```

### Response `200`
List of alerts retrieved successfully

```json5
Object({
  // (Optional): 
  "alerts": Array(
    Object({
      // (Optional): Unique alert identifier
      "alert_id": String,
      // (Optional): Alert rule that triggered this alert
      "rule_id": String,
      // (Optional): Device where alert occurred
      "device_id": String,
      // (Optional): Sensor that triggered alert
      "sensor_id": String,
      // (Optional): Alert severity
      "severity": String(Enum("critical", "warning", "info")),
      // (Optional): Alert status
      "status": String(Enum("active", "acknowledged", "resolved")),
      // (Optional): Human-readable alert message
      "message": String,
      // (Optional): Sensor value that triggered alert
      "value": Number,
      // (Optional): Threshold value
      "threshold": Number,
      // (Optional): When alert was triggered
      "triggered_at": String(date-time),
      // (Optional): When alert was acknowledged
      "acknowledged_at": String(date-time),
      // (Optional): When alert was resolved
      "resolved_at": String(date-time)
    })
  ),
  // (Optional): Total number of alerts
  "total": Integer
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

