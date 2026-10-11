package com.afflictions.limb;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Holder;

import com.afflictions.Afflictions;
import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;
import com.afflictions.data.ImplantEntry;
import com.afflictions.data.Limb;
import com.afflictions.data.LimbState;
import com.afflictions.data.PermanentState;
import com.afflictions.implant.AfflictionItems;
import com.afflictions.implant.ImplantItem;
import com.afflictions.implant.ImplantTier;

/**
 * Phase 6: amputation, implant installation and the stat effects of missing or replaced limbs.
 * <p>
 * Every limb has a penalty: 0 when intact, 1 when amputated with nothing fitted, and the implant tier's penalty
 * (see {@link ImplantTier#penalty()}) when fitted. A redstone implant with no charge counts as nothing fitted.
 * Legs scale movement speed and a fully missing leg blocks sprinting. Arms scale mining and attack speed, and a
 * fully missing offhand arm blocks everything done with the offhand.
 */
public final class LimbManager {
	private static final Identifier LEG_SPEED_ID = Afflictions.id("leg_speed");
	private static final Identifier ARM_MINING_ID = Afflictions.id("arm_mining");
	private static final Identifier ARM_ATTACK_ID = Afflictions.id("arm_attack");

	private static final int EFFECT_INTERVAL = 10;
	private static final int CHARGE_INTERVAL = 100;

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTickCount();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!player.isAlive() || player.isSpectator()) continue;
				PermanentState state = AfflictionData.getPermanent(player);
				if (state.limbs().isEmpty() && state.implants().isEmpty()) {
					if (tick % EFFECT_INTERVAL == 0) clearModifiers(player);
					continue;
				}
				if (tick % CHARGE_INTERVAL == 0) drainCharge(player, state);
				if (isSprintBlocked(state) && player.isSprinting()) player.setSprinting(false);
				if (isOffhandBlocked(player, state)) dropOffhand(player);
				if (tick % EFFECT_INTERVAL == 0) applyModifiers(player, state);
			}
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (hand == InteractionHand.OFF_HAND && isOffhandBlocked(player)) return InteractionResult.FAIL;
			return rechargeIfPossible(player, hand) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand == InteractionHand.OFF_HAND && isOffhandBlocked(player)) return InteractionResult.FAIL;
			ItemStack held = player.getItemInHand(hand);
			if (held.getItem() instanceof ImplantItem implant
					&& level.getBlockState(hit.getBlockPos()).is(Blocks.SMITHING_TABLE)) {
				if (player instanceof ServerPlayer serverPlayer) install(serverPlayer, hand, held, implant);
				return InteractionResult.SUCCESS;
			}
			return rechargeIfPossible(player, hand) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				hand == InteractionHand.OFF_HAND && isOffhandBlocked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	// ---- state queries -------------------------------------------------------------------------------------

	/** 0 for an intact limb, 1 for a missing one, otherwise what the fitted implant leaves. Can be negative. */
	public static double penalty(PermanentState state, Limb limb) {
		if (state.limb(limb) != LimbState.AMPUTATED) return 0.0;
		ImplantEntry entry = state.implants().get(limb);
		if (entry == null) return 1.0;
		Optional<ImplantTier> tier = ImplantTier.byId(entry.tier());
		if (tier.isEmpty()) return 1.0;
		if (tier.get().needsCharge() && entry.charge() <= 0) return 1.0;
		return tier.get().penalty();
	}

	public static boolean isMissing(PermanentState state, Limb limb) {
		return penalty(state, limb) >= 1.0;
	}

	public static boolean isSprintBlocked(PermanentState state) {
		return isMissing(state, Limb.LEFT_LEG) || isMissing(state, Limb.RIGHT_LEG);
	}

	/** The arm that holds the offhand item is the one opposite the player's main arm. */
	public static Limb offhandArm(Player player) {
		return player.getMainArm() == HumanoidArm.RIGHT ? Limb.LEFT_ARM : Limb.RIGHT_ARM;
	}

	public static boolean isOffhandBlocked(Player player) {
		return isOffhandBlocked(player, AfflictionData.getPermanent(player));
	}

	private static boolean isOffhandBlocked(Player player, PermanentState state) {
		return isMissing(state, offhandArm(player));
	}

	// ---- amputation ----------------------------------------------------------------------------------------

	/** Removes a limb and whatever implant was fitted to it. Does nothing if it is already gone. */
	public static boolean amputate(ServerPlayer player, Limb limb) {
		PermanentState state = AfflictionData.getPermanent(player);
		if (state.limb(limb) == LimbState.AMPUTATED && !state.implants().containsKey(limb)) return false;
		PermanentState next = state.withLimb(limb, LimbState.AMPUTATED).withoutImplant(limb);
		AfflictionData.setPermanent(player, next);
		player.sendSystemMessage(Component.literal("You lose your " + describe(limb) + "."));
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0f, 0.6f);
		return true;
	}

	/** Takes a random limb that is still intact. Returns false when none are left. */
	public static boolean amputateRandom(ServerPlayer player) {
		PermanentState state = AfflictionData.getPermanent(player);
		List<Limb> intact = new ArrayList<>();
		for (Limb limb : Limb.values()) {
			if (state.limb(limb) == LimbState.INTACT) intact.add(limb);
		}
		if (intact.isEmpty()) return false;
		return amputate(player, intact.get(player.getRandom().nextInt(intact.size())));
	}

	public static String describe(Limb limb) {
		return limb.getSerializedName().replace('_', ' ');
	}

	// ---- implants ------------------------------------------------------------------------------------------

	/** Fits the implant in hand to the best matching missing limb: an empty stump first, then a swap. */
	private static void install(ServerPlayer player, InteractionHand hand, ItemStack stack, ImplantItem item) {
		PermanentState state = AfflictionData.getPermanent(player);
		Limb[] candidates = item.isArm()
				? new Limb[] {Limb.LEFT_ARM, Limb.RIGHT_ARM}
				: new Limb[] {Limb.LEFT_LEG, Limb.RIGHT_LEG};

		Limb target = null;
		for (Limb limb : candidates) {
			if (state.limb(limb) == LimbState.AMPUTATED && !state.implants().containsKey(limb)) {
				target = limb;
				break;
			}
		}
		if (target == null) {
			for (Limb limb : candidates) {
				ImplantEntry fitted = state.implants().get(limb);
				if (state.limb(limb) == LimbState.AMPUTATED && fitted != null && !fitted.tier().equals(item.tier().id())) {
					target = limb;
					break;
				}
			}
		}
		if (target == null) {
			player.sendSystemMessage(Component.literal(
					"Nothing to fit this to: you need a missing " + (item.isArm() ? "arm" : "leg")
							+ " without an implant of this tier."));
			return;
		}

		ImplantEntry previous = state.implants().get(target);
		int charge = item.tier().needsCharge() ? AfflictionsConfig.get().redstoneInstallCharge : 0;
		AfflictionData.setPermanent(player, state.withImplant(target, new ImplantEntry(item.tier().id(), charge)));
		if (!player.hasInfiniteMaterials()) stack.shrink(1);
		if (previous != null) giveBack(player, previous, item.isArm());

		player.level().playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
		player.sendSystemMessage(Component.literal("You fit a " + item.tier().id() + " implant to your " + describe(target) + "."));
	}

	/** Returns a removed implant to the player as an item. Its charge is lost. */
	private static void giveBack(ServerPlayer player, ImplantEntry entry, boolean arm) {
		Optional<ImplantTier> tier = ImplantTier.byId(entry.tier());
		if (tier.isEmpty()) return;
		Identifier id = Afflictions.id(AfflictionItems.name(tier.get(), arm));
		net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> {
			ItemStack stack = new ItemStack(item);
			player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		});
	}

	/** Sneak + use with redstone dust refills the redstone implants that are below the maximum. */
	private static boolean rechargeIfPossible(Player player, InteractionHand hand) {
		if (!player.isShiftKeyDown()) return false;
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(Items.REDSTONE)) return false;
		if (!(player instanceof ServerPlayer serverPlayer)) {
			// The client can only guess; answering SUCCESS there would swallow normal dust placement when nothing needs it.
			return needsCharge(AfflictionData.getPermanent(player));
		}

		PermanentState state = AfflictionData.getPermanent(serverPlayer);
		AfflictionsConfig config = AfflictionsConfig.get();
		boolean changed = false;
		PermanentState next = state;
		for (Limb limb : Limb.values()) {
			ImplantEntry entry = state.implants().get(limb);
			if (entry == null || !isCharged(entry) || state.limb(limb) != LimbState.AMPUTATED) continue;
			if (entry.charge() >= config.redstoneMaxCharge) continue;
			next = next.withImplant(limb, entry.withCharge(Math.min(config.redstoneMaxCharge, entry.charge() + config.redstoneDustCharge)));
			changed = true;
			break;
		}
		if (!changed) return false;
		AfflictionData.setPermanent(serverPlayer, next);
		if (!player.hasInfiniteMaterials()) held.shrink(1);
		serverPlayer.level().playSound(null, serverPlayer.blockPosition(), SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.PLAYERS, 0.6f, 1.4f);
		return true;
	}

	private static boolean needsCharge(PermanentState state) {
		int max = AfflictionsConfig.get().redstoneMaxCharge;
		for (Limb limb : Limb.values()) {
			ImplantEntry entry = state.implants().get(limb);
			if (entry != null && isCharged(entry) && state.limb(limb) == LimbState.AMPUTATED && entry.charge() < max) return true;
		}
		return false;
	}

	/** True for implants that run on charge, so only redstone. */
	private static boolean isCharged(ImplantEntry entry) {
		return ImplantTier.byId(entry.tier()).map(ImplantTier::needsCharge).orElse(false);
	}

	private static void drainCharge(ServerPlayer player, PermanentState state) {
		PermanentState next = state;
		for (Limb limb : Limb.values()) {
			ImplantEntry entry = state.implants().get(limb);
			if (entry == null || !isCharged(entry) || state.limb(limb) != LimbState.AMPUTATED || entry.charge() <= 0) continue;
			int charge = Math.max(0, entry.charge() - CHARGE_INTERVAL);
			next = next.withImplant(limb, entry.withCharge(charge));
			if (charge == 0) {
				player.sendSystemMessage(Component.literal("Your " + describe(limb) + " implant has run out of charge. Sneak + use redstone dust to recharge it."));
			}
		}
		if (!next.equals(state)) AfflictionData.setPermanent(player, next);
	}

	// ---- stat effects --------------------------------------------------------------------------------------

	private static void applyModifiers(ServerPlayer player, PermanentState state) {
		AfflictionsConfig config = AfflictionsConfig.get();
		double legs = penalty(state, Limb.LEFT_LEG) + penalty(state, Limb.RIGHT_LEG);
		double arms = penalty(state, Limb.LEFT_ARM) + penalty(state, Limb.RIGHT_ARM);
		setModifier(player, Attributes.MOVEMENT_SPEED, LEG_SPEED_ID, -legs * config.legSpeedPenalty);
		setModifier(player, Attributes.BLOCK_BREAK_SPEED, ARM_MINING_ID, -arms * config.armMiningPenalty);
		setModifier(player, Attributes.ATTACK_SPEED, ARM_ATTACK_ID, -arms * config.armAttackSpeedPenalty);
	}

	private static void clearModifiers(ServerPlayer player) {
		setModifier(player, Attributes.MOVEMENT_SPEED, LEG_SPEED_ID, 0.0);
		setModifier(player, Attributes.BLOCK_BREAK_SPEED, ARM_MINING_ID, 0.0);
		setModifier(player, Attributes.ATTACK_SPEED, ARM_ATTACK_ID, 0.0);
	}

	private static void setModifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) return;
		AttributeModifier existing = instance.getModifier(id);
		if (Math.abs(amount) < 0.0001) {
			if (existing != null) instance.removeModifier(id);
			return;
		}
		// Two missing parts at full penalty would otherwise stall the player outright.
		double clamped = Math.max(-0.9, amount);
		if (existing == null || Math.abs(existing.amount() - clamped) > 0.0001) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, clamped, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void dropOffhand(ServerPlayer player) {
		ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
		if (offhand.isEmpty()) return;
		ItemStack moved = offhand.copy();
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		player.getInventory().placeItemBackInInventory(moved, Prediction.SERVER_ONLY);
		player.sendSystemMessage(Component.literal("You cannot hold anything in your offhand without an arm there."));
	}

	private LimbManager() {
	}
}
