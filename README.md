# Cobblemon Platino (Fabric 1.21.1 · Cobblemon 1.7.x)

Mod **solo de cliente** que da a Cobblemon una interfaz inspirada en **Pokémon Platino**.

- **Combate:** cajas de PS estilo DS (tu Pokémon arriba a la izquierda, el rival arriba a la derecha) con nombre, sexo, nivel, estado, barra de PS animada, PS numéricos y EXP.
- **Barra de equipo:** paneles azules con retrato, nivel y PS; seleccionado en dorado, debilitados en rojo.
- **Inventario (E):** fondo estilo menú de Platino (plata y dorado).
- **PC, Resumen y botones de combate de Cobblemon:** recoloreados en memoria con la paleta Platino.
- **Notas:** tecla **N** (Pokémon seleccionado) o botón **✎ Notas** en el Resumen. Se guardan en `config/cobbleplatino_notas.json`.

- **Pokéreloj (P):** reloj (hora del PC), equipo con vida actual, podómetro, lanzamonedas, indicador de eficacia de tipos, calendario (del PC, con días marcables) y calculadora. Los botones rojos (o la rueda / flechas ↑↓) cambian de aplicación. También con `/platino pokereloj`.

- **Menú DPPt (Esc):** POKéDEX, POKéMON, BOLSA, FICHA, POKéRELOJ, OPCIONES y MENÚ JUEGO (el menú de pausa normal). `/platino menu off` para volver al de siempre.
- **Bolsa (I):** objetos por bolsillos (Objetos, Medicinas, Poké Balls, MT/MO, Bayas, Combate, Obj. clave, Varios). "A LA MANO" pone el objeto en tu mano.
- **Pitido de vida baja:** en combate, con tu Pokémon en rojo. `/platino combate pitido off` para quitarlo.

Configuración: `config/cobbleplatino.json` (activar/desactivar cada parte, mover cajas y barra, modo compacto).

Requiere: Fabric API, Cobblemon 1.7.x, Fabric Language Kotlin.
