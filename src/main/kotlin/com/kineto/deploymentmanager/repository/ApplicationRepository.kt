package com.kineto.deploymentmanager.repository

import com.kineto.deploymentmanager.model.Application
import org.springframework.data.jpa.repository.JpaRepository

interface ApplicationRepository : JpaRepository<Application, String> {
    fun findAllByIdIn(applicationIds: Collection<String>): List<Application>
}