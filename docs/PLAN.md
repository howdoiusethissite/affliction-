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
| Max health loss persistence | Resets on death or survives | Resets on death (stored in transient state); scars still persist and cost max health |
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
4. **Infection, scars, max health loss.** Infection risk window, tag-based treatments, attribute modifiers for hearts, dark heart outlines, scar icons in inventory.
5. **Cleric healing.** Sneak + right-click a cleric holding emeralds; services gated by villager level; costs from config.
6. **Amputation and implants.** Implant items and recipes, smithing table install, redstone charge, stat effects per limb.
7. **Hallucinations.** Client-side fake entities and sounds driven by synced fear value. Last, because it is the most fiddly and depends on everything above.
8. **Polish (done, 0.8.0).** Tooltips on vanilla treatment items, config screen, balancing, lang file.

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

## Phase 4 notes (done, 0.4.0)

- Wounds: a mob hit of at least `infectionMinDamage` rolls `infectionChance` and opens a wound (stage 0) with a risk window. Honey bottle (`#afflictions:antiseptics`) closes it, water bottle (`#afflictions:rinses`) adds a rinse that lowers the set-in chance. When the window closes it rolls `infectionSetInChance` minus the rinse reduction.
- Stages 1 to 3 every `infectionStageTicks`: Weakness, then Hunger, then stronger Weakness plus Slowness and max health loss each interval. Regeneration clears a set-in infection; curing at `infectionScarMinStage` or later leaves a scar. A single hit of `scarSevereHitDamage` also leaves a scar.
- Max health loss comes from an empty food bar and from infection, recovers while well fed and faster asleep, and `#afflictions:max_health_restorers` (golden apples, glistering melon) restores it. Each scar costs `scarMaxHealthPenalty`.
- New commands: `/afflict infection add/remove`, `/afflict maxhealth loss`.
- Not done: exhaustion as a loss source (no definition yet), amputation from late infection (Phase 6).
- Untested in game: lost-heart outlines and scar icons render client side only. Dedicated server startup and the build were checked.

## Phase 5 notes (done, 0.5.0)

- Sneak + right-click an adult cleric while holding `clericPaymentItem` (default emerald) in the main hand. The click is consumed, so the trading screen does not open; without sneaking, vanilla trading is unchanged.
- Each use buys one service, picked in this order: infection, phobia, scar. Infection clears every wound and infection with no scar (`InfectionManager.cureAll`). Phobia removes the one with the most exposure kills. Scar removes the oldest.
- Gating by villager level and costs, all in the config: `clericInfectionMinLevel` 2 / `clericInfectionCost` 4, `clericPhobiaMinLevel` 1 / `clericPhobiaCost` 8, `clericScarMinLevel` 5 / `clericScarCost` 24. `clericHealingEnabled` turns it off.
- Creative players pay nothing. Refusals (nothing to treat, level too low, not enough items) play the villager "no" sound and explain why in chat; success plays "yes" with happy-villager particles.
- Max health loss is not a cleric service (the plan table gives it no cleric cure). Scar removal restores its max health penalty on the next MaxHealthManager tick.
- Test: `/summon villager ~ ~ ~ {VillagerData:{profession:"minecraft:cleric",level:2,type:"minecraft:plains"}}`, then `/afflict infection add @s zombie 1`, hold emeralds and sneak + right-click it.
- Checked: build, and dedicated server startup with FFInjury installed. The interaction itself has not been exercised with a real player.

## Phase 6 notes (done, 0.6.0)

- Limb penalty: 0 intact, 1 amputated with nothing fitted, otherwise the implant tier's penalty (`implantPenaltyWood` 0.5, `Iron` 0.25, `Redstone` -0.1, `Netherite` -0.15). Implants only count on an amputated limb. A redstone implant at 0 charge counts as nothing fitted. Code: `LimbManager.penalty`.
- Effects: legs scale movement speed (`legSpeedPenalty`) and a fully missing leg blocks sprinting. Arms scale mining speed (`armMiningPenalty`) and attack speed (`armAttackSpeedPenalty`). The offhand arm is the one opposite the main arm; when it is fully missing the offhand cannot be used and its item is moved to the inventory.
- Amputation sources: an infection at max stage going another interval untreated rolls `infectionAmputationChance` (default 0.25) to take a random intact limb. `/afflict limb amputate` now also drops any fitted implant. The "re-broken limb at max tier" trigger was added in Phase 8.
- New items (the only ones in the mod): `<tier>_<arm|leg>_implant` for wood, iron, redstone, netherite, with shaped recipes under `data/afflictions/recipe`, placeholder textures and an en_us lang file with only these item names. They appear in the Tools & Utilities creative tab.
- Install: right-click a smithing table holding an implant. It fills a missing limb of that kind with no implant first, otherwise swaps an implant of a different tier (the old one returns as an item, charge lost).
- Redstone charge is in ticks, drains 100 per 100 ticks while installed (`redstoneMaxCharge` 36000). Sneak + use redstone dust refills `redstoneDustCharge`. New install starts with `redstoneInstallCharge`.
- New command: `/afflict implant charge <player> <limb> <ticks>`.
- Checked: build and dedicated server startup with FFInjury (items and recipes load without errors). Not exercised with a real player: smithing table install, dust recharge, sprint and offhand blocking, attribute modifiers.

## Phase 8 notes (done, 0.8.0)

Phase 7 (hallucinations) was skipped on purpose and is still open.

- **Re-broken limb amputation (decision).** Counts the injuries in `ffinjuryLimbBreakInjuries` (default `broken_leg`, `ankle_fracture`, the two leg injuries FFInjury ships). If the player already has one at FFInjury's max tier (`InjuryAPI.getDefaultMaxTier`) before a hit, and that hit is one of `ffinjuryLimbBreakDamageTypes` (default `minecraft:fall`) for at least `ffinjuryLimbBreakMinDamage` (4.0), there is an `ffinjuryLimbBreakAmputationChance` (0.35) chance to take a random intact leg. The max-tier state is read in `ALLOW_DAMAGE`, before FFInjury reacts, so the hit that first brings an injury to max never counts. Chosen over listening to `InjuryAppliedEvent` because `addOrIncreaseInjury` may return empty at max tier and then fire nothing. Arms are never taken by this trigger.
- **Tooltips.** `TreatmentTooltips` adds gray one-liners through `ItemTooltipCallback` for the antiseptic and max health restorer tags, plain water bottles, Regeneration potions, redstone dust, the cleric payment item and the implants. Matches the server's tags, so packs extending a tag get the tooltip. `treatmentTooltipsEnabled` turns them off.
- **Config screen.** `AfflictionsConfigScreen` builds itself by reflection over the public fields of `AfflictionsConfig` (booleans toggle, numbers, text and comma-separated lists use text boxes that turn red when invalid). Headings come from `@Section` on the first field of each group. Changes apply to the live config at once and are written on Done; Cancel re-reads the file; hovering shows the default. Opened with the client command `/afflictions config`. No Mod Menu entry yet because it would add a compile dependency.
- **Lang file.** Every player-facing message, tooltip, HUD label, scar name and config label is a translation key in `en_us.json`. Config option names fall back to a humanized field name if a key is missing, so new options need no lang edit. `/afflict` output stays English.
- **Balance.** Without play data only two changes: `infectionChance` 0.35 to 0.25 (a mob hit of one heart or more opened a wound too often) and `infectionRiskWindowTicks` 2400 to 3600 (three minutes to find honey). Everything else is unchanged and should be tuned in play through the config.
- Checked: build with JDK 25 and dedicated server startup with FFInjury installed (config written with the new keys). Not exercised in a real client: the config screen layout, tooltips, and the leg re-break trigger.

## FFInjury integration (done with 0.4.0)

Soft dependency: `fabric.mod.json` only lists it under `suggests`, and the build compiles against FFInjury 0.10.0 for 26.3 (API package `com.fyxe.ffinjury.api`). The jar is All Rights Reserved and has no known Maven repo, so it is not in git: put it at `libs/ffinjury-0.10.0-26.3-fabric.jar` before building (the build fails with a message if it is missing). All API use is isolated in `FfinjuryBridge`, which is only loaded after a `isModLoaded("ffinjury")` check in `FfinjuryCompat`.

| Direction | Hook | Effect |
| --- | --- | --- |
| FFInjury to us | `InjuryAppliedEvent` (new injury) | Ids in `ffinjuryWoundInjuries` (default bleeding, burn) open an infection wound with source `ffinjury:<id>` |
| FFInjury to us | `InjuryTreatedEvent` | A bandage also seals any open wound |
| FFInjury to us | `InjuryAPI.getInjuryCount` | Each injury drains fear by `fearInjuryDrainPerSecond` |
| Us to FFInjury | Infection reaching its max stage | Grants `ffinjuryInfectionInjury` (default bleeding), guarded so it cannot loop back into a wound |
| Both | `isGameplaySuppressed` | Creative players are skipped |

`ffinjuryReplacesMobWounds` (default false) stops mob hits from opening wounds on their own when FFInjury is installed, to avoid two systems punishing the same hit. Phase 6's re-break trigger uses `InjuryAPI.getTier` (see Phase 8 notes).

Only startup with both mods was checked (the log shows the integration enabled). The event hooks have not been exercised with a real player, and running without FFInjury was not tested.

## Workflow rules

Paste this section plus the current phase into each new coding chat instead of re-explaining the mod.

- One phase per chat. Start each chat with: decisions table, current phase spec, list of existing files (names only).
- Ask for changed or new files only. Never request the full project again.
- Build errors: paste the error and the one file it names. Add a second file only if asked.
- All tuning numbers go in the config. Balancing means editing JSON, never asking for code.
- Test with `/afflict` commands instead of dying or grinding for real.
- Bump the version every iteration (0.1.0 for Phase 1, 0.2.0 for Phase 2, patch numbers for fixes).
- After a phase passes its checklist, update this doc's decisions table if anything changed, then start the next chat.

## Fear effects (0.9.0)

Tiers from fear: Uneasy below 70, Shaken below 50, Breaking below 25, breakdown at 0 (all in config). Server code is `FearEffects`, tier math is `FearTier`, client code is `FearClientEffects` (heartbeat, sway, aim jitter) and `FearOverlay` (vignette, gray wash, flickering health and hunger bars).

- **Natural regeneration** is slowed without touching the food code: a rise of at most one health point with no Regeneration effect is undone with the tier's chance. Side effect: the exhaustion that heal cost is still spent.
- **Mob detection** adds a transient `FOLLOW_RANGE` modifier to monsters near a Shaken or worse player and removes it a second after they leave.
- **Villager prices** add to every offer's special price difference when the player clicks a villager, and reset it first so a click that never opened the trade screen cannot stack.
- **Sleep** multiplies recovery per tier and caps the gain of one sleep (`fearSleepMaxRestorePerNight`) when it began at Shaken or lower.
- **Experience** is scaled in `PlayerMixin` on `Player.giveExperiencePoints`, the only new mixin.
- **Breakdown** runs from `FearManager` when fear reaches 0 (`/afflict fear set @s 0` triggers it).
- **Desaturation** is a translucent gray fill, not a real saturation shader.
- **Left out:** compass and clock spinning.
- **Not compiled or run in a game** when written. Unverified vanilla names: `Player.giveExperiencePoints`, `Player.causeFoodExhaustion`, `MerchantOffer.resetSpecialPriceDiff` and `addToSpecialPriceDiff`, `Villager.getTradingPlayer`, `SoundEvents.WARDEN_HEARTBEAT`, `SimpleSoundInstance.forUI(SoundEvent, float, float)`, `MobEffects.SPEED`, `STRENGTH`, `RESISTANCE`, `MINING_FATIGUE`, `HudElementRegistry.replaceElement`, `VanillaHudElements.HEALTH_BAR`, and the `HudElement` method name `extractRenderState`.

## 0.10.0: pause-menu settings button and held light sources

- **Settings screen** opens from an "Afflictions" button in the top-left of the pause menu (`ScreenEvents.AFTER_INIT`), as well as `/afflictions config`. Cancel returns to the pause menu.
- **Light sources** held in the offhand (and the main hand if `fearLightSourceMainHand`) cancel `fearLightSourceDrainReduction` of the dark and cave drain and add `fearLightSourceRecoveryPerSecond`, only while it would otherwise be dark or deep. Items are matched by registry id from `fearLightSourceItems`. `/afflict fear rate` shows the new `light` term.
- **Accessory slots:** vanilla has none, and no accessories mod is a dependency, so only hands are checked.
- **Not compiled or run** (no JDK 25 where this was written). Unverified: `Screens.getWidgets` in Fabric API 0.161.0, `Minecraft.setScreenAndShow` from a button, and the `copper_torch` and `copper_lantern` item ids (an id that does not exist is simply never matched).

## 0.11.0: wound locations, crippling, voluntary amputation, Trinkets

- **Wound limb:** `InfectionEntry` has a `limb` string (serialized `Limb` name, empty for older saves). It is picked at random when a mob wound or FFInjury wound opens, kept through every stage, and is the limb an untreated max-stage infection amputates first (random fallback if it is gone).
- **Display:** client `WoundStatus` draws a corner list from the synced transient state and backs `/afflictions wounds`. Times are `riskEndsTick` minus the client's game time.
- **Crippling:** `TransientState.crippled` maps a limb to the tick it recovers. `CrippleManager` sets it from `AFTER_DAMAGE` (single hit at or above `crippleDamageThreshold`, survivor only, falls pick a leg). `LimbManager.effectivePenalty` takes the larger of the implant penalty and `crippledLimbPenalty` for intact limbs, so it only touches movement, mining and attack speed (no sprint or offhand block). Resets on death.
- **Voluntary amputation:** `/amputate <limb>`, available to everyone, two-step confirm (15 s), optional smithing table within range. Refuses limbs that are already gone or already hold an implant.
- **Trinkets:** `TrinketsCompat` and `TrinketsBridge` use `TrinketsApi.getAttachment(player).isEquipped(Predicate)`. `data/trinkets/tags/item/legs/belt.json` makes torches and lanterns wearable in the belt slot. The build needs `libs/trinkets-4.2.1-26.3.jar`.
- **Not compiled or run** (no JDK 25 where this was written). Unverified: `DamageTypeTags.IS_FALL` and `BYPASSES_INVULNERABILITY`, `FabricClientCommandSource.sendFeedback`, `ServerLivingEntityEvents.AFTER_DAMAGE` also firing for hits that were survived (it is guarded by `isAlive`), and the Trinkets call above (checked against the jar's signatures with javap only).
