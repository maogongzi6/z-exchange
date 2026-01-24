package com.exchange.app.wallet.kafka.constant;

import com.exchange.common.kafka.constant.Topic;
import com.exchange.common.kafka.utils.Topics;

public class WalletTopic {
    // TODO config this in property file
    final static public String POST_LEDGER = "wallet.ledger.command.posting";
    final static public String LEDGER_REPLY = "ledger.wallet.reply.posting";
}
