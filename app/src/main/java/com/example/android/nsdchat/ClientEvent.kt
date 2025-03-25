package com.example.android.nsdchat

sealed class ClientEvent {
    data class MessageReceived(val message: String) : ClientEvent()
    data object Connected : ClientEvent()
    data object Disconnected : ClientEvent()
}