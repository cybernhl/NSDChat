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
package com.example.android.nsdchat

import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

class ChatConnection(private val mUpdateHandler: Handler) {
    private val mChatServer: ChatServer
    private var mChatClient: ChatClient? = null


    private var mSocket: Socket? = null
    var mPort: Int = -1

    init {
        mChatServer = ChatServer(mUpdateHandler)
    }

    fun tearDown() {
        mChatServer.tearDown()
        if (mChatClient != null) {
            mChatClient!!.tearDown()
        }
    }

    fun connectToServer(address: InetAddress?, port: Int) {
        mChatClient = ChatClient(address, port)
    }

    fun sendMessage(msg: String?) {
        if (mChatClient != null) {
            mChatClient!!.sendMessage(msg)
        }
    }


    @Synchronized
    fun updateMessages(msg: String?, local: Boolean) {
        var msg = msg
        Log.e(TAG, "Updating message: " + msg)

        if (local) {
            msg = "me: " + msg
        } else {
            msg = "them: " + msg
        }

        val messageBundle = Bundle()
        messageBundle.putString("msg", msg)

        val message = Message()
        message.setData(messageBundle)
        mUpdateHandler.sendMessage(message)
    }

    @set:Synchronized
    private var socket: Socket?
        get() = mSocket
        private set(socket) {
            Log.d(TAG, "setSocket being called.")
            if (socket == null) {
                Log.d(TAG, "Setting a null socket.")
            }
            if (mSocket != null) {
                if (mSocket!!.isConnected()) {
                    try {
                        mSocket!!.close()
                    } catch (e: IOException) {
                        // TODO(alexlucas): Auto-generated catch block
                        e.printStackTrace()
                    }
                }
            }
            mSocket = socket
        }

    private inner class ChatServer(handler: Handler?) {
        var mServerSocket: ServerSocket? = null
        var mThread: Thread? = null

        init {
            mThread = Thread(ServerThread())
            mThread!!.start()
        }

        fun tearDown() {
            mThread!!.interrupt()
            try {
                mServerSocket!!.close()
            } catch (ioe: IOException) {
                Log.e(TAG, "Error when closing server socket.")
            }
        }

        inner class ServerThread : Runnable {
            override fun run() {
                try {
                    // Since discovery will happen via Nsd, we don't need to care which port is
                    // used.  Just grab an available one  and advertise it via Nsd.
                    mServerSocket = ServerSocket(0)
                    mPort = mServerSocket!!.getLocalPort()

                    while (!Thread.currentThread().isInterrupted()) {
                        Log.d(TAG, "ServerSocket Created, awaiting connection")
                        mSocket = mServerSocket!!.accept()
                        Log.d(TAG, "Connected.")
                        if (mChatClient == null) {
                            val port = mSocket!!.getPort()
                            val address = mSocket!!.getInetAddress()
                            connectToServer(address, port)
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error creating ServerSocket: ", e)
                    e.printStackTrace()
                }
            }
        }
    }

    private inner class ChatClient(address: InetAddress?, port: Int) {
        private val mAddress: InetAddress?
        private val PORT: Int

        private val CLIENT_TAG = "ChatClient"

        private val mSendThread: Thread?
        private var mRecThread: Thread? = null

        init {
            Log.d(CLIENT_TAG, "Creating chatClient")
            this.mAddress = address
            this.PORT = port

            mSendThread = Thread(SendingThread())
            mSendThread.start()
        }

        inner class SendingThread : Runnable {
            var mMessageQueue: BlockingQueue<String?>
            private val QUEUE_CAPACITY = 10

            init {
                mMessageQueue = ArrayBlockingQueue<String?>(QUEUE_CAPACITY)
            }

            override fun run() {
                try {
                    if (mSocket == null) {
                        mSocket = Socket(mAddress, PORT)
                        Log.d(CLIENT_TAG, "Client-side socket initialized.")
                    } else {
                        Log.d(CLIENT_TAG, "Socket already initialized. skipping!")
                    }

                    mRecThread = Thread(ReceivingThread())
                    mRecThread!!.start()
                } catch (e: UnknownHostException) {
                    Log.d(CLIENT_TAG, "Initializing socket failed, UHE", e)
                } catch (e: IOException) {
                    Log.d(CLIENT_TAG, "Initializing socket failed, IOE.", e)
                }

                while (true) {
                    try {
                        val msg = mMessageQueue.take()
                        sendMessage(msg)
                    } catch (ie: InterruptedException) {
                        Log.d(CLIENT_TAG, "Message sending loop interrupted, exiting")
                    }
                }
            }
        }

        inner class ReceivingThread : Runnable {
            override fun run() {
                val input: BufferedReader?
                try {
                    input = BufferedReader(
                        InputStreamReader(
                            mSocket!!.getInputStream()
                        )
                    )
                    while (!Thread.currentThread().isInterrupted()) {
                        var messageStr: String? = null
                        messageStr = input.readLine()
                        if (messageStr != null) {
                            Log.d(CLIENT_TAG, "Read from the stream: " + messageStr)
                            updateMessages(messageStr, false)
                        } else {
                            Log.d(CLIENT_TAG, "The nulls! The nulls!")
                            break
                        }
                    }
                    input.close()
                } catch (e: IOException) {
                    Log.e(CLIENT_TAG, "Server loop error: ", e)
                }
            }
        }

        fun tearDown() {
            try {
                mSocket?.close()
            } catch (ioe: IOException) {
                Log.e(CLIENT_TAG, "Error when closing server socket.")
            }
        }

        fun sendMessage(msg: String?) {
            try {
                val socket: Socket? = mSocket
                if (socket == null) {
                    Log.d(CLIENT_TAG, "Socket is null, wtf?")
                } else if (socket.getOutputStream() == null) {
                    Log.d(CLIENT_TAG, "Socket output stream is null, wtf?")
                }

                val out = PrintWriter(
                    BufferedWriter(
                        OutputStreamWriter(mSocket?.getOutputStream())
                    ), true
                )
                out.println(msg)
                out.flush()
                updateMessages(msg, true)
            } catch (e: UnknownHostException) {
                Log.d(CLIENT_TAG, "Unknown Host", e)
            } catch (e: IOException) {
                Log.d(CLIENT_TAG, "I/O Exception", e)
            } catch (e: Exception) {
                Log.d(CLIENT_TAG, "Error3", e)
            }
            Log.d(CLIENT_TAG, "Client sent message: " + msg)
        }
    }

    companion object {
        private const val TAG = "ChatConnection"
    }
}
