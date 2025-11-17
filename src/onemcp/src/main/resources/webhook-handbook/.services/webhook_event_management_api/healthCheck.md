# healthCheck
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | GET |
| Relative Path   | /health   |
----------------------------------------
      
```md
Health Check

Check the health status of the API and its services
```

### Response `200`
API is healthy

```json5
Object({
  // (Optional): 
  "status": String,
  // (Optional): 
  "timestamp": String(date-time),
  // (Optional): 
  "version": String,
  // (Optional): 
  "services": Object({
    additionalProperties: String
  })
})
```

