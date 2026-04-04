package com.exchange.common.component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultPromotionClassifier implements PromotionClassifier {
    private final Set<String> classifier = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isPromoted(String id) {
        return classifier.contains(id);
    }

    @Override
    public void promote(String id) {
        classifier.add(id);
    }
}
