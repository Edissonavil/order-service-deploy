package com.aec.ordsrv.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreatePaypalOrderRequest {
    @NotBlank private String currency;          // "USD"
    @NotNull  @Positive private Double totalAmount;

    @NotNull @Size(min = 1)
    private List<ItemDto> items;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ItemDto {
        @NotNull private Long productId;
        @NotBlank private String name;
        @NotNull @Positive private Integer quantity;
        @NotNull @Positive private Double unitPrice;
        @NotBlank private String sku;           // usa el productId como SKU
    }
}

