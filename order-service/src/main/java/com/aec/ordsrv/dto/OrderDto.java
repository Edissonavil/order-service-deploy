// src/main/java/com/aec/ordsrv/dto/OrderDto.java
package com.aec.ordsrv.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor    // ← necesario
@AllArgsConstructor   // ← necesario
public class OrderDto {
    private Long id;
    private String orderId;
    private List<ItemDto> items;
    private double total;
    private Instant creadoEn;
    private String status;
    private String paymentMethod;
    private String paymentStatus;
    private String downloadUrl;
    private String customerUsername;
    private String customerEmail;
    private String customerFullName;
    private String receiptFilename;
    private String adminApprovalUser;
    private Instant approvalDate;
    private String adminComment;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDto {
        private Long id;
        private Long productId;
        @JsonProperty("quantity")
        private int cantidad;
        private double precioUnitario;
        private double subtotal;
    }
}
