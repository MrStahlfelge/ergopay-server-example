package org.ergoplatform.ergoauth;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;

/**
 * sent to user's wallet to request an authentication
 */
public class ErgoAuthRequest {
    /**
     * message that should be signed
     */
    public String signingMessage;
    /**
     * bae64-encoded serialized sigmaBoolean
     */
    public String sigmaBoolean;
    /**
     * message to show to user
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Nullable
    public String userMessage;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Nullable
    public Severity messageSeverity;

    public String replyTo;

    enum Severity {NONE, INFORMATION, WARNING, ERROR}
}
