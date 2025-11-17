# IoT Device Management API

A comprehensive API for managing IoT devices, sensors, telemetry data, and alerts.
This API provides operations to register devices, configure sensors, query telemetry streams,
manage device groups, and handle alert notifications.

## Key Features
- **Device Management**: Register, update, and monitor IoT devices
- **Sensor Configuration**: Manage sensors attached to devices
- **Telemetry Queries**: Query time-series sensor data with aggregation
- **Alert Management**: Configure and monitor threshold-based alerts
- **Device Groups**: Organize devices hierarchically for bulk operations

## Core Entities

### Devices
Physical IoT devices with firmware and connectivity status.

| Field | Type | Description |
|-------|------|-------------|
| `device_id` | string | Unique device identifier |
| `name` | string | Human-readable device name |
| `device_type` | string | Type of IoT device (e.g., "gateway") |
| `firmware_version` | string | Current firmware version |
| `status` | string | Status: online, offline, error, maintenance |
| `location` | string | Physical location description |
| `group_id` | string | Device group identifier |
| `last_seen` | string | Last communication timestamp |

### Sensors
Individual sensors measuring various metrics.

| Field | Type | Description |
|-------|------|-------------|
| `sensor_id` | string | Unique sensor identifier |
| `device_id` | string | Parent device identifier |
| `sensor_type` | string | Type: temperature, humidity, pressure, motion, light, air_quality |
| `unit` | string | Measurement unit (e.g., "celsius") |
| `calibration_offset` | number | Calibration offset value |
| `sampling_rate` | integer | Sampling rate in seconds |
| `last_reading` | number | Most recent sensor reading |
| `last_reading_time` | string | Timestamp of last reading |

### Telemetry
Time-series data streams from sensors.

| Field | Type | Description |
|-------|------|-------------|
| `timestamp` | string | Measurement timestamp |
| `value` | number | Sensor reading value |
| `quality` | string | Data quality: good, fair, poor, invalid |

### Alerts
Threshold-based notifications and warnings.

| Field | Type | Description |
|-------|------|-------------|
| `alert_id` | string | Unique alert identifier |
| `rule_id` | string | Alert rule that triggered this alert |
| `device_id` | string | Device where alert occurred |
| `sensor_id` | string | Sensor that triggered alert |
| `severity` | string | Severity: critical, warning, info |
| `status` | string | Status: active, acknowledged, resolved |
| `value` | number | Sensor value that triggered alert |
| `threshold` | number | Threshold value |

### Groups
Hierarchical device groupings for bulk operations.

| Field | Type | Description |
|-------|------|-------------|
| `group_id` | string | Unique group identifier |
| `name` | string | Group name |
| `description` | string | Group description |
| `parent_group_id` | string | Parent group for hierarchy |
| `child_groups` | array | Child group identifiers |
| `device_count` | integer | Number of devices in group |
| `tags` | array | Tags for grouping and filtering |

## Alert Rules

Configure threshold-based alerts for sensors:

- **condition**: greater_than, less_than, equals, not_equals, between
- **threshold**: Threshold value (or array for 'between')
- **severity**: critical, warning, info
- **enabled**: Whether rule is active

## Telemetry Queries

Query time-series data with aggregation:

- **start_time** / **end_time**: Time range (ISO 8601)
- **interval**: Aggregation interval (e.g., 1m, 5m, 1h, 1d)
- **aggregation**: avg, min, max, sum, count

## Device Status

Device connectivity status values:

- **online**: Device is connected and operational
- **offline**: Device is disconnected
- **error**: Device has an error condition
- **maintenance**: Device is in maintenance mode

