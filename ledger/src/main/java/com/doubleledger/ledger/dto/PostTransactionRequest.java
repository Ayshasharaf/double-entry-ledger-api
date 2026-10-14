package com.doubleledger.ledger.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import com.doubleledger.ledger.model.JournalEntryStatus;


import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents the request payload to post a complete multi-legged ledger transaction.
 */
@Schema(example = """
        {
          "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
          "description": "transfer wallet to pool",
          "legs": [
            {
              "accountId": "11111111-1111-1111-1111-111111111111",
              "amountMinorUnits": 500,
              "direction": "CREDIT"
            },
            {
              "accountId": "22222222-2222-2222-2222-222222222222",
              "amountMinorUnits": 500,
              "direction": "DEBIT"
            }
          ]
        }
        """)
public class PostTransactionRequest {

    // Domain-level idempotency key to prevent processing the same economic event twice
    private UUID idempotencyKey;

    // A description of the transfer (e.g., "Subscription payment for invoice_923")
    private String description;

    // The status of the journal entry
    private JournalEntryStatus status;
    public JournalEntryStatus getStatus() { return status; }
    public void setStatus(JournalEntryStatus status) { this.status = status; }

    // The individual splits of this transaction. They must sum to zero!
    @ArraySchema(
            minItems = 2,
            arraySchema = @Schema(description = "At least two balanced legs (e.g. CREDIT one account, DEBIT another)"))
    private List<TransactionLegDto> legs;

    // Custom metadata map allowing clients to store arbitrary info, mimicking Stripe’s "metadata" field
    private Map<String, Object> metadata;

    // --- Empty Constructor ---
    public PostTransactionRequest() {}

    // --- Getters & Setters ---
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<TransactionLegDto> getLegs() { return legs; }
    public void setLegs(List<TransactionLegDto> legs) { this.legs = legs; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}