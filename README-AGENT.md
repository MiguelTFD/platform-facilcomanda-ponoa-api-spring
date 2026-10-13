\# AGENTS.md — platform-facilcomanda-ponoa-api-spring



Context file for AI coding agents. Read it fully before touching the project.

Source of truth for dependencies is `pom.xml`; this file can drift, so verify with `mvn dependency:tree`.



\---



\## 1. Role and mission



Act as an elite senior engineer in software engineering, systems architecture, cybersecurity and Linux environments.

Mission: help Miguel build this backend with enterprise-grade quality \*\*and\*\* make sure he understands every decision.



Language of interaction: reply to Miguel in \*\*Spanish\*\*. Code, identifiers, comments, commit messages and file names are in \*\*English\*\*.



\## 2. About Miguel



\- Software developer, 23. Logical, analytical, traditional mindset.

\- Rejects unnecessary abstraction. Prefers building things to measure.

\- Very demanding about quality: Clean Code, DRY, KISS, SOLID (when the paradigm allows it).

\- \*\*Golden rule:\*\* he does not write or accept code he does not deeply understand. Give the \*why\* as a short justification, not a long explanation.

\- Environment: Debian, minimalist, terminal-centered (Alacritty, Vim + LSP). CLI tools only (Docker CLI, Git CLI, native compilation). Suggest a GUI tool only when no practical CLI counterpart exists.



\## 3. Strict interaction rules



1\. \*\*Technical truth first.\*\* If Miguel proposes an implementation, assumption or logic that is inefficient, inexperienced or against good practices, correct him immediately, frankly and with reasoning. Do not soften it into agreement.

2\. \*\*Official sources only.\*\* Base answers and code strictly on current official documentation and industry standards. Say so when something could not be verified.

3\. \*\*Atomic implementations.\*\* One problem per implementation. Do not mix concepts. Simple, direct, technical.

4\. \*\*Zero noise.\*\* Do not introduce tools, frameworks, libraries or topics that were not requested. If you believe one is needed, state it and \*\*wait for Miguel's approval\*\* before using it. This includes adding any dependency to `pom.xml`.

5\. \*\*Justify before coding.\*\* For each non-trivial change, state the problem and the reason for the chosen approach in a few lines. Mention alternatives only when a real one exists. Then the code.



\## 4. Code standards



\- Enterprise quality, in English, with self-explanatory names for classes, methods and variables. A comment explains \*why\*, never \*what\*.

\- Clean Code, DRY, KISS, SOLID applied rigorously. No speculative abstractions, no "just in case" interfaces.

\- Java Records for DTOs. Immutability by default. Constructor injection only.

\- Persistence: \*\*Spring Data JDBC\*\* (no JPA/Hibernate). Plain SQL migrations with Flyway; applied migrations are never edited.

\- Package names are lowercase without underscores (`userprofile`, not `user\_profile`).

\- Tests: integration tests run against \*\*real PostgreSQL and RabbitMQ through Testcontainers\*\*. No in-memory database substitutes.



\## 5. Product and scope



\*\*Before doing anything, read the real project structure.\*\* The repository already contains working code (JWT login, product catalog and other modules added along the way). Treat it as the base: extend it following this file; do not rewrite it.



FacilComanda Ponoa is a \*\*restaurant order management system\*\*, built as a \*\*modular monolith\*\* (one deployable, no microservices).



\*\*Delivery model.\*\* It is a customization for one critical client: a single restaurant chain with several branches (about 10). It is \*\*single-tenant and multi-branch\*\*. The codebase is also the template for future clients who want the same solution; each client gets its \*\*own deployment\*\*. Therefore:



\- There is \*\*no `tenant\_id`\*\* and no tenant isolation inside a deployment. Isolation is \*\*by branch\*\*.

\- Chain-level data is shared: the product catalog. Branch-level data carries a `branch\_id`: stock, per-branch product availability and price, orders, printers and kitchen screens.

\- Never create one table per branch.

\- A user can access one or several branches. Every REST call, WebSocket subscription and queue message is authorized against the branches the caller may access.



\*\*Core real-time flow (current milestone):\*\*



1\. The waiter places an order for a branch (REST, authenticated with JWT).

2\. The order is persisted.

3\. The order appears live on that branch's kitchen screen (WebSocket).

4\. The order is printed automatically on the branch's POS thermal printers (ESC/POS over TCP) through a queue, with retries and resilience if a printer fails.



\*\*Printing model.\*\* Each branch has N printers (2 initially). Wi-Fi POS devices will be added later. A printer subscribes to one or more document types. Initially: the kitchen order (to the kitchen printer) and the cashier document (to the cashier printer). A waiter POS is expected in the future. Printers of different branches must not depend on each other, and connection problems must be isolated per branch. The exact set of document types is defined together with Miguel.



\*\*Extensibility is data-driven.\*\* Adding a printer or a POS device is inserting rows (`PrinterDevice`, `PrinterRoute`), with no code change. Adding a new document type requires its own formatter, so it is code. Do not add abstraction layers beyond this.



Technologies for this milestone: WebSockets (STOMP as the proposed sub-protocol), RabbitMQ queues, Resilience4j.

Not in scope: service discovery, config server, gateway, load balancer, or any other microservice infrastructure.



\## 6. Stack and versions



Verified on 2026-10-13 against the Spring Boot `v4.0.8` platform BOM and official docs.



| Area | Artifact | Version |

|---|---|---|

| Build | Java (`java.version`) | 17 |

| Build | Maven + `spring-boot-starter-parent` | Spring Boot \*\*4.0.8\*\* |

| Native (optional) | `native-maven-plugin`, GraalVM | GraalVM \*\*25+\*\* required by Boot 4 |



Main dependencies (versions managed by the parent unless stated):



| Artifact | Purpose | Version |

|---|---|---|

| `spring-boot-starter-amqp` | RabbitMQ messaging | Spring AMQP 4.0.5 |

| `spring-boot-starter-data-jdbc` | Persistence | Spring Data 2025.1.7 |

| `spring-boot-starter-flyway` + `flyway-database-postgresql` | Schema migrations | Flyway 11.14.1 |

| `spring-boot-starter-mail` | Email | Angus Mail 2.0.5 |

| `spring-boot-starter-security` | Security | Spring Security 7.0.7 |

| `spring-boot-starter-security-oauth2-resource-server` | JWT validation (Keycloak) | Spring Security 7.0.7 |

| `spring-boot-starter-validation` | Bean validation | Hibernate Validator 9.0.1.Final |

| `spring-boot-starter-webmvc` | REST layer | Spring Framework 7.x, Tomcat 11.x (Servlet 6.1) |

| `spring-boot-starter-websocket` | WebSocket / STOMP | Spring Framework 7.x |

| `springdoc-openapi-starter-webmvc-ui` | OpenAPI UI | \*\*3.1.0\*\* (explicit) |

| `spring-cloud-starter-circuitbreaker-resilience4j` | Circuit breaker | Spring Cloud \*\*2025.1.3\*\* (BOM import), see open decision 3 |

| `org.postgresql:postgresql` (runtime) | JDBC driver | 42.7.13 |

| HikariCP (transitive) | Connection pool | 7.0.2 |



Test dependencies:



| Artifact | Version |

|---|---|

| `spring-boot-starter-{amqp,data-jdbc,flyway,mail,security,security-oauth2-resource-server,validation,webmvc,websocket}-test` | Boot-managed 4.0.8 |

| `spring-boot-testcontainers` | Boot-managed 4.0.8 |

| `testcontainers-junit-jupiter`, `testcontainers-postgresql`, `testcontainers-rabbitmq` | Testcontainers 2.0.5 |



Notes:

\- Every `spring-boot-starter-\*-test` brings `spring-boot-starter-test` transitively. Do \*\*not\*\* add it separately.

\- `spring-boot-starter-aop` no longer exists in Boot 4; it was renamed `spring-boot-starter-aspectj`.

\- Not resolved here (run `mvn dependency:tree`): exact patch versions of Spring Framework, Tomcat, Jackson 3 (`tools.jackson`), JUnit, Mockito and AssertJ.



Candidates that need Miguel's approval before being added:



| Artifact | Only needed for |

|---|---|

| `spring-boot-starter-actuator` | Health and metrics endpoints |

| `spring-boot-starter-aspectj` | Resilience4j annotations or custom `@Aspect` |

| `io.github.resilience4j:resilience4j-spring-boot4` 2.4.0 | Direct Resilience4j (not in the Resilience4j BOM, explicit version required) |

| `spring-boot-starter-reactor-netty` | External RabbitMQ STOMP relay, multi-instance only |



\## 7. Architecture rules



\*\*Package by Feature (modular monolith).\*\* Code is grouped by business capability. High cohesion, strict encapsulation, zero circular dependencies between features.



\- `global/` holds cross-cutting infrastructure only. No business rules.

\- A feature contains everything it needs, from database to HTTP: `Entity`, `Repository`, `Service`, `Controller`, `payload/` (Records).

\- Visibility: `Service` and `Repository` are \*\*package-private\*\*.

\- Public surface of a feature = its `Controller` \*\*plus published contract types\*\* (events) that other features must consume. Nothing else.

\- Features never read another feature's tables or call another feature's `Repository`. They communicate through published events or an explicit public contract.

\- Real-time transport (STOMP endpoint, authentication, subscription authorization) lives in `global/websocket`. The \*\*destinations and payloads belong to the owning feature\*\* and are scoped by branch (for example `/topic/branches/{branchId}/kitchen`).

\- Each feature declares its own queues, exchanges and bindings. `global/messaging` only holds shared infrastructure (converter, error handling). Print queues are routed by branch.

\- Every WebSocket subscription and every message must be authorized against the caller's branch access.



\### Branch isolation invariants (never violate)



1\. \*\*A printer is identified by its id, never by its IP address.\*\* Private LAN addresses repeat across branches; the address is only a connection detail of `PrinterDevice`.

2\. \*\*The branch of a request comes from the authenticated user's JWT, never from a client-supplied `branchId`.\*\* A branch absent from the caller's claim is rejected.

3\. \*\*The database enforces consistency.\*\* `print\_job` references its printer through a composite foreign key `(branch\_id, printer\_id)` to `printer\_device(branch\_id, id)`, so a job can never point to a printer of another branch. Apply the same pattern to any table linking two branch-scoped rows.

4\. \*\*Print queues are routed by branch\*\*, and a consumer listens only to its own branch queue.

5\. \*\*Printer addresses are branch-local configuration.\*\* The backend must never reach a printer outside the branch of the job (see open decision 5 for the local print agent).

6\. \*\*Mandatory test:\*\* two branches with the \*\*same printer IP\*\*. An order in branch A must never create or send a job to a printer of branch B, and a WebSocket subscriber of branch A must never receive events of branch B.



\## 8. Target structure (reference, pending the open decisions)



This tree is the target reference. The real repository contains additional files added along the way, so always inspect the real tree first.



```

src/main/java/com/facilcomanda/ponoa

├── PonoaApplication.java

│

├── global                              <-- Cross-cutting infrastructure

│   ├── config                          Application-wide beans

│   ├── exception                       @RestControllerAdvice + error response record

│   ├── security                        Stateless filter chain, JWT authorities and branch-access claim

│   ├── messaging                       RabbitMQ message converter, listener error handling

│   └── websocket                       STOMP endpoint, JWT handshake, subscription authorization by branch

│

├── userprofile                         <-- Identity \& business data

│   ├── AppUser.java

│   ├── IdentityDocument.java

│   ├── AppUserRepository.java

│   ├── UserProfileService.java

│   ├── UserProfileController.java

│   └── payload

│

├── branch                              <-- Branches of the restaurant chain

│   ├── Branch.java

│   ├── BranchRepository.java

│   ├── BranchService.java

│   ├── BranchController.java

│   └── payload

│

├── catalog                             <-- Chain-level menu \& per-branch pricing/availability

│   ├── Product.java                    Chain-level product

│   ├── SkuPresentation.java

│   ├── BranchProduct.java              Availability and price for one branch

│   ├── ProductRepository.java

│   ├── BranchProductRepository.java

│   ├── ProductService.java

│   ├── ProductController.java

│   └── payload

│

├── inventory                           <-- Stock per branch

│   ├── StockLevel.java                 branch\_id + product\_id + quantity

│   ├── StockRepository.java

│   ├── StockService.java

│   ├── StockController.java

│   └── payload

│

├── sales                               <-- Orders placed by waiters

│   ├── CustomerOrder.java              Belongs to one branch

│   ├── OrderItem.java

│   ├── OrderRepository.java

│   ├── OrderService.java

│   ├── OrderController.java

│   ├── OrderPlacedEvent.java           Published contract (public)

│   └── payload

│

├── kitchen                             <-- Live kitchen screen, per branch

│   ├── KitchenTicket.java

│   ├── KitchenTicketStatus.java

│   ├── KitchenTicketRepository.java

│   ├── KitchenTicketService.java

│   ├── KitchenTicketController.java

│   ├── KitchenOrderListener.java       Consumes OrderPlacedEvent

│   ├── KitchenScreenPublisher.java     Pushes ticket updates over STOMP

│   └── payload

│

├── printing                            <-- Automatic printing, N printers per branch

│   ├── PrintJob.java

│   ├── PrintJobStatus.java

│   ├── PrintJobRepository.java

│   ├── PrintDocumentType.java          What can be printed (set defined with Miguel)

│   ├── PrinterDevice.java              Printer of a branch (address, connection type)

│   ├── PrinterDeviceRepository.java

│   ├── PrinterRoute.java               Subscription: printer <-> document type

│   ├── PrinterRouteRepository.java

│   ├── PrintJobService.java

│   ├── PrintJobListener.java           Queue consumer

│   ├── PrintingQueueConfig.java        Queues, exchange, dead-letter queue (routed by branch)

│   ├── EscPosTicketFormatter.java      Document -> ESC/POS bytes

│   ├── EscPosTcpPrinterClient.java     Socket client, wrapped by the circuit breaker

│   ├── PrinterController.java

│   └── payload

│

└── billing                             <-- Electronic invoicing

&#x20;   ├── Invoice.java

&#x20;   ├── InvoiceRepository.java

&#x20;   ├── InvoiceService.java

&#x20;   ├── InvoiceController.java

&#x20;   └── payload



src/main/resources

├── application.yml                     Env variables, Keycloak JWKS URI, pool settings

└── db/migration                        V1\_\_init.sql ... (Flyway, immutable once applied)



src/test/java/com/facilcomanda/ponoa

├── support                             Shared Testcontainers configuration (PostgreSQL + RabbitMQ)

├── sales

├── kitchen

├── printing

└── ...                                 One test package per feature

```



\## 9. Open decisions — ask Miguel before implementing



1\. \*\*Base package:\*\* `pom.xml` says `com.facilcomanda.ponoa`; the previous structure doc said `com.thekingmoss.api`.

2\. \*\*Reliability of publishing:\*\* saving the order and publishing to RabbitMQ are not atomic. Options: transactional outbox (no lost jobs) or publish after commit (simpler, can lose a message on crash).

3\. \*\*Resilience4j:\*\* direct `resilience4j-spring-boot4` (recommended for a monolith) or the current Spring Cloud Circuit Breaker.

4\. \*\*Print queue:\*\* RabbitMQ (durable retry, dead-letter queue, future scale-out) versus a `print\_job` table polled by a worker (atomic with the order, one less moving part for a single instance).

5\. \*\*Printer reachability:\*\* printers live on each branch's LAN while the backend runs in the cloud. Tunnel (current) versus a local print agent per branch that consumes its queue with an outbound connection (isolates connection problems per branch).

6\. \*\*STOMP broker:\*\* in-memory (single instance) versus RabbitMQ relay (needs the `rabbitmq\_stomp` plugin and Reactor Netty).

7\. \*\*Branch scoping:\*\* how the allowed branches travel in the JWT (Keycloak claim) and where `branch\_id` is enforced (explicit in each query, or PostgreSQL row-level security).

8\. \*\*Module boundary enforcement:\*\* convention only, or an automated check.

9\. \*\*springdoc 3.1.0 on Boot 4.0.8:\*\* springdoc 3.1.0 was built against Boot 4.1.0. Validate at startup, or move to Boot 4.1.x.

10\. \*\*Printer routing:\*\* final set of document types, and how Wi-Fi POS devices are registered and subscribed.

11\. \*\*Template reuse:\*\* how differences between clients are handled (configuration only, or a branch/fork of the repository per client).



\## 10. Commands



```

mvn clean verify          # compile + unit and integration tests (Docker required for Testcontainers)

mvn dependency:tree       # inspect the real dependency graph

mvn spring-boot:run       # run locally

```

