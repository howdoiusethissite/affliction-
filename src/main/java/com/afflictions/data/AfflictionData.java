package com.afflictions.data;

import net.minecraft.world.entity.player.Player;

/** The only way later phases should touch afflictions state. */
public final class AfflictionData {
	public static PermanentState getPermanent(Player player) {
		return player.getAttachedOrElse(AfflictionAttachments.PERMANENT, PermanentState.EMPTY);
	}

	public static TransientState getTransient(Player player) {
		return player.getAttachedOrElse(AfflictionAttachments.TRANSIENT, TransientState.EMPTY);
	}

	public static void setPermanent(Player player, PermanentState state) {
		player.setAttached(AfflictionAttachments.PERMANENT, state);
	}

	public static void setTransient(Player player, TransientState state) {
		player.setAttached(AfflictionAttachments.TRANSIENT, state);
	}

	public static void addPhobia(Player player, String key) {
		PermanentState s = getPermanent(player);
		if (s.phobias().containsKey(key)) return;
		setPermanent(player, s.withPhobia(key, PhobiaEntry.EMPTY));
	}

	public static void removePhobia(Player player, String key) {
		setPermanent(player, getPermanent(player).withoutPhobia(key));
	}

	public static boolean hasPhobia(Player player, String key) {
		return getPermanent(player).phobias().containsKey(key);
	}

	/** Adds one exposure kill to a phobia the player has and returns the new count, or -1 if they lack it. */
	public static int addExposureKill(Player player, String key) {
		PermanentState s = getPermanent(player);
		PhobiaEntry entry = s.phobias().get(key);
		if (entry == null) return -1;
		PhobiaEntry updated = entry.withExposureKills(entry.exposureKills() + 1);
		setPermanent(player, s.withPhobia(key, updated));
		return updated.exposureKills();
	}

	public static void addScar(Player player, String id) {
		setPermanent(player, getPermanent(player).withScar(id));
	}

	public static void removeScar(Player player, String id) {
		setPermanent(player, getPermanent(player).withoutScar(id));
	}

	public static void setLimb(Player player, Limb limb, LimbState state) {
		setPermanent(player, getPermanent(player).withLimb(limb, state));
	}

	public static void setImplant(Player player, Limb limb, String tier) {
		PermanentState s = getPermanent(player);
		ImplantEntry old = s.implants().get(limb);
		setPermanent(player, s.withImplant(limb, old == null ? new ImplantEntry(tier, 0) : old.withTier(tier)));
	}

	public static void clearImplant(Player player, Limb limb) {
		setPermanent(player, getPermanent(player).withoutImplant(limb));
	}

	public static float getFear(Player player) {
		return getTransient(player).fear();
	}

	public static void setFear(Player player, float value) {
		setTransient(player, getTransient(player).withFear(value));
	}

	public static void clearAll(Player player) {
		setPermanent(player, PermanentState.EMPTY);
		setTransient(player, TransientState.EMPTY);
	}

	private AfflictionData() {
	}
}
