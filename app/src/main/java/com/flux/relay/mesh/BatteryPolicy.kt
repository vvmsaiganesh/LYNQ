package com.flux.relay.mesh

import android.content.Context
import android.os.BatteryManager

/**
 * Battery policy. Low power is manual, but a phone under 15% is put there automatically:
 * a rescue device that dies is worse than one that discovers peers slowly.
 */
object BatteryPolicy {

    fun level(context: Context): Int? = runCatching {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrNull()?.takeIf { it in 0..100 }

    fun shouldConserve(context: Context): Boolean = (level(context) ?: 100) <= 15
}
