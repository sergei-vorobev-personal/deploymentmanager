package com.kineto.deploymentmanager.repository

import com.kineto.deploymentmanager.model.PendingDeployment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface PendingDeploymentRepository : JpaRepository<PendingDeployment, String> {
    @Transactional
    @Modifying
    @Query(
        value = """
        UPDATE pending_deployment
        SET is_locked = true
        WHERE id IN (
            SELECT id
            FROM pending_deployment
            WHERE is_locked = false
            ORDER BY updated_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        )
        RETURNING *
        """,
        nativeQuery = true
    )
    fun findAll(@Param("limit") n: Int): List<PendingDeployment>
}