package com.aec.ordsrv.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CollaboratorMonthlyStatsDto {
    private String collaboratorUsername;
    private List<ProductSalesDto> productSales;
    private BigDecimal totalCollaboratorSales;
}