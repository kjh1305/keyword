package com.example.demo.api.inventory.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // View - 회원 목록 (관리자 전용)
    @GetMapping("/inventory/users")
    @PreAuthorize("hasRole('ADMIN')")
    public String userList(Model model) {
        List<UserDTO> users = userService.getAllUsers().stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
        model.addAttribute("users", users);
        model.addAttribute("menu", "users");
        return "inventory/user-list";
    }

    // API - 회원 목록
    @GetMapping("/api/inventory/users")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<List<UserDTO>> getUsers() {
        List<UserDTO> users = userService.getAllUsers().stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    // API - 회원 상세
    @GetMapping("/api/inventory/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(UserDTO.fromEntity(userService.getUserById(id)));
    }

    // API - 회원 추가
    @PostMapping("/api/inventory/users")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<UserDTO> createUser(@RequestBody UserDTO dto) {
        User user = userService.createUser(
                dto.getUsername(),
                dto.getPassword(),
                dto.getName(),
                dto.getRole()
        );
        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }

    // API - 회원 수정
    @PutMapping("/api/inventory/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO dto) {
        User user = userService.updateUser(id, dto.getName(), dto.getRole(), dto.getPassword());
        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }

    // API - 회원 삭제
    @DeleteMapping("/api/inventory/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }
}
