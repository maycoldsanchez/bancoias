# Registro de uso de IA

## Herramienta utilizada

* **Modelo:** GPT-5.6 Luna
* **Entorno:** ChatGPT

## Uso durante el desarrollo

La inteligencia artificial **no fue utilizada para generar el código principal del proyecto**.

El desarrollo de la solución, la implementación de la lógica de negocio y la estructura del proyecto fueron realizados durante el desarrollo del reto.

La IA se utilizó como herramienta de apoyo principalmente para:

* Identificar errores de compilación.
* Analizar bugs encontrados durante la ejecución.
* Revisar mensajes de error.
* Identificar posibles causas de problemas.
* Apoyar la corrección de algunos errores puntuales.
* Revisar problemas relacionados con Spring WebFlux y R2DBC.
* Analizar aspectos de idempotencia y concurrencia.
* Apoyar la configuración y diagnóstico de Testcontainers.
* Revisar algunas pruebas automatizadas.
* Revisar aspectos de documentación y organización de la entrega.

## Forma de trabajo

Las sugerencias de la IA fueron revisadas antes de aplicarse.

Las correcciones realizadas con apoyo de IA fueron posteriormente verificadas mediante compilación, ejecución de la aplicación y pruebas automatizadas.

La IA se utilizó principalmente como apoyo de debugging y revisión técnica, no como sustituto del desarrollo.

## Validación

La solución fue ejecutada localmente y las pruebas fueron ejecutadas contra PostgreSQL mediante Testcontainers.

La aplicación también puede ejecutarse mediante Docker Compose, levantando PostgreSQL y BancoIAS como servicios independientes.

Resultado final validado:

```text
Tests run: 20
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

## Responsabilidad sobre el código

El código final y las decisiones de implementación corresponden al desarrollo realizado para el proyecto.

La IA fue utilizada como herramienta de asistencia para detectar problemas, analizar errores y apoyar algunas correcciones durante el desarrollo.
