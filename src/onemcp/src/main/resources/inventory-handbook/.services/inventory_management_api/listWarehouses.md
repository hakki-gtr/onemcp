# listWarehouses
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | GET |
| Relative Path   | /warehouses   |
----------------------------------------
      
```md
List Warehouses

Retrieve a list of all warehouse locations with capacity and occupancy information.
```

### Response `200`
List of warehouses retrieved successfully

```json5
Object({
  // (Optional): 
  "warehouses": Array(
    Object({
      // (Optional): Unique warehouse identifier
      "id": String,
      // (Optional): Warehouse name
      "name": String,
      // (Optional): Warehouse address
      "address": String,
      // (Optional): Maximum storage capacity
      "capacity": Integer,
      // (Optional): Current items stored
      "current_occupancy": Integer
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
    })
  }),
  // (Optional): When the error occurred
  "timestamp": String(date-time)
})
```

