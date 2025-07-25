package com.aec.ordsrv.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.aec.ordsrv.config.FeignConfig;
import com.aec.ordsrv.dto.UserResponseDto;

@FeignClient(
    name = "users-service",                                     
    url  = "${USERS_SERVICE_URL:http://users-service.railway.internal}" 
)
public interface UserClient {
    @GetMapping("/api/users/by-username/{username}")
    UserResponseDto getByUsername(@PathVariable String username);
}

