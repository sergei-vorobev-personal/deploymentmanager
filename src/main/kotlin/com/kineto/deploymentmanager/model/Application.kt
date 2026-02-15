package com.kineto.deploymentmanager.model

import jakarta.persistence.Entity
import jakarta.persistence.Table

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
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

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now(),

    var error: String? = null,
)