package com.doubleledger.wallet.repository;

import com.doubleledger.wallet.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByIdAndUserId(UUID id, String userId);
}
