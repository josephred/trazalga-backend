---
name: creador-de-habilidades
description: >
  Habilidad para crear y estructurar nuevas habilidades (skills) en español
  en el workspace o de forma global, siguiendo el estándar de Google Antigravity.
  Úsala cuando el usuario te pida crear una nueva habilidad, automatizar una tarea
  repetitiva con una habilidad o documentar un flujo de trabajo.
---

# Creador de Habilidades en Español

Esta habilidad permite al agente guiar al usuario en la creación de habilidades personalizadas en español, estructurando correctamente el archivo `SKILL.md` y determinando si se requiere un script auxiliar en Python.

> [!IMPORTANT]
> Incluso si la solicitud o descripción inicial de la habilidad viene escrita en otro idioma (como inglés o cualquier otro), la habilidad resultante (`SKILL.md`, explicaciones y guías) siempre debe crearse y redactarse en **idioma español**.


## Cuándo usar esta habilidad

- El usuario solicita crear una nueva habilidad o automatizar una tarea específica.
- El usuario dice "crea una habilidad para X", "haz una habilidad de esto", o "automatiza este flujo".
- Quieres documentar un patrón recurrente del proyecto en una habilidad reutilizable.

## Instrucciones y Fases

### Fase 1: Entendimiento y Lluvia de Ideas (Brainstorming)
Realiza una conversación interactiva con el usuario en español. No hagas todas las preguntas de golpe. Divide las preguntas en rondas:

#### Ronda 1: Propósito y Alcance
1. "He aquí mi entendimiento de la habilidad que deseas crear: [resumen]. ¿Es correcto? ¿Qué cambiarías?"
2. "¿Cuáles son las entradas (inputs) y salidas (outputs) esperadas para esta habilidad?"
3. "¿Con qué frecuencia esperas usar esta habilidad? ¿Es recurrente o de una sola vez?"

#### Ronda 2: Flexibilidad y Manejo de Errores
1. "Para el paso X, si el método principal falla, ¿el agente debería: (a) pedirte ayuda, (b) intentar alternativas automáticamente, o (c) fallar con un error explícito?"
2. "¿Hay pasos donde el método exacto sea estricto (ej. usar una base de datos específica), o cualquier enfoque razonable está bien?"

#### Ronda 3: Recursos y Scripts de Código
Determina si la habilidad necesita código/scripts:
- Si requiere interactuar con APIs externas, procesar archivos, realizar cálculos complejos o ejecutar comandos específicos, **requiere un script de apoyo** (patrón CLI con `argparse` y ejecutado mediante `uv run`).
- Si consiste puramente en seguir un protocolo de razonamiento o coordinar herramientas existentes del agente, se puede hacer con una **habilidad de solo instrucciones**.

Preguntas sugeridas:
1. "Para esta habilidad, he detectado que [necesita / no necesita] un script de Python. ¿Qué opinas?"
2. (Si necesita script) "¿Qué comandos te gustaría que tuviera el script? Ejemplo: `buscar`, `analizar`, `generar`."

---

### Fase 2: Diseño de la Habilidad
Presenta un plan de implementación en español que incluya:
1. El **nombre de la habilidad** (en minúsculas, usando guiones, ej. `mi-habilidad-nueva`).
2. La **descripción** concisa.
3. El **tipo de habilidad**: De solo instrucciones o Basada en CLI.
4. La **estructura de archivos** propuesta (ej. `.agents/skills/mi-habilidad-nueva/SKILL.md`).
5. El diseño del script CLI si corresponde.

**Espera a que el usuario apruebe el diseño antes de escribir los archivos.**

---

### Fase 3: Implementación
Crea la estructura de archivos en el directorio correspondiente:
- Habilidades locales (del proyecto): `.agents/skills/<nombre-habilidad>/SKILL.md`
- Habilidades globales: `~/.gemini/config/skills/<nombre-habilidad>/SKILL.md`

> [!IMPORTANT]
> **Idioma Obligatorio:** Todo el contenido del archivo `SKILL.md` (el frontmatter YAML, el título, las instrucciones, resúmenes, nombres de parámetros y comentarios de scripts) debe estar redactado enteramente en **español**, independientemente de la lengua utilizada en la solicitud de origen.

#### Estructura del SKILL.md en Español:

```markdown
---
name: {nombre-habilidad}
description: >-
  {descripción concisa de cuándo usar la habilidad}
---

# {Título de la Habilidad}

## Resumen
{Descripción detallada del propósito.}

## Cuándo usar esta habilidad
- {Cuándo sí usarla}

## Cuándo NO usar esta habilidad
- {Cuándo no usarla}

## Instrucciones / Flujo de Trabajo
1. {Paso 1}
2. {Paso 2}
...

## Scripts Útiles (Si es basada en CLI)
{Documentar comandos de consola y ejemplos de ejecución con `uv run`.}

## Errores Comunes
{Mencionar 2-3 problemas habituales y cómo solucionarlos.}
```

#### Reglas de los Scripts CLI:
- Utiliza `uv run` en lugar de `python` o `python3` para ejecutar los scripts.
- Prioriza el uso de la biblioteca estándar de Python (`urllib`, etc.).
- Si se interactúa con APIs, implementa límites de tasa (rate limiting) y manejo robusto de excepciones (como reintentos con retraso exponencial).
- Escribe siempre los resultados grandes a archivos de salida, no a la consola estándar (stdout), para no saturar el contexto del agente.

---

### Fase 4: Verificación
Una vez implementada, haz una prueba simulando una consulta del usuario que active la nueva habilidad y verifica que el agente se comporte de acuerdo a lo especificado en la nueva habilidad.
