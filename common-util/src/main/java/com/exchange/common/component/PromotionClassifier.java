package com.exchange.common.component;

public interface PromotionClassifier {
    boolean isPromoted(String id);
    void promote(String id);
}
