#!/bin/bash
# Script to clear ArangoDB onemcp_kb database content
# Usage: ./clear-arangodb.sh [username] [password]

ARANGO_HOST="${ARANGO_HOST:-localhost}"
ARANGO_PORT="${ARANGO_PORT:-8529}"
ARANGO_USER="${1:-root}"
ARANGO_PASS="${2:-}"

echo "Attempting to clear ArangoDB database 'onemcp_kb'..."
echo "Host: $ARANGO_HOST:$ARANGO_PORT"
echo "User: $ARANGO_USER"
echo ""

# Check if arangosh is available
if command -v arangosh &> /dev/null; then
    echo "Using arangosh..."
    if [ -z "$ARANGO_PASS" ]; then
        arangosh --server.endpoint "tcp://$ARANGO_HOST:$ARANGO_PORT" \
            --server.username "$ARANGO_USER" \
            --javascript.execute-string "
            try {
                db._useDatabase('_system');
                if (db._databases().indexOf('onemcp_kb') !== -1) {
                    db._dropDatabase('onemcp_kb');
                    print('✓ Database onemcp_kb dropped successfully');
                } else {
                    print('✗ Database onemcp_kb does not exist');
                }
            } catch (e) {
                print('Error: ' + e.message);
            }
            "
    else
        arangosh --server.endpoint "tcp://$ARANGO_HOST:$ARANGO_PORT" \
            --server.username "$ARANGO_USER" \
            --server.password "$ARANGO_PASS" \
            --javascript.execute-string "
            try {
                db._useDatabase('_system');
                if (db._databases().indexOf('onemcp_kb') !== -1) {
                    db._dropDatabase('onemcp_kb');
                    print('✓ Database onemcp_kb dropped successfully');
                } else {
                    print('✗ Database onemcp_kb does not exist');
                }
            } catch (e) {
                print('Error: ' + e.message);
            }
            "
    fi
else
    echo "arangosh not found. Trying curl with authentication..."
    
    # Try with provided password (or empty if not provided)
    if [ -z "$ARANGO_PASS" ]; then
        RESPONSE=$(curl -s -u "$ARANGO_USER:" -X DELETE "http://$ARANGO_HOST:$ARANGO_PORT/_api/database/onemcp_kb")
    else
        RESPONSE=$(curl -s -u "$ARANGO_USER:$ARANGO_PASS" -X DELETE "http://$ARANGO_HOST:$ARANGO_PORT/_api/database/onemcp_kb")
    fi
    
    # Check if the response indicates success (error:false or result:true or code:200)
    if echo "$RESPONSE" | grep -q '"error":true'; then
        echo "Error: $RESPONSE"
        echo ""
        echo "Please try manually:"
        echo "1. Connect to arangosh: arangosh --server.endpoint tcp://localhost:8529"
        echo "2. Run: db._useDatabase('_system'); db._dropDatabase('onemcp_kb');"
        exit 1
    elif echo "$RESPONSE" | grep -q '"error":false\|"result":true\|"code":200'; then
        echo "✓ Database onemcp_kb dropped successfully"
        echo "Response: $RESPONSE"
    else
        # Unknown response format, show it
        echo "Response: $RESPONSE"
        echo "✓ Database deletion attempted (check response above for confirmation)"
    fi
fi

