package com.afflictions.command;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;
import com.afflictions.data.Limb;
import com.afflictions.data.LimbState;
import com.afflictions.data.PermanentState;
import com.afflictions.limb.LimbManager;

/**
 * {@code /amputate <limb>}: removes a healthy limb on purpose so an implant can be fitted. Anyone can use it, so it asks
 * twice: the first run explains and the second run, within {@link #CONFIRM_TICKS} for the same limb, does it.
 */
public final class AmputateCommand {
	private static final int CONFIRM_TICKS = 300;
	private static final String[] LIMBS = Arrays.stream(Limb.values()).map(Limb::getSerializedName).toArray(String[]::new);

	private record Pending(Limb limb, long expiresTick) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("amputate")
				.then(Commands.argument("limb", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(LIMBS, b))
						.executes(AmputateCommand::run)));
	}

	private static int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		AfflictionsConfig config = AfflictionsConfig.get();
		if (!config.voluntaryAmputationEnabled) throw fail("Voluntary amputation is turned off in the config.");

		Limb limb = parse(StringArgumentType.getString(ctx, "limb"));
		PermanentState state = AfflictionData.getPermanent(player);
		if (state.limb(limb) == LimbState.AMPUTATED) {
			throw fail(state.implants().containsKey(limb)
					? "Your " + name(limb) + " is already replaced by an implant."
					: "You have already lost your " + name(limb) + ".");
		}
		if (config.voluntaryAmputationNeedsSmithingTable && !nearSmithingTable(player, config.voluntaryAmputationTableRange)) {
			throw fail("You need a smithing table within " + config.voluntaryAmputationTableRange + " blocks to do this.");
		}

		long now = player.level().getGameTime();
		Pending pending = PENDING.get(player.getUUID());
		if (pending == null || pending.limb() != limb || now > pending.expiresTick()) {
			PENDING.put(player.getUUID(), new Pending(limb, now + CONFIRM_TICKS));
			ctx.getSource().sendSuccess(() -> Component.translatable("afflictions.message.amputate_confirm", LimbManager.limbName(limb)), false);
			return 1;
		}
		PENDING.remove(player.getUUID());
		LimbManager.amputate(player, limb);
		return 1;
	}

	private static boolean nearSmithingTable(ServerPlayer player, int range) {
		BlockPos origin = player.blockPosition();
		int r = Math.max(1, range);
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
			if (player.level().getBlockState(pos).is(Blocks.SMITHING_TABLE)) return true;
		}
		return false;
	}

	private static Limb parse(String text) throws CommandSyntaxException {
		String normalized = text.toLowerCase(Locale.ROOT);
		for (Limb limb : Limb.values()) {
			if (limb.getSerializedName().equals(normalized)) return limb;
		}
		throw fail("Limb must be one of " + String.join(", ", LIMBS));
	}

	private static String name(Limb limb) {
		return limb.getSerializedName().replace('_', ' ');
	}

	private static CommandSyntaxException fail(String message) {
		return new SimpleCommandExceptionType(Component.literal(message)).create();
	}

	private AmputateCommand() {
	}
}
