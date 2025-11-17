# listSubscriptions
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | GET |
| Relative Path   | /subscriptions   |
----------------------------------------
      
```md
List Subscriptions

List all webhook subscriptions with optional filtering by event type or status.
Supports pagination for large result sets.
```

### Response `200`
List of subscriptions retrieved successfully

```json5
Object({
  // (Optional): 
  "subscriptions": Array(
    Object({
      // (Optional): Unique subscription identifier
      "id": String,
      // (Optional): Subscribed event types
      "event_types": Array(
        String
      ),
      // (Optional): Webhook endpoint URL
      "endpoint_url": String(uri),
      // (Optional): Subscription status
      "status": String(Enum("active", "paused", "disabled")),
      // (Optional): 
      "retry_policy": Object({
        // (Optional): Maximum number of retry attempts
        "max_attempts": Integer,
        // (Optional): Retry strategy
        "strategy": String(Enum("exponential", "linear", "fixed")),
        // (Optional): Initial delay before first retry (milliseconds)
        "initial_delay_ms": Integer,
        // (Optional): Maximum delay between retries (milliseconds)
        "max_delay_ms": Integer,
        // (Optional): Multiplier for exponential backoff
        "backoff_multiplier": Number
      }),
      // (Optional): 
      "filters": Array(
        Object({
          // (Required): Field path to filter on
          "field": String,
          // (Required): Filter operator
          "operator": String(Enum("equals", "not_equals", "in", "not_in", "contains", "starts_with", "ends_with")),
          // (Optional): Filter value (type depends on operator)
          "value": OneOf(String, Number, Boolean, Array(OneOf(String, Number, Boolean)))
        })
      ),
      // (Optional): When subscription was created
      "created_at": String(date-time),
      // (Optional): When subscription was last updated
      "updated_at": String(date-time),
      // (Optional): 
      "stats": Object({
        // (Optional): Total webhooks delivered
        "total_webhooks": Integer,
        // (Optional): Successfully delivered webhooks
        "successful": Integer,
        // (Optional): Failed webhook deliveries
        "failed": Integer,
        // (Optional): Pending webhook deliveries
        "pending": Integer,
        // (Optional): Timestamp of last webhook delivery
        "last_delivery_at": String(date-time)
      })
    })
  ),
  // (Optional): Total number of subscriptions
  "total": Integer,
  // (Optional): Maximum subscriptions returned
  "limit": Integer,
  // (Optional): Number of subscriptions skipped
  "offset": Integer
})
```

### Response `400`
Invalid request parameters

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

