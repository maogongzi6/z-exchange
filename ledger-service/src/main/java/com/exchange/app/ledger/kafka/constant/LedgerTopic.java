package com.exchange.app.ledger.kafka.constant;

import com.exchange.common.kafka.constant.Topic;
import com.exchange.common.kafka.utils.Topics;

public class LedgerTopic {
    final static public String WALLET_POST = "wallet.ledger.command.posting";
    final static public String REPLY_WALLET = "ledger.wallet.reply.posting";
}
