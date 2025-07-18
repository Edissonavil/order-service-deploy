package com.aec.ordsrv.dto; // Puedes ponerlo en un paquete 'shared' o 'external' si quieres diferenciar

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Usamos @Data y @Builder de Lombok
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder // Para poder usar FileInfoDto.builder()...build()
public class FileInfoDto {
    private Long id;
    private String filename;     // El UUID generado para el archivo
    private String originalName; // El nombre original del archivo
    private String fileType;     // Tipo MIME (ej., "image/jpeg")
    private Long size;           // Tamaño en bytes
    private String uploader;     // Usuario que subió el archivo
    private String downloadUri;  // URL para descargar el archivo
}