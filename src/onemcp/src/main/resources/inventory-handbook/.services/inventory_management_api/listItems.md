# listItems
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | GET |
| Relative Path   | /items   |
----------------------------------------
      
```md
List Items

List all inventory items with optional filtering by warehouse, category, or low stock status.
Supports pagination for large result sets.
```

### Response `200`
List of items retrieved successfully

```json5
Object({
  // (Optional): 
  "items": Array(
    Object({
      // (Optional): Stock Keeping Unit
      "sku": String,
      // (Optional): Item name
      "name": String,
      // (Optional): Item description
      "description": String,
      // (Optional): Item category
      "category": String,
      // (Optional): Unit price in USD
      "unit_price": Number,
      // (Optional): Minimum stock level before reorder
      "reorder_point": Integer,
      // (Optional): Quantity to order when reorder point is reached
      "reorder_quantity": Integer,
      // (Optional): When the item was created
      "created_at": String(date-time),
      // (Optional): When the item was last updated
      "updated_at": String(date-time)
    })
  ),
  // (Optional): Total number of items
  "total": Integer,
  // (Optional): Maximum items returned
  "limit": Integer,
  // (Optional): Number of items skipped
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
    })
  }),
  // (Optional): When the error occurred
  "timestamp": String(date-time)
})
```

