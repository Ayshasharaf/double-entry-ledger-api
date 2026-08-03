package com.doubleledger.ledger.util;

import com.fasterxml.jackson.core.JsonProcessingException; // for JSON serialization failures
import com.fasterxml.jackson.databind.ObjectMapper; // for JSON serialization/deserialization
import com.fasterxml.jackson.databind.SerializationFeature; //enum
import org.springframework.stereotype.Component; //mark this as a Spring component

import java.nio.charset.StandardCharsets; //charset for UTF-8 encoding
import java.security.MessageDigest; //for SHA-256 hash computation
import java.security.NoSuchAlgorithmException; //for hash algorithm exceptions
import java.util.UUID; //for UUID generation , used in reversal hash path

/**
 * Computes a deterministic SHA-256 fingerprint of an HTTP request for idempotency checks.
 * Hashes method + path + canonical JSON body, matching common API idempotency practice.
 */
@Component
public class RequestHasher {

    private static final String TRANSACTION_POST_SCOPE = "POST:/api/v1/transactions:"; //prefix for transaction posting hash
    private static final String REVERSAL_POST_SCOPE = "POST:/api/v1/transactions/{id}/reversals:"; //prefix for reversal posting hash

    private final ObjectMapper canonicalMapper; //Canonical JSON mapper for consistent serialization

    public RequestHasher(ObjectMapper objectMapper) { //constructor for RequestHasher
        this.canonicalMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true); //sorted order so {"b":1,"a":2} and {"a":2,"b":1} hash the same.
    }

    public String hashPostTransaction(Object requestBody) {
        try {
            String canonicalBody = canonicalMapper.writeValueAsString(requestBody);  //serialize request body to JSON string
            return sha256Hex(TRANSACTION_POST_SCOPE + canonicalBody);//joins prefix and body for hashing
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to hash request body.", ex);
        }
    }

    public String hashReverseTransaction(UUID journalEntryId, Object requestBody) {
        try {
            String canonicalBody = canonicalMapper.writeValueAsString(
                    requestBody != null ? requestBody : new Object());
            return sha256Hex(REVERSAL_POST_SCOPE + journalEntryId + ":" + canonicalBody); //joins prefix, id, and body for hashing . 
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to hash request body.", ex);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available.", ex);
        }
    }
}
