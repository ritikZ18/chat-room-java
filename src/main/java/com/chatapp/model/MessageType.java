package com.chatapp.model ; 

public enum MessageType { 
TEXT, JOIN, LEAVE, SYSTEM,

// WebRTC signaling type - route point-to-point, not broadcat to room 

WEBRTC_OFFER,
WEBRTC_ANSWER,
WEBRTC_ICE_CANDIDATE,
WEBRTC_HANGUP
}