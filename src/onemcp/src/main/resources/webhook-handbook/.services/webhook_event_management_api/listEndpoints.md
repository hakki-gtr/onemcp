# listEndpoints
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | GET |
| Relative Path   | /endpoints   |
----------------------------------------
      
```md
List Endpoints

List all webhook endpoints with health status and subscription counts.
```

### Response `200`
List of endpoints retrieved successfully

```json5
Object({
  // (Optional): 
  "endpoints": Array(
    Object({
      // (Optional): Endpoint URL
      "url": String(uri),
      // (Optional): Number of active subscriptions using this endpoint
      "active_subscriptions": Integer,
      // (Optional): Last successful delivery to this endpoint
      "last_delivery_at": String(date-time),
      // (Optional): Endpoint health status
      "health_status": String(Enum("healthy", "unhealthy", "unknown"))
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

