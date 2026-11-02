package com._gods.canvas2.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.canvas2.model.User;

public interface UserRepository extends JpaRepository<User, String> {
}
