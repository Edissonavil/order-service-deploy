// CartDto.java
package com.aec.ordsrv.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CartDto {
    private List<CartItemDto> items;
    private Double total;
}
