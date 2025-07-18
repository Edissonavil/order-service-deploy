package com.aec.ordsrv.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDto {

    @JsonProperty("idProducto")
    private Long idProducto;

    @JsonProperty("id")
    private Long id;

    @JsonProperty("nombre")
    private String nombre;                // <─ nombre exacto

    @JsonProperty("precioIndividual")
    private Double precioIndividual;      // <─ precio exacto

    private List<String> categorias;
    private List<String> especialidades;
    private String pais;
    private String fotografiaProd;
    private List<String> archivosAut;     // <─ lo necesitarás para la descarga
}

