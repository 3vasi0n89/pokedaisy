package com.pokedaisy.app.companion.data

/**
 * PC anywhere (GitHub #85): the game's own PC (Bill's / Someone's: WITHDRAW, DEPOSIT, MOVE POKéMON, MOVE ITEMS) from
 * the BAG tab, the way a PC's script opens it - a five-command script run through the game's own script engine:
 * `lockall; special ShowPokemonStorageSystemPC; waitstate; releaseall; end`. The special hands the script context
 * back once the player leaves the PC, so the field comes back as a real PC leaves it. FieldItems.kt's idea: the
 * game's code, called between frames, only where it's found where these tables say.
 *
 * The script sits in [script] (EWRAM no build symbol reaches, past FieldItems' scratch word) while the PC is open.
 * Checked headless on FireRed rev 1 and Emerald: the PC's menu over the field, its boxes, and the field's controls
 * free again after backing out. The other languages, FireRed rev 0 and Ruby / Sapphire are mapped by
 * scripts/port_field_items.py (each entry checked against its gSpecials).
 */
data class PcFns(
    /** ScriptContext_SetupScript(script). */
    val setupScript: Long,
    /** ShowPokemonStorageSystemPC's index in gSpecials (FireRed / LeafGreen 60, Emerald 63). */
    val special: Int,
    /** gSpecials: [special]'s entry must be [pcFn] (thumb bit set). */
    val specials: Long,
    val pcFn: Long,
    /** CRC32 of [setupScript]'s and [pcFn]'s first 32 bytes. */
    val codeCrcs: List<Long>,
    val script: Long = 0x0203FFF4L,
)

/** Retail FireRed rev 1 (its gSpecials and ScriptContext_SetupScript traced from the ROM's script command table). */
val PC_FIRERED_REV1 = PcFns(0x08069AF8L, 60, 0x0815FDD8L, 0x0808C6BDL, listOf(0xAAC00946L, 0x9662F022L))
val PC_LEAFGREEN_REV0 = PcFns(0x08069AE4L, 60, 0x0815FD3CL, 0x0808C67DL, listOf(0xAAC00946L, 0x5333E53EL))
val PC_LEAFGREEN_REV1 = PcFns(0x08069AF8L, 60, 0x0815FDB4L, 0x0808C691L, listOf(0xAAC00946L, 0x5333E53EL))
val PC_EMERALD = PcFns(0x08098EF8L, 63, 0x081DBA64L, 0x080C7579L, listOf(0xEF3698BAL, 0x81188148L))
/** Ruby / Sapphire rev 1 and 2 (the same in both revisions): ShowPokemonStorageSystem, special 60, the same five
 * commands (pokeruby's opcodes are Emerald's). The other languages' come with FieldItemsPortsGen.kt. */
val PC_RUBY = PcFns(0x080655D8L, 60, 0x0814B1B4L, 0x08096121L, listOf(0xBC36478CL, 0x62C153A4L))
val PC_SAPPHIRE = PcFns(0x080655DCL, 60, 0x0814B1B4L, 0x08096121L, listOf(0xBC36478CL, 0xA79046B8L))

/** Whether the running ROM has [pc]'s code and its specials entry where expected. */
fun pcCodeMatches(pc: PcFns, read: (addr: Long, len: Int) -> ByteArray): Boolean {
    fun crc(a: Long) = java.util.zip.CRC32().apply { update(read(a and 1L.inv(), 32)) }.value
    val entry = read(pc.specials + 4L * pc.special, 4).let { b -> (0..3).fold(0L) { v, i -> v or ((b[i].toLong() and 0xFF) shl (8 * i)) } }
    return entry == pc.pcFn && listOf(crc(pc.setupScript), crc(pc.pcFn)) == pc.codeCrcs
}

/** Opens the PC now, on the player's free field; NOT_NOW in a menu, a battle or a script. Emu thread. */
fun openPc(fns: FieldItemFns, busy: Boolean, g: GameCalls): ItemUseResult {
    val pc = fns.pc ?: return ItemUseResult.FAILED
    return runCatching {
        if (!fieldItemCodeMatches(fns, g::read) || !pcCodeMatches(pc, g::read)) return ItemUseResult.FAILED
        if (busy || g.call(fns.fieldControlsLocked) != 0L) return ItemUseResult.NOT_NOW
        // lockall; special N; waitstate; releaseall; end (Gen 3's script opcodes, the same in both engines).
        val script = byteArrayOf(0x69, 0x25, pc.special.toByte(), (pc.special shr 8).toByte(), 0x27, 0x6B, 0x02)
        if (!g.write(pc.script, script)) return ItemUseResult.FAILED
        g.call(pc.setupScript, pc.script.toInt())
        ItemUseResult.USED
    }.getOrElse { ItemUseResult.FAILED }
}
