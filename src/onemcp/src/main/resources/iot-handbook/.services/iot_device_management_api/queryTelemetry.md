# queryTelemetry
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | GET |
| Relative Path   | /telemetry/{sensor_id}   |
----------------------------------------
      
```md
Query Telemetry

Retrieve time-series telemetry data from a sensor with optional aggregation and interval filtering.
```

### Response `200`
Telemetry data retrieved successfully

```json5
Object({
  // (Optional): Sensor identifier
  "sensor_id": String,
  // (Optional): Type of sensor
  "sensor_type": String,
  // (Optional): Measurement unit
  "unit": String,
  // (Optional): Query start time
  "start_time": String(date-time),
  // (Optional): Query end time
  "end_time": String(date-time),
  // (Optional): Aggregation interval used
  "interval": String,
  // (Optional): Aggregation function used
  "aggregation": String,
  // (Optional): 
  "data_points": Array(
    Object({
      // (Optional): Measurement timestamp
      "timestamp": String(date-time),
      // (Optional): Sensor reading value
      "value": Number,
      // (Optional): Data quality indicator
      "quality": String(Enum("good", "fair", "poor", "invalid"))
    })
  ),
  // (Optional): 
  "summary": Object({
    // (Optional): Number of data points
    "count": Integer,
    // (Optional): Minimum value
    "min": Number,
    // (Optional): Maximum value
    "max": Number,
    // (Optional): Average value
    "avg": Number
  })
})
```

### Response `400`
Invalid query parameters

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

### Response `404`
Sensor not found

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

