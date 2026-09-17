# Decisiones técnicas

## 1. Arquitectura hexagonal

Se utilizó una organización basada en arquitectura hexagonal para separar la lógica de negocio de la infraestructura.

La idea principal es que los casos de uso trabajen con interfaces (ports) y que detalles como HTTP o PostgreSQL estén implementados mediante adaptadores.

Esto permite mantener una separación clara entre negocio e infraestructura.

---

## 2. Spring WebFlux

Se eligió Spring WebFlux porque el reto requiere un flujo reactivo y no bloqueante.

Los endpoints trabajan con `Mono` y las operaciones de persistencia utilizan APIs reactivas.

---

## 3. R2DBC + PostgreSQL

Se utilizó R2DBC para acceder a PostgreSQL de forma reactiva.

PostgreSQL se utiliza como base de datos por su soporte transaccional y por permitir controlar directamente operaciones concurrentes sobre las cuentas.

---

## 4. Flyway

Flyway se utiliza para crear y versionar la estructura inicial de la base de datos.

Esto permite que el proyecto pueda levantar el esquema necesario sin depender de modificaciones manuales.

---

## 5. Docker Compose

Se incluyó Docker Compose para facilitar la ejecución completa de la aplicación.

El archivo `compose.yml` define los servicios de PostgreSQL y BancoIAS.

La aplicación espera a que PostgreSQL esté disponible mediante un healthcheck antes de iniciar.

Esto permite que una persona que evalúe el proyecto pueda levantar la aplicación sin necesidad de instalar Java, Maven o PostgreSQL localmente.

---

## 6. Dockerfile

La imagen de la aplicación utiliza una construcción en dos etapas.

Primero se utiliza Maven con JDK 21 para compilar el proyecto y generar el archivo JAR.

Después se utiliza una imagen JRE 21 para ejecutar únicamente la aplicación.

Además, el contenedor ejecuta la aplicación con un usuario no root.

La intención es mantener una imagen de ejecución separada del entorno utilizado para compilar.

---

## 7. Atomicidad

Una transferencia debe comportarse como una única operación.

Por esta razón, el siguiente flujo se ejecuta dentro de una transacción:

```text
Crear transferencia
       ↓
Debitar origen
       ↓
Acreditar destino
       ↓
Completar transferencia
```

Se utiliza `TransactionalOperator` para manejar la transacción reactiva.

Si alguna operación falla, se revierten los cambios realizados durante la transferencia.

---

## 8. Control de saldo y concurrencia

El débito no depende únicamente de consultar primero el saldo.

La actualización se realiza con una condición similar a:

```sql
UPDATE accounts
SET balance = balance - :amount
WHERE account_number = :accountNumber
  AND status = 'ACTIVE'
  AND currency = 'COP'
  AND balance >= :amount
```

De esta manera, la propia actualización comprueba que todavía exista saldo suficiente.

Esto permite manejar solicitudes concurrentes sobre una misma cuenta sin introducir un bloqueo global para toda la aplicación.

---

## 9. Idempotencia

Se utiliza `clientReference` como referencia única de la operación.

La tabla `transfers` tiene una restricción `UNIQUE` sobre este campo.

Además, se genera una huella SHA-256 utilizando los datos principales de la solicitud:

```text
clientReference
sourceAccount
destinationAccount
amount
currency
```

El comportamiento es:

* Misma referencia + mismos datos → se devuelve la transferencia existente.
* Misma referencia + datos diferentes → `IDEMPOTENCY_CONFLICT`.

Esto evita procesar dos veces la misma operación y también evita reutilizar una referencia para una transferencia diferente.

---

## 10. Enmascaramiento de cuentas

Los números de cuenta completos se utilizan internamente para realizar la transferencia, pero las respuestas HTTP los muestran enmascarados.

Por ejemplo:

```text
ACC-1001 → ****1001
```

Esto evita exponer innecesariamente la información completa de las cuentas.

---

## 11. Correlation ID

Se implementó el header:

```text
X-Correlation-Id
```

Si el cliente envía el valor, se conserva.

Si no lo envía, se genera un UUID.

El valor se devuelve en la respuesta para facilitar la trazabilidad de las solicitudes.

---

## 12. Testcontainers

Las pruebas utilizan Testcontainers con PostgreSQL.

Se tomó esta decisión para que las pruebas utilicen una base de datos real y no dependan de la instalación local de PostgreSQL del desarrollador.

Cada prueba restaura las cuentas a su estado inicial y elimina las transferencias generadas.

El contenedor utilizado por las pruebas es independiente de los servicios definidos en Docker Compose.

---

## 13. Supuestos

Para el alcance del reto se asumió que:

* La moneda soportada es COP.
* Las cuentas pertenecen al mismo sistema.
* Las transferencias se realizan entre cuentas de la misma base de datos.
* La autenticación y autorización están fuera del alcance.
* No se requiere frontend.
* No se requiere CI/CD.
* No se requiere Kubernetes.
* Los números de cuenta no deben exponerse completos en las respuestas.

---

## 14. Alcance

Se priorizó el núcleo funcional solicitado:

* Transferencias.
* Validaciones.
* Idempotencia.
* Atomicidad.
* Concurrencia.
* Consulta de transferencias.
* Pruebas automatizadas.
* Ejecución mediante Docker Compose.

No se agregaron funcionalidades adicionales que no fueran necesarias para demostrar estos comportamientos.
