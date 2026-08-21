package com.musync.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Maps the active network type to a Musync stream quality parameter.
 *
 * Quality tiers:
 *   "saver"    -> 48 kbps AAC (2G / very slow connections)
 *   "low"      -> 48 kbps AAC (3G / weak 4G)       [default]
 *   "standard" -> 128 kbps AAC (strong 4G / slow Wi-Fi)
 *   "high"     -> 160+ kbps (5G / fast Wi-Fi)
 */
object NetworkQualityHelper {

    private const val TAG = "NetworkQualityHelper"

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Returns the recommended stream quality string based on current network conditions.
     * Falls back to "low" when network type cannot be determined.
     */
    fun getRecommendedQuality(context: Context, userPreferredQuality: String = "low"): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = cm.activeNetwork ?: run {
            Log.d(TAG, "No active network - falling back to saver quality")
            return "saver"
        }

        val caps = cm.getNetworkCapabilities(network) ?: return "low"

        return when {
            // Wi-Fi or Ethernet: respect user setting
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                Log.d(TAG, "Wi-Fi/Ethernet detected - using user preferred quality: $userPreferredQuality")
                userPreferredQuality
            }

            // Cellular: map by generation
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val cellQuality = getCellularQuality(context)
                // Never exceed the user's preference
                val resolved = minQuality(cellQuality, userPreferredQuality)
                Log.d(TAG, "Cellular - cell tier: $cellQuality, user pref: $userPreferredQuality -> resolved: $resolved")
                resolved
            }

            else -> minQuality("low", userPreferredQuality)
        }
    }

    private fun getCellularQuality(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tm.dataNetworkType
            } else {
                tm.networkType
            }
            when (networkType) {
                // 2G
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_IDEN -> "saver"
                // 3G
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_EVDO_B,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_HSPAP -> "low"
                // 4G LTE
                TelephonyManager.NETWORK_TYPE_LTE -> "standard"
                // 5G NR
                TelephonyManager.NETWORK_TYPE_NR -> "high"
                else -> "low"
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_PHONE_STATE not granted - falling back to low")
            "low"
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine cellular type: ${e.message}")
            "low"
        }
    }

    /** Returns the more conservative of two quality strings. Order: saver < low < standard < high */
    private fun minQuality(a: String, b: String): String {
        val rank = mapOf("saver" to 0, "low" to 1, "standard" to 2, "high" to 3)
        return if ((rank[a] ?: 1) <= (rank[b] ?: 1)) a else b
    }
}