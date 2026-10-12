#!/usr/bin/env python3
"""Maps USE / GIVE / TAKE's addresses (FieldItems.kt) into the other-language releases.

    scripts/port_field_items.py <roms dir> [--no-write]

The English tables come from pret's decomps built clean (each byte for byte the retail ROM: pokefirered as
firered / firered_rev1 / leafgreen / leafgreen_rev1, pokeemerald, pokeruby as ruby / sapphire and their revs) and
their ELFs. For every ROM under <roms dir> of those games in another language - and English FireRed / Ruby /
Sapphire rev 0, whose tables are written straight from their ELFs - it

  1. picks the English ROM of the same game most alike (port_retail.py's nearest_english),
  2. maps each function by its code bytes (a unique 32 / 24 / 16-byte run; else BL- and pool-masked near the
     others' shift), RAM globals and gItems through the literal pools that load them (port_retail.py's helpers),
  3. checks what it found against the ROM itself: gItems' records hold ids 1..8 and the Repel's 100 steps, and the
     mapped field-use routines are what gItems points at for a Potion, an Ether, a PP Up and a Repel,
  4. writes app/.../companion/data/FieldItemsPortsGen.kt (FIELD_ITEMS_PORTS, by game code + revision).

A ROM where anything fails to map or check is reported and left out (it keeps no USE / GIVE / TAKE).
"""
import os
import statistics
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from port_retail import B, Rom, detect_items, map_code, map_code_masked, map_literal, nearest_english  # noqa: E402

OUT = os.path.join(HERE, "..", "app", "src", "main", "kotlin", "com", "pokedaisy", "app", "companion", "data",
                   "FieldItemsPortsGen.kt")

FUNCS = ("varGet", "varSet", "checkBagHasItem", "removeBagItem", "fieldControlsLocked",
         "getMonData", "setMonData", "itemNoEffect", "itemEffect", "addBagItem")
USE = ("medicine", "ppRecovery", "ppUp", "repel")  # the field-use routines of item 13, 34, 69, 86
USE_PROBE = dict(medicine=13, ppRecovery=34, ppUp=69, repel=86)

FR = dict(repelVar=0x4020, itemCount=375, pockets=(2, 4))  # POCKET_KEY_ITEMS, POCKET_TM_CASE
EM = dict(repelVar=0x4021, itemCount=377, pockets=(5, 3))  # POCKET_KEY_ITEMS, POCKET_TM_HM
RS = dict(repelVar=0x4021, itemCount=349, pockets=(5, 3))


def ref(stem, fam, **a):
    return dict(stem=stem, fam=fam, a=a)


# English references, from the clean builds' ELFs (llvm-nm).
REFS = {
    "BPRE0": ref("Pokemon - FireRed Version (USA, Europe)", FR, varGet=0x0806E568, varSet=0x0806E584,
                 checkBagHasItem=0x08099F40, removeBagItem=0x0809A1D8, fieldControlsLocked=0x08069958,
                 getMonData=0x0803FBE8, setMonData=0x0804037C, itemNoEffect=0x08042414, itemEffect=0x080413C0,
                 addBagItem=0x0809A084, items=0x083DB028, party=0x02024284, partyCount=0x02024029,
                 medicine=0x080A16E0, ppRecovery=0x080A16FC, ppUp=0x080A1718, repel=0x080A1998),
    "BPRE1": ref("Pokemon - FireRed Version (USA, Europe) (Rev 1)", FR, varGet=0x0806E57C, varSet=0x0806E598,
                 checkBagHasItem=0x08099F54, removeBagItem=0x0809A1EC, fieldControlsLocked=0x0806996C,
                 getMonData=0x0803FBFC, setMonData=0x08040390, itemNoEffect=0x08042428, itemEffect=0x080413D4,
                 addBagItem=0x0809A098, items=0x083DB098, party=0x02024284, partyCount=0x02024029,
                 medicine=0x080A16F4, ppRecovery=0x080A1710, ppUp=0x080A172C, repel=0x080A19AC),
    "BPGE0": ref("Pokemon - LeafGreen Version (USA, Europe)", FR, varGet=0x0806E568, varSet=0x0806E584,
                 checkBagHasItem=0x08099F14, removeBagItem=0x0809A1AC, fieldControlsLocked=0x08069958,
                 getMonData=0x0803FBE8, setMonData=0x0804037C, itemNoEffect=0x08042414, itemEffect=0x080413C0,
                 addBagItem=0x0809A058, items=0x083DAE64, party=0x02024284, partyCount=0x02024029,
                 medicine=0x080A16B4, ppRecovery=0x080A16D0, ppUp=0x080A16EC, repel=0x080A196C),
    "BPGE1": ref("Pokemon - LeafGreen Version (USA, Europe) (Rev 1)", FR, varGet=0x0806E57C, varSet=0x0806E598,
                 checkBagHasItem=0x08099F28, removeBagItem=0x0809A1C0, fieldControlsLocked=0x0806996C,
                 getMonData=0x0803FBFC, setMonData=0x08040390, itemNoEffect=0x08042428, itemEffect=0x080413D4,
                 addBagItem=0x0809A06C, items=0x083DAED4, party=0x02024284, partyCount=0x02024029,
                 medicine=0x080A16C8, ppRecovery=0x080A16E4, ppUp=0x080A1700, repel=0x080A1980),
    "BPEE0": ref("Pokemon - Emerald Version (USA, Europe)", EM, varGet=0x0809D694, varSet=0x0809D6B0,
                 checkBagHasItem=0x080D6724, removeBagItem=0x080D6AA4, fieldControlsLocked=0x08098E6C,
                 getMonData=0x0806A518, setMonData=0x0806ACAC, itemNoEffect=None, itemEffect=0x0806BD04,
                 addBagItem=0x080D6928, items=0x085839A0, party=0x020244EC, partyCount=0x020244E9,
                 medicine=0x080FDEA0, ppRecovery=0x080FDEF4, ppUp=0x080FDF10, repel=0x080FE0BC),
    "AXVE0": ref("Pokemon - Ruby Version (USA, Europe)", RS, varGet=0x08069254, varSet=0x08069270,
                 checkBagHasItem=0x080A92D4, removeBagItem=0x080A9538, fieldControlsLocked=0x08065548,
                 getMonData=0x0803CB60, setMonData=0x0803D1FC, itemNoEffect=None, itemEffect=0x0803E18C,
                 addBagItem=0x080A9424, items=0x083C5564, party=0x03004360, partyCount=0x03004350,
                 medicine=0x080C9DB0, ppRecovery=0x080C9E3C, ppUp=0x080C9E58, repel=0x080CA014),
    "AXVE1": ref("Pokemon - Ruby Version (USA, Europe) (Rev 1)", RS, varGet=0x08069274, varSet=0x08069290,
                 checkBagHasItem=0x080A92F4, removeBagItem=0x080A9558, fieldControlsLocked=0x08065568,
                 getMonData=0x0803CB60, setMonData=0x0803D1FC, itemNoEffect=None, itemEffect=0x0803E18C,
                 addBagItem=0x080A9444, items=0x083C5580, party=0x03004360, partyCount=0x03004350,
                 medicine=0x080C9DD0, ppRecovery=0x080C9E5C, ppUp=0x080C9E78, repel=0x080CA034),
    "AXPE0": ref("Pokemon - Sapphire Version (USA, Europe)", RS, varGet=0x08069258, varSet=0x08069274,
                 checkBagHasItem=0x080A92D4, removeBagItem=0x080A9538, fieldControlsLocked=0x0806554C,
                 getMonData=0x0803CB60, setMonData=0x0803D1FC, itemNoEffect=None, itemEffect=0x0803E18C,
                 addBagItem=0x080A9424, items=0x083C55BC, party=0x03004360, partyCount=0x03004350,
                 medicine=0x080C9DB0, ppRecovery=0x080C9E3C, ppUp=0x080C9E58, repel=0x080CA014),
    "AXPE1": ref("Pokemon - Sapphire Version (USA, Europe) (Rev 1)", RS, varGet=0x08069278, varSet=0x08069294,
                 checkBagHasItem=0x080A92F4, removeBagItem=0x080A9558, fieldControlsLocked=0x0806556C,
                 getMonData=0x0803CB60, setMonData=0x0803D1FC, itemNoEffect=None, itemEffect=0x0803E18C,
                 addBagItem=0x080A9444, items=0x083C55DC, party=0x03004360, partyCount=0x03004350,
                 medicine=0x080C9DD0, ppRecovery=0x080C9E5C, ppUp=0x080C9E78, repel=0x080CA034),
}
# The English ROMs written straight from their ELF (no hand table in FieldItems.kt).
ENGLISH_OUT = ("BPRE0", "AXVE0", "AXPE0")


def crc(rom, a):
    o = a - B
    return zlib.crc32(rom.d[o:o + 32])


def port(rom, refs, key):
    """The mapped table for rom (from English refs[key]), or (None, why)."""
    r = REFS[key]
    src = refs[key]
    a = r["a"]
    out, how = {}, {}
    # Functions: exact code first, the rest by masked code around the exact ones' shift.
    for n in FUNCS + USE:
        if a.get(n) is None:
            out[n] = None
            continue
        v = map_code(src, rom, a[n])
        if v is not None:
            out[n], how[n] = v, "code"
    shifts = [out[n] - a[n] for n in out if out[n] is not None]
    shift = int(statistics.median(shifts)) if shifts else 0
    for n in FUNCS + USE:
        if a.get(n) is None or out.get(n) is not None:
            continue
        v = map_code_masked(src, rom, a[n], a[n] + shift, radius=0x8000)
        if v is None:
            return None, f"{n} not found"
        out[n], how[n] = v, "masked"
    for n in ("party", "partyCount", "items"):
        v, votes = map_literal(src, rom, a[n])
        if v is None:
            return None, f"{n} not found"
        out[n], how[n] = v, f"lit {votes}"
    base, stride, name_len = detect_items(rom, out["items"])
    if base != out["items"]:
        return None, f"gItems {out['items']:#x} isn't the item table (found {base and hex(base)})"
    if rom.u8(base + 86 * stride + name_len + 5) != 100:
        return None, "REPEL's steps aren't 100"
    for n, item in USE_PROBE.items():
        ptr = rom.u32(base + item * stride + name_len + 14) & ~1
        if ptr != out[n]:
            return None, f"{n} {out[n]:#x}, but item {item} runs {ptr:#x}"
    if rom.code[:3] != src.code[:3]:
        return None, "no reference of its game"
    out.update(stride=stride, name_len=name_len, how=how)
    return out, None


def kt(rom, key, m):
    fam = REFS[key]["fam"]
    h = lambda v: f"0x{v:08X}L"
    funcs = [m[n] for n in ("varGet", "varSet", "checkBagHasItem", "removeBagItem", "fieldControlsLocked")]
    party = [m[n] for n in ("getMonData", "setMonData", "itemNoEffect", "itemEffect", "addBagItem") if m[n] is not None]
    masked = sorted(n for n, v in m["how"].items() if v == "masked")
    return f"""    // {os.path.basename(rom.path)} (SHA1 {rom.sha1[:8]}...), from {key}{'; masked: ' + ', '.join(masked) if masked else ''}.
    "{rom.code}{rom.rev}" to FieldItemFns(
        varGet = {h(m['varGet'])}, varSet = {h(m['varSet'])}, checkBagHasItem = {h(m['checkBagHasItem'])}, removeBagItem = {h(m['removeBagItem'])},
        fieldControlsLocked = {h(m['fieldControlsLocked'])}, repelVar = 0x{fam['repelVar']:04X}, items = {h(m['items'])},
        codeCrcs = listOf({', '.join(f'0x{crc(rom, v):08X}L' for v in funcs)}),
        itemNameLen = {m['name_len']},
        party = PartyItemFns(
            getMonData = {h(m['getMonData'])}, setMonData = {h(m['setMonData'])}, itemNoEffect = {h(m['itemNoEffect']) if m['itemNoEffect'] else 'null'},
            itemEffect = {h(m['itemEffect'])}, addBagItem = {h(m['addBagItem'])}, party = {h(m['party'])}, partyCount = {h(m['partyCount'])},
            scratch = 0x0203FFF0L, itemCount = {fam['itemCount']}, unholdablePockets = setOf({', '.join(map(str, fam['pockets']))}),
            codeCrcs = listOf({', '.join(f'0x{crc(rom, v):08X}L' for v in party)}),
            useFuncs = setOf({', '.join(h(m[n]) for n in USE)}),
        ),
    ),"""


def main():
    args = [x for x in sys.argv[1:] if not x.startswith("--")]
    if not args:
        sys.exit(__doc__)
    paths = [os.path.join(dp, f) for dp, _, fs in os.walk(args[0]) for f in fs if f.endswith(".gba")]
    by_stem = {os.path.splitext(os.path.basename(p))[0]: p for p in paths}
    refs = {}
    for k, r in REFS.items():
        if r["stem"] not in by_stem:
            sys.exit(f"English reference missing: {r['stem']}.gba")
        refs[k] = Rom(by_stem[r["stem"]])
        if f"{refs[k].code}{refs[k].rev}" != k:
            sys.exit(f"{r['stem']} is {refs[k].code}{refs[k].rev}, not {k}")
    blocks, done, js = [], set(), {}
    for p in sorted(paths):
        name = os.path.basename(p)
        if any(x in name for x in ("Pirate", "Debug", "Pokemon Box")):
            continue
        rom = Rom(p)
        key = f"{rom.code}{rom.rev}"
        if rom.code[:3] not in ("BPR", "BPG", "BPE", "AXV", "AXP") or key in done:
            continue
        if rom.lang == "E" and key not in ENGLISH_OUT:
            continue
        if rom.lang == "E":
            m = dict(REFS[key]["a"], how={}, stride=44, name_len=14)
            ref_key = key
        else:
            cands = {k: v for k, v in refs.items() if k[:3] == rom.code[:3]}
            if not cands:
                print(f"{key:6} {name[:48]:48} no English reference of its game")
                continue
            ref_key = nearest_english(rom, cands)
            m, why = port(rom, refs, ref_key)
            if m is None:
                print(f"{key:6} {name[:48]:48} FAILED from {ref_key}: {why}")
                continue
        done.add(key)
        blocks.append(kt(rom, ref_key, m))
        js[key] = dict(path=os.path.abspath(p), ref=ref_key, family=rom.code[:3], **{k: v for k, v in m.items() if k != "how"})
        masked = [n for n, v in m["how"].items() if v == "masked"]
        print(f"{key:6} {name[:48]:48} from {ref_key}  items {m['items']:#x} x{m['name_len'] + 30}"
              f"  party {m['party']:#x}{'  masked ' + ','.join(masked) if masked else ''}")
    out_json = os.path.join(HERE, "..", "build", "ports", "field_items.json")
    os.makedirs(os.path.dirname(out_json), exist_ok=True)
    import json
    with open(out_json, "w") as f:
        json.dump(js, f, indent=1)
    if "--no-write" in sys.argv:
        return
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("""package com.pokedaisy.app.companion.data

// Generated by scripts/port_field_items.py from the user's ROMs - don't edit by hand.
// USE / GIVE / TAKE's tables (FieldItems.kt) for the other-language releases and English
// FireRed / Ruby / Sapphire rev 0, by game code + revision; each checked headless.

val FIELD_ITEMS_PORTS: Map<String, FieldItemFns> = mapOf(
""" + "\n".join(blocks) + "\n)\n")
    print("wrote", os.path.relpath(OUT), len(blocks), "tables")


if __name__ == "__main__":
    main()
