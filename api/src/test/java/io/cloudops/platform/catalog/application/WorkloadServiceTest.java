package io.cloudops.platform.catalog.application;

import io.cloudops.platform.catalog.domain.Criticality;
import io.cloudops.platform.catalog.domain.Team;
import io.cloudops.platform.catalog.domain.Workload;
import io.cloudops.platform.catalog.persistence.TeamRepository;
import io.cloudops.platform.catalog.persistence.WorkloadRepository;
import io.cloudops.platform.shared.error.ConflictException;
import io.cloudops.platform.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkloadServiceTest {

    private static final UUID TEAM_ID = UUID.randomUUID();

    @Mock
    private WorkloadRepository workloads;

    @Mock
    private TeamRepository teams;

    private WorkloadService service;
    private final Team team = new Team("payments", "Payments", null, null);

    @BeforeEach
    void setUp() {
        service = new WorkloadService(workloads, new TeamService(teams));
    }

    @Test
    void registersWorkloadWithNormalizedDetails() {
        when(teams.findById(TEAM_ID)).thenReturn(Optional.of(team));
        when(workloads.saveAndFlush(any(Workload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkloadView workload = service.create(command("payments-api", " Payments API ", "  "));

        assertThat(workload.slug()).isEqualTo("payments-api");
        assertThat(workload.name()).isEqualTo("Payments API");
        assertThat(workload.description()).isNull();
        assertThat(workload.team().slug()).isEqualTo("payments");
    }

    @Test
    void duplicateSlugIsAConflict() {
        when(workloads.existsBySlug("payments-api")).thenReturn(true);

        assertThatThrownBy(() -> service.create(command("payments-api", "Payments API", null)))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(teams);
        verify(workloads, never()).saveAndFlush(any());
    }

    @Test
    void concurrentRegistrationOfTheSameSlugIsAConflict() {
        when(teams.findById(TEAM_ID)).thenReturn(Optional.of(team));
        when(workloads.saveAndFlush(any(Workload.class)))
                .thenThrow(new DataIntegrityViolationException("uq_workloads_slug"));

        assertThatThrownBy(() -> service.create(command("payments-api", "Payments API", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A workload with slug 'payments-api' already exists");
    }

    @Test
    void unknownOwningTeamIsNotFound() {
        when(teams.findById(TEAM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command("payments-api", "Payments API", null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(workloads, never()).saveAndFlush(any());
    }

    @Test
    void workloadWithIncidentHistoryCannotBeDeleted() {
        UUID id = UUID.randomUUID();
        Workload workload = new Workload("payments-api", command("payments-api", "Payments API", null).details(), team);
        when(workloads.findById(id)).thenReturn(Optional.of(workload));
        doThrow(new DataIntegrityViolationException("fk_incidents_workload")).when(workloads).flush();

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("incident history");
    }

    @Test
    void deletingAnUnknownWorkloadIsNotFound() {
        UUID id = UUID.randomUUID();
        when(workloads.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
        verify(workloads, never()).delete(any());
    }

    @Test
    void listIsPagedByNameThenSlug() {
        when(workloads.findByTeamId(any(), any())).thenReturn(Page.empty());

        service.list(TEAM_ID, 2, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(workloads).findByTeamId(eq(TEAM_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.asc("name"), Sort.Order.asc("slug"));
    }

    @Test
    void resolvingNoReferencesSkipsTheDatabase() {
        assertThat(service.references(List.of())).isEmpty();
        verifyNoInteractions(workloads);
    }

    private static CreateWorkloadCommand command(String slug, String name, String description) {
        return new CreateWorkloadCommand(slug, name, description, TEAM_ID, Criticality.HIGH, null, null);
    }
}
