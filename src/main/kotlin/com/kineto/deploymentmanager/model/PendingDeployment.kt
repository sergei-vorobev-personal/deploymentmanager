package com.kineto.deploymentmanager.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "pending_deployment")
class PendingDeployment(

    @Id
    @Column(nullable = false)
    var id: String,

    @Column(nullable = false)
    var functionName: String,

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),

    var isLocked: Boolean = false,

    var attempts: Int = 0,

    @Column(nullable = false, updatable = false)
    var maxAttempts: Int = 60,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var operation: Operation,
)