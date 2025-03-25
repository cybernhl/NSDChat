package com.example.android.nsdchat;

import java.net.InetAddress;

public interface ServerCallback {
    void onPortSet(int port);
    void onClientConnected(InetAddress address, int port);
}

