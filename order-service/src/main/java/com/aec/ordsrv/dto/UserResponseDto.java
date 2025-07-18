package com.aec.ordsrv.dto;

import lombok.Data;

@Data
public class UserResponseDto {
    private String username;
    private String email;
    private String fullName;
}
