package com.afflictions.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import com.afflictions.config.AfflictionsConfig;

/**
 * Phase 8: edits every public option of {@link AfflictionsConfig} by reflection, so a new option shows up here without
 * extra code. Paged rather than scrolled. Changes apply to the live config straight away and are written on Done.
 * In singleplayer that is the config the server uses; on a dedicated server only the client-side options matter.
 */
public final class AfflictionsConfigScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int LABEL_WIDTH = 190;
	private static final int FIELD_WIDTH = 150;
	private static final int TOP = 36;
	private static final int BOTTOM = 56;

	/** A heading when field is null, otherwise one option. */
	private record Row(String heading, Field field) {
	}

	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private final AfflictionsConfig defaults = AfflictionsConfig.defaults();
	private final List<Integer> headingY = new ArrayList<>();
	private final List<Component> headingText = new ArrayList<>();
	private final List<Integer> labelY = new ArrayList<>();
	private final List<Component> labelText = new ArrayList<>();
	private int page;

	public AfflictionsConfigScreen(Screen parent) {
		super(Component.translatable("afflictions.config.title"));
		this.parent = parent;
		for (Field field : AfflictionsConfig.class.getFields()) {
			if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
			AfflictionsConfig.Section section = field.getAnnotation(AfflictionsConfig.Section.class);
			if (section != null) this.rows.add(new Row(section.value(), null));
			this.rows.add(new Row(null, field));
		}
	}

	private int rowsPerPage() {
		return Math.max(1, (this.height - TOP - BOTTOM) / ROW_HEIGHT);
	}

	private int pageCount() {
		return Math.max(1, (this.rows.size() + rowsPerPage() - 1) / rowsPerPage());
	}

	@Override
	protected void init() {
		this.headingY.clear();
		this.headingText.clear();
		this.labelY.clear();
		this.labelText.clear();
		this.page = Math.min(this.page, pageCount() - 1);

		int perPage = rowsPerPage();
		int start = this.page * perPage;
		int left = this.width / 2 - (LABEL_WIDTH + FIELD_WIDTH) / 2;
		int y = TOP;
		for (int i = start; i < Math.min(this.rows.size(), start + perPage); i++) {
			Row row = this.rows.get(i);
			if (row.field() == null) {
				this.headingY.add(y + 8);
				this.headingText.add(Component.translatableWithFallback("afflictions.config.section." + slug(row.heading()), row.heading()));
			} else {
				addOption(row.field(), left, y);
			}
			y += ROW_HEIGHT;
		}

		int footerY = this.height - 28;
		Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(this.width / 2 - 154, footerY - 26, 20, 20).build());
		Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(this.width / 2 + 134, footerY - 26, 20, 20).build());
		prev.active = this.page > 0;
		next.active = this.page < pageCount() - 1;

		addRenderableWidget(Button.builder(Component.translatable("afflictions.config.reset"), b -> resetAll())
				.bounds(this.width / 2 - 154, footerY, 100, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> cancel())
				.bounds(this.width / 2 - 50, footerY, 100, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> done())
				.bounds(this.width / 2 + 54, footerY, 100, 20).build());
	}

	private void addOption(Field field, int left, int y) {
		this.labelY.add(y + 6);
		this.labelText.add(label(field));
		int x = left + LABEL_WIDTH;
		Tooltip tip = Tooltip.create(Component.translatable("afflictions.config.default", format(read(this.defaults, field))));

		if (field.getType() == boolean.class) {
			Button button = Button.builder(onOff(read(AfflictionsConfig.get(), field)), b -> {
				boolean value = !(Boolean) read(AfflictionsConfig.get(), field);
				write(field, value);
				b.setMessage(onOff(value));
			}).bounds(x, y, FIELD_WIDTH, 20).build();
			button.setTooltip(tip);
			addRenderableWidget(button);
			return;
		}

		EditBox box = new EditBox(this.font, x, y, FIELD_WIDTH, 20, label(field));
		box.setMaxLength(256);
		box.setValue(format(read(AfflictionsConfig.get(), field)));
		box.setTooltip(tip);
		box.setResponder(text -> box.setTextColor(apply(field, text) ? EditBox.DEFAULT_TEXT_COLOR : 0xFFFF5555));
		addRenderableWidget(box);
	}

	/** Parses the text into the field's type and stores it. Returns false (leaving the old value) if it does not parse. */
	private static boolean apply(Field field, String text) {
		try {
			Class<?> type = field.getType();
			if (type == int.class) write(field, Integer.parseInt(text.trim()));
			else if (type == double.class) write(field, Double.parseDouble(text.trim()));
			else if (type == String.class) write(field, text.trim());
			else if (List.class.isAssignableFrom(type)) {
				write(field, text.isBlank() ? List.<String>of()
						: java.util.Arrays.stream(text.split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList());
			} else return false;
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private void turn(int delta) {
		this.page = Math.max(0, Math.min(pageCount() - 1, this.page + delta));
		rebuildWidgets();
	}

	private void resetAll() {
		for (Row row : this.rows) {
			if (row.field() != null) write(row.field(), read(this.defaults, row.field()));
		}
		rebuildWidgets();
	}

	private void cancel() {
		AfflictionsConfig.reload();
		onClose();
	}

	private void done() {
		AfflictionsConfig.save();
		onClose();
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("afflictions.config.page", this.page + 1, pageCount()),
				this.width / 2, this.height - 48, 0xFFAAAAAA);
		int left = this.width / 2 - (LABEL_WIDTH + FIELD_WIDTH) / 2;
		for (int i = 0; i < this.headingText.size(); i++) {
			graphics.text(this.font, this.headingText.get(i).copy().withStyle(net.minecraft.ChatFormatting.GOLD), left, this.headingY.get(i), 0xFFFFFFFF, true);
		}
		for (int i = 0; i < this.labelText.size(); i++) {
			graphics.text(this.font, this.labelText.get(i), left, this.labelY.get(i), 0xFFFFFFFF, true);
		}
	}

	// ---- helpers -------------------------------------------------------------------------------------------

	private static Component label(Field field) {
		return Component.translatableWithFallback("afflictions.config.option." + field.getName(), humanize(field.getName()));
	}

	private static Component onOff(Object value) {
		return Boolean.TRUE.equals(value) ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
	}

	/** "infectionStageTicks" becomes "Infection stage ticks". */
	static String humanize(String name) {
		String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
		return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	private static String slug(String heading) {
		return heading.toLowerCase(Locale.ROOT).replace(' ', '_');
	}

	private static String format(Object value) {
		if (value instanceof List<?> list) return list.stream().map(String::valueOf).collect(Collectors.joining(", "));
		if (value instanceof Double d && d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf(d.longValue()) + ".0";
		return String.valueOf(value);
	}

	private static Object read(AfflictionsConfig config, Field field) {
		try {
			return field.get(config);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void write(Field field, Object value) {
		try {
			field.set(AfflictionsConfig.get(), value);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}
}
