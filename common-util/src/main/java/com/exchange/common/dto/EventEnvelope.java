 //package com.exchange.common.dto;
//
//import lombok.AllArgsConstructor;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//
//@Data
//@AllArgsConstructor
//public class EventEnvelope {
//    // unique id for kafka event
//    final public String eventId;
//    // unique id for business
//    final public String commandId;
//    // String instead of Enum, since event need to cross service (sometimes cross language),
//    // String is loose coupling, better compatibility and serialization friendly
//    final public String eventType;
//    final public byte[] payload;
//    final public long occurredAt;
//}
