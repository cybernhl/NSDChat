/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.nsdchat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ChatConnection implements ServerCallback, ClientCallback {
    private static final String TAG = "ChatConnection";
    private Handler mUpdateHandler;
    private ChatServer mChatServer;
    private ChatClient mChatClient;
    private Socket mSocket;
    private int mPort = -1;

    public ChatConnection(Handler handler) {
        mUpdateHandler = handler;
        mChatServer = new ChatServer(this);
    }

    public void tearDown() {
        mChatServer.tearDown();
        if (mChatClient != null) {
            mChatClient.tearDown();
        }
    }

    public void connectToServer(InetAddress address, int port) {
        mChatClient = new ChatClient(address, port, this);
    }

    public void sendMessage(String msg) {
        if (mChatClient != null) {
            mChatClient.sendMessage(msg);
        }
    }

    public int getLocalPort() {
        return mPort;
    }

    public void setLocalPort(int port) {
        mPort = port;
    }

    @Override
    public void onPortSet(int port) {
        setLocalPort(port);
    }

    @Override
    public void onClientConnected(InetAddress address, int port) {
        if (mChatClient == null) {
            connectToServer(address, port);
        }
    }

    @Override
    public synchronized void setSocket(Socket socket) {
        Log.d(TAG, "setSocket called");
        if (mSocket != null && mSocket.isConnected()) {
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket", e);
            }
        }
        mSocket = socket;
    }

    @Override
    public Socket getSocket() {
        return mSocket;
    }

    @Override
    public void updateMessages(String msg, boolean local) {
        String prefix = local ? "me: " : "them: ";
        msg = prefix + msg;
        Bundle bundle = new Bundle();
        bundle.putString("msg", msg);
        Message message = new Message();
        message.setData(bundle);
        mUpdateHandler.sendMessage(message);
    }
}