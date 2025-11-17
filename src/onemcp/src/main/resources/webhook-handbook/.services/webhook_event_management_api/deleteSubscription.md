# deleteSubscription
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | DELETE |
| Relative Path   | /subscriptions/{subscription_id}   |
----------------------------------------
      
```md
Delete Subscription

Delete a webhook subscription. Stops receiving events immediately.
```

### Response `204`
Subscription deleted successfully

### Response `404`
Subscription not found

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

