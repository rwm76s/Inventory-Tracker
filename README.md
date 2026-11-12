# Inventory Tracker

A small-business inventory app: it shows what stock a business has **right now** and keeps a complete, dated history of **how that stock changed over time**.

Built with Spring Boot 4 (Java 17), Spring Data JPA, Thymeleaf, and PostgreSQL.

---

## Quick start

In order to minimize the amount of dependency hassle, both the application and the database only require docker desktop to be installed.

### Docker (app + PostgreSQL)

Prerequisite: [Docker Desktop](https://www.docker.com/products/docker-desktop/) (running).

```bash
git clone https://github.com/rwm76s/Inventory-Tracker.git
cd Inventory-Tracker
docker compose up --build
```

Open **http://localhost:8080**. The first build downloads dependencies and takes a few minutes.

- Data is kept in a named Docker volume, so it survives restarts.
- Reset to a clean, re-seeded state: `docker compose down -v`, then `docker compose up --build`.
- Port already in use? Override with environment variables, e.g. `APP_PORT=8081 DB_PORT=5433 docker compose up --build`.

### Run the tests

```bash
./mvnw test
```
Or, simply comment out "-DskipTests" in the dockerfile 

---

## Try it (5-minute tour)

The sample data has 7 products, each with about 30 days of backdated history:

| SKU | Product | On hand | Status |
|---|---|---|---|
| 6579088 | Widget | 18 | In stock |
| 6574343 | Gadget | 12 | Low stock |
| 6572318 | Gasket | 0 | Out of stock |
| 6578903 | Bolt Pack | 500 | In stock |
| 6578456 | USB Cable | 42 | In stock |
| 6578103 | Shipping Labels | 50 | Low stock |
| 6570486 | Legacy Adapter | 0 | Discontinued (history kept) |

1. **Home (`/`)** links to the two main pages.
2. **Products (`/products`)** lists every product with current stock and a status badge. Search by name or SKU, or filter by status.
3. **Movements (`/movements`)** is search-first: nothing is shown until you search by name or SKU. Then you see that product's ledger (filter by date range or type), and can open **Record a movement** to add one.
4. **Try the key rule.** Search `6574343` (Gadget), then record a **Fulfillment** of `15` with **When** set to about 20 days ago. It is rejected, because it would leave stock at -3 on a later date in the history, even though today's stock (12) could cover it. Record `12` at the same date and it is accepted (the balance dips to exactly 0, which is allowed).
5. **Try the other guards.** Fulfilling more than is on hand, using reason `OTHER` without a note, or a future date are all rejected with a clear message.

### JSON API

The same logic is available as a JSON API (this is also how products are created and edited, since there is no product form in the UI):

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/products?q=&status=` | List products with on-hand and status |
| POST | `/api/products` | Create a product |
| GET / PUT | `/api/products/{id}` | Read / update a product (SKU can't change) |
| GET | `/api/products/{id}/movements?page=&size=` | Paged history, newest first |
| POST | `/api/products/{id}/movements` | Record a movement |

```bash
curl -X POST localhost:8080/api/products -H 'Content-Type: application/json' \
  -d '{"sku":"6580001","name":"Sprocket","reorderThreshold":5,"unitCost":2.50}'

curl -X POST localhost:8080/api/products/1/movements -H 'Content-Type: application/json' \
  -d '{"type":"RECEIPT","quantityDelta":10,"reason":"SUPPLIER_DELIVERY"}'
```

Errors use standard problem-detail JSON: `400` invalid input, `404` unknown product, `409` conflict (insufficient stock, duplicate SKU).

---

## What is built

- **Products** with a SKU, name, reorder threshold, unit cost, and an active flag (products are discontinued, never deleted).
- **An append-only stock ledger.** Every change (receipt, fulfillment, adjustment) is a dated row with a type, a signed quantity, a reason code, an optional note, and a generated reference (`200000123`).
- **Derived stock levels.** Current stock is the sum of a product's ledger, and the status (In stock / Low stock / Out of stock) is computed from stock and the reorder threshold. Neither is stored, so they can't drift out of sync with the history.
- **Backdating support with integrity.** A movement can be recorded for a past date (a forgotten delivery, a late count). The app guarantees stock never goes negative at *any point* in history.
- **Server-rendered pages** (home, products, movements) and the **JSON API** above.
- **Sample data** loaded on first start (idempotent: it never duplicates).
- **Docker setup** (app + PostgreSQL + named volume).

## What I intentionally left out

| Left out | Why                                                                                                                                                                                                                         |
|---|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Users, login, roles | Not needed to show stock and its history; movements record "system" as the actor. First thing I'd add next.                                                                                                                 |
| Product create/edit screens | Typically employees (like the ones this tool is targeted towards), should not be adding new product into the system when it should be handled by administrators. By implementing roles, this feature becomes more feasible. |
| Multiple locations / warehouses | Would change the model (stock per product *per location*); a single location keeps the application simpler and correct.                                                                                                     |
| Suppliers, purchase orders, customers | Those are separate workflows. Receipts and fulfillments carry a reason code and a note instead.                                                                                                                             |
| Deleting or editing movements | A ledger is only trustworthy if history can't be rewritten. Mistakes are fixed with an adjustment.                                                                                                                          |
| Reports, charts, CSV export | As much as I wanted it, it simply was not within scope for this project.                                                                                                                                                    | |

---

## Technical decisions and trade-offs

**Two timestamps on every movement.**
`occurredAt` is when it really happened (can be backdated); `createdAt` is when it was recorded. This keeps both the business timeline and the audit trail.

**Rule: stock never goes negative at any point in history.**
Checking only "now" would let a backdated fulfillment quietly create a negative balance in the past. So removal movements are validated by replaying the ledger forward from their date with the new movement applied. Positive movements can't lower any balance, so they skip the check. Trade-off: one extra query per removal that loads later movements for that one product, which is fine at this scale.

**Concurrency: a row lock per product.**
Recording a movement locks that product's row (`PESSIMISTIC_WRITE`) so two people can't both take the last unit. Only writes to the *same* product wait for each other. The lock only protects writers that go through `StockService`, which is why all writes do.

**Integrity is enforced at several layers.**
Input validation (bean validation), business rules (service), and the database (a check constraint tying the sign of the quantity to the movement type, a unique SKU, and a not-null/length schema). Movements are marked `@Immutable` in JPA, and no service, API endpoint, or page updates or deletes them. *(The repository still extends `JpaRepository`, so the delete methods exist in code but nothing calls them.)*

**SKUs are digits-only text, not integers.**
A SKU is an identifier, not a quantity: you never do arithmetic on it, leading zeros matter, and real barcodes can overflow an integer. Validation (digits only, up to 12 characters) gives the "numbers" feel without those risks, and keeps substring search simple.

**Enums for movement type, reason, and status.**
Reason codes can be grouped and counted later; free text goes in the note.

**Thymeleaf pages instead of a JavaScript front end.**
Fewer moving parts to build and explain, and the business logic stays in one place (the service layer), shared by the pages and the API. Trade-off: less interactive than a single-page app.

**PostgreSQL in Docker.**
By keeping everything in docker, there is no need to worry about setting up separate databases or installing other dependencies beforehand.

**Plain DTO records at the API boundary.**
Entities never go over HTTP, so schema changes can't leak into the JSON and lazy-loading problems can't occur.

## Testing approach

I concentrated testing on the part where a bug would be most important: **the ledger rules in `StockService`**. There are 12 tests (plus a context-loads smoke test) running against in-memory H2 with the clock pinned, so backdating cases are deterministic:

- The headline scenario: a backdated removal that looks fine "then" and "now" but would push a *middle* date negative, and its accepted twin that lands exactly on zero.
- Boundaries: exactly zero is allowed, over-removal rejected, same-instant movements, backdating before any stock existed.
- Input rules: future dates, sign vs. type, `OTHER` needing a note, unknown product.
- Derived status at the threshold boundaries.
- Rejected requests leave the ledger untouched.

**Not covered by automated tests:** controllers, the JSON API, and the Thymeleaf pages. They are thin wrappers around the tested service, and I verified them by manually with curl.

## Known limitations

- No authentication; every movement is recorded as "system" for now.
- Products are created and edited through the API only.
- The product list isn't paged (I think it is fine for this but not at scale).
- Times display in the zone the app runs in; in Docker that is UTC, so they may differ from other computer clocks.
- Hibernate creates the schema (`ddl-auto`); there are no migration scripts.

## With another week

1. Authentication, so each movement records the real user.
2. Make the movement repository truly append-only by exposing only `save` and read queries.
3. Product create/edit forms and a "History" link from each product row.
4. A running-balance column on the movement history, and a low-stock dashboard.
5. Search-first and paging on the products page for large catalogs; CSV export.

## Time spent

Approximately 12-15 hours split between Monday, Tuesday, and Wednesday.

## How I used AI tools

I used Claude (not Claude Code) as a pair programmer, working one small piece at a time and running and reviewing each piece before moving on. 
Unfortunately, due to the tight deadline, the application used more AI code than I typically would have wanted. 
However, I still feel that it adheres to my initial vision of how I wanted to design it while also learning new things along the way.

## Project layout

```
src/main/java/invoSolutions/inventorytracker/
  model/        entities and enums (Product, StockMovement, MovementType, ...)
  repository/   Spring Data repositories (queries live here)
  service/      business rules: StockService (ledger), ProductService (products/stock levels)
  api/          JSON controller, request/response records, error handling
  controller/   page controller (Thymeleaf)
  config/       seed data, clock
  exceptions/   domain exceptions mapped to HTTP errors
src/main/resources/
  templates/    Thymeleaf pages          static/css/   styles
src/test/       StockServiceTest (ledger rules)
Dockerfile, docker-compose.yml
```
