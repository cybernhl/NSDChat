package com.example.android.nsdchat;

import android.util.Log;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ChatClient {
    private static final String TAG = "ChatClient";
    private ClientCallback mCallback;
    private InetAddress mAddress;
    private int mPort;
    private SendingRunnable mSendingRunnable;

    public ChatClient(InetAddress address, int port, ClientCallback callback) {
        mAddress = address;
        mPort = port;
        mCallback = callback;
        mSendingRunnable = new SendingRunnable();
        new Thread(mSendingRunnable).start();
    }

    public void tearDown() {
        mSendingRunnable.stop();
        Socket socket = mCallback.getSocket();
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing client", e);
            }
        }
    }

    public void sendMessage(String msg) {
        mSendingRunnable.enqueueMessage(msg);
    }

    private class SendingRunnable implements Runnable {
        private BlockingQueue<String> mQueue = new ArrayBlockingQueue<>(10);
        private volatile boolean mRunning = true;

        public void enqueueMessage(String msg) {
            try {
                mQueue.put(msg);
            } catch (InterruptedException e) {
                Log.e(TAG, "Enqueue interrupted", e);
            }
        }

        public void stop() {
            mRunning = false;
        }

        @Override
        public void run() {
            try {
                Socket socket = mCallback.getSocket();
                if (socket == null) {
                    socket = new Socket(mAddress, mPort);
                    mCallback.setSocket(socket);
                    startReceiving(socket);
                }
                while (mRunning) {
                    String msg = mQueue.take();
                    send(msg, socket);
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Sending error", e);
            }
        }

        private void send(String msg, Socket socket) throws IOException {
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream()), true
            );
            out.println(msg);
            mCallback.updateMessages(msg, true);
        }

        private void startReceiving(Socket socket) {
            new Thread(new ReceivingRunnable(socket)).start();
        }
    }

    private class ReceivingRunnable implements Runnable {
        private Socket mSocket;

        ReceivingRunnable(Socket socket) {
            mSocket = socket;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    mCallback.updateMessages(line, false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Receiving error", e);
            }
        }
    }
}
