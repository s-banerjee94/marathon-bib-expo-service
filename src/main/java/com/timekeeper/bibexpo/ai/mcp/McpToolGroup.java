package com.timekeeper.bibexpo.ai.mcp;

/**
 * Marker for a class that exposes AI tools (methods annotated with {@code @Tool}). Every
 * implementing bean is collected once and served by the MCP server, so a new tool group is
 * wired in simply by implementing this interface.
 */
public interface McpToolGroup {
}
