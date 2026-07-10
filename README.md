# marketplace-allegro

Integracja CommerceLink z marketplace Allegro. Implementuje `MarketplaceProvider`
(`marketplace-api:0.2.1`), odkrywana przez SPI. Zakres: import wyłącznie opłaconych
zamówień + cykl życia (accept/ship/cancel). Eksport ofert i faktury — etap 2.

## Jak działa import „tylko opłacone"

Pobierane są checkout formy `status=READY_FOR_PROCESSING` (wg dokumentacji Allegro:
„payment completed") z `fulfillment.status=NEW`. Dodatkowo dla płatności z przedpłatą
(ONLINE, WIRE_TRANSFER, SPLIT_PAYMENT, EXTENDED_TERM) wymagane jest `payment.finishedAt`.
Zamówienia za pobraniem (CASH_ON_DELIVERY) są importowane — płatność przy odbiorze.

## Konfiguracja integracji (formularz w app)

- `clientId` / `clientSecret` — aplikacja z https://apps.developer.allegro.pl
- `refreshToken` — jednorazowy bootstrap: `./scripts/allegro-device-auth.sh <clientId> <clientSecret>`
  (dla produkcji poprzedź `ALLEGRO_OAUTH_BASE=https://allegro.pl/auth/oauth`).
  Po zapisaniu formularza aplikacja odświeża i rotuje tokeny automatycznie
  (access 12 h, refresh 3 miesiące).

## Testowanie na Allegro Sandbox

1. Załóż DWA darmowe konta na https://allegro.pl.allegrosandbox.pl (sprzedawca + kupujący);
   dane fikcyjne, przy 2FA kod SMS to zawsze 123456. Na koncie sprzedawcy uzupełnij dane
   firmy i ustawienia sprzedaży w Centrum Sprzedaży.
2. Zarejestruj aplikację na https://apps.developer.allegro.pl.allegrosandbox.pl
   (device flow, scope orders read+write) — osobne credentiale niż produkcja.
3. Refresh token sprzedawcy: `./scripts/allegro-device-auth.sh <clientId> <clientSecret>`.
4. Wystaw ofertę jako sprzedawca (UI sandboxa), kup ją jako kupujący i OPŁAĆ w symulatorze
   płatności (payments-simulator.allegrosandbox.pl — wybierz płatność zakończoną sukcesem).
   Zamówienie przejdzie w READY_FOR_PROCESSING.
5. Smoke test:
   `ALLEGRO_CLIENT_ID=... ALLEGRO_CLIENT_SECRET=... ALLEGRO_REFRESH_TOKEN=... \
    mvn test -Dtest=AllegroSandboxSmokeTest -Dallegro.sandbox.smoke=true`
6. E2E w aplikacji: uruchom app z
   `-DALLEGRO_API_URL=https://api.allegro.pl.allegrosandbox.pl`
   `-DALLEGRO_TOKEN_URL=https://allegro.pl.allegrosandbox.pl/auth/oauth/token`,
   podłącz integrację Allegro w ustawieniach sklepu i wklej refresh token.
   Uwaga: listener importu (`MarketplaceOrdersImportEventListener`) działa tylko przy
   `application.env=prod` — lokalnie import weryfikuje smoke test.

Ograniczenia sandboxa: symulator nie pozwala zmienić kwoty (brak testów nad/niedopłat),
bywa niestabilny (504), aktywacja kont bywa opóźniona.
