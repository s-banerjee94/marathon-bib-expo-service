package com.timekeeper.bibexpo.ai.mcp;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Logs every AI tool call once, by tool name: all MCP calls share one URI, so the request log context
 * alone cannot tell them apart. A call refused by this app's own rules logs at WARN without a stack; any
 * other failure is a bug and logs at ERROR with its cause.
 */
@Aspect
@Component
@Slf4j
public class McpToolLoggingAspect {

    @Around("@annotation(tool) && within(com.timekeeper.bibexpo.ai.mcp..*)")
    public Object logCall(ProceedingJoinPoint joinPoint, Tool tool) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            log.atInfo().addKeyValue("event", "mcp.tool.called").addKeyValue("tool", tool.name())
                    .log("AI tool called");
            return result;
        } catch (RuntimeException e) {
            // This app's exceptions, and argument checks, carry a message meant for the user.
            if (e instanceof IllegalArgumentException || e.getClass().getPackageName().startsWith("com.timekeeper.")) {
                log.atWarn().addKeyValue("event", "mcp.tool.rejected").addKeyValue("tool", tool.name())
                        .addKeyValue("exception", e.getClass().getSimpleName()).addKeyValue("reason", e.getMessage())
                        .log("AI tool call rejected");
            } else {
                log.atError().addKeyValue("event", "mcp.tool.failed").addKeyValue("tool", tool.name())
                        .setCause(e).log("AI tool call failed");
            }
            throw e;
        }
    }
}
