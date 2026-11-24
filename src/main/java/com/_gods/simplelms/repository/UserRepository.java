package com._gods.simplelms.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.simplelms.model.User;

public interface UserRepository extends JpaRepository<User, String> {
}
