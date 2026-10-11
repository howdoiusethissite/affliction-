package com.afflictions.fear;

import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;
import com.afflictions.data.TransientState;
import com.afflictions.phobia.PhobiaManager;

/** Phase 3: drains and restores the fear meter from the player's surroundings. */
public final class FearManager {
	/** Fear change per second from each source. Drains are positive numbers, recoveries are positive numbers. */
	public record Rates(double dark, double cave, double phobia, double campfire, double jukebox,
			double companion, double sleep, double passive) {
		public double drain() {
			return this.dark + this.cave + this.phobia;
		}

		public double recovery() {
			return this.campfire + this.jukebox + this.companion + this.sleep + this.passive;
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
			return new Rates(0, 0, 0, 0, 0, 0, config.fearSleepRecoveryPerSecond, 0);
		}

		ServerLevel level = player.level();
		double dark = isDark(player, level) ? config.fearDarkDrainPerSecond : 0;
		double cave = isInCave(player, level) ? config.fearCaveDrainPerSecond : 0;
		double phobia = PhobiaManager.activePhobiaCount(player) * config.fearPhobiaDrainPerSecond;

		double campfire = 0;
		double jukebox = 0;
		if (scanComfort) {
			boolean[] found = scanComfort(player, level);
			campfire = found[0] ? config.fearCampfireRecoveryPerSecond : 0;
			jukebox = found[1] ? config.fearJukeboxRecoveryPerSecond : 0;
		}
		double companion = scanComfort && hasCompanion(player, level) ? config.fearCompanionRecoveryPerSecond : 0;

		boolean draining = dark + cave + phobia > 0;
		double passive = draining ? 0 : config.fearPassiveRecoveryPerSecond;
		return new Rates(dark, cave, phobia, campfire, jukebox, companion, 0, passive);
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
