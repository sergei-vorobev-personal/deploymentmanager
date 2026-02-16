package com.kineto.deploymentmanager.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "application")
class Application(

    @Id
    @Column(nullable = false)
    var id: String,

    @Column(nullable = false)
    var functionName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: ApplicationState,

    @Column(nullable = false)
    var s3Key: String,

    @Column(nullable = false)
    var s3Bucket: String,

    var url: String? = null,

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now(),

    var error: String? = null,
)