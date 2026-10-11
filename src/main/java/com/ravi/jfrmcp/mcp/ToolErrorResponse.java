package com.ravi.jfrmcp.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Structured error response returned to MCP clients when a tool operation fails.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolErrorResponse(
    boolean isError,
    String errorCode,
    String message
) {
    public static ToolErrorResponse of(String errorCode, String message) {
        return new ToolErrorResponse(true, errorCode, message);
    }
}
