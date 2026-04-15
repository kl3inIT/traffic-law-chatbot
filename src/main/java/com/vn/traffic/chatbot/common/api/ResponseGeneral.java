package com.vn.traffic.chatbot.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
public class ResponseGeneral<T> {

    private int status;
    private String message;
    private T data;
    private String timestamp;

    public static <T> ResponseGeneral<T> of(int status, String message, T data) {
        return of(status, message, data, java.time.OffsetDateTime.now().toString());
    }

    public static <T> ResponseGeneral<T> ofSuccess(String message, T data) {
        return of(200, message, data);
    }

    public static <T> ResponseGeneral<T> ofCreated(String message, T data) {
        return of(201, message, data);
    }
}
