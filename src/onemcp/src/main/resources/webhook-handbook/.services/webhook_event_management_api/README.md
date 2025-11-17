# Webhook Event Management API

An event-driven API for managing webhook subscriptions, event delivery, and retry policies.
This API provides operations to subscribe to events, configure webhook endpoints, monitor
delivery status, and handle retries for failed webhooks.

## Key Features
- **Event Subscriptions**: Subscribe to specific event types
- **Webhook Delivery**: Automatic delivery of events to configured endpoints
- **Retry Management**: Configurable retry policies for failed deliveries
- **Delivery Monitoring**: Track webhook delivery status and history

## Core Entities

### Events
Event types that can trigger webhooks.

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Event type identifier (e.g., "user.created") |
| `description` | string | Human-readable description |
| `category` | string | Event category (e.g., "user", "order") |
| `version` | string | Event schema version |
| `schema` | object | JSON schema for event payload |

### Subscriptions
Webhook subscription configurations linking events to endpoints.

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique subscription identifier |
| `event_types` | array | Subscribed event types |
| `endpoint_url` | string | Webhook endpoint URL |
| `status` | string | Status: active, paused, disabled |
| `retry_policy` | object | Retry configuration |
| `filters` | array | Event filters to apply |

### Webhooks
Individual webhook delivery attempts with status tracking.

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Webhook identifier |
| `subscription_id` | string | Associated subscription |
| `event_type` | string | Event type that triggered this webhook |
| `status` | string | Delivery status: pending, delivered, failed, retrying |
| `attempts` | integer | Number of delivery attempts |
| `error` | object | Error details if delivery failed |

### Endpoints
Target URLs that receive webhook payloads.

| Field | Type | Description |
|-------|------|-------------|
| `url` | string | Endpoint URL |
| `active_subscriptions` | integer | Number of subscriptions using this endpoint |
| `health_status` | string | Endpoint health: healthy, unhealthy, unknown |

## Retry Policies

Configurable retry behavior for failed webhook deliveries:

- **max_attempts**: Maximum number of retry attempts (default: 3)
- **strategy**: Retry strategy - exponential, linear, or fixed
- **initial_delay_ms**: Initial delay before first retry (default: 1000ms)
- **max_delay_ms**: Maximum delay between retries (default: 60000ms)
- **backoff_multiplier**: Multiplier for exponential backoff (default: 2.0)

## Event Filters

Filter events before delivery using field-based filters:

- **field**: Field path to filter on (e.g., "user.role")
- **operator**: equals, not_equals, in, not_in, contains, starts_with, ends_with
- **value**: Filter value (type depends on operator)

## Webhook Status

Webhook delivery status values:

- **pending**: Waiting to be delivered
- **delivered**: Successfully delivered
- **failed**: Delivery failed after all retries
- **retrying**: Currently retrying delivery

