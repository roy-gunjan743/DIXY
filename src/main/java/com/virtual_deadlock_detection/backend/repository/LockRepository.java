package com.virtual_deadlock_detection.backend.repository;

import com.virtual_deadlock_detection.backend.entity.LockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

public interface LockRepository extends JpaRepository<LockRepository,String>{


    Optional<LockRepository> findByLockId(String lockID);
}