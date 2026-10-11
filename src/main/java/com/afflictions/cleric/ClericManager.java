package com.afflictions.cleric;

import java.util.Comparator;
import java.util.Map;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;
import com.afflictions.data.PermanentState;
import com.afflictions.data.PhobiaEntry;
import com.afflictions.infection.InfectionManager;

/**
 * Phase 5: sneak + right-click a cleric holding the payment item to buy one treatment. Each use buys the first
 * applicable service in the order infection, phobia, scar, so a player who has all three works through them in turn.
 * Which services a cleric offers depends on its villager level, and costs come from the config.
 */
public final class ClericManager {
	public enum Service {
		INFECTION("infection"),
		PHOBIA("phobia"),
		SCAR("scar");

		private final String id;

		Service(String id) {
			this.id = id;
		}

		Component label() {
			return Component.translatable("afflictions.service." + this.id);
		}
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Villager villager)) return InteractionResult.PASS;
			if (!isHealingInteraction(player.isShiftKeyDown(), player.isSpectator(), villager, player.getItemInHand(hand))) {
				return InteractionResult.PASS;
			}
			// Both sides consume the click so the trading screen never opens; only the server does the work.
			if (player instanceof ServerPlayer serverPlayer) offerServices(serverPlayer, villager);
			return InteractionResult.SUCCESS;
		});
	}

	private static boolean isHealingInteraction(boolean sneaking, boolean spectator, Villager villager, ItemStack held) {
		AfflictionsConfig config = AfflictionsConfig.get();
		return config.clericHealingEnabled && sneaking && !spectator && !villager.isBaby()
				&& villager.getVillagerData().profession().is(VillagerProfession.CLERIC)
				&& held.is(paymentItem());
	}

	private static Item paymentItem() {
		Identifier id = Identifier.tryParse(AfflictionsConfig.get().clericPaymentItem);
		return id == null ? net.minecraft.world.item.Items.EMERALD
				: BuiltInRegistries.ITEM.getOptional(id).orElse(net.minecraft.world.item.Items.EMERALD);
	}

	/** First service the player needs, in priority order, or null if there is nothing to treat. */
	public static Service nextNeeded(PermanentState permanent, boolean hasInfections) {
		if (hasInfections) return Service.INFECTION;
		if (!permanent.phobias().isEmpty()) return Service.PHOBIA;
		if (!permanent.scars().isEmpty()) return Service.SCAR;
		return null;
	}

	public static int minLevel(Service service) {
		AfflictionsConfig config = AfflictionsConfig.get();
		return switch (service) {
			case INFECTION -> config.clericInfectionMinLevel;
			case PHOBIA -> config.clericPhobiaMinLevel;
			case SCAR -> config.clericScarMinLevel;
		};
	}

	public static int cost(Service service) {
		AfflictionsConfig config = AfflictionsConfig.get();
		return Math.max(0, switch (service) {
			case INFECTION -> config.clericInfectionCost;
			case PHOBIA -> config.clericPhobiaCost;
			case SCAR -> config.clericScarCost;
		});
	}

	private static void offerServices(ServerPlayer player, Villager villager) {
		PermanentState permanent = AfflictionData.getPermanent(player);
		boolean infected = !AfflictionData.getTransient(player).infections().isEmpty();
		Service service = nextNeeded(permanent, infected);
		if (service == null) {
			refuse(player, villager, Component.translatable("afflictions.cleric.nothing"));
			return;
		}

		int level = villager.getVillagerData().level();
		if (level < minLevel(service)) {
			refuse(player, villager, Component.translatable("afflictions.cleric.too_low", service.label(), minLevel(service)));
			return;
		}

		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		int cost = cost(service);
		boolean free = player.hasInfiniteMaterials();
		if (!free && held.getCount() < cost) {
			refuse(player, villager, Component.translatable("afflictions.cleric.cost", service.label(), cost, held.getHoverName()));
			return;
		}

		if (!treat(player, service)) return;
		if (!free) held.shrink(cost);

		ServerLevel serverLevel = player.level();
		villager.playSound(SoundEvents.VILLAGER_YES, 1.0f, villager.getVoicePitch());
		serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getEyeY(), villager.getZ(),
				7, 0.5, 0.5, 0.5, 0.0);
		player.sendSystemMessage(Component.translatable("afflictions.cleric.done." + service.name().toLowerCase(java.util.Locale.ROOT)));
	}

	/** Applies the treatment itself. Returns false if there turned out to be nothing to treat. */
	private static boolean treat(ServerPlayer player, Service service) {
		switch (service) {
			case INFECTION -> InfectionManager.cureAll(player);
			case PHOBIA -> {
				Map<String, PhobiaEntry> phobias = AfflictionData.getPermanent(player).phobias();
				String key = phobias.entrySet().stream()
						.max(Comparator.<Map.Entry<String, PhobiaEntry>>comparingInt(e -> e.getValue().exposureKills())
								.thenComparing(Map.Entry::getKey))
						.map(Map.Entry::getKey).orElse(null);
				if (key == null) return false;
				AfflictionData.removePhobia(player, key);
			}
			case SCAR -> {
				var scars = AfflictionData.getPermanent(player).scars();
				if (scars.isEmpty()) return false;
				AfflictionData.removeScar(player, scars.getFirst());
			}
		}
		return true;
	}

	private static void refuse(ServerPlayer player, Villager villager, Component message) {
		villager.playSound(SoundEvents.VILLAGER_NO, 1.0f, villager.getVoicePitch());
		player.sendSystemMessage(message);
	}

	private ClericManager() {
	}
}
