package com.platino;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

/** Pantalla para escribir la nota de un Pokémon, con marco estilo Platino. */
public class NotesScreen extends Screen {
	private static final int PW = 240, PH = 150;
	private final Screen parent;
	private final Pokemon pokemon;
	private EditBoxWidget box;

	public NotesScreen(Screen parent, Pokemon pokemon) {
		super(Text.literal(com.platino.Tr.t("Notas")));
		this.parent = parent;
		this.pokemon = pokemon;
	}

	@Override
	protected void init() {
		int x = (width - PW) / 2, y = (height - PH) / 2;
		box = new EditBoxWidget(textRenderer, x + 10, y + 26, PW - 20, PH - 62,
				Text.literal(com.platino.Tr.t("Escribe aquí… (para intercambiar, le faltan IVs, etc.)")), Text.literal(com.platino.Tr.t("Nota")));
		box.setMaxLength(1000);
		box.setText(Notes.get(pokemon.getUuid()));
		addDrawableChild(box);
		setInitialFocus(box);
		addDrawableChild(ButtonWidget.builder(Text.literal(com.platino.Tr.t("Guardar")), b -> {
			Notes.set(pokemon.getUuid(), box.getText());
			close();
		}).dimensions(x + PW - 10 - 70, y + PH - 30, 70, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal(com.platino.Tr.t("Borrar")), b -> box.setText(""))
				.dimensions(x + PW - 10 - 70 - 54, y + PH - 30, 50, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal(com.platino.Tr.t("Cancelar")), b -> close())
				.dimensions(x + 10, y + PH - 30, 60, 20).build());
	}

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		super.render(c, mouseX, mouseY, delta);
	}

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
		super.renderBackground(c, mouseX, mouseY, delta);
		int x = (width - PW) / 2, y = (height - PH) / 2;
		// Marco Platino: borde oscuro, cabecera dorada, cuerpo plateado
		Draw.round(c, x + 3, y + 3, x + PW + 3, y + PH + 3, 0x80000000);
		Draw.panelGradient(c, x, y, x + PW, y + PH, Draw.OUTLINE, 0xFFD8E0E8, 0xFFA8B4C0);
		Draw.panelGradient(c, x + 3, y + 3, x + PW - 3, y + 20, Draw.GOLD_DARK, 0xFFF8E070, Draw.GOLD);
		String title = com.platino.Tr.t("✎ Notas de ") + pokemon.getDisplayName(false).getString() + com.platino.Tr.t("  Nv") + pokemon.getLevel();
		Draw.dsText(c, Draw.fit(title, PW - 16), x + 8, y + 8, Draw.TEXT_DARK, 0xFFF8F0C0);
	}

	@Override
	public void close() {
		if (client != null) client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
