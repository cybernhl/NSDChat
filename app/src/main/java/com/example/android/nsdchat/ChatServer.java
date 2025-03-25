package com.example.android.nsdchat;

import android.util.Log;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer {
    private static final String TAG = "ChatServer";
    private ServerCallback mCallback;
    private ServerSocket mServerSocket;
    private Thread mServerThread;

    public ChatServer(ServerCallback callback) {
        mCallback = callback;
        mServerThread = new Thread(new ServerRunnable());
        mServerThread.start();
    }

    public void tearDown() {
        if (mServerThread != null) {
            mServerThread.interrupt();
        }
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing server", e);
            }
        }
    }

    private class ServerRunnable implements Runnable {
        @Override
        public void run() {
            try {
                mServerSocket = new ServerSocket(0);
                mCallback.onPortSet(mServerSocket.getLocalPort());
                while (!Thread.currentThread().isInterrupted()) {
                    Socket socket = mServerSocket.accept();
                    mCallback.onClientConnected(
                            socket.getInetAddress(),
                            socket.getPort()
                    );
                }
            } catch (IOException e) {
                Log.e(TAG, "Server error", e);
            }
        }
    }
}
