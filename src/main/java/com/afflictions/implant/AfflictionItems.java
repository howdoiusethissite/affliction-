package com.afflictions.implant;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import com.afflictions.Afflictions;

/** The only new items in the mod: one arm and one leg implant per tier. */
public final class AfflictionItems {
	private static final List<ImplantItem> IMPLANTS = new ArrayList<>();

	public static void register() {
		for (ImplantTier tier : ImplantTier.values()) {
			IMPLANTS.add(registerImplant(tier, true));
			IMPLANTS.add(registerImplant(tier, false));
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
			for (ImplantItem item : IMPLANTS) entries.accept(item);
		});
	}

	/** Item id path, for example wood_arm_implant. Also used for textures, models and recipes. */
	public static String name(ImplantTier tier, boolean arm) {
		return tier.id() + (arm ? "_arm" : "_leg") + "_implant";
	}

	private static ImplantItem registerImplant(ImplantTier tier, boolean arm) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Afflictions.id(name(tier, arm)));
		ImplantItem item = new ImplantItem(new Item.Properties().setId(key).stacksTo(1), tier, arm);
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private AfflictionItems() {
	}
}
