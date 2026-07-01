package com.virtual_deadlock_detection.backend.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "thread")
public class ThreadEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @Column(nullable = false,unique = true)
    private long threadId;
    @Column(nullable = false)
    private String threadName;
    @Column(nullable = false)
    private String state;
    private String carrierThread;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @PrePersist
    public void prePersist(){
        this.createdAt=LocalDateTime.now();
    }
}