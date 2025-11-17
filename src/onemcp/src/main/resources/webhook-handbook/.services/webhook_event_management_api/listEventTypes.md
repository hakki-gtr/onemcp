# listEventTypes
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | GET |
| Relative Path   | /events   |
----------------------------------------
      
```md
List Event Types

Retrieve all event types that can be subscribed to. Includes event schemas and descriptions.
```

### Response `200`
List of event types retrieved successfully

```json5
Object({
  // (Optional): 
  "event_types": Array(
    Object({
      // (Optional): Event type identifier
      "name": String,
      // (Optional): Human-readable description
      "description": String,
      // (Optional): Event category
      "category": String,
      // (Optional): Event schema version
      "version": String,
      // (Optional): JSON schema for event payload
      "schema": Object({
        additionalProperties: true
      })
    })
  )
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
    // (Optional): Additional error details
    "details": Object({
      additionalProperties: true
    })
  }),
  // (Optional): When the error occurred
  "timestamp": String(date-time)
})
```

