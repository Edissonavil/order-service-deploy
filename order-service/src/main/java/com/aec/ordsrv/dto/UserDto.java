// src/main/java/com/aec/ordsrv/dto/UserDto.java
package com.aec.ordsrv.dto;

import lombok.Data;

@Data
public class UserDto {
    private String username;
    private String email;
    private String fullName;
    private String role;
}
