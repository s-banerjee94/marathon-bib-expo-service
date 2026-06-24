package com.timekeeper.bibexpo.ai.mcp;

/**
 * Marker for a class that exposes AI tools (methods annotated with {@code @Tool}). Every
 * implementing bean is collected once and shared by both the MCP server and the in-app chat,
 * so a new tool group is wired into both surfaces simply by implementing this interface.
 */
public interface McpToolGroup {
}
