#!/usr/bin/env python3
"""Generates what a FireRed 1.0 hack edited in place names differently from FireRed,
from the ROMs the user owns (16 MB binary hacks of BPRE rev 0; no source, nothing
is downloaded). The app runs these as FIRERED with the result laid over the shared
tables (ActiveTables' `fireRedHack`, a `HackText`).

    scripts/gen_firered_hack_tables.py rocket_edition <Pokemon FireRed Rocket Edition.gba> <firered-rev0.gba>
    scripts/gen_firered_hack_tables.py ash_gray <Pokemon Ash Gray (v4.5.3).gba> <Pokemon Ash Gray Fork Bug Fix (v1.0).gba> <firered-rev0.gba>

Each table is found through the literal-pool word retail rev 0 (sha1 41cb23d8...)
loads it from, so a repointed one is followed: gSpeciesNames, gBaseStats (28 bytes,
types +6 / +7, genderRatio +0x10), gMoveNames + gBattleMoves, gItems, sMapNames
(pointers from MAPSEC 0x58), gTypeNames and gTypeEffectiveness.

FireRed Rocket Edition moved gMoveNames (0x08901800) and gBattleMoves (0x08900000) to
add moves 355..486 (387 on are the later games' moves; 487+ are "-" filler), and
gTypeNames (0x08961B50) / gTypeEffectiveness (0x089619E0) for a FAIRY type at id 23
(CLEFAIRY is 23 / 23). Its text is mixed case ("Bulbasaur"), so its whole tables are
written in the ROM's own casing (`ownCase`). Ash Gray keeps every table at rev 0's
address and edits it in place: a few species slots, 25 moves' type / power, key
items, the Orange Islands' map sections, gender ratios (its PIKACHU) and one type
matchup (ELECTRIC on GROUND hits for half) and its own region map (`ownRegionMap`: the
MAP reads it from the ROM); its Fork Bug Fix build shares all of it
(checked). Ash Gray's names are FireRed's capitals, title-cased like the shared
tables (FireRed's own casing puts them back in capitals on screen).
"""
import hashlib
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_emerald_lang_tables import CHARS  # noqa: E402

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "kotlin",
                       "com", "pokedaisy", "app", "companion", "data")
FIRERED_REV0_SHA1 = "41cb23d8dccc8ebd7c649cd8fbb58eeace6e2fdc"
B = 0x08000000
SPECIES_COUNT, ITEMS_COUNT, FIRST_MAPSEC, SECTIONS, TYPE_COUNT = 412, 375, 0x58, 109, 24
# Retail rev 0's addresses: the hack's table is the word at the literal pools that hold these in rev 0.
REV0 = {
    "species_names": 0x08245EE0, "base_stats": 0x08254784, "move_names": 0x08247094,
    "battle_moves": 0x08250C04, "items": 0x083DB028, "map_names": 0x083F1CAC,
    "type_names": 0x0824F1A0, "type_chart": 0x0824F050,
}
GAMES = {
    "rocket_edition": dict(
        label="Pokemon FireRed Rocket Edition", out="RocketEditionGen.kt", name="RocketEdition",
        sha1s=["033d05374ae34eb84fae62c693ca8f3da23dd1bd"], own_case=True, own_region_map=False, moves_count=487,
        type_names_app={23: "Fairy"},
    ),
    "ash_gray": dict(
        label="Pokemon Ash Gray v4.5.3", out="AshGrayGen.kt", name="AshGray",
        sha1s=["1ee1557db1e4c2e0e94c6306efcc40506bc40cc9", "92ed82145c013069472153391c7aab1cfbf37b75"],
        own_case=False, own_region_map=True, moves_count=355, type_names_app={},
    ),
}
CH = dict(CHARS)


def u8(d, a):
    return d[a - B]


def u16(d, a):
    return struct.unpack_from("<H", d, a - B)[0]


def u32(d, a):
    return struct.unpack_from("<I", d, a - B)[0]


def text(d, a, maxlen):
    out = []
    for b in d[a - B:a - B + maxlen]:
        if b == 0xFF:
            break
        if b in (0xFA, 0xFB, 0xFE):
            out.append(" ")
            continue
        if b not in CH:
            return None
        out.append(CH[b])
    s = " ".join("".join(out).split())
    return s if s and set(s) not in ({"?"}, {"-"}) else None


def kstr(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def title(s):
    """The shared tables' casing: "GS BALL" -> "Gs Ball", "MAIDEN‘S PEAK" -> "Maiden‘s Peak"."""
    out, start = [], True
    for c in s:
        out.append(c.upper() if start else c.lower())
        start = not c.isalpha() and c not in "‘’'é"
    return "".join(out)


def find_pools(fr):
    """Every rev 0 literal-pool offset holding each table's address (the hack's word there is its table)."""
    out = {}
    for k, v in REV0.items():
        out[k] = [i for i in range(0, 0x1E0000, 4) if struct.unpack_from("<I", fr, i)[0] == v]
        assert out[k], k
    return out


def tables(d, pools):
    t = {}
    for k, ps in pools.items():
        words = {struct.unpack_from("<I", d, p)[0] for p in ps}
        # gBattleMoves' pool words include a couple that are code in some hacks: keep ROM pointers only,
        # the one most words agree on.
        cands = [w for w in (struct.unpack_from("<I", d, p)[0] for p in ps) if B <= w < B + len(d)]
        t[k] = max(set(cands), key=cands.count)
        assert t[k] in words
    return t


def read_all(d, t, moves_count):
    sp, ty, gr = {}, {}, {}
    for i in range(1, SPECIES_COUNT):
        sp[i] = text(d, t["species_names"] + 11 * i, 11)
        b = t["base_stats"] + 28 * i
        ty[i] = (u8(d, b + 6), u8(d, b + 7))
        gr[i] = u8(d, b + 0x10)
    mv = {}
    for i in range(1, moves_count):
        n = text(d, t["move_names"] + 13 * i, 13)
        a = t["battle_moves"] + 12 * i
        mv[i] = (n, u8(d, a + 2), u8(d, a + 1)) if n else None
    it = {}
    for slot in range(1, ITEMS_COUNT):
        a = t["items"] + 44 * slot
        n = text(d, a, 14)
        it[slot] = n if n and u16(d, a + 14) == slot else None
    ms = {}
    for i in range(SECTIONS):
        p = u32(d, t["map_names"] + 4 * i)
        ms[FIRST_MAPSEC + i] = text(d, p, 32) if B <= p < B + len(d) else None
    tn = {i: text(d, t["type_names"] + 7 * i, 7) for i in range(TYPE_COUNT)}
    chart, a = {}, t["type_chart"]
    while u8(d, a) != 0xFF:
        atk, dfn, x = u8(d, a), u8(d, a + 1), u8(d, a + 2)
        a += 3
        if atk != 0xFE:  # 0xFE: the Foresight separator
            chart[atk * 100 + dfn] = x * 10
    return dict(species=sp, types=ty, gender=gr, moves=mv, items=it, mapsecs=ms, type_names=tn, chart=chart)


def diff(a, b):
    return {k: v for k, v in b.items() if v is not None and a.get(k) != v}


def main():
    if len(sys.argv) < 3 or sys.argv[1] not in GAMES:
        sys.exit(__doc__)
    g = GAMES[sys.argv[1]]
    roms = [open(p, "rb").read() for p in sys.argv[2:-1]]
    fr = open(sys.argv[-1], "rb").read()
    if hashlib.sha1(fr).hexdigest() != FIRERED_REV0_SHA1:
        sys.exit(f"not retail FireRed rev 0 (sha1 {FIRERED_REV0_SHA1})")
    if sorted(hashlib.sha1(d).hexdigest() for d in roms) != sorted(g["sha1s"]):
        sys.exit(f"pass every {g['label']} build: sha1s {g['sha1s']}")
    pools = find_pools(fr)
    base = read_all(fr, tables(fr, pools), 355)
    hacks = [read_all(d, tables(d, pools), g["moves_count"]) for d in roms]
    for h in hacks[1:]:
        assert h == hacks[0], "the builds' tables differ"
    h = hacks[0]
    own = g["own_case"]

    if own:
        # Mixed-case text: every name as the ROM has it (the shared tables are title-cased, not the game's).
        sp = {k: v for k, v in h["species"].items() if v}
        mv = {k: v for k, v in h["moves"].items() if v}
        it = {k: v for k, v in h["items"].items() if v}
        ms = {k: v for k, v in h["mapsecs"].items() if v}
    else:
        sp = {k: title(v) for k, v in diff(base["species"], h["species"]).items()}
        mv = {k: (title(n), t_, p) for k, (n, t_, p) in diff(base["moves"], h["moves"]).items()}
        it = {k: title(v) for k, v in diff(base["items"], h["items"]).items()}
        ms = {k: title(v) for k, v in diff(base["mapsecs"], h["mapsecs"]).items()}
    ty = diff(base["types"], h["types"])
    gr = diff(base["gender"], h["gender"])
    chart = h["chart"] if h["chart"] != base["chart"] else None
    new_types = {i: n for i, n in h["type_names"].items() if n and base["type_names"].get(i) is None}

    if sys.argv[1] == "rocket_edition":
        assert sp[1] == "Bulbasaur" and ty[35] == (23, 23) and new_types == {23: "Fairy"}
        assert mv[463] == ("Play Rough", 23, 90) and 487 not in mv and it[369] == "Steroids"
        assert chart[2316] == 200 and chart[1623] == 0
    else:
        assert gr and chart[1304] == 50 and it[349] == "Gs Ball" and ms[143] == "Valencia Island"
        assert not new_types
    assert set(new_types) == set(g["type_names_app"])

    n = g["name"]
    head = ("package com.pokedaisy.app.companion.data\n\n"
            f"// GENERATED by scripts/gen_firered_hack_tables.py from the {g['label']} ROM\n"
            f"// (sha1 {g['sha1s'][0][:8]}...) against retail FireRed rev 0 - do not hand-edit. What it names /\n"
            "// types differently, by FireRed's ids (ActiveTables lays these over the shared tables).\n\n")
    gender_hex = "".join("%02x" % (gr.get(i, base["gender"].get(i, 255))) for i in range(SPECIES_COUNT))
    body = (
        f"internal val fireRedHack{n}: HackText by lazy {{\n"
        f"    HackText(\n"
        f"        species = mapOf(\n" +
        "".join(f"            {i} to {kstr(s)},\n" for i, s in sorted(sp.items())) + "        ),\n"
        f"        types = mapOf(\n" +
        "".join(f"            {i} to SpeciesTypes({a}, {b}),\n" for i, (a, b) in sorted(ty.items())) + "        ),\n"
        f"        moves = mapOf(\n" +
        "".join(f"            {i} to MoveInfo({kstr(s)}, {t_}, {p}),\n" for i, (s, t_, p) in sorted(mv.items())) + "        ),\n"
        f"        items = mapOf(\n" +
        "".join(f"            {i} to {kstr(s)},\n" for i, s in sorted(it.items())) + "        ),\n"
        "        // Section names only: the boxes and cursor grid are FireRed's.\n"
        f"        mapSecs = mapOf(\n" +
        "".join(f"            {i} to {kstr(s)},\n" for i, s in sorted(ms.items())) + "        ),\n"
        f"        typeNames = mapOf(\n" +
        "".join(f"            {i} to {kstr(s)},\n" for i, s in sorted(g["type_names_app"].items())) + "        ),\n" +
        ("        typeChart = null,\n" if chart is None else
         "        // gTypeEffectiveness: key = atkType*100+defType -> percent, pairs not listed are 100.\n"
         "        typeChart = mapOf(\n" + "".join(f"            {k} to {v},\n" for k, v in chart.items()) + "        ),\n") +
        ("        genderRatios = null,\n" if not gr else
         "        // gBaseStats genderRatio by species id (it changed some of FireRed's).\n"
         f"        genderRatios = unhexRatios(\"{gender_hex}\"),\n") +
        f"        ownCase = {str(own).lower()},\n"
        f"        ownRegionMap = {str(g['own_region_map']).lower()},\n"
        "    )\n"
        "}\n")
    path = os.path.join(OUT_DIR, g["out"])
    with open(path, "w", encoding="utf-8") as f:
        f.write(head + body)
    print("wrote", os.path.relpath(path))
    print(f"{len(sp)} species, {len(ty)} types, {len(mv)} moves, {len(it)} items, {len(ms)} map sections, "
          f"{len(gr)} gender ratios, chart {'own' if chart else 'FireRed'}")


if __name__ == "__main__":
    main()
