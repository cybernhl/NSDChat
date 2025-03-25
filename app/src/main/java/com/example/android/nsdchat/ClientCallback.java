package com.example.android.nsdchat;

import java.net.Socket;

public interface ClientCallback {
    void setSocket(Socket socket);
    Socket getSocket();
    void updateMessages(String msg, boolean local);
}
