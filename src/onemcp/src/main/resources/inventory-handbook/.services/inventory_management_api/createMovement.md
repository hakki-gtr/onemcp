# createMovement
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | POST |
| Relative Path   | /movements   |
----------------------------------------
      
```md
Create Movement

Create an inventory movement (transfer, adjustment, receipt, or shipment).
Records inventory changes between warehouses or within a warehouse.
```

### Request Body Schema
```json5
Object({
  // (Required): Item SKU
  "item_id": String,
  // (Required): Type of movement
  "movement_type": String(Enum("transfer", "adjustment", "receipt", "shipment")),
  // (Optional): Source warehouse (for transfers)
  "from_warehouse_id": String,
  // (Optional): Destination warehouse (for transfers)
  "to_warehouse_id": String,
  // (Optional): Warehouse for adjustments, receipts, shipments
  "warehouse_id": String,
  // (Required): Quantity moved
  "quantity": Integer,
  // (Optional): Reason for movement
  "reason": String
})
```

### Response `201`
Movement created successfully

```json5
Object({
  // (Optional): Movement identifier
  "id": String,
  // (Optional): Item SKU
  "item_id": String,
  // (Optional): Type of movement
  "movement_type": String,
  // (Optional): Quantity moved
  "quantity": Integer,
  // (Optional): Source warehouse
  "from_warehouse_id": String,
  // (Optional): Destination warehouse
  "to_warehouse_id": String,
  // (Optional): Warehouse for non-transfer movements
  "warehouse_id": String,
  // (Optional): Reason for movement
  "reason": String,
  // (Optional): When movement was created
  "created_at": String(date-time)
})
```

### Response `400`
Invalid movement data

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

