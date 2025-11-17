# listDeviceSensors
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | GET |
| Relative Path   | /devices/{device_id}/sensors   |
----------------------------------------
      
```md
List Device Sensors

Retrieve all sensors attached to a specific device with optional filtering by sensor type.
```

### Response `200`
List of sensors retrieved successfully

```json5
Object({
  // (Optional): 
  "sensors": Array(
    Object({
      // (Optional): Unique sensor identifier
      "sensor_id": String,
      // (Optional): Parent device identifier
      "device_id": String,
      // (Optional): Type of sensor
      "sensor_type": String,
      // (Optional): Measurement unit
      "unit": String,
      // (Optional): Calibration offset
      "calibration_offset": Number,
      // (Optional): Sampling rate in seconds
      "sampling_rate": Integer,
      // (Optional): Most recent reading
      "last_reading": Number,
      // (Optional): Timestamp of last reading
      "last_reading_time": String(date-time),
      // (Optional): When sensor was registered
      "created_at": String(date-time)
    })
  ),
  // (Optional): Total number of sensors
  "total": Integer
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

