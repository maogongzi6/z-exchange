drop database if exists trade_orderservice;

create database trade_orderservice;

create table orders (
	id bigint auto_increment primary key,
    order_id varchar(32) not null,
    reference_id varchar(32) not null,
    order_status tinyint not null,
    unique key(order_id),
    key(reference_id)
);