package com.aec.ordsrv.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


/**
 * Datos que envía el cliente cuando quiere crear una orden.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    private String currency; // Moneda de la transacción (ej. "USD")
    private double totalAmount; // Monto total de la orden
    private List<OrderItemRequest> items; // Lista de ítems en la orden

    // Clase interna para los ítems de la orden dentro de la solicitud
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        private Long productId;
        private String name;        // Nombre del producto (opcional, para referencia)
        private int quantity;
        private double unitPrice;
        private String sku;         // SKU del producto (opcional, si lo usas)
    }
}
