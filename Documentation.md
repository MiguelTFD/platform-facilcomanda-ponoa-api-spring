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
3. [Módulo 1: Usuarios y Autenticación (`/api/v1/users`, `/api/v1/profiles`)](#3-módulo-1-usuarios-y-autenticación)
4. [Módulo 2: Sucursales (`/api/v1/branches`)](#4-módulo-2-sucursales)
5. [Módulo 3: Salones y Mesas (`/api/v1/floors`, `/api/v1/tables`)](#5-módulo-3-salones-y-mesas)
6. [Módulo 4: Catálogo e Inventario Multi-Sucursal (`/api/v1/catalog`, `/api/v1/inventory`)](#6-módulo-4-catálogo-e-inventario-multi-sucursal)
7. [Módulo 5: Pedidos y Pre-Cuenta (`/api/v1/orders`)](#7-módulo-5-pedidos-y-pre-cuenta)
8. [Módulo 6: Cocina y KDS (`/api/v1/kitchen/tickets`)](#8-módulo-6-cocina-y-kds)
9. [Módulo 7: Clientes y Facturación Electrónica (`/api/v1/customers`, `/api/v1/invoices`)](#9-módulo-7-clientes-y-facturación-electrónica)
10. [Módulo 8: Cajas, Turnos y Corte Z (`/api/v1/cash`)](#10-módulo-8-cajas-turnos-y-corte-z)
11. [Módulo 9: Impresoras Térmicas y Trabajos de Impresión (`/api/v1/printers`)](#11-módulo-9-impresoras-térmicas-y-hardware-pos)
12. [Módulo 10: Eventos en Tiempo Real (WebSockets / STOMP)](#12-módulo-10-eventos-en-tiempo-real-websockets--stomp)
13. [Manejo de Errores Estándar (RFC 7807 ProblemDetail)](#13-manejo-de-errores-estándar-rfc-7807-problemdetail)

---

## 1. Convenciones Globales, Headers y Seguridad

### Headers HTTP Requeridos en las Solicitudes

| Header | Tipo | Requerido | Descripción / Regla |
| :--- | :--- | :---: | :--- |
| `Authorization` | `String` | **SÍ** | Token Bearer JWT: `Bearer <token_keycloak>` |
| `X-Branch-Id` | `Long` (numérico) | **SÍ\*** | ID de la sucursal activa donde se realiza la operación. (\*Obligatorio si el usuario pertenece a múltiples sucursales o es de rol `EMPRESA`). |
| `Idempotency-Key`| `String` (UUID / hash)| **SÍ\*\*** | Clave de idempotencia única. (\*\*Obligatorio al crear pedidos `POST /api/v1/orders`). |
| `Content-Type` | `String` | **SÍ** | `application/json` en todas las peticiones `POST`, `PUT`, `PATCH`. |

### Respuestas HTTP Comunes
* `200 OK`: Petición exitosa con cuerpo de retorno.
* `201 Created`: Recurso creado exitosamente. La cabecera `Location` contiene la URI del nuevo recurso.
* `202 Accepted`: Acción asíncrona aceptada (ej. envío a cola de impresión o DLQ).
* `204 No Content`: Modificación o borrado lógico exitoso sin cuerpo de retorno.
* `400 Bad Request`: Error de validación de campos (RFC 7807).
* `401 Unauthorized`: Token ausente, expirado o malformado.
* `403 Forbidden`: El usuario no tiene el rol necesario o no pertenece a la sucursal indicada en `X-Branch-Id`.
* `404 Not Found`: Recurso no encontrado en la sucursal activa.
* `409 Conflict`: Conflicto de estado (ej. mesa ocupada, duplicidad de nombre único, turno de caja ya abierto).
* `429 Too Many Requests`: Límite de consultas o cuota excedida (Rate Limiting / Anti-Abuso).
* `500 Internal Server Error`: Error no controlado del servidor.

---

## 2. Roles de Usuario y Permisos (RBAC)

Los roles son transmitidos dentro del token JWT en el claim `realm_access.roles` con prefijo `ROLE_`:

| Rol | Alcance en el Sistema |
| :--- | :--- |
| `EMPRESA` | Dueño o directivo general de la franquicia. Acceso irrestricto a todas las sucursales, creación de sucursales, usuarios administradores y métricas globales. |
| `ADMINISTRADOR` | Gerente o administrador de una sucursal específica. Configuración de pisos, mesas, impresoras, inventario, precios y personal. |
| `CAJERO` | Responsable del punto de venta (POS). Apertura y cierre de caja (Corte Z), cobro de pedidos, emisión de boletas/facturas y reimpresión de comprobantes. |
| `MESERO` | Atención en salón. Creación de pedidos, adición de platos, solicitud de pre-cuenta y visualización del mapa de mesas. |
| `COCINERO` | Personal de cocina y barra. Visualización de comandas en KDS, cambio de estado de preparación y reimpresión de comandas de producción. |

---

## 3. Módulo 1: Usuarios y Autenticación

Base URL: `/api/v1/users` y `/api/v1/profiles`

### 3.1. Sincronizar Perfil del Usuario Autenticado
Actualiza o crea el usuario local a partir de los claims del JWT de Keycloak.
* **Método:** `POST`
* **Ruta:** `/api/v1/profiles/sync`
* **Roles:** Cualquier usuario autenticado.
* **Respuesta (`200 OK`):** Vacío.

### 3.2. Obtener Perfil Propio
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

### 3.3. Crear Usuario Administrador de Sucursal
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

### 3.4. Crear Usuario Operativo (Staff)
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

### 3.5. Listar Usuarios
* **Método:** `GET`
* **Ruta:** `/api/v1/users?branchId={id}`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Respuesta (`200 OK`):** Array de objetos `UserResponse`.

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
  "code": "SUC-002",
  "name": "Sucursal San Isidro",
  "address": "Av. Javier Prado 1234",
  "city": "Lima",
  "phone": "+5114567890",
  "email": "sanisidro@facilcomanda.com",
  "taxId": "20601234567"
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
    "code": "SUC-001",
    "name": "Sucursal Central",
    "address": "Av. Larco 456",
    "city": "Lima",
    "phone": "+5112223344",
    "email": "central@facilcomanda.com",
    "taxId": "20601234567",
    "isActive": true
  }
]
```

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

### 5.3. Crear Mesa
* **Método:** `POST`
* **Ruta:** `/api/v1/tables`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "floorId": 10,
  "name": "Mesa 101",
  "description": "Mesa redonda para 4 personas cerca a la ventana",
  "chairs": 4
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/tables/{id}`.

### 5.4. Listar Mesas de la Sucursal
* **Método:** `GET`
* **Ruta:** `/api/v1/tables?floorId={id}` (el query param `floorId` es opcional)
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 501,
    "branchId": 1,
    "floorId": 10,
    "name": "Mesa 101",
    "description": "Mesa redonda para 4 personas",
    "state": "AVAILABLE",
    "chairs": 4
  }
]
```
> **Valores posibles de `state`:** `AVAILABLE` (Libre), `OCCUPIED` (Ocupada con orden activa), `BILL_REQUESTED` (Pre-cuenta solicitada), `CLEANING` (En limpieza), `RESERVED` (Reservada), `OUT_OF_SERVICE` (Fuera de servicio).

### 5.5. Cambiar Estado de una Mesa
* **Método:** `PATCH`
* **Ruta:** `/api/v1/tables/{id}/state`
* **Roles:** `MESERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "state": "CLEANING"
}
```
* **Respuesta (`204 No Content`)**

### 5.6. Desactivar / Borrado Lógico de Mesa
* **Método:** `DELETE` o `PATCH /api/v1/tables/{id}/deactivate`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`204 No Content`)**

---

## 6. Módulo 4: Catálogo e Inventario Multi-Sucursal

Base URL: `/api/v1/catalog` e `/api/v1/inventory`

### 6.1. Crear Producto en Catálogo Maestro
* **Método:** `POST`
* **Ruta:** `/api/v1/catalog/products`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Body:**
```json
{
  "name": "Lomo Saltado Clásico",
  "description": "Trozos de lomo fino salteados al wok con cebolla, tomate y papas fritas",
  "brandId": 1,
  "categoryId": 2,
  "productionArea": "COCINA",
  "skuPresentations": [
    {
      "name": "Porción Personal",
      "skuCode": "LOMO-PERS-01",
      "currentPrice": 48.00
    }
  ]
}
```
* **Respuesta (`201 Created`)**

### 6.2. Listar Catálogo Paginado
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

### 6.3. Configurar Precio y Disponibilidad por Sucursal
Permite sobreescribir el precio del plato y habilitarlo o deshabilitarlo según la sucursal.
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

### 6.4. Inicializar / Ajustar Stock de SKU
* **Método:** `POST`
* **Ruta:** `/api/v1/inventory`
* **Roles:** `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "skuId": 1,
  "quantity": 50
}
```
* **Respuesta (`200 OK`)**

### 6.5. Consultar Stock de una Sucursal
* **Método:** `GET`
* **Ruta:** `/api/v1/inventory` o `/api/v1/inventory/skus/{skuId}`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
[
  {
    "id": 1,
    "branchId": 1,
    "skuId": 1,
    "quantity": 48,
    "updatedAt": "2026-10-14T02:30:00Z"
  }
]
```

---

## 7. Módulo 5: Pedidos y Pre-Cuenta

Base URL: `/api/v1/orders`

### 7.1. Crear Pedido (Comanda Inicial)
Crea la orden, ocupa atómicamente la mesa, decrementa el stock en BD, emite el evento para impresión de tickets de cocina y actualiza el KDS.
* **Método:** `POST`
* **Ruta:** `/api/v1/orders`
* **Roles:** `MESERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers Requeridos:**
  * `X-Branch-Id: 1`
  * `Idempotency-Key: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d` (Previene duplicación de pedidos por doble click o reintentos de red)
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
    },
    {
      "skuId": 5,
      "quantity": 1,
      "comments": "Con hielo"
    }
  ]
}
```
* **Restricciones:** `orderType` puede ser `DINE_IN` (En salón), `TAKE_AWAY` (Para llevar) o `DELIVERY`.
* **Respuesta (`201 Created`):** Header `Location: /api/v1/orders/{orderId}`.

### 7.2. Adicionar Ítems a una Orden Existente
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

### 7.3. Cancelar Pedido
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

### 7.4. Consultar Pre-Cuenta (Resumen de Cuenta)
Calcula el subtotal (base gravada), IGV (18%) y total acumulado para mostrar al mozo o en la tablet.
* **Método:** `GET`
* **Ruta:** `/api/v1/orders/{id}/pre-check` o `/api/v1/orders/tables/{tableId}/pre-check`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
{
  "orderId": 1050,
  "branchId": 1,
  "tableName": "Mesa 101 (Terraza Principal)",
  "tableId": 501,
  "orderType": "DINE_IN",
  "items": [
    {
      "skuId": 1,
      "productName": "Lomo Saltado Clásico",
      "quantity": 2,
      "unitPrice": 52.00,
      "subtotal": 104.00,
      "comments": "Término medio"
    }
  ],
  "subtotal": 88.14,
  "igv": 15.86,
  "totalAmount": 104.00,
  "requestedAt": "2026-10-14T03:00:00Z"
}
```

### 7.5. Mandar a Imprimir Ticket de Pre-Cuenta
Cambia el estado de la mesa a `BILL_REQUESTED` y despacha el ticket físico a la impresora térmica asignada o a la impresora de caja.
* **Método:** `POST`
* **Ruta:** `/api/v1/orders/{id}/pre-check/print` o `/api/v1/orders/tables/{tableId}/pre-check/print?targetPrinterId={id}`
* **Roles:** `MESERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`202 Accepted`)**

---

## 8. Módulo 6: Cocina y KDS

Base URL: `/api/v1/kitchen/tickets`

### 8.1. Listar Comandas Activas de Cocina (KDS)
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
> **Valores posibles de `status` de ticket:** `QUEUED` (En cola), `PREPARING` (En preparación), `READY` (Platos listos para servir), `DELIVERED` (Servido en mesa), `CANCELLED` (Comanda anulada).

### 8.2. Actualizar Estado de Preparación
* **Método:** `PATCH`
* **Ruta:** `/api/v1/kitchen/tickets/{id}/status`
* **Roles:** `COCINERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`, `MESERO`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "status": "READY"
}
```
* **Respuesta (`200 OK`):** Retorna el `KitchenTicketResponse` actualizado.

---

## 9. Módulo 7: Clientes y Facturación Electrónica

Base URL: `/api/v1/customers` e `/api/v1/invoices`

### 9.1. Consulta de RUC / DNI (Con Anti-Abuso & Rate Limiting)
Consulta inteligente de 3 niveles: Base de Datos local (costo S/ 0) ➔ Caché en memoria ➔ API SUNAT/RENIEC (si hay cuota disponible).
* **Método:** `GET`
* **Ruta:** `/api/v1/customers/lookup?documentType=RUC&documentNumber=20601234567`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`, `MESERO`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
{
  "found": true,
  "name": "INVERSIONES RESTAURANTERAS S.A.C.",
  "identityDocumentType": "RUC",
  "identityDocumentNumber": "20601234567",
  "phoneNumber": null,
  "address": "CAL. LOS PINOS NRO. 120 INT. 402 LIMA - MIRAFLORES",
  "source": "LOCAL",
  "errorCode": null,
  "message": null
}
```
> **Nota de Seguridad Anti-Abuso:** Si un usuario o IP realiza 5 consultas erróneas consecutivas (datos inexistentes), el sistema aplica una cuarentena de 300 segundos retornando HTTP 429 con mensaje explicativo.

### 9.2. Cobrar Orden y Emitir Comprobante (Boleta / Factura / Ticket)
Soporta pago dividido (Split Payment: ej. parte en Efectivo y parte en Yape), calcula el vuelto exacto, asocia la venta al turno de caja abierto del cajero y genera el correlativo fiscal atómico consecutivo.
* **Método:** `POST`
* **Ruta:** `/api/v1/invoices/pay`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "orderId": 1050,
  "invoiceType": "BOLETA",
  "customer": {
    "identityDocumentType": "DNI",
    "identityDocumentNumber": "72345678",
    "name": "Maria Fernandez Quispe",
    "phoneNumber": "987112233",
    "address": "Av. Brasil 789, Lima"
  },
  "payments": [
    {
      "method": "EFECTIVO",
      "amount": 50.00,
      "reference": null
    },
    {
      "method": "YAPE",
      "amount": 54.00,
      "reference": "OP-987654"
    }
  ]
}
```
* **Restricciones:**
  * `invoiceType`: `BOLETA`, `FACTURA`, `TICKET`. (Si es `FACTURA`, el cliente debe tener `RUC` válido).
  * `payments.method`: `EFECTIVO`, `YAPE`, `PLIN`, `TARJETA`, `CREDITO`.
  * La suma de `payments.amount` debe ser mayor o igual al total de la orden.
* **Respuesta (`201 Created`):**
```json
{
  "id": 9001,
  "branchId": 1,
  "cashShiftId": 45,
  "orderId": 1050,
  "customerId": 320,
  "customerName": "Maria Fernandez Quispe",
  "customerDocumentType": "DNI",
  "customerDocumentNumber": "72345678",
  "invoiceNumber": "B001-00000045",
  "invoiceType": "BOLETA",
  "orderTotal": 104.00,
  "amountPaid": 104.00,
  "changeAmount": 0.00,
  "paidAt": "2026-10-14T03:05:00Z",
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
      "reference": "OP-987654"
    }
  ]
}
```

---

## 10. Módulo 8: Cajas, Turnos y Corte Z

Base URL: `/api/v1/cash`

### 10.1. Abrir Turno de Caja (Apertura)
Abre la sesión de caja con un saldo base inicial en efectivo.
* **Método:** `POST`
* **Ruta:** `/api/v1/cash/shifts/open`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "cashRegisterId": 1,
  "openingAmount": 150.00,
  "notes": "Apertura turno mañana - Sencillo completo"
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/cash/shifts/{shiftId}`.

### 10.2. Registrar Movimiento Manual (Ingreso / Egreso Menor)
Para registrar salidas por compras menores (ej. hielo, verduras de urgencia) o ingresos extraordinarios.
* **Método:** `POST`
* **Ruta:** `/api/v1/cash/shifts/{shiftId}/movements`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "type": "EXPENSE",
  "amount": 25.00,
  "reason": "Compra de bolsa de hielo para el bar"
}
```
* **Restricciones:** `type` puede ser `INCOME` (Ingreso) o `EXPENSE` (Gasto/Egreso).
* **Respuesta (`201 Created`)**

### 10.3. Consultar Turno Actual del Cajero
* **Método:** `GET`
* **Ruta:** `/api/v1/cash/shifts/current`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
{
  "id": 45,
  "branchId": 1,
  "cashRegisterId": 1,
  "cashRegisterName": "Caja Principal Salón",
  "cashierUserId": "e98e2197-28d8-4b71-97b7-785bc03cbbf6",
  "cashierName": "Carlos Mendoza",
  "status": "OPEN",
  "openingAmount": 150.00,
  "openedAt": "2026-10-14T08:00:00Z",
  "closedAt": null,
  "closingCashCounted": null,
  "expectedCashAmount": null,
  "cashDifference": null,
  "totalSalesAmount": 1250.00,
  "paymentsByMethod": {
    "EFECTIVO": 450.00,
    "YAPE": 350.00,
    "TARJETA": 450.00
  },
  "totalIncomes": 0.00,
  "totalExpenses": 25.00
}
```

### 10.4. Cerrar Turno de Caja (Arqueo Final)
* **Método:** `POST`
* **Ruta:** `/api/v1/cash/shifts/{shiftId}/close`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "countedCash": 575.00,
  "notes": "Cierre de turno conforme sin novedades"
}
```
* **Respuesta (`200 OK`):** Retorna `CashShiftResponse` con el cálculo automático de `expectedCashAmount` y `cashDifference` (cuadre, faltante o sobrante).

### 10.5. Reporte Fiscal y Operativo de Corte Z
Genera el balance completo del turno para auditoría o visualización en pantalla.
* **Método:** `GET`
* **Ruta:** `/api/v1/cash/shifts/{shiftId}/z-cut`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Respuesta (`200 OK`):**
```json
{
  "shiftId": 45,
  "branchId": 1,
  "branchName": "Sucursal Central",
  "cashRegisterName": "Caja Principal Salón",
  "cashierName": "Carlos Mendoza",
  "openedAt": "2026-10-14T08:00:00Z",
  "closedAt": "2026-10-14T16:00:00Z",
  "openingAmount": 150.00,
  "totalSalesAmount": 1250.00,
  "salesByPaymentMethod": {
    "EFECTIVO": 450.00,
    "YAPE": 350.00,
    "TARJETA": 450.00
  },
  "totalCashFromSales": 450.00,
  "totalChangeGiven": 0.00,
  "netCashFromSales": 450.00,
  "totalManualIncomes": 0.00,
  "totalManualExpenses": 25.00,
  "expectedCashInDrawer": 575.00,
  "actualCountedCash": 575.00,
  "cashDifference": 0.00,
  "invoiceCounts": {
    "BOLETA": 18,
    "FACTURA": 4,
    "TICKET": 2
  },
  "totalInvoicesIssued": 24,
  "notes": "Cierre de turno conforme"
}
```

---

## 11. Módulo 9: Impresoras Térmicas y Hardware POS

Base URL: `/api/v1/printers`

### 11.1. Registrar Dispositivo de Impresión
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
  "connectionType": "TCP",
  "terminalCode": "COCINA-01"
}
```
* **Respuesta (`201 Created`):** Header `Location: /api/v1/printers/{id}`.

### 11.2. Configurar Ruta de Impresión
Vincula un tipo de documento y/o área de producción con una impresora específica.
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
> **Valores posibles de `documentType`:** `KITCHEN_ORDER`, `CASHIER_RECEIPT`, `PRE_CHECK`, `Z_CUT`.

### 11.3. Reimprimir Comanda de Cocina
* **Método:** `POST`
* **Ruta:** `/api/v1/printers/kitchen/reprint`
* **Roles:** `COCINERO`, `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "orderId": 1050,
  "productionArea": "COCINA",
  "targetPrinterId": null
}
```
* **Respuesta (`202 Accepted`):**
```json
{
  "jobIds": [88],
  "reprinted": true
}
```

### 11.4. Reimprimir Comprobante de Caja
* **Método:** `POST`
* **Ruta:** `/api/v1/printers/receipts/reprint`
* **Roles:** `CAJERO`, `ADMINISTRADOR`, `EMPRESA`
* **Headers:** `X-Branch-Id: 1`
* **Body:**
```json
{
  "orderId": 1050,
  "terminalCode": "CAJA-01",
  "targetPrinterId": null,
  "reason": "Papel atascado en el primer intento"
}
```
* **Respuesta (`202 Accepted`)**

---

## 12. Módulo 10: Eventos en Tiempo Real (WebSockets / STOMP)

La API expone un broker STOMP integrado para sincronizar interfaces en tiempo real sin polling.

### Configuración de Conexión en el Front-End
* **URL del Endpoint:** `wss://<dominio>/ws` (o `https://<dominio>/ws` vía SockJS).
* **Frame `CONNECT`:**
```text
CONNECT
accept-version:1.2,1.1,1.0
heart-beat:10000,10000
Authorization:Bearer <JWT_TOKEN>
```

### Tópicos de Suscripción Disponibles

#### A. Cambios de Estado en el Mapa de Mesas
* **Destino:** `/topic/branches/{branchId}/tables`
* **Payload Recibido:**
```json
{
  "branchId": 1,
  "tableId": 501,
  "state": "OCCUPIED",
  "orderId": 1050
}
```

#### B. Actualizaciones de Stock en Tiempo Real
* **Destino:** `/topic/branches/{branchId}/stock`
* **Payload Recibido:**
```json
{
  "branchId": 1,
  "skuId": 1,
  "currentStock": 48,
  "isAvailable": true
}
```

#### C. Alertas de Fallo Físico en Impresoras Térmicas (DLQ)
Emitido cuando una impresora térmica se desconecta, se queda sin papel o falla tras 3 reintentos.
* **Destino:** `/topic/branches/{branchId}/printer-alerts`
* **Payload Recibido:**
```json
{
  "branchId": 1,
  "jobId": 99,
  "orderId": 1050,
  "documentType": "KITCHEN_ORDER",
  "printerIp": "192.168.1.200",
  "printerPort": 9100,
  "targetArea": "COCINA",
  "errorMessage": "No se pudo imprimir tras múltiples reintentos.",
  "actionRequired": "Verifique si la impresora tiene papel, está encendida o revise la conexión de red (IP: 192.168.1.200:9100)",
  "failedAt": "2026-10-14T03:10:00Z"
}
```

---

## 13. Manejo de Errores Estándar (RFC 7807 ProblemDetail)

Todas las respuestas de error de la API devuelven una estructura uniforme `application/problem+json`:

### Ejemplo 1: Error de Validación de Formulario (`400 Bad Request`)
```json
{
  "type": "https://api.facilcomanda.com/errors/invalid-argument",
  "title": "Error de Validación de Entrada",
  "status": 400,
  "detail": "Uno o más campos de la solicitud no cumplen con las validaciones requeridas.",
  "instance": "/api/v1/orders",
  "properties": {
    "errorCode": "INVALID_ARGUMENT",
    "timestamp": "2026-10-14T03:12:00Z",
    "traceId": "a98b1c4d-6e7f-4123-890a-bcdef1234567",
    "fieldErrors": {
      "items": "No puede enviar una orden con lista de productos vacía",
      "tableId": "El ID de la mesa es obligatorio"
    }
  }
}
```

### Ejemplo 2: Conflicto de Concurrencia (`409 Conflict`)
```json
{
  "type": "https://api.facilcomanda.com/errors/conflict",
  "title": "Conflicto de Estado",
  "status": 409,
  "detail": "La mesa Mesa 101 ya está ocupada o no se encuentra disponible.",
  "instance": "/api/v1/orders",
  "properties": {
    "errorCode": "CONFLICT",
    "timestamp": "2026-10-14T03:12:05Z",
    "traceId": "b12c3d4e-5f6a-4b7c-8d9e-0123456789ab"
  }
}
```

### Ejemplo 3: Límite de Rate Limiting Excedido (`429 Too Many Requests`)
```json
{
  "type": "https://api.facilcomanda.com/errors/rate-limit-exceeded",
  "title": "Límite de Consultas Excedido",
  "status": 429,
  "detail": "Has superado el límite de consultas permitidas por minuto (15 req/min). Inténtalo de nuevo en 45 segundos.",
  "instance": "/api/v1/customers/lookup",
  "properties": {
    "errorCode": "RATE_LIMIT_EXCEEDED",
    "retryAfterSeconds": 45,
    "timestamp": "2026-10-14T03:12:10Z",
    "traceId": "c34d5e6f-7a8b-4c9d-0e1f-23456789abcd"
  }
}
```

---

## 🎯 Guía Rápida para el Agente IA de Front-End

Si vas a construir una aplicación cliente para este backend:
1. **Para la App de Mozos (Tablet/Móvil):**
   * Conéctate al WebSocket `/topic/branches/{branchId}/tables` para pintar el plano del restaurante con colores según `state`.
   * Para tomar pedido: `POST /api/v1/orders` con `Idempotency-Key` único generado en el cliente.
   * Para pedir la cuenta: `POST /api/v1/orders/{id}/pre-check/print`.
2. **Para la Pantalla de Cocina (KDS):**
   * Consume `GET /api/v1/kitchen/tickets` al iniciar.
   * Conéctate al WebSocket `/topic/branches/{branchId}/kitchen` para recibir nuevas comandas instantáneamente.
   * Permite tocar la pantalla para cambiar el estado con `PATCH /api/v1/kitchen/tickets/{id}/status` (`PREPARING` ➔ `READY`).
3. **Para el Punto de Venta (POS Cajero):**
   * Escucha `/topic/branches/{branchId}/printer-alerts` para mostrar un modal de alerta roja si la impresora térmica se atasca.
   * Para cobrar: `POST /api/v1/invoices/pay` enviando los medios de pago desglosados y datos del cliente.
   * Al final del día: Genera el arqueo con `POST /api/v1/cash/shifts/{shiftId}/close` y visualiza/imprime el `GET /api/v1/cash/shifts/{shiftId}/z-cut`.
