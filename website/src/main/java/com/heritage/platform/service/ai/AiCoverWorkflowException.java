package com.heritage.platform.service.ai;

public class AiCoverWorkflowException extends RuntimeException {

    public AiCoverWorkflowException(String message) {
        super(message);
    }

    public AiCoverWorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
