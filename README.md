# marketplace-allegro

Allegro marketplace integration for the CommerceLink platform. Implements `MarketplaceProvider`
(`marketplace-api:0.2.2`), discovered via SPI. Scope: import of paid orders only + order
lifecycle (accept/ship/cancel) + offer export (full create/update/end cycle against
`/sale/product-offers`). Invoices — separate ticket (`updateInvoice` is a no-op).

## How the "paid only" import works

Checkout forms are fetched with `status=READY_FOR_PROCESSING` (per Allegro documentation:
"payment completed") and `fulfillment.status=NEW`. Additionally, for prepaid payment types
(ONLINE, WIRE_TRANSFER, SPLIT_PAYMENT, EXTENDED_TERM) `payment.finishedAt` is required.
Cash-on-delivery orders (CASH_ON_DELIVERY) are imported — payment happens on delivery.

## Integration configuration (form in the app)

- `clientId` / `clientSecret` — an application from https://apps.developer.allegro.pl
- `refreshToken` — one-time bootstrap: obtain it via the OAuth2 device flow (see
  "Testing on the Allegro Sandbox", step 3; for production use the
  `https://allegro.pl/auth/oauth` base). After saving the form the application
  refreshes and rotates tokens automatically (access 12 h, refresh 3 months).

## GPSR — responsible person and producer

The offer export uses the Allegro account dictionaries (defined in the panel:
My Allegro → Sales settings, or via the `/sale/responsible-persons` and
`/sale/responsible-producers` API):

- **Responsible person** (`productSet[0].responsiblePerson`, required by
  Allegro for producers from outside the EU): selected by the entry name
  equal to the product brand (case-insensitive); when there is no match and
  the list contains exactly one entry, that entry is used; otherwise the
  offer is sent without a responsible person.
- **Responsible producer**: the producer from the Allegro catalog product
  page is preferred; when the catalog has none, the account dictionary entry
  named after the product brand is used; with neither, the offer is skipped
  (WARN in the logs).

Convention: name the dictionary entries exactly as the brand appears in the
feed (e.g. `NZXT`); a responsible-person entry that is the only one on the
list acts as the default for all brands.

Offer parameters 224017 (manufacturer code) and 237206 (model) are added only
when they appear on the parameter list of the product's category
(`GET /sale/categories/{id}/parameters`).

## Testing on the Allegro Sandbox

1. Create TWO free accounts at https://allegro.pl.allegrosandbox.pl (seller + buyer);
   fictional data is fine, the 2FA SMS code is always 123456. On the seller account fill in
   the company data and sales settings in the Sales Center.
2. Register an application at https://apps.developer.allegro.pl.allegrosandbox.pl
   (device flow, scope orders read+write) — credentials are separate from production.
3. Seller refresh token — manual OAuth2 device flow (RFC 8628) against
   `BASE=https://allegro.pl.allegrosandbox.pl/auth/oauth` (production: `https://allegro.pl/auth/oauth`):
   `curl -u "$CLIENT_ID:$CLIENT_SECRET" -d "client_id=$CLIENT_ID" "$BASE/device"`, open the
   returned `verification_uri_complete` logged in as the seller and confirm, then poll
   `curl -u "$CLIENT_ID:$CLIENT_SECRET" -d "grant_type=urn:ietf:params:oauth:grant-type:device_code" -d "device_code=<device_code>" "$BASE/token"`
   until it returns the `refresh_token` (on `authorization_pending` wait `interval` seconds and retry).
4. List an offer as the seller (sandbox UI), buy it as the buyer and PAY for it in the
   payment simulator (payments-simulator.allegrosandbox.pl — choose a successful payment).
   The order transitions to READY_FOR_PROCESSING.
5. Smoke test:
   `ALLEGRO_CLIENT_ID=... ALLEGRO_CLIENT_SECRET=... ALLEGRO_REFRESH_TOKEN=... \
    mvn test -Dtest=AllegroSandboxSmokeTest -Dallegro.sandbox.smoke=true`

   Note: Allegro rotates refresh tokens — the smoke test's token refresh invalidates the
   token you exported. Run a fresh device-flow (step 3) to obtain a new refresh token before
   pasting it into the app form in step 6; a consumed token pasted into the form will
   immediately mark the connection as lost.
6. E2E in the application: run the app with
   `-DALLEGRO_API_URL=https://api.allegro.pl.allegrosandbox.pl`
   `-DALLEGRO_TOKEN_URL=https://allegro.pl.allegrosandbox.pl/auth/oauth/token`,
   connect the Allegro integration in the store settings and paste the refresh token.
   Note: the import listener (`MarketplaceOrdersImportEventListener`) runs only with
   `application.env=prod` — locally the import is verified by the smoke test.

Sandbox limitations: the payment simulator does not allow changing the amount (no over/underpayment
tests), it can be unstable (504s), and account activation can be delayed.
