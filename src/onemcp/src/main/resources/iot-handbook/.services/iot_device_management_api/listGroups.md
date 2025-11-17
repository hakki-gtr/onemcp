# listGroups
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | GET |
| Relative Path   | /groups   |
----------------------------------------
      
```md
List Groups

Retrieve all device groups with hierarchy information. Supports filtering by parent group.
```

### Response `200`
List of groups retrieved successfully

```json5
Object({
  // (Optional): 
  "groups": Array(
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
    // (Optional): Additional error context
    "details": Object({
      additionalProperties: true
    })
  }),
  // (Optional): When the error occurred
  "timestamp": String(date-time)
})
```

