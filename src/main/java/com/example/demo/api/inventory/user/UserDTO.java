package com.example.demo.api.inventory.user;

import lombok.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {
    private Long id;
    private String username;
    private String password;
    private String name;
    private String role;
    private String roleText;
    private LocalDateTime createdAt;
    private String createdAtStr;

    public static UserDTO fromEntity(User user) {
        UserDTO dto = UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();

        if ("ADMIN".equals(user.getRole())) {
            dto.setRoleText("관리자");
        } else {
            dto.setRoleText("일반사용자");
        }

        if (user.getCreatedAt() != null) {
            dto.setCreatedAtStr(user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        }

        return dto;
    }
}
