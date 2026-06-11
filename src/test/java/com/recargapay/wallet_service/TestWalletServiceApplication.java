package com.recargapay.wallet_service;

import org.springframework.boot.SpringApplication;

public class TestWalletServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(WalletServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
