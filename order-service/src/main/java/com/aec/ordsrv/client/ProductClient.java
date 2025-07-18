
package com.aec.ordsrv.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.aec.ordsrv.dto.ProductDto;

// src/main/java/com/aec/ordsrv/client/ProductClient.java
@FeignClient(
    name = "product-service",
    url  = "${product.service.url}",          // →  http://localhost:8083
    path = "/api/products"   
  )                   // <-- prefijo correcto
public interface ProductClient {
  @GetMapping("/{id}")
  ProductDto getById(@PathVariable("id") Long id,
      @RequestHeader("Authorization") String bearer);

       @GetMapping("/uploader/{username}")
    List<ProductDto> findByUploader(
        @PathVariable("username") String username,
        @RequestHeader("Authorization") String bearer
    );
}

