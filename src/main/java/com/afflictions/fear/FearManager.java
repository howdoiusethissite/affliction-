package com.afflictions.fear;

import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import com.afflictions.compat.FfinjuryCompat;
import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;
import com.afflictions.data.TransientState;
import com.afflictions.phobia.PhobiaManager;

/** Phase 3: drains and restores the fear meter from the player's surroundings. */
public final class FearManager {
	/** Fear change per second from each source. Drains are positive numbers, recoveries are positive numbers. */
	public record Rates(double dark, double cave, double phobia, double injury, double campfire, double jukebox,
			double companion, double sleep, double passive, double light) {
		public double drain() {
			return this.dark + this.cave + this.phobia + this.injury;
		}

		public double recovery() {
			return this.campfire + this.jukebox + this.companion + this.sleep + this.passive + this.light;
		}

		/** Fear points per second, negative when draining. */
		public double net() {
			return this.recovery() - this.drain();
		}
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int interval = Math.max(1, AfflictionsConfig.get().fearCheckIntervalTicks);
			if (server.getTickCount() % interval != 0) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickPlayer(player, interval);
			}
		});
	}

	private static void tickPlayer(ServerPlayer player, int intervalTicks) {
		if (!player.isAlive() || player.isCreative() || player.isSpectator()) return;
		TransientState state = AfflictionData.getTransient(player);
		float fear = state.fear();
		Rates rates = compute(player, fear < TransientState.MAX_FEAR);
		float updated = (float) (fear + rates.net() * intervalTicks / 20.0);
		updated = Math.max(0f, Math.min(TransientState.MAX_FEAR, updated));
		// Sleep can only lift fear so far in one night once the player is Shaken or worse.
		float ceiling = FearEffects.sleepCeiling(player, fear);
		if (updated > fear) updated = Math.min(updated, Math.max(fear, ceiling));
		if (AfflictionsConfig.get().fearEffectsEnabled && (fear <= 0f || updated <= 0f)) {
			FearEffects.breakdown(player);
			return;
		}
		// Only write on change: every setAttached on a synced attachment sends a packet.
		if (Math.abs(updated - fear) > 0.001f) AfflictionData.setFear(player, updated);
	}

	/**
	 * The current rate from every source for this player. Comfort block scans are skipped (reported as 0)
	 * when {@code scanComfort} is false, which the tick uses while fear is already full.
	 */
	public static Rates compute(ServerPlayer player, boolean scanComfort) {
		AfflictionsConfig config = AfflictionsConfig.get();
		if (player.isSleeping()) {
			double sleep = config.fearSleepRecoveryPerSecond * FearEffects.sleepMultiplier(FearTier.of(AfflictionData.getFear(player)));
			return new Rates(0, 0, 0, 0, 0, 0, 0, sleep, 0, 0);
		}

		ServerLevel level = player.level();
		double dark = isDark(player, level) ? config.fearDarkDrainPerSecond : 0;
		double cave = isInCave(player, level) ? config.fearCaveDrainPerSecond : 0;
		// A held light calms the dark: it removes part or all of the drain and adds a little recovery on top.
		double light = 0;
		if ((dark > 0 || cave > 0) && holdsLightSource(player)) {
			double keep = 1.0 - Math.max(0.0, Math.min(1.0, config.fearLightSourceDrainReduction));
			dark *= keep;
			cave *= keep;
			light = config.fearLightSourceDrainReduction > 0 ? config.fearLightSourceRecoveryPerSecond : 0;
		}
		double phobia = PhobiaManager.activePhobiaCount(player) * config.fearPhobiaDrainPerSecond;

		double injury = FfinjuryCompat.injuryCount(player) * config.fearInjuryDrainPerSecond;

		double campfire = 0;
		double jukebox = 0;
		if (scanComfort) {
			boolean[] found = scanComfort(player, level);
			campfire = found[0] ? config.fearCampfireRecoveryPerSecond : 0;
			jukebox = found[1] ? config.fearJukeboxRecoveryPerSecond : 0;
		}
		double companion = scanComfort && hasCompanion(player, level) ? config.fearCompanionRecoveryPerSecond : 0;

		boolean draining = dark + cave + phobia + injury > 0;
		double passive = draining ? 0 : config.fearPassiveRecoveryPerSecond;
		return new Rates(dark, cave, phobia, injury, campfire, jukebox, companion, 0, passive, light);
	}

	/** A configured light source item in the offhand, or the main hand when that option is on. */
	public static boolean holdsLightSource(Player player) {
		AfflictionsConfig config = AfflictionsConfig.get();
		return isLightSource(player.getOffhandItem(), config)
				|| config.fearLightSourceMainHand && isLightSource(player.getMainHandItem(), config);
	}

	private static boolean isLightSource(ItemStack stack, AfflictionsConfig config) {
		if (stack.isEmpty()) return false;
		return config.fearLightSourceItems.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
	}

	private static boolean isDark(ServerPlayer player, ServerLevel level) {
		return level.getMaxLocalRawBrightness(player.blockPosition().above()) <= AfflictionsConfig.get().fearDarkLightLevel;
	}

	/** Deep under the surface in a dimension that has one. The Nether and End have no surface, so darkness covers them. */
	private static boolean isInCave(ServerPlayer player, ServerLevel level) {
		if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) return false;
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ());
		return player.getBlockY() < surface - AfflictionsConfig.get().fearCaveDepthBlocks;
	}

	/** Returns {lit campfire nearby, playing jukebox nearby}. */
	private static boolean[] scanComfort(ServerPlayer player, ServerLevel level) {
		int r = Math.max(1, AfflictionsConfig.get().fearComfortRange);
		BlockPos origin = player.blockPosition();
		boolean campfire = false;
		boolean jukebox = false;
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
			BlockState state = level.getBlockState(pos);
			if (!campfire && state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT)) campfire = true;
			else if (!jukebox && state.getBlock() instanceof JukeboxBlock && state.getValue(JukeboxBlock.HAS_RECORD)) jukebox = true;
			if (campfire && jukebox) break;
		}
		return new boolean[] {campfire, jukebox};
	}

	private static boolean hasCompanion(ServerPlayer player, ServerLevel level) {
		double range = AfflictionsConfig.get().fearCompanionRange;
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
				e -> e.isAlive() && e.distanceToSqr(player) <= range * range
						&& (e instanceof Villager || e instanceof TamableAnimal pet && pet.isTame() && pet.isOwnedBy(player)));
		return !near.isEmpty();
	}

	private FearManager() {
	}
}
