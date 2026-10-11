package com.afflictions.implant;

import net.minecraft.world.item.Item;

/** A crafted prosthetic. Installed by right-clicking a smithing table, see {@link com.afflictions.limb.LimbManager}. */
public class ImplantItem extends Item {
	private final ImplantTier tier;
	private final boolean arm;

	public ImplantItem(Properties properties, ImplantTier tier, boolean arm) {
		super(properties);
		this.tier = tier;
		this.arm = arm;
	}

	public ImplantTier tier() {
		return this.tier;
	}

	public boolean isArm() {
		return this.arm;
	}
}
