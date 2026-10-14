package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * WILD ENCOUNTERS' switch, found in each ROM by StandardWildEncounter's code ([WildEncounters.find]). The retail
 * addresses are the pret decomps' sWildEncountersDisabled / gWildEncountersDisabled; the hacks' were checked by hand
 * (Glazed / Kaizo keep Emerald's code, the CFRU hacks hook StandardWildEncounter into their own, which reads
 * FireRed's). OFF and 50% were walked headless in Route 101's / Route 1's grass (mgba_dump `wild`). Skips a ROM that
 * isn't here.
 */
class WildEncountersTest {
    private val cases = listOf(
        "Pokemon - Emerald Version (USA, Europe).gba" to 0x02038C00L,
        "Pokemon - FireRed Version (USA).gba" to 0x020386DCL,
        "Pokemon - FireRed Version (USA, Europe) (Rev 1).gba" to 0x020386DCL,
        "Pokemon - LeafGreen Version (USA, Europe) (Rev 1).gba" to 0x020386DCL,
        "Pokemon - Ruby Version (USA, Europe) (Rev 1).gba" to 0x0202FF7CL,
        "Pokemon - Sapphire Version (USA, Europe) (Rev 2).gba" to 0x0202FF7CL,
        "emerald-multilang/Pocket Monsters - Emerald (Japan).gba" to 0x020388A0L,
        "emerald-multilang/Pokemon - Smaragd-Edition (Germany).gba" to 0x02038C00L,
        "Pokemon Glazed (v9.2.0).gba" to 0x02038C00L,
        "Pokemon Emerald Kaizo (v2.0).gba" to 0x02038C00L,
        "Pokemon Gaia (v3.2).gba" to 0x020386DCL,
        "Pokemon Ultra Violet (v1.22).gba" to 0x020386DCL,
        "Pokemon Celia's Stupid Romhack (v1.1.4).gba" to 0x020388D8L,
        "Pokemon Unbound (v2.1.1.1).gba" to 0x020386DCL,
        "Pokemon Radical Red (v4.1).gba" to 0x020386DCL,
        "Pokemon Amethyst (v1.4.1).gba" to 0x020386DCL,
        "Pokemon Sword and Shield Ultimate Plus (v1.2.1.2).gba" to 0x020386DCL,
        // pokeemerald-expansion / gcc builds: not found, so no WILD ENCOUNTERS there.
        "Pokemon Lazarus (v2.0).gba" to 0L,
        "Pokemon SoulGold (v1.1.4).gba" to 0L,
        "Pokemon Emerald Rogue (v2.2.1-EX).gba" to 0L,
    )

    @Test
    fun findsEachGamesSwitch() {
        for ((name, want) in cases) {
            val rom = File(RETAIL_ROM_DIR + name)
            if (!rom.isFile) continue
            assertEquals(name, "%08X".format(want), "%08X".format(WildEncounters.find(rom)))
        }
    }
}
