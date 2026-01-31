package com.example.demo.api.inventory.user;

import com.example.demo.api.inventory.log.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @Lazy
    private final ActivityLogService activityLogService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
        );
    }

    @PostConstruct
    @Transactional
    public void initDefaultUser() {
        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .name("관리자")
                    .role("ADMIN")
                    .build();
            userRepository.save(admin);
        }
    }

    @Transactional
    public User createUser(String username, String password, String name, String role) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("이미 존재하는 사용자명입니다: " + username);
        }
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .name(name)
                .role(role != null ? role : "USER")
                .build();
        User saved = userRepository.save(user);
        activityLogService.logCreate("USER", saved.getId(), saved.getUsername());
        return saved;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + id));
    }

    @Transactional
    public User updateUser(Long id, String name, String role, String password) {
        User user = getUserById(id);
        if (name != null && !name.isEmpty()) {
            user.setName(name);
        }
        if (role != null && !role.isEmpty()) {
            user.setRole(role);
        }
        if (password != null && !password.isEmpty()) {
            user.setPassword(passwordEncoder.encode(password));
        }
        User saved = userRepository.save(user);
        activityLogService.logUpdate("USER", saved.getId(), saved.getUsername(), null);
        return saved;
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = getUserById(id);
        // admin 계정은 삭제 불가
        if ("admin".equals(user.getUsername())) {
            throw new IllegalArgumentException("기본 관리자 계정은 삭제할 수 없습니다.");
        }
        String username = user.getUsername();
        userRepository.deleteById(id);
        activityLogService.logDelete("USER", id, username);
    }
}
