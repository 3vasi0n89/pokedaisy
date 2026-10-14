package com.pokedaisy.app.companion.data

/**
 * The QUICK MENU's BIKE and ROD: the key items the games let the player register to SELECT, used the way SELECT uses
 * them - the item written into SaveBlock1.registeredItem, SELECT pressed for the game, the player's own registered item
 * put back. The game then does everything itself (mounting, the "can't use that here" line, the fishing cast), so no
 * per-game function table is needed, only where the register is: right after money (u32) and coins (u16) in every
 * retail layout (pokefirered 0x296, pokeemerald / pokeruby 0x496, = [NativeConfig.moneyOff] + 6).
 */
object KeyItems {
    /** FireRed / LeafGreen's BICYCLE; Ruby / Sapphire / Emerald's MACH and ACRO BIKE (the ids are shared). */
    val BIKES = listOf(360, 259, 272)

    /** SUPER, GOOD, OLD ROD: the best first. */
    val RODS = listOf(264, 263, 262)

    /** The money offsets of the layouts the register is known to follow (FireRed's, Emerald's / Ruby's). */
    private val RETAIL_MONEY_OFFS = setOf(0x290L, 0x490L)

    /** The first of [ids] the bag holds, or null. */
    fun firstIn(items: List<ItemView>, ids: List<Int>): Int? = ids.firstOrNull { id -> items.any { it.itemId == id && it.quantity > 0 } }

    /** SaveBlock1.registeredItem's address for [cfg]; null where its layout isn't a retail one. Emu thread. */
    fun registerAddr(cfg: NativeConfig, read: (addr: Long, len: Int) -> ByteArray): Long? {
        if (cfg.moneyOff !in RETAIL_MONEY_OFFS) return null
        val sb1 = if (cfg.staticSaveBlocks) cfg.saveBlock1Ptr else u32le(read(cfg.saveBlock1Ptr, 4), 0)
        if (sb1 !in 0x02000000L until 0x02040000L) return null
        return sb1 + cfg.moneyOff + 6
    }
}

/** The running GBA game's addresses (the Poller's), for the few uses outside a sample (the QUICK MENU's BIKE / ROD);
 * null when none (Game Boy, an unrecognised ROM). */
@Volatile
var activeNativeConfig: NativeConfig? = null
