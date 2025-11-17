# updateItem
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | PUT |
| Relative Path   | /items/{item_id}   |
----------------------------------------
      
```md
Update Item

Update details of an existing inventory item. All fields are optional.
```

### Request Body Schema
```json5
Object({
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
  "reorder_quantity": Integer
})
```

### Response `200`
Item updated successfully

```json5
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
```

### Response `400`
Invalid item data

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

