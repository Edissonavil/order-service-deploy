// event/OrderCreatedEvent.java  (igual al que publique order-service)
package com.aec.events;

// Un record es inmutable y actúa como DTO de mensajería
public record OrderCreatedEvent(
        Long   orderId,
        String clienteUsername,
        Long   productId,
        Integer cantidad,
        Double precioUnitario,
        Double total,
        String timestampUtc            // ISO-8601, ej. 2025-06-25T11:15:30Z
) { }
