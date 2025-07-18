// CartItemDto.java
package com.aec.ordsrv.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CartItemDto {
    private Long id;          // ID interno del OrderItem
    private Long productId;
    private Double unitPrice;
    private Integer quantity;
    private Double subtotal;

    private String name; // Para item.nombre
    private List<String> especialidades; // Para item.especialidades
    private String pais; // Para item.pais
    private String fotografiaProd; // Para item.fotografiaProd
}

