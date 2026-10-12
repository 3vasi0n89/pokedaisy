package com.pokedaisy.app.companion.data

/**
 * FLY from the MAP tab, the way the game does it (FieldItems.kt's idea: the game's own functions, called between
 * frames). pokefirered's party menu: FIELD_MOVE_FLY needs FLAG_BADGE01_GET + its index (the Thunder Badge) and
 * SetUpFieldMove_Fly (the map type allows flying), then the Fly map - a town whose GetMapsecType is VISITED (its
 * FLAG_WORLD_MAP_* set) - and its choice: SetFlyWarpDestination(mapsec), which sets the warp and hands the game
 * CB2_ReturnToField with FieldCallback_UseFly. The game then plays its own Fly: the cut-in with the Pokémon under the
 * party cursor (gPartyMenu.slotId), the flight out, the warp and the landing.
 *
 * The companion does the menus' part: LockPlayerFieldControls and FadeScreen(FADE_TO_BLACK) - what closing the
 * Fly map left - then, once the screen is black, the cursor on the Pokémon that knows Fly and SetFlyWarpDestination.
 * Checked headless on the user's FireRed save: the cut-in, the flight and the landing outside the chosen town's
 * Pokémon Center, controls free after.
 */
data class FlyFns(
    val flagGet: Long,
    /** FLAG_BADGE03_GET (FireRed / LeafGreen: the Thunder Badge). */
    val badgeFlag: Int,
    /** SetUpFieldMove_Fly: TRUE where the map type allows flying (outdoors). */
    val setUpFieldMoveFly: Long,
    /** GetMapsecType(mapsec): [visited] for a town the player has been to. */
    val getMapsecType: Long,
    /** FadeScreen(mode, delay): FADE_TO_BLACK is 1. */
    val fadeScreen: Long,
    val lockControls: Long,
    /** UnlockPlayerFieldControls: only to hand the field back if the second step can't go on. */
    val unlockControls: Long,
    /** SetFlyWarpDestination(mapsec): the Fly map's choice. */
    val setFlyWarpDestination: Long,
    /** gPartyMenu.slotId: the Pokémon the Fly cut-in shows (GetCursorSelectionMonId). */
    val partyMenuSlotId: Long,
    /** sMapFlyDestinations: one {map group, map num, heal location} per map section from [firstMapsec]. */
    val destinations: Long,
    val firstMapsec: Int,
    val count: Int,
    /** CRC32 of each function's first 32 bytes: flagGet, setUpFieldMoveFly, getMapsecType, fadeScreen,
     * lockControls, unlockControls, setFlyWarpDestination. */
    val codeCrcs: List<Long>,
    /** The GetMapsecType answers the Fly map takes (FireRed: VISITED; Emerald: CITY_CANFLY, BATTLE_FRONTIER). */
    val flyTypes: Set<Int> = setOf(2),
    /** Map sections past [destinations]' table that can still be flown to (Emerald's Battle Frontier). */
    val extraMapsecs: List<Int> = emptyList(),
    /** Emerald's way of ending the Fly map, in place of [setFlyWarpDestination]. */
    val healWarp: HealWarp? = null,
    val flyMove: Int = 19,
) {
    val functions get() = listOf(flagGet, setUpFieldMoveFly, getMapsecType, fadeScreen, lockControls, unlockControls) +
        (healWarp?.let { listOf(it.toHealLocation, it.toMapWarp, it.returnToField) } ?: listOf(setFlyWarpDestination))
}

/**
 * Emerald's CB_ExitFlyMap: SetWarpDestinationToHealLocation(the section's heal location, [special] ones first;
 * Littleroot by the player's gender), else SetWarpDestinationToMapWarp(group, num, WARP_ID_NONE) - then
 * ReturnToFieldFromFlyMapSelect (CB2_ReturnToField + FieldCallback_UseFly).
 */
data class HealWarp(
    val toHealLocation: Long,
    val toMapWarp: Long,
    val returnToField: Long,
    /** gSaveBlock2Ptr; playerGender at +8. */
    val saveBlock2Ptr: Long,
    val special: Map<Int, Int>,
    val genderMapsec: Int,
    val maleHeal: Int,
    val femaleHeal: Int,
)

/** Retail FireRed rev 1: pret's pokefirered built clean as firered_rev1 (byte for byte retail) and its ELF. */
val FLY_FIRERED_REV1 = FlyFns(
    flagGet = 0x0806E6E4L, badgeFlag = 0x822, setUpFieldMoveFly = 0x08124B04L, getMapsecType = 0x080C35F0L,
    fadeScreen = 0x0807A82CL, lockControls = 0x08069954L, unlockControls = 0x08069960L, setFlyWarpDestination = 0x080C5290L,
    partyMenuSlotId = 0x0203B0A9L, destinations = 0x083F2F50L, firstMapsec = 0x58, count = 108,
    codeCrcs = listOf(0x07BEBC3AL, 0xCD8961DFL, 0x348C5B9FL, 0xF9959006L, 0x0111A86EL, 0xBAB654BAL, 0x70EA1DFCL),
)

/** Retail LeafGreen rev 0 / rev 1: built clean as leafgreen / leafgreen_rev1. */
val FLY_LEAFGREEN_REV0 = FLY_FIRERED_REV1.copy(
    flagGet = 0x0806E6D0L, setUpFieldMoveFly = 0x08124A64L, getMapsecType = 0x080C35B0L, fadeScreen = 0x0807A7ECL,
    lockControls = 0x08069940L, unlockControls = 0x0806994CL, setFlyWarpDestination = 0x080C5250L, destinations = 0x083F2D1CL,
    codeCrcs = listOf(0x07BEBC3AL, 0x5B68C93AL, 0xE4D65E08L, 0xF9959006L, 0x0111A86EL, 0xBAB654BAL, 0x059815BFL),
)
val FLY_LEAFGREEN_REV1 = FLY_FIRERED_REV1.copy(
    flagGet = 0x0806E6E4L, setUpFieldMoveFly = 0x08124ADCL, getMapsecType = 0x080C35C4L, fadeScreen = 0x0807A800L,
    lockControls = 0x08069954L, unlockControls = 0x08069960L, setFlyWarpDestination = 0x080C5264L, destinations = 0x083F2D8CL,
    codeCrcs = listOf(0x07BEBC3AL, 0xDBE480BDL, 0x50C0C136L, 0xF9959006L, 0x0111A86EL, 0xBAB654BAL, 0x059815BFL),
)

/** Retail Emerald: pret's pokeemerald built clean (byte for byte retail) and its ELF. sMapHealLocations covers map
 * sections 0..49; Ever Grande flies to the city (the game's League spot needs the cursor's top tile). */
val FLY_EMERALD = FlyFns(
    flagGet = 0x0809D790L, badgeFlag = 0x86C, setUpFieldMoveFly = 0x081B5884L, getMapsecType = 0x08123D58L,
    fadeScreen = 0x080ABCD0L, lockControls = 0x08098E54L, unlockControls = 0x08098E60L, setFlyWarpDestination = 0,
    partyMenuSlotId = 0x0203CED1L, destinations = 0x085A1E3CL, firstMapsec = 0, count = 50,
    codeCrcs = listOf(0xBCF97BF3L, 0x13BE1A7AL, 0x4B625BC4L, 0xF9959006L, 0xB9FB85A7L, 0xB037C8AFL, 0x74DBCE3AL, 0x2F1D18CAL, 0xA284286DL),
    flyTypes = setOf(2, 4), extraMapsecs = listOf(58),
    healWarp = HealWarp(
        toHealLocation = 0x08084CCCL, toMapWarp = 0x08084C28L, returnToField = 0x080B69DCL, saveBlock2Ptr = 0x03005D90L,
        // MAPSEC_EVER_GRANDE_CITY, MAPSEC_BATTLE_FRONTIER (HEAL_LOCATION_EVER_GRANDE_CITY, _BATTLE_FRONTIER_OUTSIDE_EAST)
        special = mapOf(15 to 11, 58 to 22), genderMapsec = 0, maleHeal = 12, femaleHeal = 13,
    ),
)

/** Whether the running ROM has [fly]'s functions where they're expected. */
fun flyCodeMatches(fly: FlyFns, read: (addr: Long, len: Int) -> ByteArray): Boolean =
    fly.functions.zip(fly.codeCrcs).all { (fn, crc) -> java.util.zip.CRC32().apply { update(read(fn, 32)) }.value == crc }

enum class FlyResult {
    /** Ready ([FlyTargets.destinations]) / on its way. */
    OK,
    NOT_NOW, NO_BADGE,
    /** NOT_NOW in a battle (the app's answer, like [ItemUseResult.IN_BATTLE]). */
    IN_BATTLE,
    /** Not from this map (indoors, a cave). */
    CANT_FLY_HERE,
    /** Nobody in the party knows Fly (any more). */
    NO_FLYER,
    FAILED,
}

/** Where FLY can go now: the visited towns' map sections, and the Pokémon that would carry the player. */
data class FlyTargets(
    val result: FlyResult,
    val destinations: List<Int> = emptyList(),
    val flyerSlot: Int = -1,
    val flyerPersonality: Long = 0,
)

private const val MON_SIZE_FLY = 100
private const val GET_MON_PERSONALITY = 0
private const val GET_MON_MOVE1 = 13
private const val GET_MON_IS_EGG = 45

/** The first party Pokémon that knows Fly (not an egg): its slot and personality, or null. */
private fun flyer(fns: FieldItemFns, fly: FlyFns, g: GameCalls): Pair<Int, Long>? {
    val p = fns.party ?: return null
    val count = (g.read(p.partyCount, 1)[0].toInt() and 0xFF).coerceAtMost(6)
    for (slot in 0 until count) {
        val mon = (p.party + slot * MON_SIZE_FLY).toInt()
        if (g.call(p.getMonData, mon, GET_MON_IS_EGG) != 0L) continue
        if ((0 until 4).any { g.call(p.getMonData, mon, GET_MON_MOVE1 + it) == fly.flyMove.toLong() }) {
            return slot to g.call(p.getMonData, mon, GET_MON_PERSONALITY)
        }
    }
    return null
}

/** Why FLY can't start now, or null: the party menu's own checks, in its order. */
private fun flyGate(fns: FieldItemFns, fly: FlyFns, busy: Boolean, g: GameCalls): FlyResult? = when {
    !fieldItemCodeMatches(fns, g::read) || !flyCodeMatches(fly, g::read) -> FlyResult.FAILED
    busy || g.call(fns.fieldControlsLocked) != 0L -> FlyResult.NOT_NOW
    g.call(fly.flagGet, fly.badgeFlag) != 1L -> FlyResult.NO_BADGE
    g.call(fly.setUpFieldMoveFly) != 1L -> FlyResult.CANT_FLY_HERE
    else -> null
}

/** FLY's destinations right now (the Fly map's visited towns) and its Pokémon. Emu thread. */
fun flyTargets(fns: FieldItemFns, busy: Boolean, g: GameCalls): FlyTargets {
    val fly = fns.fly ?: return FlyTargets(FlyResult.FAILED)
    return runCatching {
        flyGate(fns, fly, busy, g)?.let { return FlyTargets(it) }
        val (slot, personality) = flyer(fns, fly, g) ?: return FlyTargets(FlyResult.NO_FLYER)
        val table = g.read(fly.destinations, fly.count * 3)
        val towns = ((0 until fly.count).filter { i ->
            (table[i * 3].toInt() or table[i * 3 + 1].toInt() or table[i * 3 + 2].toInt()) != 0
        }.map { fly.firstMapsec + it } + fly.extraMapsecs).filter { g.call(fly.getMapsecType, it).toInt() in fly.flyTypes }
        FlyTargets(FlyResult.OK, towns, slot, personality)
    }.getOrElse { FlyTargets(FlyResult.FAILED) }
}

/**
 * FLY's first step, to [mapsec]: the checks again (the destination still visited, the Pokémon still there), then
 * what closing the Fly map leaves - controls locked, the screen fading to black. [flyFinish] follows once it's
 * black. Emu thread.
 */
fun flyStart(fns: FieldItemFns, mapsec: Int, target: FlyTargets, busy: Boolean, g: GameCalls): FlyResult {
    val fly = fns.fly ?: return FlyResult.FAILED
    return runCatching {
        flyGate(fns, fly, busy, g)?.let { return it }
        val known = mapsec - fly.firstMapsec in 0 until fly.count || mapsec in fly.extraMapsecs
        if (!known || g.call(fly.getMapsecType, mapsec).toInt() !in fly.flyTypes) return FlyResult.FAILED
        val (slot, personality) = flyer(fns, fly, g) ?: return FlyResult.NO_FLYER
        if (slot != target.flyerSlot || personality != target.flyerPersonality) return FlyResult.NO_FLYER
        g.call(fly.lockControls)
        g.call(fly.fadeScreen, 1, 0)
        FlyResult.OK
    }.getOrElse { FlyResult.FAILED }
}

/** FLY's second step: the cut-in's Pokémon under the party cursor, then the Fly map's choice; the game flies.
 * If that can't happen, the field comes back as it was (faded in, controls free). */
fun flyFinish(fns: FieldItemFns, mapsec: Int, target: FlyTargets, g: GameCalls): Boolean {
    val fly = fns.fly ?: return false
    val ok = runCatching {
        g.write(fly.partyMenuSlotId, byteArrayOf(target.flyerSlot.toByte())) && warp(fly, mapsec, g)
    }.getOrDefault(false)
    if (!ok) runCatching { g.call(fly.fadeScreen, 0, 0); g.call(fly.unlockControls) }
    return ok
}

private fun warp(fly: FlyFns, mapsec: Int, g: GameCalls): Boolean {
    val h = fly.healWarp ?: return g.call(fly.setFlyWarpDestination, mapsec) >= 0
    fun u32(a: Long) = g.read(a, 4).let { b -> (0..3).fold(0L) { v, i -> v or ((b[i].toLong() and 0xFF) shl (8 * i)) } }
    val i = mapsec - fly.firstMapsec
    val entry = if (i in 0 until fly.count) g.read(fly.destinations + i * 3, 3).map { it.toInt() and 0xFF } else listOf(0, 0, 0)
    val heal = h.special[mapsec] ?: if (mapsec == h.genderMapsec) {
        if (g.read(u32(h.saveBlock2Ptr) + 8, 1)[0].toInt() == 0) h.maleHeal else h.femaleHeal
    } else entry[2]
    val ok = if (heal != 0) g.call(h.toHealLocation, heal) >= 0
    else entry[0] or entry[1] != 0 && g.call(h.toMapWarp, entry[0], entry[1], -1) >= 0
    return ok && g.call(h.returnToField) >= 0
}

/** Frames [flyStart]'s fade takes to reach black before [flyFinish]: FadeScreen at delay 0, with room to spare. */
const val FLY_FADE_FRAMES = 30
