package com.virtual_deadlock_detection.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "deadlock_nodes")
public class DeadlockNodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which report does this node belong to?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private DeadlockReportEntity report;

    // Which thread is involved?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id")
    private ThreadEntity thread;

    // Position of this thread in the deadlock chain
    @Column(nullable = false)
    private Integer orderIndex;
}