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

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import java.net.InetAddress

class ChatConnection @JvmOverloads constructor(private val handler: Handler? = null) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("ChatConnection", "Coroutine error", throwable)
        mainHandler.post {
            // 发送错误消息到 Handler
            val msg = mainHandler.obtainMessage().apply {
                data.putString("error", throwable.message)
            }
            mainHandler.sendMessage(msg)
        }
    }
    private val coroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + exceptionHandler
    )

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages

    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState

    private val serverEvents = Channel<ServerEvent>()
    private val clientEvents = Channel<ClientEvent>()
    private var localPort = -1
    fun getLocalPort() = localPort

    fun setLocalPort(port: Int) {
        localPort = port
    }

    private val mainHandler by lazy {
        handler ?: Handler(Looper.getMainLooper()).also {
            Log.d("ChatConnection", "Created default main handler")
        }
    }

    private lateinit var chatServer: ChatServer
    private lateinit var chatClient: ChatClient

    init {
//        startEventProcessing()
        coroutineScope.launch {
            messages.collect { messages ->
                sendViaHandler(messages.lastOrNull())
            }
        }
    }

    private fun sendViaHandler(message: String?) {
        message?.let {
            val msg = Message.obtain().apply {
                data.putString("msg", it)
            }
            mainHandler.sendMessage(msg)
        }
    }

    fun registerJavaListener(listener: MessageListener) {
        coroutineScope.launch {
            messages.collect { list ->
                list.lastOrNull()?.let {
                    listener.onNewMessage(it)
                }
            }
        }
    }

    fun startServer() {
        chatServer = ChatServer(object : ServerCallback {
            override fun onPortSet(port: Int) {
                coroutineScope.launch {
                    serverEvents.send(ServerEvent.PortAssigned(port))
                }
            }

            override fun onClientConnected(address: InetAddress, port: Int) {
                coroutineScope.launch {
                    serverEvents.send(ServerEvent.ClientConnected(address, port))
                }
            }
        })
    }

    fun connectToServer(address: InetAddress, port: Int) {
        chatClient = ChatClient(address, port, object : ClientCallback {
            override fun onMessageReceived(message: String) {
                coroutineScope.launch {
                    clientEvents.send(ClientEvent.MessageReceived(message))
                }
            }

            override fun onConnectionStateChanged(connected: Boolean) {
                coroutineScope.launch {
                    clientEvents.send(
                        if (connected) ClientEvent.Connected
                        else ClientEvent.Disconnected
                    )
                }
            }
        })
    }

    fun sendMessage(message: String) {
        coroutineScope.launch {
            chatClient?.sendMessage(message)
            addMessage("me: $message")
        }
    }

    fun tearDown() {
        coroutineScope.cancel()
        chatServer.tearDown()
        chatClient.tearDown()
    }

    private fun startEventProcessing() {
        coroutineScope.launch {
            launch { processServerEvents() }
            launch { processClientEvents() }
        }
    }

    private suspend fun processServerEvents() {
        serverEvents.consumeAsFlow().collect { event ->
            when (event) {
                is ServerEvent.PortAssigned -> handlePortAssigned(event.port)
                is ServerEvent.ClientConnected -> handleClientConnected(event.address, event.port)
            }
        }
    }

    private suspend fun processClientEvents() {
        clientEvents.consumeAsFlow().collect { event ->
            when (event) {
                is ClientEvent.MessageReceived -> addMessage("them: ${event.message}")
                ClientEvent.Connected -> updateConnectionState("Connected")
                ClientEvent.Disconnected -> updateConnectionState("Disconnected")
            }
        }
    }

    private fun handlePortAssigned(port: Int) {
        Log.d("ChatConnection", "Server port assigned: $port")
    }

    private fun handleClientConnected(address: InetAddress, port: Int) {
        Log.d("ChatConnection", "Client connected from $address:$port")
        connectToServer(address, port)
    }

    private fun addMessage(message: String) {
        _messages.update { it + message }
    }

    private fun updateConnectionState(state: String) {
        _connectionState.value = state
    }
}