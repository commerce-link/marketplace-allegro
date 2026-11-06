package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AllegroBillingWireFormatTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesBillingEntriesWithUnknownFields() throws Exception {
        // given: shaped like a real sandbox response, with fields the adapter does not read
        String json = """
                {
                  "billingEntries": [
                    {
                      "id": "c4b5e1a2-0000-4000-8000-000000000001",
                      "occurredAt": "2026-11-05T12:37:14.015Z",
                      "type": {"id": "SUC", "name": "Prowizja od sprzedaży", "group": {"id": "obligatory"}},
                      "offer": {"id": "7654321", "name": "Laptop"},
                      "value": {"amount": "-10.10", "currency": "PLN"},
                      "tax": {"percentage": "23"},
                      "balance": {"amount": "-113.53", "currency": "PLN"},
                      "order": {"id": "29b3b2a0-c60d-11f1-a870-7b4a0514b9e7"},
                      "asset": "DEBIT",
                      "marketplaceId": "allegro-pl",
                      "additionalInfo": [{"type": "feeId", "name": "Fee", "value": "x"}]
                    },
                    {
                      "id": "c4b5e1a2-0000-4000-8000-000000000002",
                      "occurredAt": "2026-10-31T22:59:59Z",
                      "type": {"id": "SUM", "name": "Podsumowanie"},
                      "value": {"amount": "0.00", "currency": "PLN"},
                      "asset": "DEBIT"
                    }
                  ]
                }
                """;

        // when
        AllegroBillingEntriesResponse response = objectMapper.readValue(json, AllegroBillingEntriesResponse.class);

        // then
        AllegroBillingEntriesResponse.Entry suc = response.billingEntries().get(0);
        assertEquals("SUC", suc.type().id());
        assertEquals("-10.10", suc.value().amount());
        assertEquals("7654321", suc.offer().id());
        assertEquals("29b3b2a0-c60d-11f1-a870-7b4a0514b9e7", suc.order().id());
        assertEquals("23", suc.tax().percentage());
        AllegroBillingEntriesResponse.Entry summary = response.billingEntries().get(1);
        assertNull(summary.offer());
        assertNull(summary.order());
        assertNull(summary.tax());
    }
}
