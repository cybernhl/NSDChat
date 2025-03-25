package com.example.android.nsdchat;

import java.net.Socket;

public interface ClientCallback {
    void onMessageReceived(String message);
    void onConnectionStateChanged(boolean connected);
}
