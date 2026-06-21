package com.chatapp.signaling;

public enum SignalingType {
    OFFER,          // caller → callee: SDP offer
    ANSWER,         // callee → caller: SDP answer
    ICE_CANDIDATE,  // either side: ICE candidate
    HANGUP          // either side: end call
}