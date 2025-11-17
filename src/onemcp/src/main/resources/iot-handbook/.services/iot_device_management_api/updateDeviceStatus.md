# updateDeviceStatus
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | PATCH |
| Relative Path   | /devices/{device_id}   |
----------------------------------------
      
```md
Update Device Status

Update device connectivity status or put device in maintenance mode.
```

### Request Body Schema
```json5
Object({
  // (Optional): Device connectivity status
  "status": String(Enum("online", "offline", "error", "maintenance")),
  // (Optional): Last communication timestamp
  "last_seen": String(date-time),
  // (Optional): Additional device metadata
  "metadata": Object({
    additionalProperties: true
  })
})
```

### Response `200`
Device status updated successfully

```json5
Object({
  // (Optional): Unique device identifier
  "device_id": String,
  // (Optional): Device name
  "name": String,
  // (Optional): Type of device
  "device_type": String,
  // (Optional): Firmware version
  "firmware_version": String,
  // (Optional): Current device status
  "status": String(Enum("online", "offline", "error", "maintenance")),
  // (Optional): Physical location
  "location": String,
  // (Optional): Device group identifier
  "group_id": String,
  // (Optional): Last communication timestamp
  "last_seen": String(date-time),
  // (Optional): 
  "sensors": Array(
    Object({
      // (Optional): Unique sensor identifier
      "sensor_id": String,
      // (Optional): Type of sensor
      "sensor_type": String,
      // (Optional): Measurement unit
      "unit": String,
      // (Optional): Most recent sensor reading
      "last_reading": Number,
      // (Optional): Timestamp of last reading
      "last_reading_time": String(date-time)
    })
  ),
  // (Optional): When device was registered
  "created_at": String(date-time),
  // (Optional): When device was last updated
  "updated_at": String(date-time)
})
```

### Response `400`
Invalid status data

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

### Response `404`
Device not found

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

