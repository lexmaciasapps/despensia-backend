package com.despensia.users.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UsersController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "module", "users");
    }
}
