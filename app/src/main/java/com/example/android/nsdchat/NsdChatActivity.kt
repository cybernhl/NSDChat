package com.example.android.nsdchat

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import com.example.android.nsdchat.databinding.MainBinding

class NsdChatActivity : Activity() {
    companion object {
        const val TAG: String = "NsdChat"
    }

    private lateinit var binding: MainBinding
    var mNsdHelper: NsdHelper? = null
    private var mUpdateHandler: Handler? = null
    var mConnection: ChatConnection? = null

    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Creating chat activity")
        binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())


        mUpdateHandler = object : Handler() {
            override fun handleMessage(msg: Message) {
                val chatLine = msg.getData().getString("msg")
                addChatLine(chatLine)
            }
        }
    }

    fun clickAdvertise(v: View?) {
        // Register service
        if (mConnection!!.mPort > -1) {
            mNsdHelper!!.registerService(mConnection!!.mPort)
        } else {
            Log.d(TAG, "ServerSocket isn't bound.")
        }
    }

    fun clickDiscover(v: View?) {
        mNsdHelper!!.discoverServices()
    }

    fun clickConnect(v: View?) {
        val service = mNsdHelper!!.chosenServiceInfo
        if (service != null) {
            Log.d(TAG, "Connecting.")
            mConnection!!.connectToServer(
                service.host,
                service.port
            )
        } else {
            Log.d(TAG, "No service to connect to!")
        }
    }

    fun clickSend(v: View?) {
        binding.chatInput?.let { messageView ->
            val messageString = messageView.getText().toString()
            if (!messageString.isEmpty()) {
                mConnection!!.sendMessage(messageString)
            }
            messageView.setText("")
        }
    }

    fun addChatLine(line: String?) {
        binding.status.append("\n" + line)
    }

    override fun onStart() {
        Log.d(TAG, "Starting.")
        mConnection = ChatConnection(mUpdateHandler!!)

        mNsdHelper = NsdHelper(this)
        mNsdHelper!!.initializeNsd()
        super.onStart()
    }

    override fun onPause() {
        Log.d(TAG, "Pausing.")
        if (mNsdHelper != null) {
            mNsdHelper!!.stopDiscovery()
        }
        super.onPause()
    }

    override fun onResume() {
        Log.d(TAG, "Resuming.")
        super.onResume()
        if (mNsdHelper != null) {
            mNsdHelper!!.discoverServices()
        }
    }

    // For KitKat and earlier releases, it is necessary to remove the
    // service registration when the application is stopped.  There's
    // no guarantee that the onDestroy() method will be called (we're
    // killable after onStop() returns) and the NSD service won't remove
    // the registration for us if we're killed.
    // In L and later, NsdService will automatically unregister us when
    // our connection goes away when we're killed, so this step is
    // optional (but recommended).
    override fun onStop() {
        Log.d(TAG, "Being stopped.")
        mNsdHelper!!.tearDown()
        mConnection!!.tearDown()
        mNsdHelper = null
        mConnection = null
        super.onStop()
    }

    override fun onDestroy() {
        Log.d(TAG, "Being destroyed.")
        super.onDestroy()
    }
}