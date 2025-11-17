# Inventory Management API

A comprehensive API for managing inventory, warehouses, and stock levels.
This API provides operations to track items, manage warehouses, monitor stock levels,
and handle inventory movements.

## Key Features
- **Item Management**: Create, update, and query inventory items
- **Warehouse Operations**: Manage multiple warehouse locations
- **Stock Tracking**: Real-time stock level monitoring
- **Movement Tracking**: Track inventory transfers and adjustments

## Core Entities

### Items
Product catalog entries with SKUs, descriptions, pricing, and reorder points.

| Field | Type | Description |
|-------|------|-------------|
| `sku` | string | Stock Keeping Unit (unique identifier) |
| `name` | string | Item name |
| `description` | string | Item description |
| `category` | string | Item category |
| `unit_price` | number | Unit price in USD |
| `reorder_point` | integer | Minimum stock level before reorder |
| `reorder_quantity` | integer | Quantity to order when reorder point is reached |

### Warehouses
Storage locations with capacity and address information.

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique warehouse identifier |
| `name` | string | Warehouse name |
| `address` | string | Warehouse address |
| `capacity` | integer | Maximum storage capacity |
| `current_occupancy` | integer | Current items stored |

### Stock
Current inventory levels per item per warehouse.

| Field | Type | Description |
|-------|------|-------------|
| `item_id` | string | Item SKU |
| `warehouse_id` | string | Warehouse identifier |
| `quantity` | integer | Current stock quantity |
| `reserved` | integer | Reserved quantity (pending orders) |
| `available` | integer | Available quantity (quantity - reserved) |

### Movements
Inventory transfers and adjustments between warehouses.

| Field | Type | Description |
|-------|------|-------------|
| `movement_type` | string | Type: transfer, adjustment, receipt, shipment |
| `item_id` | string | Item SKU |
| `quantity` | integer | Quantity moved |
| `from_warehouse_id` | string | Source warehouse (for transfers) |
| `to_warehouse_id` | string | Destination warehouse (for transfers) |

## Movement Types

- **transfer**: Move items between warehouses
- **adjustment**: Adjust stock level within a warehouse (damage, loss, etc.)
- **receipt**: Receive new stock into a warehouse
- **shipment**: Ship items out of a warehouse

