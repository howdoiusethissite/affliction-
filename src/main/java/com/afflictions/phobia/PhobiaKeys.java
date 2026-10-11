package com.afflictions.phobia;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Phobia keys: an entity type id ("minecraft:zombie") or one of the environmental keys below. */
public final class PhobiaKeys {
	public static final String FALL = "fall";
	public static final String DROWN = "drown";
	public static final String LAVA = "lava";
	public static final String FIRE = "fire";

	public static final List<String> ENVIRONMENTAL = List.of(FALL, DROWN, LAVA, FIRE);

	public static boolean isEnvironmental(String key) {
		return ENVIRONMENTAL.contains(key);
	}

	public static String ofEntityType(EntityType<?> type) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
	}

	/** Lets commands accept "zombie" for "minecraft:zombie". */
	public static String normalize(String input) {
		String key = input.trim().toLowerCase(Locale.ROOT);
		if (isEnvironmental(key) || key.contains(":")) return key;
		return "minecraft:" + key;
	}

	public static Stream<String> allKnownKeys() {
		return Stream.concat(ENVIRONMENTAL.stream(),
				BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Object::toString));
	}

	/** The phobia a death by this damage source should cause, or null if it should cause none. */
	public static String ofDeath(DamageSource source) {
		if (source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Player)) {
			return ofEntityType(attacker.getType());
		}
		if (source.is(DamageTypes.LAVA)) return LAVA;
		if (source.is(DamageTypes.DROWN)) return DROWN;
		if (source.is(DamageTypes.FALL) || source.is(DamageTypes.ENDER_PEARL)) return FALL;
		if (source.is(DamageTypeTags.IS_FIRE)) return FIRE;
		return null;
	}

	private PhobiaKeys() {
	}
}
