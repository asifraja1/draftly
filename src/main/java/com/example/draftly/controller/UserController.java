package com.example.draftly.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/user")
    public ResponseEntity<?> getUser(OAuth2AuthenticationToken authentication) {
        if (authentication == null) {
            return ResponseEntity.ok(Map.of("authenticated", false));
        }
        String email = authentication.getPrincipal().getAttribute("email");
        String name = authentication.getPrincipal().getAttribute("name");
        String picture = authentication.getPrincipal().getAttribute("picture");
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "email", email != null ? email : "",
                "name", name != null ? name : "",
                "picture", picture != null ? picture : ""
        ));
    }
}
