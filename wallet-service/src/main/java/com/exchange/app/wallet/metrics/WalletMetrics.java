package com.exchange.app.wallet.metrics;

import com.exchange.common.metrics.MetricDef;

public final class WalletMetrics {
    public static final MetricDef WALLET_REQUESTS = new MetricDef(
            "zexchange.wallet.requests",
            "Wallet business request result count"
    );

    public static final MetricDef WALLET_PROCESSING_DURATION = new MetricDef(
            "zexchange.wallet.processing.duration",
            "Wallet business processing duration"
    );

    private WalletMetrics() {
    }
}
