package com.timekeeper.bibexpo.ai.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider bibExpoMcpTools(List<McpToolGroup> toolGroups) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(toolGroups.toArray())
                .build();
    }
}
