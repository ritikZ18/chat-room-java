package com.chatapp.signaling;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Serialised as JSON into Message.content for all WEBRTC_* messages.
 * payload field carries:
 *   OFFER/ANSWER       → raw SDP string
 *   ICE_CANDIDATE      → JSON: {"candidate":"...","sdpMLineIndex":0,"sdpMid":"0"}
 *   HANGUP             → empty string
 */
public class SignalingPayload {
    @JsonProperty("signalingType") private String signalingType;
    @JsonProperty("fromUserId")    private String fromUserId;
    @JsonProperty("toUserId")      private String toUserId;
    @JsonProperty("payload")       private String payload;

    public SignalingPayload() {}

    public SignalingPayload(String signalingType, String fromUserId,
                            String toUserId, String payload) {
        this.signalingType = signalingType;
        this.fromUserId    = fromUserId;
        this.toUserId      = toUserId;
        this.payload       = payload;
    }

    public String getSignalingType() { return signalingType; }
    public String getFromUserId()    { return fromUserId; }
    public String getToUserId()      { return toUserId; }
    public String getPayload()       { return payload; }
}