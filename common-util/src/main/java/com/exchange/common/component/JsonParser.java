package com.exchange.common.component;

import com.exchange.common.exception.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JsonParser {
    // inject mapper by IoC
    private final ObjectMapper mapper;

    public <T> String encode(T obj) {
        if (obj == null) {
            throw new JsonParseException("encode exception, empty value");
        }
        String json;
        try {
            json = mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JsonParseException("encode exception", e);
        }
        return json;
    }

    public <T> T decode(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new JsonParseException("decode exception", e);
        }
    }
}
