package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.CRC32

/** FLY's rules (Fly.kt) against a fake game, and FireRed rev 1's table against its ROM. */
class FlyTest {
    private class Game : GameCalls {
        val zero = CRC32().apply { update(ByteArray(32)) }.value
        val fly = FLY_FIRERED_REV1.copy(codeCrcs = List(7) { zero })
        val fns = FIELD_ITEMS_FIRERED_REV1.copy(
            codeCrcs = List(5) { zero }, party = FIELD_ITEMS_FIRERED_REV1.party!!.copy(codeCrcs = List(5) { zero }), fly = fly,
        )
        val p = fns.party!!
        var locked = false
        var badge = true
        var outdoors = true
        val visited = mutableSetOf(0x58, 0x5B) // PALLET TOWN, CERULEAN CITY
        /** Each slot: personality, moves, egg. */
        val party = mutableListOf(Triple(0x111L, intArrayOf(33, 45, 0, 0), false), Triple(0x222L, intArrayOf(19, 0, 0, 0), false))
        val calls = mutableListOf<String>()
        var flewTo = -1
        val ram = mutableMapOf<Long, Byte>()

        override fun call(fn: Long, a0: Int, a1: Int, a2: Int, a3: Int): Long = when (fn) {
            fns.fieldControlsLocked -> if (locked) 1 else 0
            fly.flagGet -> { assertEquals(fly.badgeFlag, a0); if (badge) 1 else 0 }
            fly.setUpFieldMoveFly -> if (outdoors) 1 else 0
            fly.getMapsecType -> if (a0 in visited) 2 else 1
            fly.lockControls -> { calls += "lock"; locked = true; 1 }
            fly.unlockControls -> { calls += "unlock"; locked = false; 1 }
            fly.fadeScreen -> { calls += "fade $a0"; 0 }
            fly.setFlyWarpDestination -> { calls += "dest $a0"; flewTo = a0; 0 }
            p.getMonData -> {
                val (pid, moves, egg) = party[((a0.toLong() and 0xFFFFFFFFL) - p.party).toInt() / 100]
                when (a1) { 0 -> pid; in 13..16 -> moves[a1 - 13].toLong(); 45 -> if (egg) 1 else 0; else -> error("field $a1") }
            }
            else -> -1
        }

        override fun read(addr: Long, len: Int): ByteArray = when {
            addr == p.partyCount -> byteArrayOf(party.size.toByte())
            addr == fly.destinations -> ByteArray(len) { 1 } // every map section has a destination
            addr >= 0x08000000L -> ByteArray(len)
            else -> ByteArray(len) { ram[addr + it] ?: 0 }
        }

        override fun write(addr: Long, bytes: ByteArray): Boolean { bytes.forEachIndexed { i, b -> ram[addr + i] = b }; return true }
    }

    @Test
    fun `the visited towns and the Pokemon that knows Fly`() {
        val g = Game()
        val t = flyTargets(g.fns, busy = false, g = g)
        assertEquals(FlyResult.OK, t.result)
        assertEquals(listOf(0x58, 0x5B), t.destinations)
        assertEquals(1 to 0x222L, t.flyerSlot to t.flyerPersonality)
    }

    @Test
    fun `the party menu's checks, in its order`() {
        val g = Game()
        assertEquals(FlyResult.NOT_NOW, flyTargets(g.fns, busy = true, g = g).result)
        g.badge = false
        assertEquals(FlyResult.NO_BADGE, flyTargets(g.fns, false, g).result)
        g.badge = true; g.outdoors = false
        assertEquals(FlyResult.CANT_FLY_HERE, flyTargets(g.fns, false, g).result)
        g.outdoors = true
        g.party[1] = Triple(0x222L, intArrayOf(19, 0, 0, 0), true) // an egg doesn't fly
        assertEquals(FlyResult.NO_FLYER, flyTargets(g.fns, false, g).result)
        g.locked = true
        assertEquals(FlyResult.NOT_NOW, flyTargets(g.fns, false, g).result)
    }

    @Test
    fun `flying locks the field, fades to black, then hands the game the Fly map's choice`() {
        val g = Game()
        val t = flyTargets(g.fns, false, g)
        assertEquals(FlyResult.OK, flyStart(g.fns, 0x5B, t, false, g))
        assertEquals(listOf("lock", "fade 1"), g.calls)
        assertTrue(flyFinish(g.fns, 0x5B, t, g))
        assertEquals(0x5B, g.flewTo)
        // The cut-in's Pokémon: the one that knows Fly, under gPartyMenu.slotId.
        assertEquals(1, g.read(g.fly.partyMenuSlotId, 1)[0].toInt())
    }

    @Test
    fun `a town not visited, or a party that changed, doesn't fly`() {
        val g = Game()
        val t = flyTargets(g.fns, false, g)
        assertEquals(FlyResult.FAILED, flyStart(g.fns, 0x59, t, false, g)) // VIRIDIAN: not visited
        g.party[1] = Triple(0x999L, intArrayOf(19, 0, 0, 0), false)
        assertEquals(FlyResult.NO_FLYER, flyStart(g.fns, 0x5B, t, false, g))
        assertTrue(g.calls.isEmpty())
    }

    @Test
    fun `a second step that can't go on hands the field back`() {
        val g = Game()
        val t = flyTargets(g.fns, false, g)
        flyStart(g.fns, 0x5B, t, false, g)
        val broken = g.fns.copy(fly = g.fly.copy(setFlyWarpDestination = 0x0BADL))
        assertFalse(flyFinish(broken, 0x5B, t, g))
        assertEquals(listOf("lock", "fade 1", "fade 0", "unlock"), g.calls)
        assertFalse(g.locked)
    }

    @Test
    fun `each game's FLY code is where its table says, and nowhere else`() {
        val lg0 = File(RETAIL_ROM_DIR + "PokemonLeafGreenVersion.gba")
        if (lg0.isFile) assertTrue(flyCodeMatches(FLY_LEAFGREEN_REV0) { a, n -> lg0.readBytes().copyOfRange((a - 0x08000000).toInt(), (a - 0x08000000).toInt() + n) })
        val roms = listOf("Pokemon - FireRed Version (USA, Europe) (Rev 1).gba", "Pokemon - LeafGreen Version (USA, Europe) (Rev 1).gba",
            "Pokemon - Emerald Version (USA, Europe).gba").map { File(RETAIL_ROM_DIR + it) }
        assumeTrue(roms.all { it.isFile })
        val reads = roms.map { f -> f.readBytes().let { b -> { a: Long, n: Int -> b.copyOfRange((a - 0x08000000).toInt(), (a - 0x08000000).toInt() + n) } } }
        assertTrue(flyCodeMatches(FLY_FIRERED_REV1, reads[0]))
        assertFalse(flyCodeMatches(FLY_FIRERED_REV1, reads[1]))
        assertFalse(flyCodeMatches(FLY_FIRERED_REV1, reads[2]))
        assertTrue(fieldItemSupport(FIELD_ITEMS_FIRERED_REV1, reads[0])!!.canFly)
        assertTrue(flyCodeMatches(FLY_LEAFGREEN_REV1, reads[1]))
        assertTrue(flyCodeMatches(FLY_EMERALD, reads[2]))
        assertFalse(flyCodeMatches(FLY_EMERALD, reads[0]))
        // sMapHealLocations: LITTLEROOT TOWN is MAP_LITTLEROOT_TOWN (0.9), HEAL_LOCATION_LITTLEROOT_TOWN_BRENDANS_HOUSE_2F (1).
        assertEquals(listOf(0, 9, 1), reads[2](FLY_EMERALD.destinations, 3).map { it.toInt() })
        // sMapFlyDestinations: PALLET TOWN's entry is MAP_PALLET_TOWN (3.0) with HEAL_LOCATION_PALLET_TOWN (1).
        assertEquals(listOf(3, 0, 1), reads[0](FLY_FIRERED_REV1.destinations, 3).map { it.toInt() })
    }

    @Test
    fun `Emerald warps by heal location, Littleroot by the player's gender`() {
        val calls = mutableListOf<String>()
        val h = FLY_EMERALD.healWarp!!
        val g = object : GameCalls {
            var female = false
            override fun call(fn: Long, a0: Int, a1: Int, a2: Int, a3: Int): Long {
                when (fn) { h.toHealLocation -> calls += "heal $a0"; h.toMapWarp -> calls += "warp $a0.$a1 $a2"; h.returnToField -> calls += "field" }
                return 0
            }
            override fun read(addr: Long, len: Int): ByteArray = when (addr) {
                h.saveBlock2Ptr -> byteArrayOf(0x00, 0x00, 0x02, 0x02) // 0x02020000
                0x02020008L -> byteArrayOf(if (female) 1 else 0)
                FLY_EMERALD.destinations -> byteArrayOf(0, 9, 1)
                FLY_EMERALD.destinations + 11 * 3 -> byteArrayOf(0, 4, 7) // FORTREE CITY
                FLY_EMERALD.destinations + 6 * 3 -> byteArrayOf(0, 13, 0) // PACIFIDLOG: no heal location in this fake
                else -> ByteArray(len)
            }
            override fun write(addr: Long, bytes: ByteArray) = true
        }
        val fns = FIELD_ITEMS_EMERALD
        val t = FlyTargets(FlyResult.OK, emptyList(), 3, 0)
        for (m in listOf(11, 0, 15, 58, 6)) assertTrue(flyFinish(fns, m, t, g))
        g.female = true
        assertTrue(flyFinish(fns, 0, t, g))
        assertEquals(
            listOf("heal 7", "field", "heal 12", "field", "heal 11", "field", "heal 22", "field", "warp 0.13 -1", "field", "heal 13", "field"),
            calls,
        )
    }
}
