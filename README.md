# Gentoro OneMCP

OneMCP is an open-source runtime that makes it easy for AI agents to use your API accurately and efficiently.

You provide your API materials — such as the specification, documentation, and authentication details (collectively called the handbook) — and OneMCP immediately exposes your system through a single, natural-language interface.

It removes the need to handcraft MCP tools or connectors while achieving high performance and low token cost through a smart execution-plan system designed for caching and reuse.

## 🚀 Quick Start


First, install the CLI:

```bash
curl -sSL https://raw.githubusercontent.com/Gentoro-OneMCP/onemcp/main/cli/install.sh | bash
```

Then start chatting (auto-setup wizard launches on first run):

```bash
onemcp chat
```

You'll see an interactive chat interface like this:

```
╔══════════════════════════════════════╗
║     Gentoro OneMCP - Chat Mode       ║
╚══════════════════════════════════════╝

Handbook: ecommerce-api
Provider: openai
MCP URL: http://localhost:8080/mcp
Type 'exit' to quit, 'clear' to clear history, 'switch' to change handbook
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

💡 Mock Server Active - Try These Example Queries:

  > Show me electronics sales in California last quarter.
  > List top customers by revenue.
  > Compare revenue trends by region.
  > What are the top-selling products this month?
  > Show me sales data for New York vs Texas.

Type "help" anytime for more commands.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

? You: Show me sales data for New York vs Texas.
✔ Response received
Agent:
Sales summary for 2024 (grouped by state)

- New York
  - Total sales: $12,340,000
  - Orders: 48,200
  - Average order value (AOV): $256

- Texas
  - Total sales: $9,870,000
  - Orders: 42,500
  - Average order value (AOV): $232

Key takeaways
- New York generated ~25% more total sales than Texas ($12.34M vs $9.87M).
- New York had about 13% more orders and a higher AOV (~$24 higher), suggesting both higher volume and larger order size in NY.
```

## 📚 Documentation

Full documentation is available at [https://onemcp.gentoro.com/docs](https://onemcp.gentoro.com/docs)

### ℹ️ CLI Details

For detailed CLI documentation, all commands, and advanced features, see the [CLI README](cli/README.md).