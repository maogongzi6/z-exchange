drop database if exists trade_ledgerservice;

create database trade_ledgerservice;

use trade_ledgerservice;

create table assets (
	id bigint auto_increment primary key,
    asset_id varchar(64) not null,
    asset_type tinyint not null,
    symbol varchar(64) not null,
    decimals int not null,
    extra_data text,
    unique key(asset_id)
);
insert into assets (asset_id, asset_type, symbol, decimals) values ("asset-1", 1, "USD", 1), ("asset-2", 1, "CNY", 1);

create table accounts (
	id bigint auto_increment primary key,
    account_id varchar(64) not null,
    service_id tinyint not null,
    reference_id varchar(64) not null,
    category tinyint not null,
    normal_side tinyint not null,
    owner_id varchar(64) not null,
    owner_type tinyint not null,
    asset_id varchar(64) not null,
    account_status tinyint not null,
    extra_data text,
    unique key(account_id),
    unique key(service_id, reference_id),
    key(owner_type, owner_id)
);
-- insert into accounts (account_id, category, normal_side, owner_id, owner_type, asset_id, account_status) values ("account-1", 1, 1, "owner-1", 2, "asset-1", 1), ("account-2", 1, 2, "owner-2", 2, "asset-1", 1);

create table ledger_entries (
	id bigint auto_increment primary key,
    entry_id varchar(64) not null,
    txn_id varchar(64) not null,
    asset_id varchar(64) not null,
    account_id varchar(64) not null,
    amount bigint not null,
    direction tinyint not null,
    unique key(entry_id),
    key(txn_id, id),
    key(account_id, direction)
);

create table ledger_transactions (
	id bigint auto_increment primary key,
    txn_id varchar(64) not null,
    reference_id varchar(64) not null,
    metadata json not null default (JSON_OBJECT()),
    version bigint not null,
    created_at timestamp(3) not null,
    updated_at timestamp(3) not null,
    unique key(txn_id),
    unique key(reference_id)
);

create table outbox (
	id bigint auto_increment primary key,
	event_id varchar(64) not null,
    event_type tinyint not null,
    command_id varchar(64) not null,
    outbox_status tinyint not null,
    destination varchar(64) not null,
    partition_key varchar(64) not null,
    payload blob not null,
    attempt_count int not null,
    last_attempt_at timestamp(3),
    next_attempt_at timestamp(3) not null,
    last_error varchar(128),
    finalized_at timestamp(3),
    metadata json,
    created_at timestamp(3) not null,
    updated_at timestamp(3) not null,
    unique key(event_id),
    key(command_id),
    /* Oldest-due-first claim filtering, ordering, and tuple-cursor pagination. */
    key idx_outbox_claim(outbox_status, next_attempt_at, id)
);

