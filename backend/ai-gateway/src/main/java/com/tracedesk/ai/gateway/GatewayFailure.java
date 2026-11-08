package com.tracedesk.ai.gateway;

/** Safe fixed categories: never attach request data, SQL or upstream exceptions. */
public final class GatewayFailure extends RuntimeException {
    public enum Code { INVALID_REQUEST, FORBIDDEN, STALE_SOURCE, AI_DISABLED, AI_UNAVAILABLE,
        AI_QUOTA_EXCEEDED, AI_CAPACITY_EXCEEDED, AI_CIRCUIT_OPEN, STALE_TICKET,
        AI_TIMEOUT, AI_INVALID_RESPONSE, AI_OUTPUT_TOO_LARGE, AI_REJECTED, AI_RATE_LIMITED }
    private final Code code;
    public GatewayFailure(Code code) { super(code.name(), null, false, false); this.code = code; }
    public Code code() { return code; }
}
