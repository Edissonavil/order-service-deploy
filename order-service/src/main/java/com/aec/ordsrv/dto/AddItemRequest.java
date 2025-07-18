package com.aec.ordsrv.dto;
import lombok.*;
import jakarta.validation.constraints.*;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddItemRequest {
    @NotNull
    private Long productId;

    @NotNull @Positive
    private Double unitPrice;

    @NotNull @Positive
    private Integer cantidad;
}
