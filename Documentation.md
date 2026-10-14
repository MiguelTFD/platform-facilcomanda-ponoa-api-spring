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
13. [Módulo 11: Reportes Agregados (`/api/v1/reports`)](#11-módulo-11-reportes-agregados)
14. [Módulo 12: Impresoras Térmicas y Hardware POS (`/api/v1/printers`)](#12-módulo-12-impresoras-térmicas-y-hardware-pos)
15. [Módulo 13: Eventos en Tiempo Real (WebSockets / STOMP)](#13-módulo-13-eventos-en-tiempo-real-websockets--stomp)
16. [Manejo de Errores Estándar (RFC 7807 ProblemDetail)](#14-manejo-de-errores-estándar-rfc-7807-problemdetail)
17. [Guía Rápida para el Agente IA de Front-End](#15-guía-rápida-para-el-agente-ia-de-front-end)

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
* **Ruta:** `/api/v1/profiles/me`
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

### 3.5. Crear Administrador de Sucursal
* **Método:** `POST`
* **Ruta:** `/api/v1/users/admin`
* **Roles:** `EMPRESA`
* **Body:**
```json
{
  "email": "admin.miraflores@facilcomanda.com",
  "firstName": "Roberto",
  "lastName": "Pérez",
  "phoneNumber": "999888777",
  "branchId": 2
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/users/{id}`.

### 3.6. Crear Personal Operativo (Staff)
* **Método:** `POST`
* **Ruta:** `/api/v1/users/staff`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "email": "mozo1@facilcomanda.com",
  "firstName": "Juan",
  "lastName": "Gómez",
  "phoneNumber": "912345678",
  "role": "MESERO",
  "branchId": 1
}
```
* **Restricciones:** `role` debe ser `MESERO`, `COCINERO` o `CAJERO`.
* **Respuesta (`201 Created`):** Header `Location: /api/v1/users/{id}`.

### 3.7. Listar Usuarios
* **Método:** `GET`
* **Ruta:** `/api/v1/users?branchId={id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Respuesta (`200 OK`):** Array de `UserResponse`.

### 3.8. Obtener Detalle de Usuario
* **Método:** `GET`
* **Ruta:** `/api/v1/users/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Respuesta (`200 OK`):** Objeto `UserResponse`.

### 3.9. Actualizar Usuario
* **Método:** `PUT`
* **Ruta:** `/api/v1/users/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "firstName": "Juan Carlos",
  "lastName": "Gómez Silva",
  "phoneNumber": "912345679",
  "role": "MESERO",
  "branchId": 1,
  "isActive": true
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

## 5. Módulo 3: Salones y Mesas

Base URL: `/api/v1`

### 5.1. Crear Piso / Salón
* **Método:** `POST`
* **Ruta:** `/api/v1/floors`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "name": "Terraza Principal",
  "description": "Área de mesas al aire libre con vista a la calle"
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
    "name": "Terraza Principal",
    "description": "Área de mesas al aire libre con vista a la calle"
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
* **Regla:** Falla con `409 Conflict` si alguna mesa en el piso está ocupada.
* **Respuesta (`204 No Content`)**

### 5.5. Crear Mesa
* **Método:** `POST`
* **Ruta:** `/api/v1/tables`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "floorId": 10,
  "name": "Mesa 101",
  "description": "Cerca a la ventana exterior",
  "chairs": 4
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/tables/{id}`.

### 5.6. Listar Mesas de la Sucursal
* **Método:** `GET`
* **Ruta:** `/api/v1/tables?floorId=10` (parámetro `floorId` opcional)
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 501,
    "branchId": 1,
    "floorId": 10,
    "name": "Mesa 101",
    "description": "Cerca a la ventana exterior",
    "state": "AVAILABLE",
    "chairs": 4
  }
]
```
> **Valores posibles de `state` de mesa:** `AVAILABLE` (Libre), `OCCUPIED` (Ocupada con comanda), `BILL_REQUESTED` (Pre-cuenta solicitada), `OUT_OF_SERVICE` (Fuera de servicio / mantenimiento).

### 5.7. Actualizar Mesa
* **Método:** `PUT`
* **Ruta:** `/api/v1/tables/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "floorId": 10,
  "name": "Mesa 101 VIP",
  "description": "Mesa esquinera cómoda",
  "chairs": 6
}
```
* **Respuesta (`204 No Content`)**

### 5.8. Cambiar Estado de la Mesa
* **Método:** `PATCH`
* **Ruta:** `/api/v1/tables/{id}/state`
* **Roles:** `MESERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:** `{ "state": "OUT_OF_SERVICE" }`
* **Respuesta (`204 No Content`)**

### 5.9. Desactivar / Eliminar Mesa
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

### 7.1. Crear Producto en Catálogo Maestro
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
      "currentPrice": 48.00
    }
  ]
}
```
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
    "startingPrice": 48.00
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
      "categoryId": 1
    }
  ],
  "skus": [
    {
      "id": 1,
      "skuCode": "LOMO-PERS-01",
      "presentationName": "Porción Personal",
      "currentPrice": 48.00,
      "isActive": true
    }
  ]
}
```

### 7.4. Editar Información Básica del Producto
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
  "categoryIds": [1]
}
```
* **Respuesta (`204 No Content`)**

### 7.5. Desactivar Producto (Soft-Delete)
* **Método:** `DELETE`
* **Ruta:** `/api/v1/catalog/products/{id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Respuesta (`204 No Content`)**

### 7.6. Listar Menú Enriquecido de la Sucursal
Devuelve los productos y SKUs activos con sus nombres, códigos, precios vigentes (o sobreescritos por sucursal) y disponibilidad.
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
    "isAvailable": true
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

## 8. Módulo 6: Pedidos y Pre-Cuenta

Base URL: `/api/v1/orders`

### 8.1. Crear Pedido (Comanda Inicial)
Crea la orden, ocupa atómicamente la mesa, decrementa el stock en BD, emite el evento para impresión de tickets de cocina y actualiza el KDS.
* **Método:** `POST`
* **Ruta:** `/api/v1/orders`
* **Roles:** `MESERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers Requeridos:**
  * `X-Branch-Id: 1`
  * `Idempotency-Key: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d` (Previene duplicación de pedidos)
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
Devuelve la lista de órdenes activas de la sucursal con el nombre legible de cada plato y mesa.
* **Método:** `GET`
* **Ruta:** `/api/v1/orders`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 1050,
    "branchId": 1,
    "appUserId": "e98e2197-28d8-4b71-97b7-785bc03cbbf6",
    "idempotencyKey": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "tableId": 501,
    "tableName": "Mesa 101 (Terraza Principal)",
    "orderType": "DINE_IN",
    "notes": "Cliente alérgico a los mariscos",
    "totalAmount": 104.00,
    "status": "PENDING",
    "cancelledBy": null,
    "cancellationReason": null,
    "cancelledAt": null,
    "items": [
      {
        "skuId": 1,
        "name": "Porción Personal",
        "quantity": 2,
        "unitPrice": 52.00,
        "subtotal": 104.00,
        "comments": "Término medio, papas bien crocantes"
      }
    ]
  }
]
```

### 8.3. Obtener Detalle de un Pedido (Enriquecido)
* **Método:** `GET`
* **Ruta:** `/api/v1/orders/{id}`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):** Objeto `OrderResponse` individual idéntico al elemento de la lista anterior.

### 8.4. Adicionar Ítems a una Orden Existente
Permite agregar platos o bebidas adicionales a una mesa ya abierta. Imprime únicamente la adición en las impresoras de producción correspondientes.
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

### 8.5. Cancelar Pedido
Libera la mesa, devuelve el stock al inventario, cancela las comandas de cocina y registra el motivo y usuario responsable de la cancelación.
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

### 8.6. Consultar Pre-Cuenta (Resumen de Cuenta)
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
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 801,
    "branchId": 1,
    "orderId": 1050,
    "status": "QUEUED",
    "createdAt": "2026-10-14T02:45:00Z",
    "updatedAt": "2026-10-14T02:45:00Z"
  }
]
```
> **Valores posibles de `status`:** `QUEUED`, `PREPARING`, `READY`, `DELIVERED`, `CANCELLED`.

### 9.2. Actualizar Estado de Preparación
* **Método:** `PATCH`
* **Ruta:** `/api/v1/kitchen/tickets/{id}/status`
* **Roles:** `COCINERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`, `MESERO`
* **Headers:** `X-Branch-Id: 1`
* **Body:** `{ "status": "READY" }`
* **Respuesta (`200 OK`)**

---

## 10. Módulo 8: Clientes y Facturación Electrónica

Base URL: `/api/v1/customers` e `/api/v1/invoices`

### 10.1. Consulta Rápida de DNI / RUC (Lookup con Rate Limiting)
* **Método:** `GET`
* **Ruta:** `/api/v1/customers/lookup?docType=DNI&docNumber=71234567`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
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

### 10.2. Cobrar Orden y Emitir Comprobante (Boleta / Factura / Ticket)
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
* **Respuesta (`201 Created`):**
```json
{
  "invoiceId": 901,
  "invoiceNumber": "B001-00000042",
  "invoiceType": "BOLETA",
  "orderTotal": 104.00,
  "amountPaid": 104.00,
  "changeAmount": 0.00,
  "paidAt": "2026-10-14T03:10:00Z"
}
```

### 10.3. Reimprimir Comprobante de Pago
* **Método:** `POST`
* **Ruta:** `/api/v1/invoices/{id}/print`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`202 Accepted`)**

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
| `/topic/branches/{branchId}/printer-alerts` | Pantalla de Caja | Alertas urgentes de hardware (impresora sin papel, desconectada o trabajo enviado a DLQ). |

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
   * Permite tocar la pantalla para cambiar el estado con `PATCH /api/v1/kitchen/tickets/{id}/status` (`PREPARING` ➔ `READY`).
4. **Para el Punto de Venta (POS Cajero):**
   * Escucha `/topic/branches/{branchId}/printer-alerts` para mostrar modal de alerta roja si la impresora térmica se atasca.
   * Para cobrar: `POST /api/v1/invoices/pay` enviando los medios de pago desglosados y datos del cliente.
   * Para gastos diarios: `POST /api/v1/expenses`.
   * Al final del día: Genera el arqueo con `POST /api/v1/cash/shifts/{shiftId}/close` y visualiza/imprime el `GET /api/v1/cash/shifts/{shiftId}/z-cut`.
5. **Para el Portal Web de Administración / Franquicia:**
   * Gestiona salones (`/api/v1/floors`), mesas (`/api/v1/tables`), categorías (`/api/v1/categories`), marcas (`/api/v1/catalog/brands`), productos (`/api/v1/catalog/products`) y personal (`/api/v1/users`).
   * Visualiza gráficos de facturación con `GET /api/v1/reports/sales-summary` y `GET /api/v1/reports/product-sales`.
