package com.pokedaisy.app.companion.data

/**
 * Using an item from the companion, the way the game does it - by calling the
 * game's own functions between frames (MgbaCore.pkCall), not by steering its
 * menus with button presses. The Repels: what ItemUseOutOfBattle_Repel
 * ends in, without its message box - if VarGet(VAR_REPEL_STEP_COUNT) is 0,
 * VarSet(VAR_REPEL_STEP_COUNT, the item's holdEffectParam) and
 * RemoveBagItem(item, 1). The game's own step counter then counts it down and
 * says "REPEL's effect wore off" by itself. Medicine and held items go to a
 * party Pokémon ([PartyItemFns], below).
 *
 * Only where the player could open the bag: on the field, controls free
 * (ArePlayerFieldControlsLocked: a script, a message or the START menu holds
 * them) and no menu screen up. Each address is retail's (the pinned decomp
 * builds' functions, found byte for byte in the retail ROMs), checked headless
 * with mgba_dump's `call` on the user's saves: the Repel left the bag, the
 * counter read 100 and dropped 7 after 7 steps; FireRed's controls read locked
 * while its quest-log recap was up.
 */
data class FieldItemFns(
    val varGet: Long,
    val varSet: Long,
    val checkBagHasItem: Long,
    val removeBagItem: Long,
    val fieldControlsLocked: Long,
    /** VAR_REPEL_STEP_COUNT (FireRed 0x4020, Emerald 0x4021). */
    val repelVar: Int,
    /** gItems: struct Item, holdEffectParam at +0x13 (a Repel's steps) with [itemNameLen] 14. */
    val items: Long,
    /** CRC32 of each function's first 32 bytes (varGet, varSet, checkBagHasItem, removeBagItem,
     * fieldControlsLocked): a build that moved them (FireRed's QoL build) is left alone. */
    val codeCrcs: List<Long>,
    /** USE on a party Pokémon and GIVE; null = the Repels only. */
    val party: PartyItemFns? = null,
    /** struct Item's name length: 14 (44-byte records), Japanese 10 (40). Every field after it moves with it. */
    val itemNameLen: Int = 14,
    /** FLY from the MAP tab (Fly.kt); null = none. */
    val fly: FlyFns? = null,
) {
    val functions get() = listOf(varGet, varSet, checkBagHasItem, removeBagItem, fieldControlsLocked)
    val itemSize get() = itemNameLen + 30
    /** Offsets in struct Item: holdEffectParam, importance, pocket, fieldUseFunc. */
    val itemParamOff get() = itemNameLen + 5
    val itemImportanceOff get() = itemNameLen + 10
    val itemPocketOff get() = itemNameLen + 12
    val itemFieldUseOff get() = itemNameLen + 14
}

/**
 * Medicine and held items, as party_menu.c does them (pokefirered: ItemUseCB_Medicine /
 * ItemUseCB_TryRestorePP / ItemUseCB_PPUp, and the GIVE path's GiveItemToMon /
 * Task_HandleSwitchItemsYesNoInput), minus its menus and messages:
 *
 * USE: PokemonItemUseNoEffect(mon, item, slot, move) says which Pokémon (and moves)
 * it would help - the game's own "It won't have any effect." check, plus its
 * HP Up-on-Shedinja rule (NotUsingHPEVItemOnShedinja) - then
 * ExecuteTableBasedItemEffect(mon, item, slot, move) and RemoveBagItem(item, 1).
 * Not Rare Candy or the evolution stones: their effect alone would skip the party
 * menu's move learning and evolution. Not Mail (its letter isn't written here).
 *
 * GIVE: RemoveBagItem(item, 1); a Pokémon already holding something gets its item
 * back into the bag first - AddBagItem(old, 1), and if that finds no room the new
 * one goes back (AddBagItem(item, 1)) and nothing changes, the game's own switch
 * order (taking the new one out first frees its slot for the old one) - then
 * SetMonData(mon, MON_DATA_HELD_ITEM, &item), which re-encrypts the box and fixes its
 * checksum. &item is [scratch]: RAM no build symbol reaches (see the retail table).
 * Eggs can't use or hold anything (IsSelectedMonNotEgg), and a Pokémon holding Mail
 * keeps it (the game asks for it to be taken first).
 *
 * TAKE (the summary's, party_menu.c's TryTakeMonItem): AddBagItem(held, 1) - no room and
 * nothing changes - then SetMonData(mon, MON_DATA_HELD_ITEM, &ITEM_NONE). Not Mail (the game
 * sends that to the PC or loses it, after asking).
 */
data class PartyItemFns(
    /** GetMonData(mon, field, NULL) (GetMonData3: the 2-argument GetMonData is the same code). */
    val getMonData: Long,
    val setMonData: Long,
    /** PokemonItemUseNoEffect(mon, item, partyIndex, moveIndex): TRUE = it wouldn't do anything. FireRed has
     * it; Emerald doesn't (its party menu just runs the effect): null = ask [itemEffect] itself on the Pokémon,
     * then put its 100 bytes back - the same answer, nothing kept. */
    val itemNoEffect: Long?,
    /** ExecuteTableBasedItemEffect(mon, item, partyIndex, moveIndex): FALSE = it did something. */
    val itemEffect: Long,
    val addBagItem: Long,
    /** gPlayerParty (struct Pokemon, 100 bytes each) and gPlayerPartyCount (a byte). */
    val party: Long,
    val partyCount: Long,
    /** Two bytes of RAM the game never uses, for SetMonData's argument (restored after). */
    val scratch: Long,
    /** gItems' length and struct Item's pocket ids no Pokémon can hold (key items, TMs / HMs). */
    val itemCount: Int,
    val unholdablePockets: Set<Int>,
    /** CRC32 of each function's first 32 bytes (getMonData, setMonData, itemNoEffect where there is one, itemEffect, addBagItem). */
    val codeCrcs: List<Long>,
    /** What USE works on: medicine (one Pokémon), PP items for one move / for all of them. */
    val medicine: Set<Int> = MEDICINE_ITEMS,
    val ppOneMove: Set<Int> = PP_ONE_MOVE_ITEMS,
    val ppAllMoves: Set<Int> = PP_ALL_MOVES_ITEMS,
    val mail: Set<Int> = MAIL_ITEMS,
    val hpUp: Int = 63,
    val shedinja: Int = 303,
    /** The game's own field-use routines for medicine, PP items and Repels (FieldUseFunc_Medicine / _Ether /
     * _PpUp / _Repel, Emerald's ItemUseOutOfBattle_*): an item is offered only if its gItems entry points at one
     * of them - a hack that turned an id into something else keeps it off. Empty = the id sets alone. */
    val useFuncs: Set<Long> = emptySet(),
) {
    val functions get() = listOfNotNull(getMonData, setMonData, itemNoEffect, itemEffect, addBagItem)
    val usable get() = medicine + ppOneMove + ppAllMoves
}

/** Whether the running ROM has [fns]' functions (its party ones too) where they're expected. */
fun fieldItemCodeMatches(fns: FieldItemFns, readRom: (addr: Long, len: Int) -> ByteArray): Boolean {
    val party = fns.party
    val all = fns.functions.zip(fns.codeCrcs) + (party?.let { it.functions.zip(it.codeCrcs) }.orEmpty())
    return all.all { (fn, crc) -> java.util.zip.CRC32().apply { update(readRom(fn, 32)) }.value == crc }
}

/** REPEL / SUPER REPEL / MAX REPEL: the same ids in both games. */
val REPEL_ITEMS = setOf(86, 83, 84)

/** USE on one Pokémon (FieldUseFunc_Medicine in pokefirered's items.json, FireRed / Emerald ids): Potion ...
 * Revival Herb, Lava Cookie, Berry Juice, the vitamins, the status / HP Berries. Not the Flutes. */
val MEDICINE_ITEMS: Set<Int> = (13..33).toSet() + setOf(38, 44, 63, 64, 65, 66, 67, 70, 133, 134, 135, 136, 137, 139, 141, 142)

/** PP items for one move (Ether, Max Ether, Leppa Berry, PP Up, PP Max): a move is picked too. */
val PP_ONE_MOVE_ITEMS = setOf(34, 35, 138, 69, 71)

/** PP Up / PP Max: the move pick's "Boost PP of which move?" (the others restore PP). */
val PP_BOOST_ITEMS = setOf(69, 71)

/** PP items for every move (Elixir, Max Elixir): no move to pick. */
val PP_ALL_MOVES_ITEMS = setOf(36, 37)

/** ORANGE MAIL .. RETRO MAIL (ItemIsMail). */
val MAIL_ITEMS = (121..132).toSet()

/** Retail FireRed rev 1 (BPRE). Every address from the pret pokefirered build at the pinned
 * commit, built as firered_rev1 (byte for byte the retail ROM, SHA1 dd5945db...) and its ELF. */
val FIELD_ITEMS_FIRERED_REV1 = FieldItemFns(
    varGet = 0x0806E57CL, varSet = 0x0806E598L, checkBagHasItem = 0x08099F54L, removeBagItem = 0x0809A1ECL,
    fieldControlsLocked = 0x0806996CL, repelVar = 0x4020, items = 0x083DB098L,
    codeCrcs = listOf(0xE6DE47BAL, 0x6E7CC2BEL, 0xD30D656EL, 0xC40BF208L, 0x9C26CCA4L),
    party = PartyItemFns(
        getMonData = 0x0803FBFCL, setMonData = 0x08040390L, itemNoEffect = 0x08042428L, itemEffect = 0x080413D4L,
        addBagItem = 0x0809A098L, party = 0x02024284L, partyCount = 0x02024029L,
        // EWRAM's last symbol (__malloc_current_mallinfo) ends at 0x0203FBB0 and nothing writes
        // past it (the only EWRAM_END user is the title screen's Colosseum multiboot copy):
        // the last 16 bytes read 0 on the field and through a save.
        scratch = 0x0203FFF0L,
        itemCount = 375, unholdablePockets = setOf(2, 4), // POCKET_KEY_ITEMS, POCKET_TM_CASE
        codeCrcs = listOf(0xA73A32CDL, 0x71F5A020L, 0x788AB547L, 0xEADAC6C9L, 0xC316CD20L),
        useFuncs = setOf(0x080A16F4L, 0x080A1710L, 0x080A172CL, 0x080A19ACL), // FieldUseFunc_Medicine, _Ether, _PpUp, _Repel
    ),
    fly = FLY_FIRERED_REV1,
)

/** Retail Emerald (BPEE): its functions sit where the pinned pokeemerald build has them (the QoL build's too, and
 * Emerald Kaizo's, retail edited in place). Every address from pret's pokeemerald built clean (byte for byte retail,
 * SHA1 f3ae0881...) and its ELF. Emerald has no PokemonItemUseNoEffect: the no-effect check is its party menu's own,
 * ExecuteTableBasedItemEffect, on the Pokémon and then undone ([PartyItemFns.itemNoEffect]). */
val FIELD_ITEMS_EMERALD = FieldItemFns(
    varGet = 0x0809D694L, varSet = 0x0809D6B0L, checkBagHasItem = 0x080D6724L, removeBagItem = 0x080D6AA4L,
    fieldControlsLocked = 0x08098E6CL, repelVar = 0x4021, items = 0x085839A0L,
    codeCrcs = listOf(0x7D0D07B6L, 0x519C39C0L, 0x312F6576L, 0x379EF742L, 0x2532A407L),
    party = PartyItemFns(
        getMonData = 0x0806A518L, setMonData = 0x0806ACACL, itemNoEffect = null, itemEffect = 0x0806BD04L,
        addBagItem = 0x080D6928L, party = 0x020244ECL, partyCount = 0x020244E9L,
        // EWRAM's last symbol (sRayScene) ends at 0x0203CF64; nothing writes past it (EWRAM_END is defined, never used).
        scratch = 0x0203FFF0L,
        itemCount = 377, unholdablePockets = setOf(5, 3), // POCKET_KEY_ITEMS, POCKET_TM_HM
        codeCrcs = listOf(0x9670EBDEL, 0x8D555FF8L, 0xEADAC6C9L, 0x75BC5970L),
        // ItemUseOutOfBattle_Medicine, _PPRecovery, _PPUp, _Repel
        useFuncs = setOf(0x080FDEA0L, 0x080FDEF4L, 0x080FDF10L, 0x080FE0BCL),
    ),
    fly = FLY_EMERALD,
)

/** Retail LeafGreen rev 0 (BPGE): pret's pokefirered built clean as leafgreen (byte for byte retail, SHA1 574fa542...). */
val FIELD_ITEMS_LEAFGREEN_REV0 = FieldItemFns(
    varGet = 0x0806E568L, varSet = 0x0806E584L, checkBagHasItem = 0x08099F14L, removeBagItem = 0x0809A1ACL,
    fieldControlsLocked = 0x08069958L, repelVar = 0x4020, items = 0x083DAE64L,
    codeCrcs = listOf(0xE6DE47BAL, 0x6E7CC2BEL, 0xD30D656EL, 0xC40BF208L, 0x9C26CCA4L),
    party = FIELD_ITEMS_FIRERED_REV1.party!!.copy(
        getMonData = 0x0803FBE8L, setMonData = 0x0804037CL, itemNoEffect = 0x08042414L, itemEffect = 0x080413C0L,
        addBagItem = 0x0809A058L,
        codeCrcs = listOf(0x7841F205L, 0x9B11B0A6L, 0x788AB547L, 0xEADAC6C9L, 0xC316CD20L),
        useFuncs = setOf(0x080A16B4L, 0x080A16D0L, 0x080A16ECL, 0x080A196CL),
    ),
    fly = FLY_LEAFGREEN_REV0,
)

/** Retail LeafGreen rev 1 (BPGE): built clean as leafgreen_rev1 (SHA1 7862c67b...). FireRed rev 1's party code and RAM. */
val FIELD_ITEMS_LEAFGREEN_REV1 = FieldItemFns(
    varGet = 0x0806E57CL, varSet = 0x0806E598L, checkBagHasItem = 0x08099F28L, removeBagItem = 0x0809A1C0L,
    fieldControlsLocked = 0x0806996CL, repelVar = 0x4020, items = 0x083DAED4L,
    codeCrcs = listOf(0xE6DE47BAL, 0x6E7CC2BEL, 0xD30D656EL, 0xC40BF208L, 0x9C26CCA4L),
    party = FIELD_ITEMS_FIRERED_REV1.party!!.copy(
        addBagItem = 0x0809A06CL, useFuncs = setOf(0x080A16C8L, 0x080A16E4L, 0x080A1700L, 0x080A1980L),
    ),
    fly = FLY_LEAFGREEN_REV1,
)

/** Ruby / Sapphire's party side, revs 1 and 2 alike (pret's pokeruby built clean as each, byte for byte retail): the
 * party in IWRAM, 349 items, no PokemonItemUseNoEffect (its party menu runs the effect, like Emerald's). EWRAM's
 * last symbol ends at 0x0203A3D5 and gSharedMem's offsets stop at 0x1FFF0, so the scratch word is free here too. */
private val RUBY_SAPPHIRE_PARTY = PartyItemFns(
    getMonData = 0x0803CB60L, setMonData = 0x0803D1FCL, itemNoEffect = null, itemEffect = 0x0803E18CL,
    addBagItem = 0x080A9444L, party = 0x03004360L, partyCount = 0x03004350L, scratch = 0x0203FFF0L,
    itemCount = 349, unholdablePockets = setOf(5, 3), // POCKET_KEY_ITEMS, POCKET_TM_HM
    codeCrcs = listOf(0x7D459654L, 0x59B8ECA4L, 0xEADAC6C9L, 0x891E35D5L),
    useFuncs = setOf(0x080C9DD0L, 0x080C9E5CL, 0x080C9E78L, 0x080CA034L), // the same in both, revs 1 and 2
)

/** Retail Ruby rev 1 / rev 2 (AXVE; SHA1 610b96a9... / 5b64eacf...). */
val FIELD_ITEMS_RUBY = FieldItemFns(
    varGet = 0x08069274L, varSet = 0x08069290L, checkBagHasItem = 0x080A92F4L, removeBagItem = 0x080A9558L,
    fieldControlsLocked = 0x08065568L, repelVar = 0x4021, items = 0x083C5580L,
    codeCrcs = listOf(0x98EFB2B6L, 0x82AA042BL, 0x87E0CA93L, 0xD7C65886L, 0xA306873DL),
    party = RUBY_SAPPHIRE_PARTY,
)

/** Retail Sapphire rev 1 / rev 2 (AXPE; SHA1 4722efb8... / 89b45fb1...): Ruby's code a few bytes on. */
val FIELD_ITEMS_SAPPHIRE = FIELD_ITEMS_RUBY.copy(
    varGet = 0x08069278L, varSet = 0x08069294L, fieldControlsLocked = 0x0806556CL, items = 0x083C55DCL,
)

/** Glazed 9.2.0: retail Emerald edited in place - Emerald's code and RAM, its VarGet / VarSet hooked into its own
 * extended variables (a jump at their entry; still the game's own functions to call). Its edited items are what
 * [PartyItemFns.useFuncs] keeps honest. */
val FIELD_ITEMS_GLAZED = FIELD_ITEMS_EMERALD.copy(
    fly = null, // its own region (Tunod): the Fly map's tables aren't mapped
    codeCrcs = listOf(0x9D16316AL, 0xAC747C5FL) + FIELD_ITEMS_EMERALD.codeCrcs.drop(2),
)

/** The CFRU hacks keep every function the tables use where FireRed rev 0 has it (each checked by CRC), with their
 * own item table: repointed and grown ([items]; ids past the real ones never reach the bag, so a generous
 * [itemCount] is harmless). Sword and Shield moved Berries and Mail into pocket 2 beside the key items, so only the
 * TM case's pocket keeps items out by pocket there - its key items by their importance flag. */
private fun cfru(items: Long, unholdablePockets: Set<Int> = setOf(2, 4)): FieldItemFns? = FIELD_ITEMS_PORTS["BPRE0"]?.let { t ->
    t.copy(items = items, party = t.party!!.copy(itemCount = 0x300, unholdablePockets = unholdablePockets))
}

/**
 * The tables for the running game: by [kind] for the hacks built on retail code (Glazed on Emerald; Orange Islands,
 * Ultra Violet and the CFRU hacks on FireRed rev 0), else by header code and revision for the retail games, which
 * run as FIRERED / EMERALD. The code CRCs still decide ([fieldItemSupport]).
 */
fun fieldItemsForGame(kind: GameKind?, gameCode: String, revision: Int): FieldItemFns? = when (kind) {
    GameKind.GLAZED -> FIELD_ITEMS_GLAZED.takeIf { gameCode == "BPEE" }
    GameKind.ORANGE_ISLANDS -> FIELD_ITEMS_PORTS["BPRE0"].takeIf { gameCode == "BPRE" && revision == 0 }
    GameKind.UNBOUND -> cfru(0x08876200L)
    GameKind.RADICAL_RED -> cfru(0x093C0000L)
    GameKind.ODYSSEY, GameKind.GAIA -> cfru(0x083DB028L)
    GameKind.AMETHYST -> cfru(0x0872DB0CL)
    GameKind.SWORD_SHIELD -> cfru(0x093C0000L, unholdablePockets = setOf(4))
    GameKind.FIRERED -> if (gameCode == "PUVV") FIELD_ITEMS_PORTS["BPRE0"] else fieldItemsFor(gameCode, revision)
    GameKind.EMERALD -> fieldItemsFor(gameCode, revision)
    else -> null
}

/** The tables for a ROM by its header's game code and revision byte; the code CRCs still decide ([fieldItemSupport]). */
fun fieldItemsFor(gameCode: String, revision: Int): FieldItemFns? = when (gameCode) {
    "BPRE" -> FIELD_ITEMS_FIRERED_REV1.takeIf { revision == 1 }
    "BPGE" -> when (revision) { 0 -> FIELD_ITEMS_LEAFGREEN_REV0; 1 -> FIELD_ITEMS_LEAFGREEN_REV1; else -> null }
    "BPEE" -> FIELD_ITEMS_EMERALD
    "AXVE" -> FIELD_ITEMS_RUBY.takeIf { revision == 1 || revision == 2 }
    "AXPE" -> FIELD_ITEMS_SAPPHIRE.takeIf { revision == 1 || revision == 2 }
    else -> null
} ?: FIELD_ITEMS_PORTS["$gameCode$revision"] // the other languages, English FireRed / Ruby / Sapphire rev 0

enum class ItemUseResult {
    USED, STILL_ACTIVE, NOT_NOW, NONE_LEFT, FAILED,
    /** NOT_NOW because a battle is on (the app's answer, from the snapshot: the game code only says NOT_NOW). */
    IN_BATTLE,
    /** The game's "It won't have any effect." */
    NO_EFFECT,
    /** GIVE: the Pokémon holds it now ([ItemUseOutcome.swapped] = what it held before, 0 = nothing). */
    GIVEN,
    /** GIVE: no room in the bag for the item the Pokémon held; nothing changed. */
    BAG_FULL,
    /** The party isn't what the pick showed any more (a switch, a trade): nothing changed. */
    PARTY_CHANGED,
    /** TAKE: the held item ([ItemUseOutcome.swapped]) is in the bag now. */
    TAKEN,
}

/** What using [itemId] did and, for a Repel, how many steps it lasts; a GIVE's item held before ([swapped]). */
data class ItemUseOutcome(val result: ItemUseResult, val steps: Int = 0, val swapped: Int = 0)

/** What the game's functions are reached through: [call] runs one (r0-r3) and gives back r0, -1 if it
 * never returned; [read] reads memory (ROM or RAM); [write] writes RAM. The emu thread, between frames. */
interface GameCalls {
    fun call(fn: Long, a0: Int = 0, a1: Int = 0, a2: Int = 0, a3: Int = 0): Long
    fun read(addr: Long, len: Int): ByteArray
    fun write(addr: Long, bytes: ByteArray): Boolean
}

/**
 * What the companion can do with each item on this ROM: built once per ROM ([fieldItemSupport]) and
 * read while drawing the bag, so it never changes with the moment (a battle, a menu) - those get NOT
 * NOW when tapped. The row mark ([marked]) shows only where the party actions are there too.
 */
class FieldItemSupport(
    val fns: FieldItemFns,
    private val holdable: Set<Int>,
    /** What USE works on here: [PartyItemFns.usable] as the ROM's gItems has it ([PartyItemFns.useFuncs]), plus the Repels. */
    private val usable: Set<Int> = fns.party?.usable.orEmpty() + REPEL_ITEMS,
    /** FLY's code is where [FieldItemFns.fly] says. */
    val canFly: Boolean = false,
) {
    private val party = fns.party
    /** USE: a Repel on the spot, or medicine / a PP item on a picked Pokémon. */
    fun canUse(itemId: Int): Boolean = itemId in usable && (itemId in REPEL_ITEMS || party != null)
    fun canGive(itemId: Int): Boolean = party != null && itemId in holdable
    /** USE opens the party pick (everything but the Repels). */
    fun picksMon(itemId: Int): Boolean = party != null && itemId in usable && itemId !in REPEL_ITEMS
    /** ... and then one of the Pokémon's moves. */
    fun picksMove(itemId: Int): Boolean = party != null && itemId in party.ppOneMove
    /** The bag row's mark: something to do with it here. */
    fun marked(itemId: Int): Boolean = party != null && (canUse(itemId) || canGive(itemId))
    /** TAKE on the summary. */
    val canTake: Boolean get() = party != null
}

/** [fns]' support on the running ROM, or null when its code isn't where [fns] says (a QoL build,
 * a hack). The holdable items come from the ROM's gItems ([holdableItems]). */
fun fieldItemSupport(fns: FieldItemFns, read: (addr: Long, len: Int) -> ByteArray): FieldItemSupport? {
    if (!fieldItemCodeMatches(fns, read)) return null
    val party = fns.party ?: return FieldItemSupport(fns, emptySet())
    val items = read(fns.items, party.itemCount * fns.itemSize)
    val fly = fns.fly?.let { flyCodeMatches(it, read) } == true
    return FieldItemSupport(fns, holdableItems(fns, items), usableItems(fns, items), canFly = fly)
}

/** What the bag's GIVE takes (item_menu.c's Task_ItemContext_FieldGive): importance 0 and not a key
 * item, minus the TM case (left out here) and Mail. [items] is gItems. */
fun holdableItems(fns: FieldItemFns, items: ByteArray): Set<Int> {
    val p = fns.party ?: return emptySet()
    return (1 until p.itemCount).filter { id ->
        val o = id * fns.itemSize
        o + fns.itemSize <= items.size && items[o + fns.itemImportanceOff].toInt() == 0 &&
            (items[o + fns.itemPocketOff].toInt() and 0xFF) !in p.unholdablePockets && id !in p.mail
    }.toSet()
}

/** USE's items (the party's and the Repels) whose gItems entry runs one of [PartyItemFns.useFuncs]; all of them
 * when the table lists none. */
fun usableItems(fns: FieldItemFns, items: ByteArray): Set<Int> {
    val p = fns.party ?: return REPEL_ITEMS
    val all = p.usable + REPEL_ITEMS
    if (p.useFuncs.isEmpty()) return all
    return all.filter { id ->
        val o = id * fns.itemSize + fns.itemFieldUseOff
        o + 4 <= items.size && (java.nio.ByteBuffer.wrap(items, o, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFEL) in p.useFuncs
    }.toSet()
}

/** Why a Pokémon can't be picked; [OK] = it can. */
enum class SlotVerdict { OK, NO_EFFECT, EGG, HOLDS_MAIL, HOLDS_IT }

/** One party slot as USE / GIVE sees it: the Pokémon ([personality], checked again before acting),
 * what it holds, and for a one-move item which moves the item would help. */
data class PartyTarget(
    val slot: Int,
    val personality: Long,
    val heldItem: Int,
    val verdict: SlotVerdict,
    val moves: List<Boolean> = emptyList(),
)

/** The party pick: [result] USED = pick from [targets]; otherwise why not (NOT_NOW, NONE_LEFT, FAILED). */
data class PartyTargets(val result: ItemUseResult, val targets: List<PartyTarget> = emptyList())

private const val MON_SIZE = 100
private const val MON_DATA_PERSONALITY = 0
private const val MON_DATA_SPECIES = 11
private const val MON_DATA_HELD_ITEM = 12
private const val MON_DATA_MOVE1 = 13
private const val MON_DATA_IS_EGG = 45

/** Uses [itemId] on the field - a Repel. [busy] is whether a battle or a menu screen is up. Emu thread. */
fun useFieldItem(fns: FieldItemFns, itemId: Int, busy: Boolean, g: GameCalls): ItemUseOutcome {
    if (itemId !in REPEL_ITEMS || !fieldItemCodeMatches(fns, g::read)) return ItemUseOutcome(ItemUseResult.FAILED)
    if (busy || g.call(fns.fieldControlsLocked) != 0L) return ItemUseOutcome(ItemUseResult.NOT_NOW)
    if (g.call(fns.varGet, fns.repelVar) != 0L) return ItemUseOutcome(ItemUseResult.STILL_ACTIVE)
    if (g.call(fns.checkBagHasItem, itemId, 1) != 1L) return ItemUseOutcome(ItemUseResult.NONE_LEFT)
    val steps = g.read(fns.items + itemId.toLong() * fns.itemSize + fns.itemParamOff, 1)[0].toInt() and 0xFF
    if (steps == 0) return ItemUseOutcome(ItemUseResult.FAILED)
    if (g.call(fns.varSet, fns.repelVar, steps) < 0 || g.call(fns.removeBagItem, itemId, 1) != 1L) {
        return ItemUseOutcome(ItemUseResult.FAILED)
    }
    return ItemUseOutcome(ItemUseResult.USED, steps)
}

/** Why [itemId] can't go to the party right now, or null if it can: the common checks of USE and GIVE. */
private fun partyGate(fns: FieldItemFns, itemId: Int, allowed: Boolean, busy: Boolean, g: GameCalls): ItemUseResult? = when {
    fns.party == null || !allowed || !fieldItemCodeMatches(fns, g::read) -> ItemUseResult.FAILED
    busy || g.call(fns.fieldControlsLocked) != 0L -> ItemUseResult.NOT_NOW
    g.call(fns.checkBagHasItem, itemId, 1) != 1L -> ItemUseResult.NONE_LEFT
    else -> null
}

private fun PartyItemFns.mon(slot: Int) = (party + slot * MON_SIZE).toInt()

/** GetMonData, failing loudly: a call that never returned would read as a 0 field otherwise. */
private fun PartyItemFns.get(g: GameCalls, slot: Int, field: Int): Long =
    g.call(getMonData, mon(slot), field).also { if (it < 0) throw IllegalStateException("GetMonData didn't return") }

/** Whether USE of [itemId] on [slot] would do something (move [move] for a PP item). */
private fun PartyItemFns.helps(g: GameCalls, itemId: Int, slot: Int, move: Int): Boolean {
    if (itemId == hpUp && get(g, slot, MON_DATA_SPECIES) == shedinja.toLong()) return false
    itemNoEffect?.let { return g.call(it, mon(slot), itemId, slot, move) == 0L }
    // The effect itself, undone: the Pokémon's struct is all it touches on the field (HP, status, PP, EVs and
    // stats, friendship). Whatever happens, its bytes go back.
    val saved = g.read(mon(slot).toLong() and 0xFFFFFFFFL, MON_SIZE)
    return try {
        g.call(itemEffect, mon(slot), itemId, slot, move) == 0L
    } finally {
        if (!g.write(mon(slot).toLong() and 0xFFFFFFFFL, saved)) throw IllegalStateException("couldn't put the Pokémon back")
    }
}

/**
 * Who [itemId] could go to: every party slot with its verdict - for USE ([give] false) the game's
 * no-effect check (per move for a one-move PP item, a move slot with no move left out), for GIVE eggs
 * and Mail holders out. [support] says what the ROM allows. Emu thread.
 */
fun partyTargets(support: FieldItemSupport, itemId: Int, give: Boolean, busy: Boolean, g: GameCalls): PartyTargets {
    val fns = support.fns
    partyGate(fns, itemId, if (give) support.canGive(itemId) else support.picksMon(itemId), busy, g)?.let { return PartyTargets(it) }
    val p = fns.party!!
    return runCatching {
        val count = (g.read(p.partyCount, 1)[0].toInt() and 0xFF).coerceAtMost(6)
        PartyTargets(ItemUseResult.USED, (0 until count).map { slot ->
            val personality = p.get(g, slot, MON_DATA_PERSONALITY)
            val held = p.get(g, slot, MON_DATA_HELD_ITEM).toInt()
            val egg = p.get(g, slot, MON_DATA_IS_EGG) != 0L
            when {
                egg -> PartyTarget(slot, personality, held, SlotVerdict.EGG)
                give && held in p.mail -> PartyTarget(slot, personality, held, SlotVerdict.HOLDS_MAIL)
                give && held == itemId -> PartyTarget(slot, personality, held, SlotVerdict.HOLDS_IT)
                give -> PartyTarget(slot, personality, held, SlotVerdict.OK)
                itemId in p.ppOneMove -> {
                    val moves = (0 until 4).map { m -> p.get(g, slot, MON_DATA_MOVE1 + m) != 0L && p.helps(g, itemId, slot, m) }
                    PartyTarget(slot, personality, held, if (moves.any { it }) SlotVerdict.OK else SlotVerdict.NO_EFFECT, moves)
                }
                else -> PartyTarget(slot, personality, held, if (p.helps(g, itemId, slot, 0)) SlotVerdict.OK else SlotVerdict.NO_EFFECT)
            }
        })
    }.getOrElse { PartyTargets(ItemUseResult.FAILED) }
}

/** Whether [slot] still holds the Pokémon the pick showed (and isn't an egg). */
private fun PartyItemFns.stillThere(g: GameCalls, slot: Int, personality: Long): Boolean =
    slot in 0 until (g.read(partyCount, 1)[0].toInt() and 0xFF) &&
        get(g, slot, MON_DATA_PERSONALITY) == personality && get(g, slot, MON_DATA_IS_EGG) == 0L

/**
 * USE of medicine / a PP item on the Pokémon in [target] ([move] for a one-move PP item): the game's
 * no-effect check again, its effect, then one out of the bag - checked to be there first, so an
 * effect is never had for free. Emu thread.
 */
fun useOnMon(support: FieldItemSupport, itemId: Int, target: PartyTarget, move: Int, busy: Boolean, g: GameCalls): ItemUseOutcome {
    val fns = support.fns
    partyGate(fns, itemId, support.picksMon(itemId), busy, g)?.let { return ItemUseOutcome(it) }
    val p = fns.party!!
    return runCatching {
        val slot = target.slot
        val m = if (itemId in p.ppOneMove) move.coerceIn(0, 3) else 0
        when {
            !p.stillThere(g, slot, target.personality) -> ItemUseOutcome(ItemUseResult.PARTY_CHANGED)
            !p.helps(g, itemId, slot, m) -> ItemUseOutcome(ItemUseResult.NO_EFFECT)
            g.call(p.itemEffect, p.mon(slot), itemId, slot, m) != 0L -> ItemUseOutcome(ItemUseResult.NO_EFFECT)
            g.call(fns.removeBagItem, itemId, 1) != 1L -> ItemUseOutcome(ItemUseResult.FAILED)
            else -> ItemUseOutcome(ItemUseResult.USED)
        }
    }.getOrElse { ItemUseOutcome(ItemUseResult.FAILED) }
}

/**
 * GIVE [itemId] to the Pokémon in [target], as the party menu does (see [PartyItemFns]): never loses
 * an item - whatever fails after the bag changed puts the bag and the Pokémon back. Emu thread.
 */
fun giveToMon(support: FieldItemSupport, itemId: Int, target: PartyTarget, busy: Boolean, g: GameCalls): ItemUseOutcome {
    val fns = support.fns
    partyGate(fns, itemId, support.canGive(itemId), busy, g)?.let { return ItemUseOutcome(it) }
    val p = fns.party!!
    val slot = target.slot
    val old = runCatching {
        if (!p.stillThere(g, slot, target.personality)) return ItemUseOutcome(ItemUseResult.PARTY_CHANGED)
        p.get(g, slot, MON_DATA_HELD_ITEM).toInt()
    }.getOrElse { return ItemUseOutcome(ItemUseResult.FAILED) }
    if (old in p.mail) return ItemUseOutcome(ItemUseResult.FAILED)
    if (old == itemId) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
    if (g.call(fns.removeBagItem, itemId, 1) != 1L) return ItemUseOutcome(ItemUseResult.NONE_LEFT)
    if (old != 0 && g.call(p.addBagItem, old, 1) != 1L) {
        g.call(p.addBagItem, itemId, 1)
        return ItemUseOutcome(ItemUseResult.BAG_FULL)
    }
    if (setHeldItem(p, g, slot, itemId)) return ItemUseOutcome(ItemUseResult.GIVEN, swapped = old)
    // Back as it was: the old item out of the bag and onto the Pokémon, the new one back in.
    if (old != 0 && g.call(fns.removeBagItem, old, 1) == 1L) setHeldItem(p, g, slot, old)
    g.call(p.addBagItem, itemId, 1)
    return ItemUseOutcome(ItemUseResult.FAILED)
}

/** SetMonData(mon, MON_DATA_HELD_ITEM, &item) through [PartyItemFns.scratch], then read back. */
private fun setHeldItem(p: PartyItemFns, g: GameCalls, slot: Int, item: Int): Boolean = runCatching {
    val before = g.read(p.scratch, 2)
    if (!g.write(p.scratch, byteArrayOf(item.toByte(), (item shr 8).toByte()))) return false
    val called = g.call(p.setMonData, p.mon(slot), MON_DATA_HELD_ITEM, p.scratch.toInt())
    g.write(p.scratch, before)
    called >= 0 && p.get(g, slot, MON_DATA_HELD_ITEM) == item.toLong()
}.getOrDefault(false)

/**
 * What each party Pokémon holds, for TAKE: [PartyTarget.heldItem] with OK where it can be taken,
 * NO_EFFECT where there's nothing, HOLDS_MAIL / EGG. Reads only (GetMonData), so not gated on the
 * field. Emu thread.
 */
fun partyHeldItems(support: FieldItemSupport, g: GameCalls): PartyTargets {
    val p = support.fns.party ?: return PartyTargets(ItemUseResult.FAILED)
    if (!fieldItemCodeMatches(support.fns, g::read)) return PartyTargets(ItemUseResult.FAILED)
    return runCatching {
        val count = (g.read(p.partyCount, 1)[0].toInt() and 0xFF).coerceAtMost(6)
        PartyTargets(ItemUseResult.USED, (0 until count).map { slot ->
            val held = p.get(g, slot, MON_DATA_HELD_ITEM).toInt()
            val verdict = when {
                p.get(g, slot, MON_DATA_IS_EGG) != 0L -> SlotVerdict.EGG
                held == 0 -> SlotVerdict.NO_EFFECT
                held in p.mail -> SlotVerdict.HOLDS_MAIL
                else -> SlotVerdict.OK
            }
            PartyTarget(slot, p.get(g, slot, MON_DATA_PERSONALITY), held, verdict)
        })
    }.getOrElse { PartyTargets(ItemUseResult.FAILED) }
}

/** TAKE the item [target]'s Pokémon holds into the bag (see [PartyItemFns]); [ItemUseOutcome.swapped] is the
 * item. Never loses it: no room leaves it held, a SetMonData that didn't take puts the bag back. Emu thread. */
fun takeFromMon(support: FieldItemSupport, target: PartyTarget, busy: Boolean, g: GameCalls): ItemUseOutcome {
    val fns = support.fns
    val p = fns.party ?: return ItemUseOutcome(ItemUseResult.FAILED)
    if (!fieldItemCodeMatches(fns, g::read)) return ItemUseOutcome(ItemUseResult.FAILED)
    if (busy || g.call(fns.fieldControlsLocked) != 0L) return ItemUseOutcome(ItemUseResult.NOT_NOW)
    val held = runCatching {
        if (!p.stillThere(g, target.slot, target.personality)) return ItemUseOutcome(ItemUseResult.PARTY_CHANGED)
        p.get(g, target.slot, MON_DATA_HELD_ITEM).toInt()
    }.getOrElse { return ItemUseOutcome(ItemUseResult.FAILED) }
    if (held == 0) return ItemUseOutcome(ItemUseResult.NO_EFFECT)
    if (held in p.mail) return ItemUseOutcome(ItemUseResult.FAILED)
    if (g.call(p.addBagItem, held, 1) != 1L) return ItemUseOutcome(ItemUseResult.BAG_FULL, swapped = held)
    if (setHeldItem(p, g, target.slot, 0)) return ItemUseOutcome(ItemUseResult.TAKEN, swapped = held)
    g.call(fns.removeBagItem, held, 1)
    return ItemUseOutcome(ItemUseResult.FAILED)
}
