package com.example.android.nsdchat

import java.net.InetAddress

sealed class ServerEvent {
    data class PortAssigned(val port: Int) : ServerEvent()
    data class ClientConnected(val address: InetAddress, val port: Int) : ServerEvent()
}