# getStockLevel
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | GET |
| Relative Path   | /stock/{item_id}   |
----------------------------------------
      
```md
Get Stock Level

Get current stock levels for an item across all warehouses or a specific warehouse.
```

### Response `200`
Stock levels retrieved successfully

```json5
Object({
  // (Optional): Item SKU
  "item_id": String,
  // (Optional): 
  "stock_levels": Array(
    Object({
      // (Optional): Item SKU
      "item_id": String,
      // (Optional): Warehouse identifier
      "warehouse_id": String,
      // (Optional): Current stock quantity
      "quantity": Integer,
      // (Optional): Reserved quantity (pending orders)
      "reserved": Integer,
      // (Optional): Available quantity (quantity - reserved)
      "available": Integer,
      // (Optional): When stock was last updated
      "last_updated": String(date-time)
    })
  ),
  // (Optional): Total quantity across all warehouses
  "total_quantity": Integer
})
```

### Response `404`
Item not found

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

