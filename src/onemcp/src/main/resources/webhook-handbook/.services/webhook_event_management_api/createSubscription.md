# createSubscription
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | POST |
| Relative Path   | /subscriptions   |
----------------------------------------
      
```md
Create Subscription

Create a new webhook subscription. Requires event types and endpoint URL.
Optionally configure retry policies, filters, and custom headers.
```

### Request Body Schema
```json5
Object({
  // (Required): List of event types to subscribe to
  "event_types": Array(
    String
  ),
  // (Required): Webhook endpoint URL
  "endpoint_url": String(uri),
  // (Optional): Secret for webhook signature verification
  "secret": String,
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
  // (Optional): Optional filters to apply to events
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
  // (Optional): Custom headers to include in webhook requests
  "headers": Object({
    additionalProperties: String
  })
})
```

### Response `201`
Subscription created successfully

```json5
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
```

### Response `400`
Invalid subscription data

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

