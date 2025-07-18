package com.aec.ordsrv.model;

import java.time.Instant; // Mantener si tienes otros Instant aquí, aunque no sean los de Order
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.ToString; // Útil para depuración

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "order") // Evita ciclos infinitos al imprimir OrderItem que hace referencia a Order
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;
    private Double precioUnitario; // Ya existe, ¡bien!
    private Integer cantidad;     // Ya existe, ¡bien!
    private Double subtotal;

    // ELIMINAR ESTOS CAMPOS: NO PERTENECEN AQUÍ
    // @Column(name = "order_date") private Instant orderDate;
    // @Column(name = "created_at") private Instant createdAt;
    // @Column(name = "approval_date") private Instant approvalDate;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // Constructor para facilitar la creación de OrderItem
    public OrderItem(Long productId, Double precioUnitario, Integer cantidad) {
        this.productId = productId;
        this.precioUnitario = precioUnitario;
        this.cantidad = cantidad;
        this.subtotal = precioUnitario * cantidad;
    }

    public void setPrecioUnitario(Double precioUnitario) {
        this.precioUnitario = precioUnitario;
        updateSubtotal();
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
        updateSubtotal();
    }

    private void updateSubtotal() {
        if (this.precioUnitario != null && this.cantidad != null) {
            this.subtotal = this.precioUnitario * this.cantidad;
        } else {
            this.subtotal = 0.0;
        }
    }
}