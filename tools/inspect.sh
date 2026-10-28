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
