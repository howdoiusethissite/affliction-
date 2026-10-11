package com.afflictions.client;

import java.util.List;
import java.util.Locale;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import com.afflictions.config.AfflictionsConfig;
import com.afflictions.health.MaxHealthManager;
import com.afflictions.implant.ImplantItem;
import com.afflictions.infection.InfectionManager;

/**
 * Phase 8: one-line hints on the vanilla items this mod gives a use. The item sets come from the same tags the
 * server matches against, so a pack that adds items to a tag gets the tooltip too.
 */
public final class TreatmentTooltips {
	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (AfflictionsConfig.get().treatmentTooltipsEnabled) addLines(stack, lines);
		});
	}

	private static void addLines(ItemStack stack, List<Component> lines) {
		AfflictionsConfig config = AfflictionsConfig.get();
		Item item = stack.getItem();

		if (item instanceof ImplantItem implant) {
			lines.add(hint("afflictions.tooltip.implant." + (implant.isArm() ? "arm" : "leg")));
			lines.add(hint("afflictions.tooltip.implant.tier." + implant.tier().id()));
			return;
		}
		if (stack.is(InfectionManager.ANTISEPTICS)) lines.add(hint("afflictions.tooltip.antiseptic"));
		else if (InfectionManager.isRinse(stack)) lines.add(hint("afflictions.tooltip.rinse"));
		if (stack.is(MaxHealthManager.RESTORERS)) {
			lines.add(Component.translatable("afflictions.tooltip.restorer", trim(config.restorerMaxHealthRestore / 2.0))
					.withStyle(ChatFormatting.GRAY));
		}
		if (hasRegeneration(stack)) lines.add(hint("afflictions.tooltip.regeneration"));
		if (stack.is(Items.REDSTONE)) lines.add(hint("afflictions.tooltip.redstone_dust"));
		if (config.clericHealingEnabled && stack.is(paymentItem())) lines.add(hint("afflictions.tooltip.cleric_payment"));
	}

	private static boolean hasRegeneration(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null) return false;
		for (MobEffectInstance effect : contents.getAllEffects()) {
			if (effect.getEffect().is(MobEffects.REGENERATION)) return true;
		}
		return false;
	}

	private static Item paymentItem() {
		Identifier id = Identifier.tryParse(AfflictionsConfig.get().clericPaymentItem);
		return id == null ? Items.EMERALD : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(Items.EMERALD);
	}

	private static Component hint(String key) {
		return Component.translatable(key).withStyle(ChatFormatting.GRAY);
	}

	static String trim(double value) {
		return value == Math.floor(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
	}

	private TreatmentTooltips() {
	}
}
