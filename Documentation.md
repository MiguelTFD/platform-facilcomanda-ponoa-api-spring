# FacilComanda Ponoa API - Especificación Completa del Backend para Front-End & Agentes IA

> **Versión de la API:** `v1`  
> **Protocolo:** REST (HTTP/1.1 y HTTP/2) + WebSockets (STOMP sobre SockJS)  
> **Formato de intercambio:** `application/json;charset=UTF-8`  
> **Estándar de Errores:** RFC 7807 (`application/problem+json`)  
> **Seguridad:** OAuth2 Resource Server con tokens Bearer JWT (Keycloak)  

---

## 📑 Tabla de Contenidos

1. [Convenciones Globales, Headers y Seguridad](#1-convenciones-globales-headers-y-seguridad)
2. [Roles de Usuario y Permisos (RBAC)](#2-roles-de-usuario-y-permisos-rbac)
3. [Módulo 1: Autenticación y Usuarios (`/api/v1/auth`, `/api/v1/users`, `/api/v1/profiles`)](#3-módulo-1-autenticación-y-usuarios)
4. [Módulo 2: Sucursales (`/api/v1/branches`)](#4-módulo-2-sucursales)
5. [Módulo 3: Salones y Mesas (`/api/v1/floors`, `/api/v1/tables`)](#5-módulo-3-salones-y-mesas)
6. [Módulo 4: Categorías y Marcas (`/api/v1/categories`, `/api/v1/catalog/brands`)](#6-módulo-4-categorías-y-marcas)
7. [Módulo 5: Catálogo de Productos e Inventario Multi-Sucursal (`/api/v1/catalog`, `/api/v1/inventory`)](#7-módulo-5-catálogo-de-productos-e-inventario-multi-sucursal)
8. [Módulo 6: Pedidos y Pre-Cuenta (`/api/v1/orders`)](#8-módulo-6-pedidos-y-pre-cuenta)
9. [Módulo 7: Cocina y KDS (`/api/v1/kitchen/tickets`)](#9-módulo-7-cocina-y-kds)
10. [Módulo 8: Clientes y Facturación Electrónica (`/api/v1/customers`, `/api/v1/invoices`)](#10-módulo-8-clientes-y-facturación-electrónica)
11. [Módulo 9: Cajas, Turnos y Corte Z (`/api/v1/cash`)](#11-módulo-9-cajas-turnos-y-corte-z)
12. [Módulo 10: Gastos Operativos (`/api/v1/expenses`)](#12-módulo-10-gastos-operativos)
13. [Módulo 11: Reportes Agregados (`/api/v1/reports`)](#13-módulo-11-reportes-agregados)
14. [Módulo 12: Impresoras Térmicas y Hardware POS (`/api/v1/printers`)](#14-módulo-12-impresoras-térmicas-y-hardware-pos)
15. [Módulo 13: Eventos en Tiempo Real (WebSockets / STOMP)](#15-módulo-13-eventos-en-tiempo-real-websockets--stomp)
16. [Manejo de Errores Estándar (RFC 7807 ProblemDetail)](#16-manejo-de-errores-estándar-rfc-7807-problemdetail)
17. [Documentación Interactiva (OpenAPI 3.0 & Swagger UI)](#17-documentación-interactiva-openapi-30--swagger-ui)
18. [Guía Rápida para el Agente IA de Front-End](#18-guía-rápida-para-el-agente-ia-de-front-end)

---

## 1. Convenciones Globales, Headers y Seguridad

### Headers HTTP Requeridos en las Solicitudes

| Header | Tipo | Requerido | Descripción / Regla |
| :--- | :--- | :---: | :--- |
| `Authorization` | `String` | **SÍ\*** | Token Bearer JWT: `Bearer <token_keycloak>`. (\*No requerido en `/api/v1/auth/**`). |
| `X-Branch-Id` | `Long` (numérico) | **SÍ\*\*** | ID de la sucursal activa. (\*\*Obligatorio si el usuario tiene rol `EMPRESA` o no tiene sucursal fija en el token). |
| `Idempotency-Key`| `String` (UUID / hash)| **SÍ\*\*\*** | Clave de idempotencia única. (\*\*\*Obligatorio al crear pedidos `POST /api/v1/orders`). |
| `Content-Type` | `String` | **SÍ** | `application/json` en todas las peticiones `POST`, `PUT`, `PATCH`. |

### Respuestas HTTP Comunes
* `200 OK`: Petición exitosa con cuerpo de retorno.
* `201 Created`: Recurso creado exitosamente. La cabecera `Location` contiene la URI del nuevo recurso.
* `202 Accepted`: Acción asíncrona aceptada (ej. solicitud de impresión a hardware o reimpresión).
* `204 No Content`: Modificación, desactivación o eliminación lógica exitosa sin cuerpo.
* `400 Bad Request`: Error de validación de campos con desglose RFC 7807.
* `401 Unauthorized`: Token ausente, expirado o credenciales erróneas en login.
* `403 Forbidden`: El usuario no posee el rol requerido o intenta acceder a otra sucursal.
* `404 Not Found`: Recurso no encontrado.
* `409 Conflict`: Conflicto con regla de negocio (ej. mesa ocupada, turno ya abierto, nombre duplicado).
* `422 Unprocessable Entity`: Stock insuficiente para la cantidad solicitada.
* `429 Too Many Requests`: Límite de consultas excedido (Rate Limiting de DNI/RUC con header `Retry-After`).
* `502 Bad Gateway`: Impresora térmica fuera de línea o inalcanzable.
* `500 Internal Server Error`: Error no controlado del servidor con `errorId` para soporte.

---

## 2. Roles de Usuario y Permisos (RBAC)

Los roles provienen del claim `realm_access.roles` del token JWT emitido por Keycloak:

| Rol | Alcance en el Sistema |
| :--- | :--- |
| `EMPRESA` | Dueño o directivo general de la franquicia. Acceso irrestricto a todas las sucursales, creación y edición de sucursales, usuarios administradores y reportes agregados globales. |
| `ADMINISTRADOR` | Gerente de una sucursal específica. Configuración de pisos, mesas, impresoras, inventario, precios, gastos y personal de la sucursal. |
| `CAJERO` | Punto de venta (POS). Apertura y cierre de caja (Corte Z), cobro de pedidos, emisión de comprobantes, registro de gastos y movimientos. |
| `MESERO` | Salón. Creación de pedidos, adición de platos, solicitud de pre-cuenta y visualización del mapa de mesas. |
| `COCINERO` | Cocina y barra. Visualización de comandas en KDS, cambio de estado de preparación y reimpresión de comandas. |

---

## 3. Módulo 1: Autenticación y Usuarios

### 3.1. Proxy de Inicio de Sesión (Login)
Permite a cualquier frontend autenticar al usuario enviando email y contraseña a Spring Boot, evitando llamadas directas a Keycloak y problemas de CORS.
* **Método:** `POST`
* **Ruta:** `/api/v1/auth/login`
* **Autenticación:** Pública (No requiere token).
* **Body:**
```json
{
  "email": "cajero.norte@facilcomanda.com",
  "password": "password123"
}
```
* **Respuesta (`200 OK`):**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsIn...",
  "token_type": "Bearer",
  "expires_in": 300,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsIn...",
  "refresh_expires_in": 1800,
  "scope": "openid email profile"
}
```

### 3.2. Refrescar Token de Acceso
Intercambia un `refresh_token` por un nuevo `access_token` válido.
* **Método:** `POST`
* **Ruta:** `/api/v1/auth/refresh`
* **Autenticación:** Pública (No requiere token).
* **Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsIn..."
}
```
* **Respuesta (`200 OK`):** Mismo formato que login.

### 3.3. Sincronizar Perfil Propio
* **Método:** `POST`
* **Ruta:** `/api/v1/profiles/sync`
* **Roles:** Cualquier usuario autenticado.
* **Respuesta (`200 OK`)**

### 3.4. Obtener Perfil Propio
* **Método:** `GET`
* **Ruta:** `/api/v1/users/me` (o alternativamente `/api/v1/profiles/me`)
* **Roles:** Cualquier usuario autenticado.
* **Respuesta (`200 OK`):**
```json
{
  "id": "e98e2197-28d8-4b71-97b7-785bc03cbbf6",
  "email": "cajero.norte@facilcomanda.com",
  "firstName": "Carlos",
  "lastName": "Mendoza",
  "phoneNumber": "987654321",
  "role": "CAJERO",
  "branchId": 1,
  "isActive": true,
  "createdAt": "2026-10-14T02:00:00Z"
}
```

### 3.5. Crear Administrador de Sucursal (Soporta Múltiples Sucursales y Roles)
* **Método:** `POST`
* **Ruta:** `/api/v1/users/admin`
* **Roles:** `EMPRESA`
* **Body:**
```json
{
  "email": "admin.miraflores@facilcomanda.com",
  "password": "PasswordAdmin123!",
  "firstName": "Roberto",
  "lastName": "Pérez",
  "phoneNumber": "999888777",
  "branchId": 2,
  "branchIds": [1, 2],
  "roles": ["ADMINISTRADOR", "CAJERO"]
}
```
* **Aprovisionamiento Automático:** Si se envía `password`, el backend crea automáticamente el usuario y contraseña en Keycloak con los roles indicados y asigna todas las sucursales en base de datos (`app_user_branches`) y en atributos de Keycloak (`branch_id` y `branches`).
* **Respuesta (`201 Created`):** Header `Location: /api/v1/users/{id}`.

### 3.6. Crear Personal Operativo / Staff (Soporta Múltiples Sucursales y Roles)
* **Método:** `POST`
* **Ruta:** `/api/v1/users/staff`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "email": "mozo1@facilcomanda.com",
  "password": "PasswordMozo123!",
  "firstName": "Juan",
  "lastName": "Gómez",
  "phoneNumber": "912345678",
  "role": "MESERO",
  "roles": ["MESERO", "CAJERO"],
  "branchId": 1,
  "branchIds": [1, 2]
}
```
* **Restricciones:** Los roles permitidos son `MESERO`, `COCINERO` y `CAJERO` (se puede enviar `role` individual o `roles` múltiples). Se puede asignar una sucursal (`branchId`) o múltiples sucursales (`branchIds`).
* **Aprovisionamiento Automático:** Si se envía `password`, el backend crea automáticamente el usuario y contraseña en Keycloak con todos los roles y sucursales asignados.
* **Respuesta (`201 Created`):** Header `Location: /api/v1/users/{id}`.

### 3.7. Listar Usuarios
* **Método:** `GET`
* **Ruta:** `/api/v1/users?branchId={id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Respuesta (`200 OK`):** Array de `UserResponse` (incluye `role`, `roles`, `branchId` y `branchIds`).

### 3.8. Obtener Detalle de Usuario
* **Método:** `GET`
* **Ruta:** `/api/v1/users/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Respuesta (`200 OK`):** Objeto `UserResponse`.

### 3.9. Actualizar Usuario (Datos, Contraseña, Roles y Sucursales)
* **Método:** `PUT`
* **Ruta:** `/api/v1/users/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "firstName": "Juan Carlos",
  "lastName": "Gómez Silva",
  "phoneNumber": "912345679",
  "password": "NuevaClaveSegura123!",
  "role": "MESERO",
  "roles": ["MESERO", "CAJERO"],
  "branchId": 1,
  "branchIds": [1, 2],
  "isActive": true
}
```
> **Nota:** El campo `password` es opcional; si se envía con valor no vacío, el backend sincroniza inmediatamente el cambio de contraseña con Keycloak (`PUT /admin/realms/{realm}/users/{id}/reset-password`).
* **Respuesta (`204 No Content`)**

### 3.9.1. Asignar Múltiples Sucursales a un Usuario
* **Método:** `PUT`
* **Ruta:** `/api/v1/users/{id}/branches`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "branchIds": [1, 2, 3]
}
```
* **Respuesta (`204 No Content`)**

### 3.9.2. Asignar Múltiples Roles a un Usuario
* **Método:** `PUT`
* **Ruta:** `/api/v1/users/{id}/roles`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "roles": ["MESERO", "CAJERO"]
}
```
* **Respuesta (`204 No Content`)**

### 3.10. Desactivar Usuario (Soft-Delete)
* **Método:** `PATCH /api/v1/users/{id}/deactivate` o `DELETE /api/v1/users/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Respuesta (`204 No Content`)**

---

## 4. Módulo 2: Sucursales

Base URL: `/api/v1/branches`

### 4.1. Crear Sucursal
* **Método:** `POST`
* **Ruta:** `/api/v1/branches`
* **Roles:** `EMPRESA`
* **Body:**
```json
{
  "name": "Sucursal San Isidro",
  "code": "SUC-002",
  "address": "Av. Javier Prado 1234",
  "phoneNumber": "+5114567890"
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/branches/{id}`.

### 4.2. Listar Sucursales Activas
* **Método:** `GET`
* **Ruta:** `/api/v1/branches`
* **Roles:** Cualquier usuario autenticado.
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 1,
    "name": "Sucursal Central",
    "code": "SUC-001",
    "address": "Av. Larco 456",
    "phoneNumber": "+5112223344",
    "isActive": true
  }
]
```

### 4.3. Obtener Detalle de Sucursal
* **Método:** `GET`
* **Ruta:** `/api/v1/branches/{id}`
* **Roles:** Cualquier usuario autenticado.
* **Respuesta (`200 OK`)**

### 4.4. Actualizar Sucursal
* **Método:** `PUT`
* **Ruta:** `/api/v1/branches/{id}`
* **Roles:** `EMPRESA`
* **Body:**
```json
{
  "name": "Sucursal San Isidro Premium",
  "address": "Av. Javier Prado Oeste 1234, San Isidro",
  "phoneNumber": "+5114567899",
  "isActive": true
}
```
* **Respuesta (`204 No Content`)**

---

## 5. Módulo 3: Pisos, Zonas y Mesas

Base URL: `/api/v1`

> **Jerarquía espacial:** `Sucursal (Branch)` -> `Piso / Salón (Floor)` -> `Zona (Zone: ej. Juegos, Karaoke, VIP)` -> `Mesa (Table)`.
> * Un **Piso** puede tener `N` **Zonas**, y cada **Zona** pertenece a un único **Piso**.
> * Una **Zona** puede tener `N` **Mesas**, y una **Mesa** puede estar asignada a una **Zona** (además de su Piso).

### 5.1. Crear Piso / Salón
* **Método:** `POST`
* **Ruta:** `/api/v1/floors`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "name": "Segundo Piso",
  "description": "Nivel de entretenimiento y salones temáticos"
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/floors/{id}`.

### 5.2. Listar Pisos / Salones
* **Método:** `GET`
* **Ruta:** `/api/v1/floors`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 10,
    "branchId": 1,
    "name": "Segundo Piso",
    "description": "Nivel de entretenimiento y salones temáticos"
  }
]
```

### 5.3. Actualizar Piso / Salón
* **Método:** `PUT`
* **Ruta:** `/api/v1/floors/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "name": "Terraza Lounge & Bar",
  "description": "Área techada con música ambiental"
}
```
* **Respuesta (`204 No Content`)**

### 5.4. Desactivar Piso / Salón
* **Método:** `PATCH /api/v1/floors/{id}/deactivate` o `DELETE /api/v1/floors/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Regla:** Falla con `409 Conflict` si alguna mesa en el piso está ocupada. Al desactivar el piso se desactivan también sus zonas y mesas asociadas.
* **Respuesta (`204 No Content`)**

### 5.5. Crear Zona dentro de un Piso
* **Método:** `POST`
* **Ruta:** `/api/v1/zones`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "floorId": 10,
  "name": "Karaoke",
  "description": "Zona con escenario y cabinas de canto"
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/zones/{id}`.

### 5.6. Listar Zonas de la Sucursal (o por Piso)
* **Método:** `GET`
* **Ruta:** `/api/v1/zones?floorId=10` (parámetro `floorId` opcional)
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 50,
    "branchId": 1,
    "floorId": 10,
    "name": "Karaoke",
    "description": "Zona con escenario y cabinas de canto"
  },
  {
    "id": 51,
    "branchId": 1,
    "floorId": 10,
    "name": "Juegos",
    "description": "Zona recreativa y arcade"
  }
]
```

### 5.7. Obtener Detalle de una Zona
* **Método:** `GET`
* **Ruta:** `/api/v1/zones/{id}`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):** Objeto `ZoneResponse`.

### 5.8. Actualizar Zona
* **Método:** `PUT`
* **Ruta:** `/api/v1/zones/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "floorId": 10,
  "name": "Karaoke VIP",
  "description": "Zona remodelada con sonido envolvente"
}
```
* **Nota:** Si se cambia el `floorId` de una zona a otro piso de la misma sucursal, todas las mesas asignadas a esa zona actualizan automáticamente su `floorId` para mantener consistencia jerárquica.
* **Respuesta (`204 No Content`)**

### 5.9. Desactivar / Eliminar Zona
* **Método:** `PATCH /api/v1/zones/{id}/deactivate` o `DELETE /api/v1/zones/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Regla:** Falla con `409 Conflict` si alguna mesa perteneciente a la zona se encuentra actualmente en estado `OCCUPIED`. Si todas están libres, desactiva (soft-delete) la zona y sus mesas.
* **Respuesta (`204 No Content`)**

### 5.10. Crear Mesa (Asignada a Zona y Piso)
* **Método:** `POST`
* **Ruta:** `/api/v1/tables`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "floorId": 10,
  "zoneId": 50,
  "name": "Mesa 101",
  "description": "Frente a la pantalla principal de Karaoke",
  "chairs": 4
}
```
* **Notas:**
  * Si se envía `zoneId`, el backend valida que dicha zona pertenezca a la sucursal y al `floorId` indicado. Si se envía `zoneId` y se omite `floorId`, el backend deduce automáticamente el `floorId` a partir de la zona.
* **Respuesta (`201 Created`):** Header `Location: /api/v1/tables/{id}`.

### 5.11. Listar Mesas de la Sucursal (Filtro por Piso y/o Zona)
* **Método:** `GET`
* **Ruta:** `/api/v1/tables?floorId=10&zoneId=50` (parámetros `floorId` y `zoneId` opcionales)
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 501,
    "branchId": 1,
    "floorId": 10,
    "zoneId": 50,
    "name": "Mesa 101",
    "description": "Frente a la pantalla principal de Karaoke",
    "state": "AVAILABLE",
    "chairs": 4
  }
]
```
> **Valores posibles de `state` de mesa:** `AVAILABLE` (Libre), `OCCUPIED` (Ocupada con comanda), `BILL_REQUESTED` (Pre-cuenta solicitada), `OUT_OF_SERVICE` (Fuera de servicio / mantenimiento).

### 5.12. Actualizar Mesa
* **Método:** `PUT`
* **Ruta:** `/api/v1/tables/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "floorId": 10,
  "zoneId": 50,
  "name": "Mesa 101 VIP",
  "description": "Mesa esquinera cómoda en Karaoke",
  "chairs": 6
}
```
* **Respuesta (`204 No Content`)**

### 5.13. Cambiar Estado de la Mesa
* **Método:** `PATCH`
* **Ruta:** `/api/v1/tables/{id}/state`
* **Roles:** `MESERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:** `{ "state": "OUT_OF_SERVICE" }`
* **Respuesta (`204 No Content`)**

### 5.14. Desactivar / Eliminar Mesa
* **Método:** `PATCH /api/v1/tables/{id}/deactivate` o `DELETE /api/v1/tables/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`204 No Content`)**

---

## 6. Módulo 4: Categorías y Marcas

Base URL: `/api/v1/categories` y `/api/v1/catalog/brands`

### 6.1. Listar Categorías
* **Método:** `GET`
* **Ruta:** `/api/v1/categories`
* **Roles:** Cualquier usuario autenticado.
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 1,
    "name": "Platos de Fondo",
    "description": "Platos fuertes principales",
    "isActive": true
  },
  {
    "id": 2,
    "name": "Bebidas y Cócteles",
    "description": "Bebidas frías, calientes y tragos",
    "isActive": true
  }
]
```

### 6.2. Crear Categoría
* **Método:** `POST`
* **Ruta:** `/api/v1/categories`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "name": "Entradas Criollas",
  "description": "Causas, papas rellenas y anticuchos"
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/categories/{id}`.

### 6.3. Actualizar Categoría
* **Método:** `PUT`
* **Ruta:** `/api/v1/categories/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "name": "Entradas y Piques",
  "description": "Para compartir en mesa"
}
```
* **Respuesta (`204 No Content`)**

### 6.4. Desactivar Categoría (Soft-Delete)
* **Método:** `DELETE`
* **Ruta:** `/api/v1/categories/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Respuesta (`204 No Content`)**

### 6.5. Listar Marcas
* **Método:** `GET`
* **Ruta:** `/api/v1/catalog/brands`
* **Roles:** Cualquier usuario autenticado.
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 1,
    "name": "FacilComanda Gourmet",
    "description": "Línea premium de la casa",
    "isActive": true
  }
]
```

### 6.6. Crear Marca
* **Método:** `POST`
* **Ruta:** `/api/v1/catalog/brands`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "name": "Cusqueña",
  "description": "Cervezas industriales premium"
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/catalog/brands/{id}`.

### 6.7. Actualizar Marca
* **Método:** `PUT`
* **Ruta:** `/api/v1/catalog/brands/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "name": "Cusqueña Cerveza",
  "description": "Variedades Trigo, Negra y Dorada"
}
```
* **Respuesta (`204 No Content`)**

### 6.8. Desactivar Marca (Soft-Delete)
* **Método:** `DELETE`
* **Ruta:** `/api/v1/catalog/brands/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Respuesta (`204 No Content`)**

---

## 7. Módulo 5: Catálogo de Productos e Inventario Multi-Sucursal

Base URL: `/api/v1/catalog` e `/api/v1/inventory`

### 7.1. Crear Producto en Catálogo Maestro (Destino de Impresión por Producto y Control de Stock por SKU)
* **Método:** `POST`
* **Ruta:** `/api/v1/catalog/products`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "brandId": 1,
  "name": "Lomo Saltado Clásico",
  "baseDescription": "Trozos de lomo fino salteados al wok con cebolla, tomate y papas fritas",
  "productionArea": "COCINA",
  "categoryIds": [1],
  "skus": [
    {
      "skuCode": "LOMO-PERS-01",
      "presentationName": "Porción Personal",
      "currentPrice": 48.00,
      "requiresStock": false
    }
  ]
}
```
* **Notas:**
  * `productionArea`: Define el destino de impresión de cada producto de manera individual (ej. `COCINA`, `BARRA`, `PARRILLA`, `HORNO`), independientemente de su categoría.
  * `requiresStock`: Booleano opcional en cada SKU (por defecto `true`). Si se envía `false` (ej. platos preparados a pedido), al crear órdenes, adicionar ítems o cancelar órdenes no se exigirá ni descontará stock de inventario para ese SKU.
* **Respuesta (`201 Created`):** Header `Location: /api/v1/catalog/products/{id}`.

### 7.2. Listar Resumen del Catálogo
* **Método:** `GET`
* **Ruta:** `/api/v1/catalog/products?pageNumber=0&pageSize=20`
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 101,
    "name": "Lomo Saltado Clásico",
    "brandName": "FacilComanda Gourmet",
    "startingPrice": 48.00,
    "productionArea": "COCINA"
  }
]
```

### 7.3. Obtener Detalle de un Producto con Presentaciones (SKUs)
* **Método:** `GET`
* **Ruta:** `/api/v1/catalog/products/{id}`
* **Respuesta (`200 OK`):**
```json
{
  "id": 101,
  "brandId": 1,
  "brandName": "FacilComanda Gourmet",
  "name": "Lomo Saltado Clásico",
  "baseDescription": "Trozos de lomo fino salteados al wok con cebolla, tomate y papas fritas",
  "productionArea": "COCINA",
  "isActive": true,
  "categories": [
    {
      "id": 1,
      "name": "Platos de Fondo"
    }
  ],
  "skus": [
    {
      "id": 1,
      "skuCode": "LOMO-PERS-01",
      "presentationName": "Porción Personal",
      "currentPrice": 48.00,
      "isActive": true,
      "requiresStock": false
    }
  ]
}
```

### 7.4. Editar Producto y Presentaciones (SKUs)
* **Método:** `PUT`
* **Ruta:** `/api/v1/catalog/products/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "brandId": 1,
  "name": "Lomo Saltado Clásico Angus",
  "baseDescription": "Carne Angus certificada salteada al wok",
  "productionArea": "COCINA",
  "categoryIds": [1],
  "skus": [
    {
      "id": 1,
      "skuCode": "LOMO-PERS-01",
      "presentationName": "Porción Personal",
      "currentPrice": 52.00,
      "isActive": true,
      "requiresStock": false
    }
  ]
}
```
* **Respuesta (`204 No Content`)**

### 7.4.1. Cambiar Destino de Impresión Individual de un Producto
* **Método:** `PATCH`
* **Ruta:** `/api/v1/catalog/products/{id}/production-area`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "productionArea": "PARRILLA"
}
```
* **Respuesta (`204 No Content`)**

### 7.5. Desactivar Producto (Soft-Delete)
* **Método:** `DELETE`
* **Ruta:** `/api/v1/catalog/products/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Respuesta (`204 No Content`)**

### 7.6. Listar Menú Enriquecido de la Sucursal
Devuelve los productos y SKUs activos con sus nombres, códigos, precios vigentes (o sobreescritos por sucursal), disponibilidad, destino de impresión (`productionArea`) y si requiere stock (`requiresStock`).
* **Método:** `GET`
* **Ruta:** `/api/v1/catalog/branches/products` o `/api/v1/catalog/branches/{branchId}/products`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 15,
    "branchId": 1,
    "skuId": 1,
    "skuCode": "LOMO-PERS-01",
    "productName": "Lomo Saltado Clásico",
    "presentationName": "Porción Personal",
    "price": 52.00,
    "isAvailable": true,
    "productionArea": "COCINA",
    "requiresStock": false
  }
]
```

### 7.7. Configurar Precio y Disponibilidad por Sucursal
* **Método:** `PUT`
* **Ruta:** `/api/v1/catalog/branches/products`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "skuId": 1,
  "price": 52.00,
  "isAvailable": true
}
```
* **Respuesta (`200 OK`)**

### 7.8. Ajustar Stock de SKU
* **Método:** `POST`
* **Ruta:** `/api/v1/inventory`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:** `{ "skuId": 1, "quantity": 50 }`
* **Respuesta (`200 OK`)**

### 7.9. Consultar Stock de una Sucursal
* **Método:** `GET`
* **Ruta:** `/api/v1/inventory` o `/api/v1/inventory/skus/{skuId}`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`)**

---

## 8. Módulo 6: Pedidos, Atención por Ítem y Pre-Cuenta

Base URL: `/api/v1/orders`

### Reglas de Negocio Clave de Mesas Multi-Orden y Estados por Ítem
1. **Múltiples Órdenes Abiertas por Mesa:** Una mesa puede tener `N` órdenes abiertas simultáneamente (ej. segundas o terceras rondas independientes).
2. **Ventana de Espera de 10s desde la Última Orden `PENDING`:** Cada nueva orden asignada a una mesa valida (mediante índice parcial optimizado `idx_customer_orders_table_pending_created` y bloqueo pesimista `SELECT ... FOR UPDATE` sobre la fila de la mesa) que hayan transcurrido **al menos 10 segundos** desde la última orden con estado `PENDING` en esa misma mesa. Si no existe ninguna orden en estado `PENDING` para la mesa (o ya pasaron $\ge 10\text{ s}$), la orden se crea instantáneamente.
3. **Estado de Atención Individual por Ítem (`status`):**
   * `PENDING`: Estado inicial al crearse el ítem (requiere atención).
   * `COMPLETED`: Ítem atendido/entregado en mesa (marcado exclusivamente por el rol `MESERO`).
   * `MODIFIED`: Si un ítem se edita después de creado (cantidad o comentarios), pasa automáticamente a `MODIFIED` y vuelve a requerir atención.
   * `CANCELLED`: Ítem anulado individualmente (su subtotal pasa a `0.00` y repone stock si `requiresStock = true`).
4. **Recálculo Automático y Transaccional del Estado de la Orden:**
   * La orden pasa a `COMPLETED` **únicamente cuando TODOS sus ítems activos** (`status != 'CANCELLED'`) están en `COMPLETED`.
   * Si posteriormente se agrega un ítem nuevo o se modifica un ítem existente (`MODIFIED`), la orden regresa automáticamente a `PENDING` dentro de la misma transacción.
5. **Liberación de Mesa (`AVAILABLE`):** La mesa vuelve al estado `AVAILABLE` **únicamente cuando TODAS sus órdenes** están en estado `CANCELLED` o `PAID`.

### 8.1. Crear Pedido (Comanda Inicial o Ronda Adicional en Mesa)
Crea la orden, adquiere bloqueo pesimista sobre la mesa para serializar creaciones concurrentes, verifica la regla de 10 segundos sobre órdenes `PENDING` de la misma mesa, ocupa la mesa si estaba `AVAILABLE` (o mantiene `OCCUPIED` si ya tenía órdenes abiertas), decrementa el stock de los SKUs que lo requieran (`requiresStock = true`) y emite el evento de impresión. Soporta resolución idempotente mediante `Idempotency-Key` para evitar duplicados ante reintentos de red.
* **Método:** `POST`
* **Ruta:** `/api/v1/orders`
* **Roles:** `MESERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers Requeridos:**
  * `X-Branch-Id: 1`
  * `Idempotency-Key: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d` (Previene duplicación de pedidos en reintentos)
* **Body:**
```json
{
  "tableId": 501,
  "orderType": "DINE_IN",
  "notes": "Cliente alérgico a los mariscos",
  "items": [
    {
      "skuId": 1,
      "quantity": 2,
      "comments": "Término medio, papas bien crocantes"
    }
  ]
}
```
* **Restricciones:** `orderType` puede ser `DINE_IN` (En salón), `TAKE_AWAY` (Para llevar) o `DELIVERY`.
* **Respuesta (`201 Created`):** Header `Location: /api/v1/orders/{orderId}`.

### 8.2. Listar Pedidos de la Sucursal (Enriquecido)
Devuelve la lista de órdenes de la sucursal ordenadas de más reciente a más antigua (`id DESC`), incluyendo fechas exactas (`createdAt`, `updatedAt`), indicador de modificación (`isModified`) y el `status` individual de cada ítem.
* **Método:** `GET`
* **Ruta:** `/api/v1/orders` (o con filtro: `/api/v1/orders?status=PENDING`)
* **Headers:** `X-Branch-Id: 1`
* **Parámetros de consulta (Query Params):**
  * `status` (opcional, `String`): Filtra por estado de la orden (ej. `PENDING`, `COMPLETED`, `CANCELLED`). Si se omite, devuelve todas las órdenes de la sucursal.
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 1050,
    "branchId": 1,
    "appUserId": "e98e2197-28d8-4b71-97b7-785bc03cbbf6",
    "idempotencyKey": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "tableId": 501,
    "tableName": "Mesa 101",
    "orderType": "DINE_IN",
    "notes": "Cliente alérgico a los mariscos",
    "totalAmount": 104.00,
    "status": "PENDING",
    "cancelledBy": null,
    "cancellationReason": null,
    "cancelledAt": null,
    "createdAt": "2026-10-25T00:15:30Z",
    "updatedAt": "2026-10-25T00:15:30Z",
    "isModified": false,
    "items": [
      {
        "skuId": 1,
        "name": "Porción Personal",
        "quantity": 2,
        "unitPrice": 52.00,
        "subtotal": 104.00,
        "comments": "Término medio, papas bien crocantes",
        "status": "PENDING"
      }
    ]
  }
]
```

### 8.3. Listar Todas las Órdenes Activas de una Mesa
Devuelve todas las órdenes abiertas (no `PAID` ni `CANCELLED`) asociadas a una mesa específica.
* **Método:** `GET`
* **Ruta:** `/api/v1/orders/tables/{tableId}`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):** Lista de objetos `OrderResponse` activos en la mesa.

### 8.4. Obtener Detalle de un Pedido (Enriquecido)
* **Método:** `GET`
* **Ruta:** `/api/v1/orders/{id}`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):** Objeto `OrderResponse` individual.

### 8.5. Adicionar Ítems a una Orden Existente
Permite agregar platos o bebidas adicionales a una orden en estado `PENDING` o `COMPLETED`. Si el SKU ya existía en la orden, se acumula la cantidad y el ítem pasa a `MODIFIED`; si es un SKU nuevo, ingresa con estado `PENDING`. La orden recalcula su total y vuelve a estado `PENDING` en la misma transacción.
* **Método:** `POST`
* **Ruta:** `/api/v1/orders/{id}/items`
* **Roles:** `MESERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "additionNotes": "Ronda adicional de bebidas",
  "items": [
    {
      "skuId": 5,
      "quantity": 2,
      "comments": "Sin hielo"
    }
  ]
}
```
* **Respuesta (`200 OK`)**

### 8.6. Editar un Ítem de la Orden (`MODIFIED` ➔ Orden vuelve a `PENDING`)
Modifica la cantidad y/o comentarios de un ítem existente en la orden, ajusta el stock por la diferencia de cantidad (si `requiresStock = true`), cambia el estado del ítem a `MODIFIED` y recalcula el estado de la orden (regresando a `PENDING`) en la misma transacción.
* **Método:** `PUT` o `PATCH`
* **Ruta:** `/api/v1/orders/{id}/items/{skuId}`
* **Roles:** `MESERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "quantity": 3,
  "comments": "Con papas extra crocantes"
}
```
* **Respuesta (`200 OK`)**

### 8.7. Marcar un Ítem como Atendido (`COMPLETED`) — Exclusivo `MESERO`
Permite al mesero marcar un ítem del detalle como atendido (`COMPLETED`). En la misma transacción se verifica si todos los ítems activos de la orden están en `COMPLETED`; de ser así, la orden pasa automáticamente a estado `COMPLETED`.
* **Método:** `POST` `/api/v1/orders/{id}/items/{skuId}/attend` (o `PATCH` `/api/v1/orders/{id}/items/{skuId}/status` con body `{ "status": "COMPLETED" }`)
* **Roles:** Exclusivamente `MESERO`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`)**

### 8.8. Cancelar un Ítem Individual de la Orden (`CANCELLED`)
Marca el ítem con estado `CANCELLED`, repone su stock en inventario (si `requiresStock = true`) y recalcula el total y el estado de la orden en la misma transacción.
* **Método:** `DELETE`
* **Ruta:** `/api/v1/orders/{id}/items/{skuId}`
* **Roles:** `MESERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`)**

### 8.9. Cancelar Pedido Completo
Cancela la orden, devuelve el stock de los ítems activos al inventario, cancela las comandas de cocina y libera la mesa a `AVAILABLE` **solo si no quedan otras órdenes abiertas en dicha mesa**.
* **Método:** `POST`
* **Ruta:** `/api/v1/orders/{id}/cancel`
* **Roles:** `MESERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "reason": "Cliente se retiró por urgencia médica personal"
}
```
* **Respuesta (`200 OK`)**

### 8.10. Consultar Pre-Cuenta (Resumen de Cuenta)
Calcula el subtotal (base gravada), IGV (18%), propina sugerida (10%) y total acumulado.
* **Método:** `GET`
* **Ruta:** `/api/v1/orders/{id}/pre-check` o `/api/v1/orders/tables/{tableId}/pre-check`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
{
  "orderId": 1050,
  "branchId": 1,
  "tableId": 501,
  "tableName": "Mesa 101",
  "orderType": "DINE_IN",
  "subtotal": 88.14,
  "taxAmount": 15.86,
  "totalAmount": 104.00,
  "suggestedTip10": 10.40,
  "items": [
    {
      "skuId": 1,
      "name": "Porción Personal",
      "quantity": 2,
      "unitPrice": 52.00,
      "lineTotal": 104.00,
      "comments": "Término medio"
    }
  ]
}
```

### 8.7. Mandar a Imprimir Ticket de Pre-Cuenta
Cambia el estado de la mesa a `BILL_REQUESTED` y despacha el ticket físico a la impresora térmica asignada o a la impresora de caja.
* **Método:** `POST`
* **Ruta:** `/api/v1/orders/{id}/pre-check/print` o `/api/v1/orders/tables/{tableId}/pre-check/print?targetPrinterId={id}`
* **Roles:** `MESERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`202 Accepted`)**

---

## 9. Módulo 7: Cocina y KDS

Base URL: `/api/v1/kitchen/tickets`

### 9.1. Listar Comandas Activas de Cocina (KDS)
* **Método:** `GET`
* **Ruta:** `/api/v1/kitchen/tickets`
* **Roles:** `COCINERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`, `MESERO`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):** Lista FIFO acotada (máx 100) de tickets de preparación en cocina.
```json
[
  {
    "id": 801,
    "branchId": 1,
    "orderId": 1050,
    "status": "PENDING",
    "notes": "Mesa: Mesa 5 (Terraza) | Notas: Sin cebolla",
    "createdAt": "2026-10-14T02:45:00Z"
  }
]
```
> **Valores válidos de `status`:** `PENDING`, `IN_PREPARATION`, `READY`, `DELIVERED`, `CANCELLED`.

### 9.2. Obtener Comanda de Cocina por ID
* **Método:** `GET`
* **Ruta:** `/api/v1/kitchen/tickets/{id}`
* **Roles:** `COCINERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`, `MESERO`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):** Detalle del `KitchenTicketResponse`.

### 9.3. Actualizar Estado de Preparación
* **Método:** `PATCH`
* **Ruta:** `/api/v1/kitchen/tickets/{id}/status`
* **Roles:** `COCINERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`, `MESERO`
* **Headers:** `X-Branch-Id: 1`
* **Body:** `{ "status": "IN_PREPARATION" }` o `{ "status": "READY" }`
* **Respuesta (`200 OK`):** Objeto `KitchenTicketResponse` actualizado. Transmite notificación WebSocket por `/topic/branches/{branchId}/kitchen`.

---

## 10. Módulo 8: Clientes y Facturación Electrónica

Base URL: `/api/v1/customers` e `/api/v1/invoices`

### 10.1. Listar Clientes de la Base de Datos (Tabla del Front-End)
Devuelve el listado de clientes activos registrados en el sistema, ordenados de forma descendente por creación (`id DESC`). Permite búsqueda rápida en tiempo real mediante el parámetro `query`.
* **Método:** `GET`
* **Ruta:** `/api/v1/customers` (o con búsqueda: `/api/v1/customers?query=Juan` o `/api/v1/customers?query=71234567`)
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`, `MESERO`
* **Parámetros de consulta (Query Params):**
  * `query` (opcional, `String`): Filtro de búsqueda por coincidencia parcial en nombre completo o número de documento.
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 1,
    "name": "JUAN PEREZ COTRINA",
    "identityDocumentType": "DNI",
    "identityDocumentNumber": "71234567",
    "phoneNumber": "987654321",
    "address": "AV. AREQUIPA 123, LIMA",
    "isActive": true,
    "createdAt": "2026-10-14T02:00:00Z",
    "updatedAt": "2026-10-14T02:00:00Z"
  }
]
```

### 10.2. Obtener Detalle de un Cliente ("Ojito" / Modal de Detalle)
Recupera la información completa de un cliente específico por su ID numérico.
* **Método:** `GET`
* **Ruta:** `/api/v1/customers/{id}`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`, `MESERO`
* **Respuesta (`200 OK`):**
```json
{
  "id": 1,
  "name": "JUAN PEREZ COTRINA",
  "identityDocumentType": "DNI",
  "identityDocumentNumber": "71234567",
  "phoneNumber": "987654321",
  "address": "AV. AREQUIPA 123, LIMA",
  "isActive": true,
  "createdAt": "2026-10-14T02:00:00Z",
  "updatedAt": "2026-10-14T02:00:00Z"
}
```

### 10.3. Registrar o Actualizar Cliente Manualmente
Registra un nuevo cliente o actualiza sus datos si ya existe el tipo y número de documento (upsert atómico).
* **Método:** `POST`
* **Ruta:** `/api/v1/customers`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "name": "MARIA LOPEZ VEGA",
  "identityDocumentType": "DNI",
  "identityDocumentNumber": "45678901",
  "phoneNumber": "912345678",
  "address": "CALLE REAL 456, LIMA"
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/customers/{id}` y cuerpo con el `CustomerResponse` creado.

### 10.4. Consulta Rápida de DNI / RUC (Lookup con Rate Limiting)
Consulta en vivo contra caché local o servicios externos (RENIEC / SUNAT) con protección anti-abuso.
* **Método:** `GET`
* **Ruta:** `/api/v1/customers/lookup?documentType=DNI&documentNumber=71234567`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`, `MESERO`
* **Respuesta (`200 OK`):**
```json
{
  "documentType": "DNI",
  "documentNumber": "71234567",
  "fullName": "JUAN PEREZ COTRINA",
  "address": "AV. AREQUIPA 123, LIMA",
  "source": "EXTERNAL_CACHE"
}
```

### 10.5. Cobrar Orden y Emitir Comprobante (Boleta / Factura / Ticket)
* **Método:** `POST`
* **Ruta:** `/api/v1/invoices/pay`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "orderId": 1050,
  "invoiceType": "BOLETA",
  "customerName": "JUAN PEREZ COTRINA",
  "customerDocType": "DNI",
  "customerDocNumber": "71234567",
  "customerAddress": "AV. AREQUIPA 123",
  "notes": "Consumo de restaurante",
  "payments": [
    {
      "method": "EFECTIVO",
      "amount": 50.00
    },
    {
      "method": "YAPE",
      "amount": 54.00,
      "reference": "OP-987123"
    }
  ]
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/invoices/{id}`
```json
{
  "id": 901,
  "branchId": 1,
  "cashShiftId": 10,
  "orderId": 1050,
  "customerId": 15,
  "customerName": "JUAN PEREZ COTRINA",
  "customerDocumentType": "DNI",
  "customerDocumentNumber": "71234567",
  "invoiceNumber": "B001-00000042",
  "invoiceType": "BOLETA",
  "orderTotal": 104.00,
  "amountPaid": 104.00,
  "changeAmount": 0.00,
  "cashierUserId": "127c2fc4-ec58-4e82-ad44-0f85db14beae",
  "cashierEmail": "cajero@facilcomanda.com",
  "restaurantTableId": 5,
  "restaurantTableName": "Mesa 5",
  "notes": "Consumo de restaurante",
  "paidAt": "2026-10-14T03:10:00Z",
  "payments": [
    {
      "id": 1,
      "method": "EFECTIVO",
      "amount": 50.00,
      "reference": null
    },
    {
      "id": 2,
      "method": "YAPE",
      "amount": 54.00,
      "reference": "OP-987123"
    }
  ]
}
```

### 10.6. Listar Comprobantes de Pago de la Sucursal
* **Método:** `GET`
* **Ruta:** `/api/v1/invoices`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):** Lista acotada (máx 100) de `InvoiceResponse` ordenados por fecha descendente.

### 10.7. Obtener Comprobante por ID
* **Método:** `GET`
* **Ruta:** `/api/v1/invoices/{id}`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):** Objeto `InvoiceResponse` con el detalle completo del comprobante y sus medios de pago.

### 10.8. Obtener Comprobante por ID de Orden
* **Método:** `GET`
* **Ruta:** `/api/v1/invoices/orders/{orderId}`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):** Objeto `InvoiceResponse` asociado a la orden especificada.

> [!NOTE]
> Para la **reimpresión física** de comprobantes de pago en ticketeras térmicas ESC/POS, consulte el endpoint del Módulo de Impresión en [§ 14.7. Reimpresión de Comprobante de Pago](#147-reimpresion-de-comprobante-de-pago) (`POST /api/v1/printers/receipts/reprint`).

---

## 11. Módulo 9: Cajas, Turnos y Corte Z

Base URL: `/api/v1/cash`

### 11.1. Crear Caja Registradora
* **Método:** `POST`
* **Ruta:** `/api/v1/cash/registers`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "name": "Caja Principal - Salón 1",
  "description": "Terminal punto de venta con gaveta eléctrica"
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/cash/registers/{id}`.

### 11.2. Listar Cajas Registradoras de la Sucursal
* **Método:** `GET`
* **Ruta:** `/api/v1/cash/registers`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 1,
    "branchId": 1,
    "name": "Caja Principal - Salón 1",
    "description": "Terminal punto de venta con gaveta eléctrica",
    "isActive": true,
    "createdAt": "2026-10-14T10:00:00Z"
  }
]
```

### 11.3. Actualizar Caja Registradora
* **Método:** `PUT`
* **Ruta:** `/api/v1/cash/registers/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "name": "Caja Principal - Barra",
  "description": "Reubicada a la barra principal",
  "isActive": true
}
```
* **Respuesta (`204 No Content`)**

### 11.4. Desactivar / Eliminar Caja Registradora
* **Método:** `DELETE`
* **Ruta:** `/api/v1/cash/registers/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Regla:** Desactivación lógica (soft-delete). No permite desactivar si la caja tiene un turno abierto activo.
* **Respuesta (`204 No Content`)**

### 11.5. Abrir Turno de Caja
* **Método:** `POST`
* **Ruta:** `/api/v1/cash/shifts/open`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "cashRegisterId": 1,
  "openingAmount": 150.00,
  "notes": "Fondo inicial para vuelto"
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/cash/shifts/{shiftId}`.

### 11.6. Registrar Movimiento Manual de Efectivo (Ingreso / Egreso)
* **Método:** `POST`
* **Ruta:** `/api/v1/cash/shifts/{shiftId}/movements`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "type": "PAYMENT_OUT",
  "amount": 30.00,
  "reason": "Pago a proveedor de hielo"
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/cash/shifts/{shiftId}/movements/{id}`.

### 11.7. Consultar Turno Actual del Cajero
* **Método:** `GET`
* **Ruta:** `/api/v1/cash/shifts/current`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):** Objeto `CashShiftResponse`.

### 11.8. Consultar Turno por ID
* **Método:** `GET`
* **Ruta:** `/api/v1/cash/shifts/{shiftId}`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):** Objeto `CashShiftResponse`.

### 11.9. Listar Todos los Turnos de la Sucursal
* **Método:** `GET`
* **Ruta:** `/api/v1/cash/shifts`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):** Array de `CashShiftResponse`.

### 11.10. Cerrar Turno de Caja (Arqueo Final)
* **Método:** `POST`
* **Ruta:** `/api/v1/cash/shifts/{shiftId}/close`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:** `{ "countedCash": 575.00, "notes": "Cierre conforme" }`
* **Respuesta (`200 OK`):** Retorna `CashShiftResponse` con cálculo de cuadre.

### 11.11. Reporte Fiscal y Operativo de Corte Z
* **Método:** `GET`
* **Ruta:** `/api/v1/cash/shifts/{shiftId}/z-cut`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):** Retorna `ZCutReportResponse` con total de ventas, recaudación desglosada por método de pago y comprobantes emitidos.

---

## 12. Módulo 10: Gastos Operativos

Base URL: `/api/v1/expenses`

Permite registrar compras menores de emergencia, insumos de cocina, compras en efectivo y egresos diarios asociados a la caja chica de la sucursal.

### 12.1. Listar Gastos Operativos
* **Método:** `GET`
* **Ruta:** `/api/v1/expenses?from=2026-10-01&to=2026-10-14&branchId=1`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1` (opcional si se pasa `branchId`)
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 12,
    "branchId": 1,
    "shiftId": 45,
    "amount": 35.50,
    "reason": "Compra de perejil y limones en mercado local",
    "registeredByUserId": "e98e2197-28d8-4b71-97b7-785bc03cbbf6",
    "registeredByName": "Carlos Mendoza",
    "createdAt": "2026-10-14T11:20:00Z"
  }
]
```

### 12.2. Registrar Nuevo Gasto
Si no se especifica `shiftId`, el backend lo asocia automáticamente al turno de caja abierto del cajero o de la sucursal.
* **Método:** `POST`
* **Ruta:** `/api/v1/expenses`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "shiftId": null,
  "amount": 25.00,
  "reason": "Compra de hielo para barra"
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/expenses/{id}`.

### 12.3. Eliminar / Anular Gasto
* **Método:** `DELETE`
* **Ruta:** `/api/v1/expenses/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Regla:** Solo permitido si el turno de caja correspondiente aún se encuentra abierto (`OPEN`).
* **Respuesta (`204 No Content`)**

---

## 13. Módulo 11: Reportes Agregados

Base URL: `/api/v1/reports`

Módulo analítico para gerencia y directivos. Permite consolidar ventas por rango de fechas (día, semana, mes) y ranking de platos más vendidos.

### 13.1. Resumen Consolidado de Ventas (Sales Summary)
* **Método:** `GET`
* **Ruta:** `/api/v1/reports/sales-summary?from=2026-10-01T00:00:00Z&to=2026-10-14T23:59:59Z&branchId=1`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1` (si no se envía y el usuario es `EMPRESA`, consolida a nivel de franquicia completa).
* **Respuesta (`200 OK`):**
```json
{
  "branchId": 1,
  "from": "2026-10-01T00:00:00Z",
  "to": "2026-10-14T23:59:59Z",
  "totalSales": 48520.00,
  "netAmount": 41118.64,
  "taxAmount": 7401.36,
  "invoiceCount": 940,
  "salesByPaymentMethod": {
    "EFECTIVO": 18200.00,
    "YAPE": 15400.00,
    "TARJETA": 12920.00,
    "PLIN": 2000.00
  },
  "salesByInvoiceType": {
    "BOLETA": 38400.00,
    "FACTURA": 8620.00,
    "TICKET": 1500.00
  }
}
```

### 13.2. Ranking de Productos Más Vendidos (Product Sales Ranking)
* **Método:** `GET`
* **Ruta:** `/api/v1/reports/product-sales?from=2026-10-01T00:00:00Z&to=2026-10-14T23:59:59Z&limit=10&branchId=1`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
{
  "branchId": 1,
  "from": "2026-10-01T00:00:00Z",
  "to": "2026-10-14T23:59:59Z",
  "items": [
    {
      "skuId": 1,
      "skuCode": "LOMO-PERS-01",
      "productName": "Lomo Saltado Clásico",
      "presentationName": "Porción Personal",
      "quantitySold": 284,
      "totalRevenue": 14768.00
    },
    {
      "skuId": 5,
      "skuCode": "CHICHA-LIT-01",
      "productName": "Chicha Morada de la Casa",
      "presentationName": "Jarra 1L",
      "quantitySold": 310,
      "totalRevenue": 6200.00
    }
  ]
}
```

---

## 14. Módulo 12: Impresoras Térmicas y Hardware POS

Base URL: `/api/v1/printers`

### 14.1. Registrar Dispositivo de Impresión
* **Método:** `POST`
* **Ruta:** `/api/v1/printers`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "name": "Impresora Cocina Caliente",
  "ipAddress": "192.168.1.200",
  "port": 9100,
  "paperWidthMm": 80,
  "locationDescription": "Estación de fogones y woks"
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/printers/{id}`.

### 14.2. Listar Impresoras
* **Método:** `GET`
* **Ruta:** `/api/v1/printers`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 1,
    "branchId": 1,
    "name": "Impresora Cocina Caliente",
    "ipAddress": "192.168.1.200",
    "port": 9100,
    "connectionType": "RAW_TCP",
    "terminalCode": "PRN-KITCHEN-01",
    "isActive": true
  }
]
```

### 14.3. Actualizar Dispositivo de Impresión
* **Método:** `PUT`
* **Ruta:** `/api/v1/printers/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "name": "Impresora Cocina Caliente (Epson TM-T20III)",
  "ipAddress": "192.168.1.205",
  "port": 9100,
  "connectionType": "RAW_TCP",
  "terminalCode": "PRN-KITCHEN-01",
  "isActive": true
}
```
* **Respuesta (`204 No Content`)**

### 14.4. Desactivar / Eliminar Impresora
* **Método:** `DELETE`
* **Ruta:** `/api/v1/printers/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Regla:** Desactivación lógica (soft-delete).
* **Respuesta (`204 No Content`)**

### 14.5. Configurar Ruta de Producción
Vincula un área de producción (`COCINA`, `BARRA`, `HORNO`, `CAJA`) o tipo de documento (`KITCHEN_ORDER`, `CASHIER_RECEIPT`, `PRE_CHECK`) con la impresora física encargada de recibir las comandas.
* **Método:** `POST`
* **Ruta:** `/api/v1/printers/{id}/routes`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "documentType": "KITCHEN_ORDER",
  "productionArea": "COCINA"
}
```
* **Respuesta (`200 OK`)**

### 14.6. Reimpresión de Comanda en Cocina
* **Método:** `POST`
* **Ruta:** `/api/v1/printers/kitchen/reprint`
* **Roles:** `COCINERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "orderId": 1050,
  "productionArea": "COCINA",
  "targetPrinterId": 1
}
```
* **Respuesta (`202 Accepted`):** `{ "jobIds": [501], "reprinted": true }`

### 14.7. Reimpresión de Recibo de Caja
* **Método:** `POST`
* **Ruta:** `/api/v1/printers/receipts/reprint`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "orderId": 1050,
  "terminalCode": "POS-01",
  "targetPrinterId": 2,
  "reason": "Papel arrugado al cortar"
}
```
* **Respuesta (`202 Accepted`):** `{ "jobId": 502, "reprinted": true }`

### 14.8. Reintentar Trabajo de Impresión desde Dead Letter Queue (DLQ)
Si una impresora se quedó sin papel o sufrió un corte de energía, el trabajo se retiene en DLQ. El operador puede reintentarlo una vez subsanado el problema físico.
* **Método:** `POST`
* **Ruta:** `/api/v1/printers/jobs/{id}/retry`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`, `COCINERO`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`202 Accepted`):** `{ "jobId": 502, "status": "RETRYING" }`

### 14.9. Listar Trabajos de Impresión por Sucursal
* **Método:** `GET`
* **Ruta:** `/api/v1/printers/jobs?status=FAILED`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):** Array de `PrintJobResponse`.

### 14.10. Confirmación (ACK) de Impresión por Agente Local
Utilizado por el software **Print Agent** instalado en la PC de caja o Raspberry Pi de la sucursal para reportar al backend si el ticket físico se imprimió exitosamente o si la impresora local falló (sin papel, apagada, etc.).
* **Método:** `POST`
* **Ruta:** `/api/v1/printers/jobs/{id}/ack`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`, `COCINERO`, `MESERO`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "status": "COMPLETED",
  "errorMessage": null
}
```
*Si falló:*
```json
{
  "status": "FAILED",
  "errorMessage": "Impresora 192.168.1.200:9100 no responde (sin papel o apagada)"
}
```
* **Respuesta (`200 OK`):**
```json
{
  "jobId": 502,
  "status": "COMPLETED",
  "acknowledged": true
}
```
> [!NOTE]
> Al recibir un ACK con estado `FAILED`, el backend actualiza el trabajo a `FAILED` y dispara automáticamente una alerta en tiempo real a `/topic/branches/{branchId}/printer-alerts` para notificar al personal en pantalla.

### 14.11. Modos de Impresión (`app.printing.mode`)
Configurable mediante la variable de entorno `APP_PRINTING_MODE`:
* **`HYBRID` (Por defecto):** Emite el trabajo por WebSocket STOMP (`/topic/branches/{branchId}/print-jobs`) con el binario ESC/POS codificado en Base64. A la vez intenta socket TCP directo; si la IP es de red local privada (LAN) inalcanzable desde un VPS Cloud, pasa limpiamente a estado `DISPATCHED` a la espera del ACK del Print Agent local, sin saturar la cola de RabbitMQ ni enviar a DLQ.
* **`AGENT` (Recomendado para VPS Cloud en producción):** Emite el binario ESC/POS por WebSocket a `/topic/branches/{branchId}/print-jobs` y marca el trabajo inmediatamente como `DISPATCHED`. Cero latencia de timeout TCP.
* **`DIRECT`:** Conexión directa TCP desde el servidor Spring Boot a la impresora (modo clásico on-premise). Si falla, reintenta y deriva a DLQ.

---

## 15. Módulo 13: Eventos en Tiempo Real (WebSockets / STOMP)

* **Endpoint de Conexión:** `/ws`
* **Transporte:** SockJS + STOMP
* **Autenticación en Handshake:** Header STOMP `Authorization: Bearer <token_jwt>`

### Canales de Suscripción (Topics)

| Destino STOMP | Perfil de Destinatario | Contenido del Mensaje |
| :--- | :--- | :--- |
| `/topic/branches/{branchId}/tables` | Mozos y Cajeros | Actualización en vivo del estado de la mesa (`state: AVAILABLE`, `OCCUPIED`, `BILL_REQUESTED`). |
| `/topic/branches/{branchId}/kitchen` | Pantallas KDS | Nuevas comandas creadas o adición de platos (`ticketId`, `orderId`, platos, comentarios). |
| `/topic/branches/{branchId}/print-jobs` | Print Agent Local (Caja/Raspberry Pi) | Trabajos de comanda/recibo con binario ESC/POS Base64 (`jobId`, `printerIp`, `port`, `payloadBase64`). |
| `/topic/branches/{branchId}/printer-alerts` | Pantalla de Caja | Alertas urgentes de hardware (impresora sin papel, desconectada o falla reportada por agente). |

---

## 16. Manejo de Errores Estándar (RFC 7807 ProblemDetail)

Todas las excepciones del backend devuelven el formato estándar RFC 7807 (`Content-Type: application/problem+json`).

### Formato General
```json
{
  "type": "https://api.facilcomanda.com/errors/{code}",
  "title": "Breve descripción humana del tipo de error",
  "status": 400,
  "detail": "Explicación detallada de la causa del fallo",
  "instance": "/api/v1/ruta-solicitada",
  "properties": {
    "timestamp": "2026-10-14T03:12:00Z"
  }
}
```

### Catálogo de Tipos de Error Estandarizados

| Código HTTP | URI de Tipo (`type`) | `title` | Propiedades Adicionales (`properties`) |
| :---: | :--- | :--- | :--- |
| `400` | `.../errors/validation-failed` | Validación de Datos Fallida | `invalidParameters`: mapa `{ campo: mensaje }` |
| `400` | `.../errors/invalid-parameter-type` | Tipo de Parámetro Inválido | `parameterName`, `invalidValue` |
| `400` | `.../errors/missing-header` | Encabezado HTTP Requerido Faltante | `headerName` (ej. `X-Branch-Id`) |
| `400` | `.../errors/malformed-json` | Cuerpo de Solicitud Inválido | `timestamp` |
| `400` | `.../errors/bad-request` | Solicitud Inválida | `timestamp` |
| `401` | `.../errors/unauthorized` | Autenticación Fallida | `timestamp` |
| `403` | `.../errors/forbidden` | Acceso Denegado | `timestamp` |
| `404` | `.../errors/not-found` | Recurso No Encontrado | `timestamp` |
| `409` | `.../errors/conflict` | Conflicto con Regla de Negocio / Operación Duplicada | `timestamp` |
| `422` | `.../errors/insufficient-stock` | Stock Insuficiente | `timestamp` |
| `429` | `.../errors/cooldown` | Cooldown de Reimpresión Activo | `secondsRemaining`: segundos a esperar |
| `429` | `.../errors/rate-limit` | Límite de Consultas Excedido | `retryAfterSeconds`, `limitType` |
| `502` | `.../errors/printer-offline` | Falla de Comunicación con Impresora | `timestamp` |
| `500` | `.../errors/internal-error` | Error Interno del Servidor | `errorId`: UUID de rastreo para soporte |

---

## 17. Documentación Interactiva (OpenAPI 3.0 & Swagger UI)

El backend expone la especificación OpenAPI autogenerada y la interfaz visual Swagger UI con soporte completo para autenticación Bearer JWT:

* **Swagger UI:** `http://localhost:8081/swagger-ui/index.html`
* **OpenAPI 3 JSON Spec:** `http://localhost:8081/v3/api-docs`

### ¿Cómo probar endpoints protegidos desde Swagger UI?
1. Haz login llamando a `POST /api/v1/auth/login` con tus credenciales para obtener el `access_token`.
2. En Swagger UI, haz clic en el botón verde con el candado **"Authorize"** en la parte superior derecha.
3. En el campo **Value**, ingresa únicamente el token JWT (o `Bearer <token>` si tu cliente lo requiere).
4. Haz clic en **Authorize** y luego **Close**.
5. Ahora todas las peticiones que ejecutes en Swagger UI llevarán automáticamente el header `Authorization: Bearer <token>`. Recuerda enviar también el header `X-Branch-Id` cuando el endpoint lo requiera.

---

## 18. Guía Rápida para el Agente IA de Front-End

Si vas a construir una aplicación cliente para este backend:
1. **Flujo de Inicio de Sesión:**
   * Utiliza `POST /api/v1/auth/login` con `{ email, password }`. Almacena en memoria o storage seguro `access_token` y `refresh_token`.
   * En cada petición HTTP incluye `Authorization: Bearer <access_token>` y `X-Branch-Id: <id>`.
   * Cuando el token expire (recibas `401 Unauthorized`), llama a `POST /api/v1/auth/refresh`.
2. **Para la App de Mozos (Tablet/Móvil):**
   * Conéctate a `/topic/branches/{branchId}/tables` para pintar el plano del restaurante con colores según `state`.
   * Para tomar pedido: `POST /api/v1/orders` con `Idempotency-Key` único.
   * Para pedir la cuenta: `POST /api/v1/orders/{id}/pre-check/print`.
3. **Para la Pantalla de Cocina (KDS):**
   * Consume `GET /api/v1/kitchen/tickets` al iniciar.
   * Conéctate al WebSocket `/topic/branches/{branchId}/kitchen` para recibir nuevas comandas instantáneamente.
   * Permite tocar la pantalla para cambiar el estado con `PATCH /api/v1/kitchen/tickets/{id}/status` (`IN_PREPARATION` ➔ `READY`).
4. **Para el Punto de Venta (POS Cajero):**
   * Escucha `/topic/branches/{branchId}/printer-alerts` para mostrar modal de alerta roja si la impresora térmica se atasca.
   * Para cobrar: `POST /api/v1/invoices/pay` enviando los medios de pago desglosados y datos del cliente.
   * Para gastos diarios: `POST /api/v1/expenses`.
   * Al final del día: Genera el arqueo con `POST /api/v1/cash/shifts/{shiftId}/close` y visualiza/imprime el `GET /api/v1/cash/shifts/{shiftId}/z-cut`.
5. **Para el Portal Web de Administración / Franquicia:**
   * Gestiona salones (`/api/v1/floors`), mesas (`/api/v1/tables`), categorías (`/api/v1/categories`), marcas (`/api/v1/catalog/brands`), productos (`/api/v1/catalog/products`) y personal (`/api/v1/users`).
   * Visualiza gráficos de facturación con `GET /api/v1/reports/sales-summary` y `GET /api/v1/reports/product-sales`.
