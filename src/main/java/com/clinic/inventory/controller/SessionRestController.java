package com.clinic.inventory.controller;

import com.clinic.inventory.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionRestController {
    private final CurrentUserService currentUser;

    @GetMapping("/me")
    public Map<String,Object> me(Authentication authentication) {
        var u = currentUser.require(authentication);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("id", u.getId()); result.put("email", u.getEmail()); result.put("fullName", u.getFullName());
        result.put("role", u.getRole().getName()); result.put("permissions", u.getRole().getPermissions().stream().map(p -> p.getCode()).sorted().toList());
        return result;
    }
}
