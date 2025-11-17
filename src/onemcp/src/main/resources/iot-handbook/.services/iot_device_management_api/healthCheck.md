# healthCheck
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | GET |
| Relative Path   | /health   |
----------------------------------------
      
```md
Health Check

Check the health status of the API including connected devices and active alerts
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
  // (Optional): Number of currently connected devices
  "connected_devices": Integer,
  // (Optional): Number of active alerts
  "active_alerts": Integer
})
```

