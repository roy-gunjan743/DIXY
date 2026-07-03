package com.virtual_deadlock_detection.backend.repository;

import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ThreadRepository extends JpaRepository<ThreadEntity,Long> {

    Optional<ThreadEntity> findByThreadId(Long threadID);
    Optional<ThreadEntity> findByThreadName(String threadName);
}