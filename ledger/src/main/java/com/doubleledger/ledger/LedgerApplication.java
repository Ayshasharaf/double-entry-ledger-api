package com.doubleledger.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = "com.doubleledger")
@org.springframework.boot.persistence.autoconfigure.EntityScan(basePackages = "com.doubleledger")
@EnableJpaRepositories(basePackages = "com.doubleledger")
@EnableAsync
public class LedgerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LedgerApplication.class, args);
	}

}
