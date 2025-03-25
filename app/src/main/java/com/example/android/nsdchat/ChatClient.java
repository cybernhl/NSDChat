package com.example.android.nsdchat;

import android.util.Log;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ChatClient {
    private static final String TAG = "ChatClient";
    private final ClientCallback callback;
    private final InetAddress address;
    private final int port;

    private Socket socket; // 新增 Socket 成员变量
    private SendingThread sendingThread;
    private ReceivingThread receivingThread;

    public ChatClient(InetAddress address, int port, ClientCallback callback) {
        this.address = address;
        this.port = port;
        this.callback = callback;
        startConnection();
    }

    private void startConnection() {
        new Thread(() -> {
            try {
                socket = new Socket(address, port);
                callback.onConnectionStateChanged(true);

                // 启动发送和接收线程
                sendingThread = new SendingThread(socket);
                receivingThread = new ReceivingThread(socket);
                new Thread(sendingThread).start();
                new Thread(receivingThread).start();

            } catch (IOException e) {
                Log.e(TAG, "Connection failed", e);
                callback.onConnectionStateChanged(false);
            }
        }).start();
    }

    public void tearDown() {
        try {
            if (socket != null) {
                socket.close();
            }
            if (sendingThread != null) {
                sendingThread.stop();
            }
            callback.onConnectionStateChanged(false);
        } catch (IOException e) {
            Log.e(TAG, "Teardown error", e);
        }
    }

    public void sendMessage(String msg) {
        if (sendingThread != null) {
            sendingThread.enqueueMessage(msg);
        }
    }

    private class SendingThread implements Runnable {
        private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
        private volatile boolean running = true;
        private final PrintWriter writer;

        public SendingThread(Socket socket) throws IOException {
            this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        }

        public void enqueueMessage(String msg) {
            if (running) {
                queue.offer(msg);
            }
        }

        public void stop() {
            running = false;
            writer.close();
        }

        @Override
        public void run() {
            try {
                while (running) {
                    String msg = queue.take();
                    writer.println(msg);
                    Log.d(TAG, "Sent: " + msg);
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "Sending interrupted");
            }
        }
    }

    private class ReceivingThread implements Runnable {
        private final BufferedReader reader;

        public ReceivingThread(Socket socket) throws IOException {
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        @Override
        public void run() {
            try {
                String message;
                while ((message = reader.readLine()) != null) {
                    callback.onMessageReceived(message);
                }
            } catch (IOException e) {
                Log.e(TAG, "Receiving error", e);
            } finally {
                callback.onConnectionStateChanged(false);
            }
        }
    }
}

