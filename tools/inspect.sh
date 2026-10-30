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
# --- Quinta ronda: abrir Resumen y Pokédex
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  { javap -p -cp "$CJAR" 'com.cobblemon.mod.common.client.gui.summary.Summary$Companion';
    grep -i 'gui/pokedex/PokedexGUI' inspect/cobblemon-files.txt;
    javap -p -cp "$CJAR" 'com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI$Companion';
    javap -p -cp "$CJAR" com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI | head -20;
    grep -iE 'pokedex.*(Type|Types)\.class' inspect/cobblemon-files.txt | head;
    javap -p -cp "$CJAR" com.cobblemon.mod.common.item.PokedexItem 2>&1 | head -30;
    javap -p -cp "$CJAR" com.cobblemon.mod.common.api.item.PokedexType 2>&1 | head -30;
  } > inspect/open-screens.txt 2>&1
fi
if [ -n "$MJAR" ] && [ -f "$MJAR" ]; then
  javap -p -cp "$MJAR" net.minecraft.client.MinecraftClient | grep -iE 'openGameMenu|setScreen' > inspect/mc-menu.txt
  javap -p -cp "$MJAR" net.minecraft.client.gui.screen.Screen | grep -iE 'getTooltipFromItem' >> inspect/mc-menu.txt
fi
# --- Sexta ronda: qué hace la opción Bag en combate y cómo se usan objetos
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  { javap -p -c -cp "$CJAR" com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection | awk '/lambda/{f=1} f' | grep -E 'lambda|invoke|getstatic' | head -80;
    echo "==== BattleGUI.close / minimize";
    javap -p -c -cp "$CJAR" com.cobblemon.mod.common.client.gui.battle.BattleGUI | awk '/ (close|minimizeBattle)\(/{f=1} f{print} f&&/^$/{f=0}' | grep -E '^  [a-z]|invoke|putfield|getfield' | head -40;
    echo "==== clases de uso de objetos";
    grep -iE 'item/(battle|medicine|healing)|PartySelect|BagItem|UseItem|partyselect' inspect/cobblemon-files.txt | grep class | head -40;
  } > inspect/battle-bag.txt 2>&1
fi
# --- Séptima ronda: cómo sabe Cobblemon qué objetos se usan en combate
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  { grep -iE 'item/.*Potion.*\.class' inspect/cobblemon-files.txt | head;
    for c in com.cobblemon.mod.common.battles.BagItems com.cobblemon.mod.common.item.battle.BagItemLike com.cobblemon.mod.common.item.battle.SimpleBagItemLike; do echo "==== $c"; javap -p -cp "$CJAR" $c; done;
    P=$(grep -iE 'item/interactive/PotionItem\.class|item/PotionItem\.class' inspect/cobblemon-files.txt | head -1 | sed 's/\.class$//; s#/#.#g');
    echo "==== $P"; [ -n "$P" ] && javap -p -cp "$CJAR" "$P" | head -15;
    javap -p -c -cp "$CJAR" com.cobblemon.mod.common.battles.BagItems | grep -E 'invoke|instanceof|getstatic' | head -40;
  } > inspect/bagitems.txt 2>&1
fi
# --- Octava ronda: cómo dibuja el Resumen el modelo completo
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  { javap -p -c -constants -cp "$CJAR" com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget | awk '/ renderWidget\(| render\(|renderPKM|drawProfile/{f=1} f' | grep -E '^  [a-z]|invoke|ldc|getstatic|fconst|bipush|sipush|new ' | head -90;
    echo "==== FloatingState"; javap -p -cp "$CJAR" com.cobblemon.mod.common.client.render.models.blockbench.FloatingState | head -20;
    echo "==== PoseType"; javap -p -cp "$CJAR" com.cobblemon.mod.common.entity.PoseType | grep 'static final' | head -20;
  } > inspect/model-widget.txt 2>&1
fi
# --- Octava ronda: cómo dibuja el Resumen el modelo completo
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  { javap -p -cp "$CJAR" com.cobblemon.mod.common.entity.PoseType | grep 'static final com.cobblemon.mod.common.entity.PoseType ';
    javap -p -c -constants -cp "$CJAR" com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget | awk '/renderPKM|renderWidget/{f=1} f' | grep -E '^  [a-z]|ldc|bipush|sipush|fconst|dconst|getstatic|invoke' | head -90;
  } > inspect/model-widget.txt 2>&1
fi
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  javap -p -c -constants -cp "$CJAR" com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget | awk '/private final void renderPKM/{f=1} f' | sed -n '1,120p' > inspect/model-widget-full.txt
  javap -p -c -constants -cp "$CJAR" com.cobblemon.mod.common.client.gui.summary.Summary | grep -B12 'ModelWidget."<init>"' | head -40 >> inspect/model-widget-full.txt
fi
# --- Novena ronda: ángulo de los modelos en las casillas del PC
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  javap -p -c -constants -cp "$CJAR" com.cobblemon.mod.common.client.gui.pc.StorageSlot | grep -E '^  [a-z]|ldc|bipush|sipush|fconst|dconst|Vector3f|Quaternion|drawProfile|translate|scale|PoseType' | head -80 > inspect/pc-slot.txt 2>&1
fi
# --- Décima ronda: dibujo completo de las casillas del PC y del equipo del Resumen
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  { echo "==== StorageSlot.renderSlot";
    javap -p -c -constants -cp "$CJAR" com.cobblemon.mod.common.client.gui.pc.StorageSlot | awk '/public final void renderSlot/{f=1} f{print} /drawProfilePokemon/{if(f){exit}}' | grep -vE 'aload|astore|checkcast|dup$|pop$';
    echo "==== PartySlotWidget";
    javap -p -c -constants -cp "$CJAR" com.cobblemon.mod.common.client.gui.summary.widgets.PartySlotWidget | awk '/renderWidget/{f=1} f{print} /drawProfilePokemon/{if(f){exit}}' | grep -vE 'aload|astore|checkcast|dup$|pop$';
  } > inspect/slot-render.txt 2>&1
fi
# --- Undécima ronda: datos para la calculadora de daño
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  for c in com.cobblemon.mod.common.api.pokemon.PokemonSpecies com.cobblemon.mod.common.pokemon.Species com.cobblemon.mod.common.pokemon.FormData \
           com.cobblemon.mod.common.api.pokemon.stats.Stats com.cobblemon.mod.common.api.pokemon.stats.Stat \
           com.cobblemon.mod.common.api.moves.Moves com.cobblemon.mod.common.api.moves.MoveTemplate com.cobblemon.mod.common.api.moves.categories.DamageCategories com.cobblemon.mod.common.api.moves.categories.DamageCategory \
           com.cobblemon.mod.common.api.moves.MoveSet com.cobblemon.mod.common.api.moves.Move \
           com.cobblemon.mod.common.api.pokemon.Natures com.cobblemon.mod.common.pokemon.Nature \
           com.cobblemon.mod.common.api.abilities.AbilityPool com.cobblemon.mod.common.api.abilities.PotentialAbility com.cobblemon.mod.common.api.abilities.AbilityTemplate com.cobblemon.mod.common.api.abilities.Ability \
           com.cobblemon.mod.common.pokemon.IVs com.cobblemon.mod.common.pokemon.EVs com.cobblemon.mod.common.api.pokemon.stats.PokemonStatContainer \
           com.cobblemon.mod.common.api.types.ElementalTypes com.cobblemon.mod.common.api.types.ElementalType ; do
    echo "==== $c"; javap -p -cp "$CJAR" "$c" 2>&1 | grep -E 'public' | grep -vE 'lambda|access\$|\$default|Codec|encode|decode|saveTo|loadFrom|toNbt|fromNbt|toJson|fromJson|Buffer|Struct|Molang|Mo[A-Z]' | head -60
  done > inspect/calc-api.txt 2>&1
  { for c in com.cobblemon.mod.common.pokemon.Pokemon; do javap -p -cp "$CJAR" "$c" | grep -E 'public final .* (getIvs|getEvs|getNature|getMintedNature|getEffectiveNature|getMoveSet|getAbility|getStat|getAttack|getDefence|getSpecialAttack|getSpecialDefence|getSpeed|getTypes|getPrimaryType|getSecondaryType)\('; done; } >> inspect/calc-api.txt 2>&1
  grep -E 'item/(held|battle)/|HeldItem|ChoiceItem|item/.*(Band|Specs|Orb|Belt|Vest|Eviolite)' inspect/cobblemon-files.txt | grep class | head -30 >> inspect/calc-api.txt
fi
# --- Duodécima ronda: paquetes para cambiar movimientos y orden del equipo
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  { for c in com.cobblemon.mod.common.net.messages.server.storage.party.SwapPartyPokemonPacket com.cobblemon.mod.common.net.messages.server.storage.party.MovePartyPokemonPacket \
           com.cobblemon.mod.common.net.messages.server.RequestMoveSwapPacket com.cobblemon.mod.common.net.messages.server.BenchMovePacket \
           com.cobblemon.mod.common.api.moves.BenchedMoves com.cobblemon.mod.common.api.moves.BenchedMove com.cobblemon.mod.common.api.storage.party.PartyPosition \
           com.cobblemon.mod.common.api.net.NetworkPacket com.cobblemon.mod.common.CobblemonNetwork com.cobblemon.mod.common.client.storage.ClientParty com.cobblemon.mod.common.client.storage.ClientStorage; do
      echo "==== $c"; javap -p -cp "$CJAR" "$c" 2>&1 | grep -vE 'lambda|access\$' | head -50; done
    echo "==== summary bytecode (packets)"
    for c in $(grep -E '^com/cobblemon/mod/common/client/gui/summary/.*\.class$' inspect/cobblemon-files.txt | sed 's/\.class$//' | tr '/' '.'); do
      javap -p -c -cp "$CJAR" "$c" 2>/dev/null | grep -E '^  [a-z].*\(|Packet|sendToServer|PartyPosition' | grep -B1 -E 'Packet|PartyPosition' | sed "s/^/$c: /"; done
  } > inspect/move-party-api.txt 2>&1
fi
# --- Decimotercera ronda: PC y menú de equipo
if [ -n "$CJAR" ] && [ -f "$CJAR" ]; then
  { for c in com.cobblemon.mod.common.client.storage.ClientPC com.cobblemon.mod.common.client.storage.ClientBox com.cobblemon.mod.common.api.storage.pc.PCPosition \
           com.cobblemon.mod.common.client.gui.pc.PCGUIConfiguration com.cobblemon.mod.common.client.gui.pc.PCGUI\$Companion \
           com.cobblemon.mod.common.net.messages.server.storage.pc.MovePCPokemonPacket com.cobblemon.mod.common.net.messages.server.storage.pc.SwapPCPokemonPacket \
           com.cobblemon.mod.common.net.messages.server.storage.pc.MovePCPokemonToPartyPacket com.cobblemon.mod.common.net.messages.server.storage.pc.MovePartyPokemonToPCPacket \
           com.cobblemon.mod.common.net.messages.server.storage.SwapPCPartyPokemonPacket com.cobblemon.mod.common.net.messages.server.storage.pc.ReleasePCPokemonPacket \
           com.cobblemon.mod.common.net.messages.server.storage.party.ReleasePartyPokemonPacket com.cobblemon.mod.common.net.messages.server.storage.pc.UnlinkPlayerFromPCPacket \
           com.cobblemon.mod.common.net.messages.server.storage.pc.SortPCBoxPacket com.cobblemon.mod.common.net.messages.server.SendOutPokemonPacket \
           com.cobblemon.mod.common.net.messages.client.storage.pc.OpenPCPacket com.cobblemon.mod.common.client.storage.ClientStorageManager; do
      echo "==== $c"; javap -p -cp "$CJAR" "$c" 2>&1 | grep -vE 'lambda|access\$|encode|decode|Codec|sendToPlayer|sendToAll' | head -60; done
    echo "==== PCGUI.closeNormally / onClose / init (bytecode)"
    javap -p -c -cp "$CJAR" com.cobblemon.mod.common.client.gui.pc.PCGUI | awk '/ (closeNormally|close|removed|init)\(/{f=1} f{print} f&&/^$/{f=0}' | grep -E '^  [a-z]|invoke|new ' | head -80
    echo "==== OpenPCHandler"
    for c in $(grep -E 'net/client(handling)?/.*(OpenPC|ClosePC)Handler.*\.class$' inspect/cobblemon-files.txt | sed 's/\.class$//' | tr '/' '.'); do echo "-- $c"; javap -p -c -cp "$CJAR" "$c" | grep -E '^  [a-z]|invoke|new ' | head -40; done
    echo "==== StorageWidget handling (packets)"
    javap -p -c -cp "$CJAR" com.cobblemon.mod.common.client.gui.pc.StorageWidget | grep -E '^  [a-z].*\(|Packet\."<init>"|PCPosition\."<init>"|PartyPosition\."<init>"' | head -80
  } > inspect/pc-api.txt 2>&1
fi
