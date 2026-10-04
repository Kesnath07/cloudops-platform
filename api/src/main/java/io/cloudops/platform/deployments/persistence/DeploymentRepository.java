package io.cloudops.platform.deployments.persistence;

import io.cloudops.platform.deployments.domain.Deployment;
import io.cloudops.platform.deployments.domain.DeploymentEnvironment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    Page<Deployment> findByWorkloadId(UUID workloadId, Pageable pageable);

    Page<Deployment> findByWorkloadIdAndEnvironment(UUID workloadId, DeploymentEnvironment environment,
                                                    Pageable pageable);

    /**
     * Most recent deployment of every workload to one environment, served by the
     * (environment, workload_id, deployed_at) index.
     */
    @Query("""
            select d from Deployment d
            where d.environment = :environment
              and d.deployedAt = (select max(latest.deployedAt) from Deployment latest
                                  where latest.workloadId = d.workloadId
                                    and latest.environment = :environment)
            """)
    List<Deployment> findLatestPerWorkload(@Param("environment") DeploymentEnvironment environment);
}
