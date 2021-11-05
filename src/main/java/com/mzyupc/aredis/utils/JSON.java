package com.mzyupc.aredis.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.util.List;

public class JSON {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private JSON() {
        throw new IllegalStateException("Utility class");
    }

    public static Object parseObject(String text) {
        return parseObject(text, Object.class);
    }

    @SneakyThrows(JsonProcessingException.class)
    public static <T> T parseObject(String text, Class<T> clazz) {
        return objectMapper.readValue(text, clazz);
    }

    @SneakyThrows(JsonProcessingException.class)
    public static String toJSONString(Object value, boolean format) {
        if (format) {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } else {
            return objectMapper.writeValueAsString(value);
        }
    }

    public static String toJSONString(Object value) {
        return toJSONString(value, false);
    }

    @SneakyThrows(JsonProcessingException.class)
    public static <T> List<T> parseArray(String text, Class<T> clazz) {
        return objectMapper.readValue(text, objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
    }
}
