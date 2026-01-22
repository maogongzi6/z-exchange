package com.exchange.app.wallet.kafka.constant;

import com.exchange.common.kafka.constant.Topic;
import com.exchange.common.kafka.utils.Topics;

public class WalletTopic {
    final static public String POST_LEDGER = Topics.form(Topic.WALLET, Topic.LEDGER, Topic.COMMAND, "posting");
    final static public String LEDGER_REPLY = Topics.form(Topic.LEDGER, Topic.WALLET, Topic.REPLY, "posting");
}
