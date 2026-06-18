---
name: senior-fullstack
description: >-
  Kit de herramientas completo para un desarrollador fullstack senior con herramientas modernas y mejores prácticas.
risk: critical
source: community
date_added: "2026-02-27"
---

# Fullstack Senior

Kit de herramientas completo para un desarrollador fullstack senior con herramientas modernas y mejores prácticas.

## Inicio Rápido

### Capacidades Principales

Esta habilidad proporciona tres capacidades centrales a través de scripts automatizados:

```bash
# Script 1: Andamiaje Fullstack
uv run scripts/fullstack_scaffolder.py [opciones]

# Script 2: Andamiaje de Proyecto
uv run scripts/project_scaffolder.py [opciones]

# Script 3: Analizador de Calidad de Código
uv run scripts/code_quality_analyzer.py [opciones]
```

## Capacidades Centrales

### 1. Andamiaje Fullstack (Fullstack Scaffolder)

Herramienta automatizada para tareas de andamiaje fullstack.

**Características:**
- Andamiaje automatizado
- Mejores prácticas integradas
- Plantillas configurables
- Controles de calidad

**Uso:**
```bash
uv run scripts/fullstack_scaffolder.py <ruta-del-proyecto> [opciones]
```

### 2. Andamiaje de Proyecto (Project Scaffolder)

Herramienta exhaustiva de análisis y optimización.

**Características:**
- Análisis profundo
- Métricas de rendimiento
- Recomendaciones
- Correcciones automatizadas

**Uso:**
```bash
uv run scripts/project_scaffolder.py <ruta-objetivo> [--verbose]
```

### 3. Analizador de Calidad de Código (Code Quality Analyzer)

Herramientas avanzadas para tareas especializadas.

**Características:**
- Automatización a nivel experto
- Configuraciones personalizadas
- Listo para integraciones
- Salida a nivel de producción

**Uso:**
```bash
uv run scripts/code_quality_analyzer.py [argumentos] [opciones]
```

## Documentación de Referencia

### Guía de Stack Tecnológico (Tech Stack Guide)

Guía exhaustiva disponible en `references/tech_stack_guide.md`:

- Patrones y prácticas detalladas
- Ejemplos de código
- Mejores prácticas
- Antipatrones a evitar
- Escenarios del mundo real

### Patrones de Arquitectura (Architecture Patterns)

Documentación completa del flujo de trabajo en `references/architecture_patterns.md`:

- Procesos paso a paso
- Estrategias de optimización
- Integración de herramientas
- Ajuste de rendimiento
- Guía de solución de problemas

### Flujos de Desarrollo (Development Workflows)

Guía de referencia técnica en `references/development_workflows.md`:

- Detalles del stack tecnológico
- Ejemplos de configuración
- Patrones de integración
- Consideraciones de seguridad
- Pautas de escalabilidad

## Stack Tecnológico

**Lenguajes:** TypeScript, JavaScript, Python, Go, Swift, Kotlin
**Frontend:** React, Next.js, React Native, Flutter
**Backend:** Node.js, Express, GraphQL, REST APIs
**Bases de Datos:** PostgreSQL, Prisma, NeonDB, Supabase
**DevOps:** Docker, Kubernetes, Terraform, GitHub Actions, CircleCI
**Cloud:** AWS, GCP, Azure

## Flujo de Trabajo de Desarrollo

### 1. Configuración e Instalación

```bash
# Instalar dependencias
npm install
# o
pip install -r requirements.txt

# Configurar entorno
cp .env.example .env
```

### 2. Ejecutar Controles de Calidad

```bash
# Usar el script analizador
uv run scripts/project_scaffolder.py .

# Revisar recomendaciones
# Aplicar correcciones
```

### 3. Implementar Mejores Prácticas

Sigue los patrones y prácticas documentados en:
- `references/tech_stack_guide.md`
- `references/architecture_patterns.md`
- `references/development_workflows.md`

## Resumen de Mejores Prácticas

### Calidad del Código
- Seguir patrones establecidos
- Escribir pruebas exhaustivas
- Documentar decisiones
- Revisar regularmente

### Rendimiento
- Medir antes de optimizar
- Usar almacenamiento en caché adecuado
- Optimizar rutas críticas
- Monitorear en producción

### Seguridad
- Validar todas las entradas
- Usar consultas parametrizadas
- Implementar autenticación adecuada
- Mantener las dependencias actualizadas

### Mantenibilidad
- Escribir código claro
- Usar nombres consistentes
- Añadir comentarios útiles
- Mantenerlo simple

## Comandos Comunes

```bash
# Desarrollo
npm run dev
npm run build
npm run test
npm run lint

# Análisis
uv run scripts/project_scaffolder.py .
uv run scripts/code_quality_analyzer.py --analyze

# Despliegue
docker build -t app:latest .
docker-compose up -d
kubectl apply -f k8s/
```

## Solución de Problemas

### Problemas Comunes

Revisa la sección exhaustiva de solución de problemas en `references/development_workflows.md`.

### Obtener Ayuda

- Revisar la documentación de referencia
- Revisar los mensajes de salida de los scripts
- Consultar la documentación del stack tecnológico
- Revisar los registros de errores (logs)

## Recursos

- Referencia de Patrones: `references/tech_stack_guide.md`
- Guía de Flujo de Trabajo: `references/architecture_patterns.md`
- Guía Técnica: `references/development_workflows.md`
- Scripts de Herramientas: Directorio `scripts/`

## Cuándo Usar
Esta habilidad es aplicable para ejecutar el flujo de trabajo o las acciones descritas en el resumen.

## Limitaciones
- Usa esta habilidad solo cuando la tarea coincida claramente con el alcance descrito anteriormente.
- No trates la salida como un sustituto de la validación específica del entorno, pruebas o revisión experta.
- Detente y pide aclaraciones si faltan entradas requeridas, permisos, límites de seguridad o criterios de éxito.
