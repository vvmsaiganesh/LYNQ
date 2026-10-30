package com.flux.relay.ai

import com.flux.relay.data.Message
import com.flux.relay.data.MessageKind
import com.flux.relay.data.MessageStatus

/**
 * Battery-aware policy tiers. Every background decision (how often to scan, whether
 * non-emergency traffic is held, whether online AI calls are allowed) resolves
 * through a single tier, so the behaviour is predictable and explainable to judges.
 */
object BatteryTiers {

    enum class Tier {
        NORMAL,     // >= 50%: full discovery, eager AI
        FRUGAL,     // 30-49%: scan bursts, AI on demand only
        RESERVE,    // 16-29%: long gaps, only urgent traffic relays eagerly
        RESCUE;     // <= 15%: SOS + critical alerts only, everything else queues

        val label: String
            get() = when (this) {
                NORMAL -> "NORMAL"
                FRUGAL -> "FRUGAL"
                RESERVE -> "RESERVE"
                RESCUE -> "RESCUE"
            }

        val scanSeconds: Int
            get() = when (this) {
                NORMAL -> -1        // continuous
                FRUGAL -> 8
                RESERVE -> 8
                RESCUE -> 5
            }

        val gapSeconds: Int
            get() = when (this) {
                FRUGAL -> 50
                RESERVE -> 120
                RESCUE -> 300
                else -> 0
            }
    }

    fun tierFor(level: Int?): Tier = when {
        level == null -> Tier.NORMAL
        level >= 50 -> Tier.NORMAL
        level >= 30 -> Tier.FRUGAL
        level >= 16 -> Tier.RESERVE
        else -> Tier.RESCUE
    }

    /** In RESCUE the app stays useful only for things that save lives. */
    fun shouldSendMessage(message: Message, tier: Tier): Boolean = when (tier) {
        Tier.RESCUE -> message.kind == MessageKind.SOS || (message.aiUrgency ?: 0) >= 4
        Tier.RESERVE -> message.kind != MessageKind.CHAT || (message.aiUrgency ?: 0) >= 2
        else -> true
    }

    /** Online calls are the first thing sacrificed as the battery drains. */
    fun allowsOnlineAi(tier: Tier): Boolean = tier == Tier.NORMAL || tier == Tier.FRUGAL

    /** Low-power radio mode below 30% instead of the previous hard cut at 15%. */
    fun forcesLowPowerRadio(tier: Tier): Boolean = tier == Tier.RESERVE || tier == Tier.RESCUE

    fun describe(tier: Tier): String = when (tier) {
        Tier.NORMAL -> "Battery healthy — full discovery, all traffic flows."
        Tier.FRUGAL -> "Below 50% — scanning in 8-second bursts every 50 seconds."
        Tier.RESERVE -> "Below 30% — long scan gaps; only urgent traffic relays eagerly."
        Tier.RESCUE -> "Below 15% — rescue mode: SOS and critical alerts only. Everything else queues."
    }
}