package com.aec.ordsrv.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ProductSalesDto {
    private Long productId;
    private String productName; // Este será null inicialmente o se resolverá después
    private String paymentMethod;
    private BigDecimal totalSalesAmount;
    private Integer totalQuantity; // Cambiado a Integer para que coincida con OrderItem.cantidad
    private Long orderCount;
    
}