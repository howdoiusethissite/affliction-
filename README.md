# Afflictions

A Fabric mod for Minecraft 26.3 (Java 25) that makes survival tense. Deaths leave phobias, untreated wounds become infections and scars, and severe damage can cost a limb that you replace with a crafted implant. It works alone and also reads FFInjury when that mod is installed.

Version 0.10.0. Hallucinations (Phase 7 in `docs/PLAN.md`) are not implemented yet.

## Systems

| System | What happens | Cure |
| --- | --- | --- |
| Phobias | Dying to a mob, a fall, drowning, lava or fire leaves a phobia. Near the source you get Nausea, Darkness and faster fear drain. | Cleric for emeralds, or kill the feared mob enough times |
| Fear | A 0 to 100 meter drained by darkness, caves, phobias and injuries, shown as a small bar beside the hunger bar. | Campfires, jukeboxes, pets or villagers nearby, sleep, a torch or lantern in the offhand |
| Fear effects | Low fear has tiers: Uneasy (below 70), Shaken (below 50), Breaking (below 25) and a one-time breakdown at 0. See the table below. | Raise fear |
| Infection | Mob hits can open a wound with a short window before it sets in. Infections escalate through debuffs, max health loss and amputation risk. | Honey bottle or water rinse during the window, Regeneration or a cleric afterwards |
| Scars | Late-cured infections and very heavy hits leave a permanent max health penalty, shown above the inventory. | Master cleric |
| Max health loss | Starvation and infection take hearts, drawn as dark outlines. | Eating well and sleeping, golden apples, glistering melon |
| Amputation | An infection left at its worst stage can take a limb. With FFInjury, a leg already at max injury tier that is hit again by a fall can take one too. | Implant |
| Implants | Wood, iron, redstone and netherite arms and legs, installed by right-clicking a smithing table. Redstone needs dust to recharge, netherite needs nothing. | Swap at a smithing table |

### Fear tiers

Fear 100 is calm and 0 is a breakdown. Thresholds are in the config; tiers stack, so Breaking keeps everything from the tiers above it.

| Tier | Effects |
| --- | --- |
| Uneasy (below 70) | Heartbeat that speeds up as fear drops, darkened screen edges and a gray wash, natural regeneration sometimes undone |
| Shaken (below 50) | Camera sway, random jitter while drawing a bow or crossbow, extra hunger, monsters notice you from farther away, villagers charge more, sleep restores less and never more than 30 fear a night |
| Breaking (below 25) | Health and hunger bars flicker and vanish, getting hit can trigger a speed burst followed by Slowness and Weakness, hard hits can make you drop the held item, experience gain is halved |
| Breakdown (0) | Fear jumps back to 30 and either a long debuff (Weakness, Slowness, Mining Fatigue, possibly a new phobia) or, rarely, a steeled moment (Strength and Resistance) |

Not done: the compass and clock spinning in the Breaking tier. The item models read their needle angle in client code that has no Fabric hook, so it needs a mixin or resource overrides of the vanilla models.

State lives on the player, so milk never touches it. Phobias, scars, amputations and implants survive death (configurable); fear, infections and lost hearts reset.

## Using it

- Tooltips on honey, water bottles, golden apples, Regeneration potions, redstone dust, emeralds and the implants explain what each does.
- The settings screen opens from the "Afflictions" button in the top-left corner of the pause menu or with `/afflictions config`. Changes apply at once and are saved on Done. In singleplayer that is the config the game uses; on a dedicated server edit `config/afflictions.json` there instead.
- Holding a torch, soul torch, copper torch, redstone torch, lantern, soul lantern or copper lantern in the offhand cancels the darkness and cave fear drain and restores a little fear. The item list, the strength and a main-hand option are in the config (`fearLightSource*`). Vanilla has no accessory slots, so there is no accessory check yet.
- Test everything with `/afflict` (permission level 2): `list`, `phobia`, `scar`, `limb`, `implant`, `fear`, `infection`, `maxhealth`, `clear`.
- Every tuning number is in `config/afflictions.json`. The settings screen lists every key with its default on hover.

## Building

1. Install JDK 25.
2. Put FFInjury 0.10.0 for 26.3 (Fabric) at `libs/ffinjury-0.10.0-26.3-fabric.jar`. It is All Rights Reserved, so it is not in git. The build stops with a message if it is missing.
3. Run `./gradlew build`, or `./gradlew runServer` for a dedicated server check.

FFInjury is optional at runtime (`suggests` in `fabric.mod.json`). All of its API use is isolated in `FfinjuryBridge`.

## Translations

All player-facing text is in `assets/afflictions/lang/en_us.json`. Admin command output is plain English.

## Docs

`docs/PLAN.md` holds the design, decisions and per-phase notes, including what has and has not been tested in a real game.

## License

CC0-1.0, see `LICENSE`.
