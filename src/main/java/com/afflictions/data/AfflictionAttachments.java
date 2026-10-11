package com.afflictions.data;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import com.afflictions.Afflictions;

public final class AfflictionAttachments {
	/** Phobias, scars, limbs, implants. Copied to the new player on death. */
	public static final AttachmentType<PermanentState> PERMANENT = AttachmentRegistry.create(
			Afflictions.id("permanent_state"),
			builder -> builder
					.initializer(() -> PermanentState.EMPTY)
					.persistent(PermanentState.CODEC)
					.syncWith(PermanentState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
					.copyOnDeath());

	/** Fear and infections. Not copied on death, so it resets by itself. */
	public static final AttachmentType<TransientState> TRANSIENT = AttachmentRegistry.create(
			Afflictions.id("transient_state"),
			builder -> builder
					.initializer(() -> TransientState.EMPTY)
					.persistent(TransientState.CODEC)
					.syncWith(TransientState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	public static void initialize() {
	}

	private AfflictionAttachments() {
	}
}
