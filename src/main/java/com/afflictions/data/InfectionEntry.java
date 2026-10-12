package com.afflictions.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One wound or infection, keyed in {@link TransientState#infections()} by source.
 * Stage 0 is a fresh wound inside its risk window and riskEndsTick is when the window closes.
 * From stage 1 the infection has set in and riskEndsTick is when it escalates to the next stage.
 * rinses counts water rinses applied during the window, each of which lowers the chance it sets in.
 * limb is the serialized name of the {@link Limb} the wound is on, or empty when unknown (older saves, FFInjury).
 */
public record InfectionEntry(String source, long riskEndsTick, int stage, int rinses, String limb) {
	public InfectionEntry(String source, long riskEndsTick, int stage) {
		this(source, riskEndsTick, stage, 0, "");
	}

	public InfectionEntry(String source, long riskEndsTick, int stage, int rinses) {
		this(source, riskEndsTick, stage, rinses, "");
	}

	public InfectionEntry(String source, long riskEndsTick, int stage, Limb limb) {
		this(source, riskEndsTick, stage, 0, limb == null ? "" : limb.getSerializedName());
	}

	/** The wounded limb, or null when it is not recorded. */
	public Limb limbOrNull() {
		for (Limb candidate : Limb.values()) {
			if (candidate.getSerializedName().equals(this.limb)) return candidate;
		}
		return null;
	}

	public static final Codec<InfectionEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("source").forGetter(InfectionEntry::source),
			Codec.LONG.optionalFieldOf("risk_ends_tick", 0L).forGetter(InfectionEntry::riskEndsTick),
			Codec.INT.optionalFieldOf("stage", 0).forGetter(InfectionEntry::stage),
			Codec.INT.optionalFieldOf("rinses", 0).forGetter(InfectionEntry::rinses),
			Codec.STRING.optionalFieldOf("limb", "").forGetter(InfectionEntry::limb)
	).apply(i, InfectionEntry::new));

	public static final StreamCodec<io.netty.buffer.ByteBuf, InfectionEntry> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, InfectionEntry::source,
			ByteBufCodecs.VAR_LONG, InfectionEntry::riskEndsTick,
			ByteBufCodecs.VAR_INT, InfectionEntry::stage,
			ByteBufCodecs.VAR_INT, InfectionEntry::rinses,
			ByteBufCodecs.STRING_UTF8, InfectionEntry::limb,
			InfectionEntry::new);

	public InfectionEntry withRiskEndsTick(long tick) {
		return new InfectionEntry(this.source, tick, this.stage, this.rinses, this.limb);
	}

	public InfectionEntry withStage(int stage) {
		return new InfectionEntry(this.source, this.riskEndsTick, stage, this.rinses, this.limb);
	}

	public InfectionEntry withRinses(int rinses) {
		return new InfectionEntry(this.source, this.riskEndsTick, this.stage, rinses, this.limb);
	}
}
