package com.afflictions.fear;

import com.afflictions.config.AfflictionsConfig;

/** Where the fear meter currently sits. Thresholds come from the config. */
public enum FearTier {
	CALM,
	UNEASY,
	SHAKEN,
	BREAKING;

	public static FearTier of(float fear) {
		AfflictionsConfig config = AfflictionsConfig.get();
		if (!config.fearEffectsEnabled) return CALM;
		if (fear < config.fearBreakingBelow) return BREAKING;
		if (fear < config.fearShakenBelow) return SHAKEN;
		if (fear < config.fearUneasyBelow) return UNEASY;
		return CALM;
	}

	public boolean atLeast(FearTier other) {
		return this.ordinal() >= other.ordinal();
	}

	/** 0 at the top of the Uneasy tier, 1 at fear 0. */
	public static float severity(float fear) {
		double top = Math.max(1.0, AfflictionsConfig.get().fearUneasyBelow);
		return (float) Math.max(0.0, Math.min(1.0, 1.0 - fear / top));
	}
}
