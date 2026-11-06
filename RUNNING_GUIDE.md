# Guía de Ejecución, Despliegue y Ciclo de Vida Operativo

Este documento describe paso a paso cómo compilar, ejecutar en desarrollo o producción, acceder a los servicios auxiliares (Keycloak, Swagger, RabbitMQ, Actuator) y comprender el ciclo de vida y flujo operativo completo de **FacilComanda Ponoa API**.

---

## 📋 1. Requisitos Previos

Antes de comenzar, asegúrate de tener instalado en tu máquina o servidor:

* **Java Development Kit (JDK):** Versión 17 o 21 (Recomendado: [Eclipse Temurin 17/21](https://adoptium.net/)).
* **Docker y Docker Compose:** Versión 24+ con plugin Compose v2+.
* **Git:** Para clonar y gestionar el repositorio.
* **Maven:** No es indispensable tenerlo instalado globalmente, el proyecto incluye el wrapper ejecutable (`./mvnw` en Linux/macOS o `mvnw.cmd` en Windows).

---

## 🚀 2. Cómo Compilar y Correr el Proyecto

Dispones de dos modalidades de ejecución según tu objetivo:

### Modalidad A: Desarrollo Local (Recomendado para programar y depurar)

En esta modalidad corres los servicios de infraestructura (PostgreSQL, RabbitMQ, Keycloak) en Docker, y compilas y ejecutas la API Spring Boot directamente en tu terminal o IDE (IntelliJ, VS Code, Eclipse).

#### Paso 1: Levantar los servicios de infraestructura
```bash
# Inicia PostgreSQL (5432), RabbitMQ (5672 / 15672) y Keycloak (8080)
docker compose up -d postgres rabbitmq keycloak
```

Verifica que los servicios estén saludables (*healthy*):
```bash
docker compose ps
```

#### Paso 2: Compilar el código fuente y ejecutar pruebas
```bash
# En Windows (PowerShell / CMD):
.\mvnw.cmd clean test

# En Linux / macOS:
./mvnw clean test
```

#### Paso 3: Iniciar la API Spring Boot
```bash
# En Windows:
.\mvnw.cmd spring-boot:run

# En Linux / macOS:
./mvnw spring-boot:run
```
La API arrancará en el puerto **`8081`** y aplicará automáticamente las migraciones de base de datos Flyway (V1 a V16).

---

### Modalidad B: Despliegue Completo con Docker Compose (Contenedores)

Ideal para pruebas de integración completas, servidores de QA o despliegues locales idénticos a producción.

#### Paso 1: Configurar variables de entorno
Copia la plantilla de entorno:
```bash
# Linux / macOS:
cp .env.production.example .env

# Windows (PowerShell):
Copy-Item .env.production.example .env
```

#### Paso 2: Construir y levantar todo el stack
```bash
docker compose up -d --build
```
Este comando:
1. Compilará la imagen Docker multi-stage de la API usando Eclipse Temurin JRE 17 con usuario sin privilegios root (`appuser`).
2. Levantará PostgreSQL 15, RabbitMQ 3.13 con Management UI y Keycloak 24.
3. Esperará a que la base de datos y RabbitMQ superen sus *healthchecks* antes de encender la API.

#### Comandos útiles para monitorear Docker Compose:
```bash
# Ver el estado de todos los contenedores:
docker compose ps

# Ver los logs en vivo de la API:
docker compose logs -f api

# Reiniciar solo el contenedor de la API tras cambios:
docker compose restart api

# Detener todos los servicios preservando la base de datos:
docker compose down

# Detener todos los servicios eliminando volúmenes (reset de fábrica):
docker compose down -v
```

---

## 🌐 3. Acceso a Servicios, Dashboards y Herramientas

Una vez iniciado el sistema, podrás acceder a las siguientes interfaces web:

### 3.1. Documentación Swagger / OpenAPI
Interfaz interactiva para explorar y probar todos los endpoints REST directamente desde el navegador:

* **URL Swagger UI:** [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)
* **Especificación OpenAPI (JSON):** [http://localhost:8081/v3/api-docs](http://localhost:8081/v3/api-docs)

> **¿Cómo autenticarse en Swagger?**
> 1. Obtén un token Bearer JWT de Keycloak (ver sección 3.2).
> 2. En Swagger UI, pulsa el botón verde **Authorize** (arriba a la derecha).
> 3. Pega el valor en el formato: `Bearer <tu_token_jwt>`.
> 4. Pulsa **Authorize** y luego **Close**. Ahora podrás invocar cualquier endpoint con tu sesión y rol correspondiente.

---

### 3.2. Servidor de Identidad Keycloak (IAM)

Keycloak gestiona los usuarios, roles y emisión de tokens OAuth2 / OIDC:

* **Consola de Administración:** [http://localhost:8080/admin](http://localhost:8080/admin)
* **Credenciales por defecto:**
  * **Usuario:** `admin`
  * **Contraseña:** `admin`
* **Realm del Proyecto:** `fcp-realm`

#### ¿Cómo obtener un Token JWT desde la terminal (cURL o Postman)?

##### Opción A: A través de la API (Proxy de Autenticación - Recomendado)
Evita exponer Keycloak directamente al navegador y resuelve problemas de CORS:
```bash
curl -X POST "http://localhost:8081/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "cajero1@facilcomanda.com",
    "password": "password123"
  }'
```

##### Opción B: Directo a Keycloak (IAM OIDC)
Invocando el endpoint de token de Keycloak:
```bash
curl -X POST "http://localhost:8080/realms/fcp-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=fcp-api" \
  -d "username=cajero1@facilcomanda.com" \
  -d "password=password123"
```

**Respuesta recibida:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 300,
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_expires_in": 1800
}
```
Copia el valor de `access_token` para enviarlo en la cabecera `Authorization: Bearer <access_token>`.

---

### 3.3. RabbitMQ Management Dashboard

Permite inspeccionar en tiempo real las colas de impresión térmica, tasas de consumo y la Dead Letter Queue:

* **Consola Web:** [http://localhost:15672](http://localhost:15672)
* **Usuario por defecto:** `guest`
* **Contraseña por defecto:** `guest`
* **Colas Principales a Monitorear:**
  * **`printing.jobs`:** Cola principal donde ingresan los trabajos de comandas de cocina y recibos de caja.
  * **`printing.dlq`:** Cola de mensajes muertos. Si una impresora se desconecta o agota papel tras 3 reintentos, el mensaje cae aquí y dispara la alerta por WebSocket.
* **Exchanges:**
  * `printing.exchange` (Topic Exchange).
  * `printing.dlx` (Dead Letter Exchange).

---

### 3.4. Spring Boot Actuator (Monitoreo, Salud & SRE)

Endpoints estándar para supervisión de salud del contenedor y extracción de métricas Prometheus:

* **Estado de Salud General:** [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)
* **Sonda Liveness (Kubernetes / Docker):** [http://localhost:8081/actuator/health/liveness](http://localhost:8081/actuator/health/liveness)
* **Sonda Readiness (Tráfico listo):** [http://localhost:8081/actuator/health/readiness](http://localhost:8081/actuator/health/readiness)
* **Métricas Prometheus:** [http://localhost:8081/actuator/prometheus](http://localhost:8081/actuator/prometheus) (Latencias HTTP, consumo JVM, pool de HikariCP, cola RabbitMQ).
* **Información de Versión:** [http://localhost:8081/actuator/info](http://localhost:8081/actuator/info)

---

## 🔄 4. Ciclo de Vida y Flujo Operativo End-to-End

El siguiente diagrama y pasos representan el ciclo de vida completo de un restaurante operando en el mundo real con **FacilComanda Ponoa**:

```
 [ADMINISTRADOR]              [CAJERO]                  [MESERO]                  [COCINERO]
        │                        │                         │                          │
  1. Configuración               │                         │                          │
     Sucursal, Mesas,            │                         │                          │
     Carta e Impresoras          │                         │                          │
        │                        │                         │                          │
        │                  2. Apertura Turno               │                          │
        │                     POST /cash/open              │                          │
        │                        │                         │                          │
        │                        │                  3. Toma de Pedido                 │
        │                        │                     POST /orders                   │
        │                        │                         │                          │
        │                        │                         ├─► Auto: Ocupa Mesa       │
        │                        │                         ├─► Auto: Descuenta Stock  │
        │                        │                         └─► Auto: Imprime Ticket ──┼──► 4. Prepara Platos
        │                        │                                                    │      KDS (PATCH /tickets)
        │                        │                  5. Pide Pre-Cuenta                │
        │                        │                     POST /pre-check/print          │
        │                        │                         │                          │
        │                  6. Cobro & Factura              │                          │
        │                     POST /invoices/pay           │                          │
        │                        ├─► Auto: Libera Mesa ────┘                          │
        │                        └─► Auto: Imprime Boleta/Factura                     │
        │                        │                                                    │
        │                  7. Cierre & Arqueo                                         │
        │                     POST /cash/close                                        │
        │                     GET /cash/z-cut ──► Imprime Ticket Corte Z               │
```

---

### Paso 1: Configuración Inicial del Restaurante (Rol: `EMPRESA` / `ADMINISTRADOR`)
1. **Crear Sucursal:** `POST /api/v1/branches` (ej. Código: `SUC-001`, Nombre: "Sucursal Central").
2. **Definir Salones y Mesas:**
   * Crear salón: `POST /api/v1/floors` (ej. "Salón Principal", "Terraza").
   * Crear mesas: `POST /api/v1/tables` (ej. "Mesa 1", "Mesa 2", asignadas al salón).
3. **Cargar Carta y Precios:**
   * Crear producto maestro: `POST /api/v1/catalog/products`.
   * Habilitar precio por sucursal: `PUT /api/v1/catalog/branches/products`.
   * Asignar stock inicial: `POST /api/v1/inventory`.
4. **Configurar Impresoras Térmicas:**
   * Registrar hardware con su IP local: `POST /api/v1/printers` (ej. IP `192.168.1.200`, puerto `9100`).
   * Configurar enrutamiento: `POST /api/v1/printers/{id}/routes` (vincular `KITCHEN_ORDER` con el área `COCINA` o `BARRA`).
5. **Crear y Aprovisionar Personal (Meseros, Cajeros, Cocineros, Administradores):**
   * El usuario `EMPRESA` o `ADMINISTRADOR` llama a `POST /api/v1/users/admin` o `POST /api/v1/users/staff` enviando email, datos personales, rol y `password`.
   * El backend de Spring Boot se encarga de crear el usuario, fijar la contraseña y mapear el rol en Keycloak automáticamente de forma atómica.


---

### Paso 2: Apertura de Caja (Rol: `CAJERO`)
Al iniciar la jornada, el cajero abre su turno indicando el fondo sencillo inicial:
* Endpoint: `POST /api/v1/cash/shifts/open`
* Header: `X-Branch-Id: 1`
* Body:
  ```json
  {
    "cashRegisterId": 1,
    "openingAmount": 150.00,
    "notes": "Apertura turno mañana"
  }
  ```
El sistema garantiza que no pueda abrir dos cajas simultáneas en la misma sucursal.

---

### Paso 3: Toma de Pedido en Salón (Rol: `MESERO`)
El mozo ingresa a la aplicación en su tablet/móvil, ve el mapa de mesas libres en verde (`AVAILABLE`) y registra la comanda:
* Endpoint: `POST /api/v1/orders`
* Headers:
  * `X-Branch-Id: 1`
  * `Idempotency-Key: <UUID>` (Garantiza que un doble toque accidental no duplique el pedido)
* Body:
  ```json
  {
    "tableId": 10,
    "orderType": "DINE_IN",
    "notes": "Sin sal",
    "items": [
      { "skuId": 1, "quantity": 2, "comments": "Término medio" }
    ]
  }
  ```
**Lo que el backend ejecuta automáticamente de forma atómica:**
1. Cambia la mesa a `OCCUPIED` (bloqueo atómico anti-colisión).
2. Descuenta el inventario mediante `UPDATE ... WHERE quantity >= amount`.
3. Notifica por WebSocket a todas las tablets que la mesa está ocupada.
4. Despacha el trabajo de impresión a RabbitMQ hacia la impresora térmica de cocina correspondiente.
5. Registra el ticket en el sistema KDS de cocina.

---

### Paso 4: Preparación en Cocina (Rol: `COCINERO`)
El personal de cocina visualiza el pedido en el KDS:
* Consulta inicial: `GET /api/v1/kitchen/tickets` (o suscripción en tiempo real a `/topic/branches/{id}/kitchen`).
* Cuando empieza a cocinar: `PATCH /api/v1/kitchen/tickets/{id}/status` con `{"status": "PREPARING"}`.
* Cuando el plato está listo para salir: `PATCH /api/v1/kitchen/tickets/{id}/status` con `{"status": "READY"}` (avisa al mozo para servir).

---

### Paso 5: Solicitud de la Pre-Cuenta (Rol: `MESERO` / `CLIENTE`)
Cuando los comensales terminan de comer y piden la cuenta:
1. El mozo consulta el desglose con IGV calculado: `GET /api/v1/orders/tables/{tableId}/pre-check`.
2. Manda a imprimir el ticket térmico: `POST /api/v1/orders/tables/{tableId}/pre-check/print`.
3. La mesa pasa automáticamente al estado `BILL_REQUESTED` (amarillo en el mapa).

---

### Paso 6: Cobro y Facturación en Caja (Rol: `CAJERO`)
El cliente se acerca a caja o paga en mesa:
1. **Consulta RUC/DNI:** El cajero escribe el documento del cliente en `GET /api/v1/customers/lookup?documentType=RUC&documentNumber=20601234567`. La API busca localmente sin costo, o consulta la API fiscal con protección anti-abuso.
2. **Registro de Cobro y Comprobante:**
   * Endpoint: `POST /api/v1/invoices/pay`
   * Body con pago dividido (*Split Payment*):
     ```json
     {
       "orderId": 50,
       "invoiceType": "BOLETA",
       "customer": {
         "identityDocumentType": "DNI",
         "identityDocumentNumber": "72345678",
         "name": "Juan Pérez"
       },
       "payments": [
         { "method": "EFECTIVO", "amount": 40.00 },
         { "method": "YAPE", "amount": 60.00, "reference": "OP-12345" }
       ]
     }
     ```
3. **Lo que ejecuta el backend:**
   * Genera correlativo fiscal consecutivo inmutable (`B001-00000045`) con bloqueo pesimista en PostgreSQL (`FOR UPDATE`).
   * Asocia la recaudación al turno de caja abierto del cajero.
   * Libera automáticamente la mesa poniéndola en `AVAILABLE` (o `CLEANING`).
   * Envía la orden de impresión del ticket final a la impresora de caja.

---

### Paso 7: Cierre de Turno y Corte Z (Rol: `CAJERO` / `ADMINISTRADOR`)
Al terminar la jornada:
1. El cajero cuenta el dinero físico de la gaveta e invoca el cierre:
   * Endpoint: `POST /api/v1/cash/shifts/{shiftId}/close`
   * Body:
     ```json
     {
       "countedCash": 550.00,
       "notes": "Cierre de turno conforme"
     }
     ```
2. El sistema calcula en milisegundos:
   $$\text{Efectivo Esperado} = \text{Apertura} + \text{Ventas en Efectivo} + \text{Ingresos Manuales} - \text{Gastos Manuales} - \text{Vueltos}$$
3. Genera el reporte final **Corte Z**:
   * Endpoint: `GET /api/v1/cash/shifts/{shiftId}/z-cut`
   * Imprime físicamente el ticket térmico de cierre diario en la impresora POS con el resumen de Boletas, Facturas y arqueo de caja.

---

## 🛠️ 5. Solución de Problemas Frecuentes (Troubleshooting)

### Error 1: "Port 8081 / 5432 / 8080 already in use"
* **Causa:** Hay otra aplicación o servicio local ocupando alguno de los puertos.
* **Solución:**
  * En Windows: Identifica y finaliza el proceso con: `netstat -ano | findstr :8081` y `taskkill /PID <PID> /F`.
  * En Linux/macOS: `lsof -i :8081` y `kill -9 <PID>`.

### Error 2: "Circuit breaker tripped or printer communication failed"
* **Causa:** La API no puede conectar por socket TCP a la IP/puerto configurado para la impresora térmica.
* **Comprobación:**
  1. Verifica que la impresora esté encendida, conectada a la misma red y tenga papel.
  2. Haz un ping o telnet desde el servidor: `telnet 192.168.1.200 9100`.
  3. Revisa la cola `printing.dlq` en RabbitMQ Dashboard (`http://localhost:15672`) y el canal de alertas `/topic/branches/{id}/printer-alerts`.

### Error 3: "Access Denied / 403 Forbidden"
* **Causa:** El token JWT carece del rol necesario o estás enviando un `X-Branch-Id` al que tu usuario no está asignado.
* **Comprobación:**
  1. Decodifica el token en [jwt.io](https://jwt.io) y revisa el campo `realm_access.roles`.
  2. Si eres administrador o mozo, verifica en la tabla `user_branch_access` que tu usuario tenga permiso en esa sucursal.
