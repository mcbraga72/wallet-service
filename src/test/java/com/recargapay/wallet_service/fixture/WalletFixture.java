package com.recargapay.wallet_service.fixture;

import com.recargapay.wallet_service.domain.Wallet;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

public class WalletFixture {

    public static Wallet wallet(UUID userId) {
        Wallet wallet = new Wallet(userId);
        wallet.prePersist();
        return wallet;
    }

    public static Wallet wallet(UUID userId, Long id) {
        Wallet wallet = wallet(userId);
        ReflectionTestUtils.setField(wallet, "id", id);
        return wallet;
    }
}
