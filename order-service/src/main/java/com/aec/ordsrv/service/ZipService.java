package com.aec.ordsrv.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

@Service
@RequiredArgsConstructor
public class ZipService {

    @Value("${file.upload-dir}")
    private String uploadDir; // /home/tesis/.../uploads

    /**
     * Empaqueta en ZIP todos los archivos de /uploads/productos/{productId}
     */
    public void streamProductZip(Long productId, OutputStream os) throws IOException {
        // Ajuste: carpeta "productos"
        Path productFolder = Paths.get(uploadDir, "productos", productId.toString());

        if (!Files.isDirectory(productFolder)) {
            throw new FileNotFoundException("No existe carpeta de archivos para el producto: " + productFolder);
        }

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os))) {
            boolean added = false;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(productFolder)) {
                for (Path file : ds) {
                    if (!Files.isRegularFile(file)) continue;
                    String name = file.getFileName().toString();
                    // opcional: filtra imágenes si no quieres incluirlas
                    if (name.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$")) continue;

                    added = true;
                    zos.putNextEntry(new ZipEntry(name));
                    Files.copy(file, zos);
                    zos.closeEntry();
                }
            }
            if (!added) {
                throw new IOException("No se encontraron archivos descargables en " + productFolder);
            }
            zos.finish();
        }
    }
}
