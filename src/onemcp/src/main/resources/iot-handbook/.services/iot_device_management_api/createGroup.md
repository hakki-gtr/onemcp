# createGroup
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | POST |
| Relative Path   | /groups   |
----------------------------------------
      
```md
Create Group

Create a new device group, optionally as a child of another group for hierarchical organization.
```

### Request Body Schema
```json5
Object({
  // (Required): Group name
  "name": String,
  // (Optional): Group description
  "description": String,
  // (Optional): Parent group for hierarchical organization
  "parent_group_id": String,
  // (Optional): Tags for grouping and filtering
  "tags": Array(
    String
  )
})
```

### Response `201`
Group created successfully

```json5
Object({
  // (Optional): Unique group identifier
  "group_id": String,
  // (Optional): Group name
  "name": String,
  // (Optional): Group description
  "description": String,
  // (Optional): Parent group identifier
  "parent_group_id": String,
  // (Optional): Child group identifiers
  "child_groups": Array(
    String
  ),
  // (Optional): Number of devices in this group
  "device_count": Integer,
  // (Optional): Group tags
  "tags": Array(
    String
  ),
  // (Optional): When group was created
  "created_at": String(date-time)
})
```

### Response `400`
Invalid group data

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
    // (Optional): Additional error context
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
    // (Optional): Additional error context
    "details": Object({
      additionalProperties: true
    })
  }),
  // (Optional): When the error occurred
  "timestamp": String(date-time)
})
```

