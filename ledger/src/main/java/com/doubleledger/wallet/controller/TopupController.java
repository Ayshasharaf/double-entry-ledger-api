package com.doubleledger.wallet.controller;

import com.doubleledger.wallet.dto.CreateTopupRequest;
import com.doubleledger.wallet.dto.TopupResponse;
import com.doubleledger.wallet.service.TopupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets/{walletId}/topups")
public class TopupController {

	private final TopupService topupService;

	public TopupController(TopupService topupService) {
		this.topupService = topupService;
	}

	@PostMapping
	public ResponseEntity<TopupResponse> createTopup(
			@PathVariable UUID walletId,
			@RequestHeader("X-User-Id") String userId,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody CreateTopupRequest request) {
		var topup = topupService.createTopup(
				walletId, userId, request.amountMinorUnits(), idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(TopupResponse.from(topup));
	}
}