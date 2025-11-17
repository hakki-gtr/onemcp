# retryWebhook
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | POST |
| Relative Path   | /webhooks/{webhook_id}/retry   |
----------------------------------------
      
```md
Retry Webhook

Manually retry delivery of a failed webhook. Respects retry policy limits.
```

### Response `202`
Retry request accepted

```json5
Object({
  // (Optional): Webhook identifier
  "id": String,
  // (Optional): New status after retry request
  "status": String,
  // (Optional): When retry will be attempted
  "retry_scheduled_at": String(date-time)
})
```

### Response `400`
Webhook cannot be retried

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

