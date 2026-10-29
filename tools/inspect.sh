#!/usr/bin/env bash
# Inspecciona las clases de Cobblemon/Minecraft remapeadas (yarn) y guarda firmas en inspect/
set -u
mkdir -p inspect
gradle -q printModJars > inspect/jars.txt 2>&1 || true
CJAR=$(grep 'JAR: ' inspect/jars.txt | grep 'remapped_mods' | grep '/com/cobblemon/' | head -1 | sed 's/^JAR: //')
MJAR=$(grep 'JAR: .*minecraft-merged' inspect/jars.txt | grep -v sources | head -1 | sed 's/^JAR: //')
echo "cobblemon=$CJAR" > inspect/paths.txt; echo "minecraft=$MJAR" >> inspect/paths.txt
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  unzip -Z1 "$CJAR" > inspect/cobblemon-files.txt
  grep '^assets/cobblemon/textures/gui/' inspect/cobblemon-files.txt > inspect/gui-textures.txt || true
  CLS=$(grep -E '^com/cobblemon/mod/common/(client/(gui/(battle|summary|pc|PartyOverlay|interact)|CobblemonClient|storage/ClientParty|storage/ClientStorage|storage/ClientPC|battle/ClientBattle|keybind)|api/gui/|pokemon/Pokemon\.class|pokemon/Species\.class|api/types/ElementalType|client/render/models/blockbench/FloatingState|client/gui/GuiUtils|client/render/RenderHelper|client/gui/PokemonGuiUtils)' inspect/cobblemon-files.txt | grep '\.class$' | sed 's/\.class$//' | tr '/' '.')
  for c in $CLS; do echo "==== $c"; javap -p -cp "$CJAR" "$c" 2>&1; done > inspect/cobblemon-javap.txt
  mkdir -p inspect/tex && cd inspect/tex && unzip -q -o "$CJAR" 'assets/cobblemon/textures/gui/battle/*' 'assets/cobblemon/textures/gui/party/*' 'assets/cobblemon/textures/gui/summary/*' 'assets/cobblemon/textures/gui/pc/*' 'assets/cobblemon/textures/gui/interact/*' 2>/dev/null; cd ../..
  python3 - <<'PY' > inspect/gui-texture-sizes.txt
import struct,glob
for f in sorted(glob.glob('inspect/tex/**/*.png',recursive=True)):
    b=open(f,'rb').read(24); w,h=struct.unpack('>II',b[16:24]); print(f[len('inspect/tex/'):],w,h)
PY
  rm -rf inspect/tex
fi
if [ -n "$MJAR" ] && [ -f "$MJAR" ]; then
  for c in net.minecraft.client.gui.screen.ingame.InventoryScreen net.minecraft.client.gui.screen.ingame.HandledScreen net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen net.minecraft.client.gui.DrawContext; do
    echo "==== $c"; javap -p -c -cp "$MJAR" "$c" 2>&1 | grep -E '^(  [a-z]|  (public|protected|private|static)|.*invoke)' ; done > inspect/minecraft-javap.txt
fi
# --- Segunda ronda: clases extra y bytecode con nombres de variables
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  for c in com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon com.cobblemon.mod.common.pokemon.status.PersistentStatus com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer com.cobblemon.mod.common.pokemon.Gender com.cobblemon.mod.common.api.pokemon.status.Status com.cobblemon.mod.common.pokemon.FormData com.cobblemon.mod.common.pokemon.RenderablePokemon com.cobblemon.mod.common.entity.PoseType com.cobblemon.mod.common.client.render.models.blockbench.PosableState; do echo "==== $c"; javap -p -cp "$CJAR" "$c" 2>&1; done > inspect/cobblemon-javap2.txt
  for c in com.cobblemon.mod.common.client.gui.battle.BattleOverlay com.cobblemon.mod.common.client.gui.PartyOverlay; do echo "==== $c"; javap -p -c -l -constants -cp "$CJAR" "$c" 2>&1; done > inspect/cobblemon-bytecode.txt
  javap -p -cp "$CJAR" com.cobblemon.mod.common.client.gui.summary.Summary com.cobblemon.mod.common.client.gui.pc.PCGUI com.cobblemon.mod.common.client.gui.pc.StorageSlot com.cobblemon.mod.common.client.gui.pc.StorageWidget > inspect/cobblemon-screens.txt 2>&1
fi
# --- Tercera ronda
if [ -n "$MJAR" ] && [ -f "$MJAR" ]; then
  javap -p -c -cp "$MJAR" net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen | awk '/drawBackground\(/{f=1} f&&/invoke/{print} f&&/^$/{exit}' > inspect/creative-bg.txt 2>&1
fi
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  javap -p -c -constants -cp "$CJAR" com.cobblemon.mod.common.client.gui.summary.Summary | awk '/ (init|mouseClicked|keyPressed)\(/{f=1} f{print} f&&/^$/{f=0}' | grep -E '^  [a-z]|BASE|width|height|invoke' > inspect/summary-methods.txt 2>&1
  javap -cp "$CJAR" com.cobblemon.mod.common.client.gui.summary.widgets.NicknameEntryWidget | head -3 >> inspect/summary-methods.txt
fi
# --- Cuarta ronda: Pokédex del cliente
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  for c in com.cobblemon.mod.common.api.storage.player.client.ClientPokedexManager com.cobblemon.mod.common.api.pokedex.AbstractPokedexManager com.cobblemon.mod.common.api.pokedex.SpeciesDexRecord com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress; do echo "==== $c"; javap -p -cp "$CJAR" "$c" 2>&1 | grep -v 'lambda\|access\$'; done > inspect/pokedex.txt
fi
