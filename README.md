# BancoIAS - API de Transferencias

API REST reactiva para realizar y consultar transferencias entre cuentas bancarias.

## Tecnologías

* Java 21
* Spring Boot 4.1.1
* Spring WebFlux
* Spring Data R2DBC
* PostgreSQL 16
* Flyway
* MapStruct
* Lombok
* JUnit 5
* WebTestClient
* Testcontainers
* Maven
* Docker
* Docker Compose

## Requisitos

### Para ejecutar la aplicación

Se necesita:

* Docker Desktop
* Docker Compose
* Un archivo `.env` configurado a partir de `.env.example`

No es necesario tener Java, Maven ni PostgreSQL instalados localmente, ya que la aplicación y PostgreSQL se ejecutan mediante Docker Compose.

### Para ejecutar las pruebas

Las pruebas automatizadas se ejecutan desde Maven y utilizan Testcontainers.

Se necesita:

* Java 21 o superior
* Maven 3.9 o superior
* Docker Desktop

Docker Compose no es necesario para ejecutar las pruebas.

Las pruebas levantan su propio contenedor de PostgreSQL mediante Testcontainers, independiente del PostgreSQL utilizado por Docker Compose.

---

## Configuración

La aplicación utiliza variables de entorno para la conexión a PostgreSQL.

Crear un archivo `.env` en la raíz del proyecto tomando como referencia `.env.example`.

Ejemplo:

```env
POSTGRES_HOST=localhost
POSTGRES_PORT=5433
POSTGRES_DB=bancoias
POSTGRES_USER=bancoias_app
POSTGRES_PASSWORD=bancoias_local
```

El archivo `.env` es únicamente para configuración local y no debe versionarse.

Cuando la aplicación se ejecuta mediante Docker Compose, PostgreSQL y la API se levantan como servicios independientes.

Dentro de Docker, la aplicación se conecta a PostgreSQL utilizando el nombre del servicio:

```text
postgres:5432
```

Las migraciones de base de datos se ejecutan automáticamente mediante Flyway al iniciar la aplicación.

---

## Obtener el proyecto

Clonar el repositorio:

```bash
git clone https://github.com/maycoldsanchez/bancoias.git
```

Ingresar al proyecto:

```bash
cd bancoias
```

Crear el archivo `.env` tomando como referencia `.env.example`:

```bash
cp .env.example .env
```

En Windows también se puede crear o copiar el archivo manualmente desde `.env.example`.

Después de configurar el archivo `.env`, la aplicación puede levantarse mediante Docker Compose.

---

## Ejecutar la aplicación con Docker Compose

La forma recomendada para ejecutar el proyecto completo es mediante Docker Compose.

Con Docker Desktop iniciado y el archivo `.env` configurado:

```bash
docker compose up --build
```

Esto levanta:

* PostgreSQL 16
* La aplicación BancoIAS

PostgreSQL se ejecuta en el puerto configurado en `POSTGRES_PORT`.

La aplicación queda disponible en:

```text
http://localhost:8080
```

Para verificar el estado de la aplicación:

```bash
curl http://localhost:8080/actuator/health
```

La respuesta esperada es similar a:

```json
{
  "status": "UP"
}
```

Para detener los servicios:

```bash
docker compose down
```

El volumen de PostgreSQL se mantiene para conservar los datos.

Si se necesita eliminar también los datos almacenados:

```bash
docker compose down -v
```

---

## Ejecutar la aplicación sin Docker Compose

También es posible ejecutar la aplicación directamente con Maven, siempre que exista un PostgreSQL disponible y correctamente configurado.

```bash
mvn spring-boot:run
```

La aplicación queda disponible en:

```text
http://localhost:8080
```

Esta opción requiere tener Java, Maven y PostgreSQL disponibles en el entorno local.

---

## Ejecutar las pruebas

Las pruebas automatizadas utilizan Testcontainers para levantar un PostgreSQL independiente.

Por esta razón, Docker Desktop debe estar iniciado.

Ejecutar todas las pruebas:

```bash
mvn test
```

Ejecutar la suite principal:

```bash
mvn -Dtest=TransferApiTest -DforkCount=0 test
```

Resultado validado de la suite:

```text
Tests run: 20
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

Las pruebas restauran el estado de las cuentas entre ejecuciones para evitar que una prueba afecte a otra.

El PostgreSQL utilizado por Testcontainers es independiente del PostgreSQL utilizado por Docker Compose.

---

## Endpoints

### Crear transferencia

```http
POST /api/transfers
Content-Type: application/json
```

Ejemplo:

```json
{
  "clientReference": "TEST-001",
  "sourceAccount": "ACC-1001",
  "destinationAccount": "ACC-1002",
  "amount": 100000.00,
  "currency": "COP"
}
```

Respuesta:

```json
{
  "transferId": "550e8400-e29b-41d4-a716-446655440000",
  "clientReference": "TEST-001",
  "sourceAccount": "****1001",
  "destinationAccount": "****1002",
  "amount": 100000.00,
  "currency": "COP",
  "status": "COMPLETED",
  "processedAt": "2026-09-17T12:00:00Z"
}
```

La creación exitosa responde con `201 Created`.

### Consultar transferencia

```http
GET /api/transfers/{transferId}
```

Ejemplo:

```bash
curl http://localhost:8080/api/transfers/550e8400-e29b-41d4-a716-446655440000
```

Los números de cuenta se muestran enmascarados en la respuesta.

---

## Reglas principales

La transferencia valida:

* La moneda debe ser `COP`.
* El monto debe ser mayor que cero.
* El monto puede tener máximo dos decimales.
* La cuenta origen debe existir y estar activa.
* La cuenta destino debe existir y estar activa.
* Las cuentas origen y destino deben ser diferentes.
* La cuenta origen debe tener saldo suficiente.

---

## Idempotencia

`clientReference` identifica de forma única una solicitud.

Si se recibe nuevamente la misma referencia con los mismos datos, se devuelve la transferencia existente y no se realiza un segundo movimiento.

Si la referencia ya existe pero los datos son diferentes, se responde con:

```text
IDEMPOTENCY_CONFLICT
```

Para comparar las solicitudes se utiliza una huella SHA-256 de los datos de la transferencia.

---

## Concurrencia y atomicidad

La transferencia se ejecuta dentro de una transacción reactiva.

El flujo es:

```text
Crear transferencia
       ↓
Debitar origen
       ↓
Acreditar destino
       ↓
Marcar como COMPLETED
```

Si alguna operación falla, los cambios realizados se revierten.

El débito utiliza una actualización SQL que comprueba el saldo disponible directamente en la base de datos:

```sql
balance >= :amount
```

Esto permite controlar solicitudes concurrentes sobre una misma cuenta sin utilizar un bloqueo global de la aplicación.

---

## Correlation ID

La API utiliza el header:

```text
X-Correlation-Id
```

Si el cliente proporciona uno, se conserva. Si no, la aplicación genera uno automáticamente.

El mismo identificador se devuelve en la respuesta.

---

## Arquitectura

El proyecto sigue una organización basada en arquitectura hexagonal, separando la lógica de negocio de los detalles de infraestructura.

De forma general:

```text
Controller
    ↓
Handler
    ↓
Use Case
    ↓
Ports
    ↓
Adapters
    ↓
PostgreSQL / R2DBC
```

Las principales decisiones técnicas están documentadas en:

```text
docs/ADR.md
```

---

## Docker

El proyecto incluye:

```text
compose.yml
.docker/Dockerfile
```

El `compose.yml` define dos servicios:

```text
postgres
    ↓
app-bancoias
```

El servicio `postgres` utiliza PostgreSQL 16 Alpine.

El servicio `app-bancoias` construye la aplicación mediante el `Dockerfile` y espera a que PostgreSQL se encuentre saludable antes de iniciar.

La imagen de la aplicación utiliza una construcción en dos etapas:

```text
Maven + JDK 21
       ↓
Compilación del proyecto
       ↓
JRE 21
       ↓
Aplicación BancoIAS
```

La aplicación se ejecuta dentro del contenedor con un usuario no root.

---

## Pruebas

Las pruebas de integración utilizan un PostgreSQL real mediante Testcontainers.

Se cubren escenarios como:

* Transferencia exitosa.
* Consulta por ID.
* Cuentas inexistentes.
* Cuentas bloqueadas.
* Cuenta origen y destino iguales.
* Montos inválidos.
* Moneda no soportada.
* Saldo insuficiente.
* Montos con decimales.
* Idempotencia.
* Conflicto de idempotencia.
* Conservación del saldo cuando una transferencia falla.
* Correlation ID.
* Concurrencia.

La suite validada actualmente contiene 20 pruebas exitosas.

---

## Documentación adicional

### Decisiones técnicas

```text
docs/ADR.md
```

### Uso de inteligencia artificial

```text
docs/AI_USAGE.md
```

El documento de IA especifica el modelo utilizado y cómo fue utilizado durante el desarrollo.

---

## Versión para evaluación

La evaluación debe realizarse sobre la rama y el commit indicados en la entrega final.

Para consultar la rama actual:

```bash
git branch --show-current
```

Para consultar el commit:

```bash
git rev-parse HEAD
```
