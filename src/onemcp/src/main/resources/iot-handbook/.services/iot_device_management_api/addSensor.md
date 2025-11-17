# addSensor
----------------------------------------
| Property        | Value              |
|-----------------|--------------------|
| Method          | POST |
| Relative Path   | /devices/{device_id}/sensors   |
----------------------------------------
      
```md
Add Sensor

Register a new sensor on an existing device. Requires sensor ID, type, and unit.
```

### Request Body Schema
```json5
Object({
  // (Required): Unique sensor identifier
  "sensor_id": String,
  // (Required): Type of sensor
  "sensor_type": String(Enum("temperature", "humidity", "pressure", "motion", "light", "air_quality")),
  // (Required): Measurement unit
  "unit": String,
  // (Optional): Calibration offset value
  "calibration_offset": Number,
  // (Optional): Sampling rate in seconds
  "sampling_rate": Integer
})
```

### Response `201`
Sensor added successfully

```json5
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
```

### Response `400`
Invalid sensor data

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

