package com.afflictions.implant;

import java.util.Locale;
import java.util.Optional;

import com.afflictions.config.AfflictionsConfig;

/** The four implant tiers, in the same order as the strings stored in {@link com.afflictions.data.ImplantEntry}. */
public enum ImplantTier {
	WOOD, IRON, REDSTONE, NETHERITE;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<ImplantTier> byId(String id) {
		for (ImplantTier tier : values()) {
			if (tier.id().equals(id)) return Optional.of(tier);
		}
		return Optional.empty();
	}

	public boolean needsCharge() {
		return this == REDSTONE;
	}

	/** Penalty this tier leaves on the limb it replaces, before the charge check. */
	public double penalty() {
		AfflictionsConfig config = AfflictionsConfig.get();
		return switch (this) {
			case WOOD -> config.implantPenaltyWood;
			case IRON -> config.implantPenaltyIron;
			case REDSTONE -> config.implantPenaltyRedstone;
			case NETHERITE -> config.implantPenaltyNetherite;
		};
	}
}
