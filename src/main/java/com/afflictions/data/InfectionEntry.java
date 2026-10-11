package com.afflictions.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record InfectionEntry(String source, long riskEndsTick, int stage) {
	public static final Codec<InfectionEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("source").forGetter(InfectionEntry::source),
			Codec.LONG.optionalFieldOf("risk_ends_tick", 0L).forGetter(InfectionEntry::riskEndsTick),
			Codec.INT.optionalFieldOf("stage", 0).forGetter(InfectionEntry::stage)
	).apply(i, InfectionEntry::new));

	public static final StreamCodec<io.netty.buffer.ByteBuf, InfectionEntry> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, InfectionEntry::source,
			ByteBufCodecs.VAR_LONG, InfectionEntry::riskEndsTick,
			ByteBufCodecs.VAR_INT, InfectionEntry::stage,
			InfectionEntry::new);

	public InfectionEntry withRiskEndsTick(long tick) {
		return new InfectionEntry(this.source, tick, this.stage);
	}

	public InfectionEntry withStage(int stage) {
		return new InfectionEntry(this.source, this.riskEndsTick, stage);
	}
}
