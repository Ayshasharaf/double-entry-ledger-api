package com.doubleledger.wallet.repository;

import com.doubleledger.wallet.model.Topup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TopupRepository extends JpaRepository<Topup, UUID> {

    Optional<Topup> findByIdempotencyKey(String idempotencyKey);
}
