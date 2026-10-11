# Afflictions Mod Plan (working title)

Oct 11, 2026 · @no

## Overview

A Fabric mod for Minecraft 26.3 (Java 25) that makes survival tense: deaths leave phobias, untreated wounds become infections and scars, and severe damage can cost a limb that you replace with crafted implants. Inspired by The Long Dark, Project Zomboid, Fear & Hunger and RimWorld. Designed to sit beside FFInjury (Fyxe) and reuse vanilla items wherever possible; new items only for implants.

Target: Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Mojang mappings.

## Open decisions

Lock these before Phase 1; each one changes the data model. Suggested defaults are filled in so coding can start if nothing changes.

| Decision | Options | Suggested default |
| --- | --- | --- |
| Mod name and ID | Undecided | Pick before Phase 1 so packages never get renamed |
| FFInjury relationship | Hard dependency, soft (optional) integration, standalone | Soft: works alone, reads FFInjury injuries through its API when installed |
| What survives death | Phobias, scars, amputations, implants, sanity | Phobias, scars, amputations and implants persist; sanity resets to full; infections clear |
| Persistence toggle | Config flag for hardcore-only permanence | Yes, `permanentConditionsSurviveDeath` (default true) |
| Fear HUD | Own bar, vanilla effect icon, hidden | Own small bar beside the hunger bar |
| Milk interaction | Clears mod conditions or not | Mod state is player data, so milk never touches it |

## Systems

Every system stores state on the player, never as items or vanilla status effects, so milk and death rules stay under mod control.

| System | Trigger | Effect | Cure |
| --- | --- | --- | --- |
| Phobia | Dying to a mob type, fall, drowning, lava or fire | Near the source (default 16 blocks): short Nausea burst on first sighting, Darkness, faster fear drain | Cleric (novice+) for emeralds, or exposure: kill N of that mob while phobic |
| Fear / sanity (0 to 100) | Darkness, caves, active phobias, injuries, bleeding | Low fear feeds hallucinations and slows healing | Campfires, jukebox playing, pets or villagers nearby, sleep |
| Infection | Mob-dealt wounds roll an infection risk; window before it sets in | Escalating debuffs, then max health loss, then amputation risk | Honey bottle during risk window; water bottle rinse lowers chance; cleric (apprentice+) or regeneration once set in |
| Scars | Severe or untreated injuries and infections | Small permanent stat penalty, shown as an inventory icon with hover details | Master cleric, enchanted golden apple, or beacon Regeneration over time |
| Max health loss | Starvation, exhaustion, infection | Lost hearts drawn as dark outlines | Slow recovery from eating well and sleeping; golden apple or glistering melon speeds it |
| Amputation | Untreated infection past its limit, or a re-broken limb at max tier | Arm: no offhand, slower mining and attacks. Leg: no sprint, slower movement | Implant |
| Implants (new items) | Crafted, installed at a smithing table | Wood and iron restore function; redstone beats natural but needs dust to recharge; netherite is endgame with no upkeep | Swap or upgrade at the smithing table |
| Hallucinations | Low fear | Client-side fake mobs (no shadow, no footsteps, vanish when hit) and fake sounds; at near-zero fear some can deal real damage | Restore fear |

All treatment items are matched by item tags (for example `#mod:antiseptics`) so packs can add Farmer's Delight or FFInjury items without code changes.

## Build phases

One phase per chat. Each ends with a build that launches and something testable in game; do not start the next phase until it does.

1. **Player data and debug commands.** No gameplay. Data object, persistence, client sync, `/afflict` commands. Detailed below.
2. **Phobias.** Record killer on death, apply phobia on respawn, proximity effects, exposure cure counter.
3. **Fear meter.** Drain and recovery sources, HUD bar, phobias feed into drain.
4. **Infection, scars, max health loss.** Infection risk window, tag-based treatments, attribute modifiers for hearts, dark heart outlines, scar icons in inventory. Optional FFInjury hooks here.
5. **Cleric healing.** Sneak + right-click a cleric holding emeralds; services gated by villager level; costs from config.
6. **Amputation and implants.** Implant items and recipes, smithing table install, redstone charge, stat effects per limb.
7. **Hallucinations.** Client-side fake entities and sounds driven by synced fear value. Last, because it is the most fiddly and depends on everything above.
8. **Polish.** Tooltips on vanilla treatment items, config screen, balancing, lang file.

## Phase 1 spec (hand to coding model)

Goal: every later system has a place to store its state, saved across restarts, synced to the owning client, with commands to edit it. Zero gameplay effects in this phase. Version 0.1.0.

**Storage.** Use Fabric's Data Attachment API (`AttachmentRegistry.create` with a builder). Two attachments on the player, both immutable records with a `Codec` and a `StreamCodec`:

- `permanent_state`: `.persistent(CODEC)`, `.syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly())`, `.copyOnDeath()`. Holds phobias, scars, limbs, implants.
- `transient_state`: `.persistent(CODEC)`, `.syncWith(..., targetOnly())`, no `copyOnDeath`. Holds fear and infections; resets on death automatically.

If config `permanentConditionsSurviveDeath` is false, clear `permanent_state` in `ServerPlayerEvents.AFTER_RESPAWN` (copyOnDeath is fixed at registration, so the toggle is done by clearing).

**Records.**

```java
record PermanentState(
    Map<String, PhobiaEntry> phobias,   // key: entity type id or "fall", "drown", "lava", "fire"
    List<String> scars,                 // scar ids
    Map<Limb, LimbState> limbs,         // LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG
    Map<Limb, ImplantEntry> implants)
record PhobiaEntry(int exposureKills)
enum LimbState { INTACT, AMPUTATED }
record ImplantEntry(String tier, int charge) // tier: wood, iron, redstone, netherite

record TransientState(
    float fear,                          // 0..100, default 100
    Map<String, InfectionEntry> infections)
record InfectionEntry(String source, long riskEndsTick, int stage)
```

All records get static `EMPTY` defaults and `with...` copy methods. Never mutate in place; always `setAttached` or `modifyAttached` with a new record.

**Accessor class.** `AfflictionData` with static helpers (`getPermanent(player)`, `addPhobia(player, key)`, `setFear(player, value)`, etc.) so later phases never touch attachments directly.

**Commands** (permission level 2), registered with `CommandRegistrationCallback`:

| Command | Action |
| --- | --- |
| `/afflict list <player>` | Print both states in chat |
| `/afflict phobia add/remove <player> <key>` | Edit phobias |
| `/afflict scar add/remove <player> <id>` | Edit scars |
| `/afflict limb amputate/restore <player> <limb>` | Edit limbs |
| `/afflict implant set/clear <player> <limb> <tier>` | Edit implants |
| `/afflict fear set <player> <0-100>` | Set fear |
| `/afflict clear <player>` | Reset both states |

**Config.** Simple JSON in `config/<modid>.json`, loaded on init, created with defaults if missing. Phase 1 keys: `permanentConditionsSurviveDeath` (true). Later phases add keys; never hardcode tuning numbers.

**Done when:**

- [ ] Build passes and game launches
- [ ] `/afflict phobia add` then relog: phobia still listed
- [ ] Add phobia and fear 40, die: phobia kept, fear back to 100
- [ ] Config toggle false, die: phobia cleared
- [ ] Client can read synced values (log them once on change, debug only)

## Workflow rules

Paste this section plus the current phase into each new coding chat instead of re-explaining the mod.

- One phase per chat. Start each chat with: decisions table, current phase spec, list of existing files (names only).
- Ask for changed or new files only. Never request the full project again.
- Build errors: paste the error and the one file it names. Add a second file only if asked.
- All tuning numbers go in the config. Balancing means editing JSON, never asking for code.
- Test with `/afflict` commands instead of dying or grinding for real.
- Bump the version every iteration (0.1.0 for Phase 1, 0.2.0 for Phase 2, patch numbers for fixes).
- After a phase passes its checklist, update this doc's decisions table if anything changed, then start the next chat.
