package com.example.draftly.repository;

import com.example.draftly.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository

        extends JpaRepository<User, Long> {

    Optional<User> findByEmail(

            String email

    );

}
