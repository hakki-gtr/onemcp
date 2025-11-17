# getWebhookStatus
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | GET |
| Relative Path   | /webhooks/{webhook_id}   |
----------------------------------------
      
```md
Get Webhook Status

Get delivery status and details for a specific webhook including retry attempts and errors.
```

### Response `200`
Webhook status retrieved successfully

```json5
Object({
  // (Optional): Webhook identifier
  "id": String,
  // (Optional): Associated subscription ID
  "subscription_id": String,
  // (Optional): Event type that triggered this webhook
  "event_type": String,
  // (Optional): Delivery status
  "status": String(Enum("pending", "delivered", "failed", "retrying")),
  // (Optional): Target endpoint URL
  "endpoint_url": String(uri),
  // (Optional): Number of delivery attempts
  "attempts": Integer,
  // (Optional): Timestamp of last delivery attempt
  "last_attempt_at": String(date-time),
  // (Optional): When next retry will be attempted (if applicable)
  "next_retry_at": String(date-time),
  // (Optional): 
  "error": Object({
    // (Optional): Error code
    "code": String,
    // (Optional): Human-readable error message
    "message": String,
    // (Optional): Additional error details
    "details": Object({
      additionalProperties: true
    }),
    // (Optional): HTTP status code received (if any)
    "http_status": Integer
  }),
  // (Optional): When webhook was created
  "created_at": String(date-time)
})
```

### Response `404`
Webhook not found

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

