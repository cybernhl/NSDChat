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

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.DiscoveryListener
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdServiceInfo
import android.util.Log

class NsdHelper(context: Context) {
    var mContext: Context?

    var mNsdManager: NsdManager
    var mResolveListener: NsdManager.ResolveListener? = null
    var mDiscoveryListener: DiscoveryListener? = null
    var mRegistrationListener: RegistrationListener? = null

    var mServiceName: String = "NsdChat"

    var chosenServiceInfo: NsdServiceInfo? = null

    init {
        mContext = context
        mNsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    fun initializeNsd() {
        initializeResolveListener()

        //mNsdManager.init(mContext.getMainLooper(), this);
    }

    fun initializeDiscoveryListener() {
        mDiscoveryListener = object : DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {
                Log.d(TAG, "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service discovery success" + service)
                if (service.getServiceType() != SERVICE_TYPE) {
                    Log.d(TAG, "Unknown Service Type: " + service.getServiceType())
                } else if (service.getServiceName() == mServiceName) {
                    Log.d(TAG, "Same machine: " + mServiceName)
                } else if (service.getServiceName().contains(mServiceName)) {
                    mNsdManager.resolveService(service, mResolveListener)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo?) {
                Log.e(TAG, "service lost" + service)
                if (chosenServiceInfo == service) {
                    chosenServiceInfo = null
                }
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.i(TAG, "Discovery stopped: " + serviceType)
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode)
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode)
            }
        }
    }

    fun initializeResolveListener() {
        mResolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Resolve failed" + errorCode)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.e(TAG, "Resolve Succeeded. " + serviceInfo)

                if (serviceInfo.getServiceName() == mServiceName) {
                    Log.d(TAG, "Same IP.")
                    return
                }
                chosenServiceInfo = serviceInfo
            }
        }
    }

    fun initializeRegistrationListener() {
        mRegistrationListener = object : RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                mServiceName = NsdServiceInfo.getServiceName()
                Log.d(TAG, "Service registered: " + mServiceName)
            }

            override fun onRegistrationFailed(arg0: NsdServiceInfo?, arg1: Int) {
                Log.d(TAG, "Service registration failed: " + arg1)
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered: " + arg0.getServiceName())
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.d(TAG, "Service unregistration failed: " + errorCode)
            }
        }
    }

    fun registerService(port: Int) {
        tearDown() // Cancel any previous registration request
        initializeRegistrationListener()
        val serviceInfo = NsdServiceInfo()
        serviceInfo.setPort(port)
        serviceInfo.setServiceName(mServiceName)
        serviceInfo.setServiceType(SERVICE_TYPE)

        mNsdManager.registerService(
            serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener
        )
    }

    fun discoverServices() {
        stopDiscovery() // Cancel any existing discovery request
        initializeDiscoveryListener()
        mNsdManager.discoverServices(
            SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener
        )
    }

    fun stopDiscovery() {
        if (mDiscoveryListener != null) {
            try {
                mNsdManager.stopServiceDiscovery(mDiscoveryListener)
            } finally {
            }
            mDiscoveryListener = null
        }
    }

    fun tearDown() {
        if (mRegistrationListener != null) {
            try {
                mNsdManager.unregisterService(mRegistrationListener)
            } finally {
            }
            mRegistrationListener = null
        }
    }

    companion object {
        const val SERVICE_TYPE: String = "_http._tcp."

        const val TAG: String = "NsdHelper"
    }
}
