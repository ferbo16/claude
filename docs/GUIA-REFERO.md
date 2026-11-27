# Cómo darle a Pausa el estilo de una web famosa (con Refero + Claude)

Guía para personas que no saben de diseño ni de programación.

---

## 1. ¿Qué es esto, en una frase?

**Refero Styles** (https://styles.refero.design) es un catálogo de más de 2.000 "estilos" sacados
de webs conocidas (Notion, Airbnb, Mercury, Vercel…). Cada estilo viene con una **ficha
de instrucciones** llamada `DESIGN.md` que Claude sabe leer.

Piensa en ello como en una **receta de decoración**: en vez de decirle a Claude "hazlo bonito",
le das la receta exacta (qué colores, qué letra, qué tan redondeadas las esquinas, qué hacer
y qué evitar) y Claude la aplica a la app.

| Palabra rara | Qué significa |
|---|---|
| **Estilo** | El "look" de una web: sus colores, letras y formas. |
| **DESIGN.md** | Un archivo de texto con ese look explicado paso a paso. |
| **Token** | Un valor concreto del diseño, p. ej. "color de fondo = `#171721`". |
| **Skill** | Un "manual de instrucciones" guardado en este repositorio que le enseña a Claude a hacer una tarea. |
| **MCP / conector** | Un "enchufe" que deja a Claude consultar Refero directamente. |

---

## 2. Forma fácil y gratuita (ya está preparada en este repositorio)

Ya hay una skill llamada **`refero-style`** en `.claude/skills/refero-style/`. No tienes que
instalar nada.

### Paso a paso

1. **Elige un estilo.** Abre https://styles.refero.design en el navegador. Mira las tarjetas;
   puedes buscar ("dark", "minimal", "Notion"…) o usar las colecciones de arriba.
2. **Copia el enlace.** Entra en el estilo que te guste y copia la dirección de la barra del
   navegador. Se parece a esto:
   `https://styles.refero.design/style/3172cd4d-118a-4a16-a259-6b634d32322e`
3. **Pídeselo a Claude.** En una sesión de Claude Code con este repositorio, escribe algo como:

   > Aplica este estilo de Refero a la app: https://styles.refero.design/style/3172cd4d-118a-4a16-a259-6b634d32322e

   (También puedes escribir `/refero-style` y pegar el enlace.)
4. **Claude hará solo esto:**
   - Descarga la receta y la guarda en `design/DESIGN.md`.
   - Cambia los colores de la app (`colors.xml`), el tema (`themes.xml`) y, si hace falta,
     esquinas y espacios de las pantallas.
   - Te explica con palabras normales qué cambió.
5. **Prueba la app.** Al subir los cambios (push), GitHub crea el APK automáticamente: pestaña
   **Actions** → último *Android build* → descarga **Pausa-debug-apk** e instálalo.
6. **¿No te gusta?** Dile a Claude "más claro", "el botón en otro color", "vuelve al estilo de
   antes"… Todo queda guardado en Git, así que siempre se puede deshacer.

### Ejemplos de lo que puedes pedir

- "Ponle a Pausa el estilo de este enlace, pero deja la alarma en rojo."
- "Quiero que se vea como esta web, pero en modo claro."
- "Explícame qué colores tiene el DESIGN.md antes de cambiar nada."

---

## 3. Forma avanzada: conectar Refero directamente a Claude (MCP)

Con esto Claude puede **buscar estilos, pantallas y flujos en Refero por sí mismo**, sin que tú
copies enlaces. **Necesita una cuenta de Refero** (y, según su web, una suscripción que incluya MCP:
mira https://refero.design/pricing).

- **En la app o web de Claude (claude.ai):** Ajustes → Conectores → *Añadir conector
  personalizado*. Nombre: `Refero`. URL: `https://api.refero.design/mcp`. Después inicia sesión
  con tu cuenta de Refero cuando te lo pida.
- **En Claude Code en tu ordenador:** abre una terminal y ejecuta
  ```bash
  claude mcp add --transport http refero https://api.refero.design/mcp
  ```
  Si Refero te da un token en https://refero.design/mcp, sigue el comando exacto que te muestra
  esa página (añade `--header "Authorization: Bearer …"`). **No pegues tu token en un chat.**
- Documentación oficial: https://doc.refero.design/mcp/getting-started

> En las sesiones de Claude Code en la nube, el entorno **"claude"** ya permite la web de estilos.
> Para usar el MCP desde la nube también habría que permitir `api.refero.design`.

---

## 4. Cosas a tener en cuenta

- Los DESIGN.md **los genera Refero**, no son las guías oficiales de esas marcas. Usa el estilo
  como inspiración: no copies logos ni nombres de marcas en la app.
- Las fuentes de muchas marcas son de pago; Claude usará una parecida y gratuita.
- Pausa es una app Android y los estilos vienen de webs: Claude adapta la receta (píxeles →
  `dp`, CSS → XML de Android). Algunos detalles web (sombras, animaciones) pueden no trasladarse igual.
- Los colores rojo (alarma) y verde (éxito) se mantienen reconocibles porque tienen significado.
