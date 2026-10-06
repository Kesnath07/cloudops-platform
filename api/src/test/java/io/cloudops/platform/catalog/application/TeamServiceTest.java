package io.cloudops.platform.catalog.application;

import io.cloudops.platform.catalog.domain.Team;
import io.cloudops.platform.catalog.persistence.TeamRepository;
import io.cloudops.platform.shared.error.ConflictException;
import io.cloudops.platform.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teams;

    @InjectMocks
    private TeamService service;

    @Test
    void createsTeamWithNormalizedText() {
        when(teams.saveAndFlush(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamView team = service.create(new TeamCommand("payments", "  Payments ", "   ", ""));

        assertThat(team.slug()).isEqualTo("payments");
        assertThat(team.name()).isEqualTo("Payments");
        assertThat(team.description()).isNull();
        assertThat(team.contactEmail()).isNull();
    }

    @Test
    void duplicateSlugIsAConflict() {
        when(teams.existsBySlug("payments")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new TeamCommand("payments", "Payments", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("payments");
        verify(teams, never()).saveAndFlush(any());
    }

    @Test
    void concurrentCreationOfTheSameSlugIsAConflict() {
        when(teams.saveAndFlush(any(Team.class))).thenThrow(new DataIntegrityViolationException("uq_teams_slug"));

        assertThatThrownBy(() -> service.create(new TeamCommand("payments", "Payments", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A team with slug 'payments' already exists");
    }

    @Test
    void updateKeepsSlugAndReplacesDetails() {
        UUID id = UUID.randomUUID();
        Team existing = new Team("payments", "Payments", "Card processing", "old@example.org");
        when(teams.findById(id)).thenReturn(Optional.of(existing));

        TeamView updated = service.update(id, new TeamCommand("ignored", "Payments Platform", null, "new@example.org"));

        assertThat(updated.slug()).isEqualTo("payments");
        assertThat(updated.name()).isEqualTo("Payments Platform");
        assertThat(updated.description()).isNull();
        assertThat(updated.contactEmail()).isEqualTo("new@example.org");
    }

    @Test
    void unknownTeamIsNotFound() {
        UUID id = UUID.randomUUID();
        when(teams.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void listIsOrderedByNameThenSlug() {
        when(teams.findAll(any(Sort.class))).thenReturn(List.of());

        service.list();

        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(teams).findAll(sort.capture());
        assertThat(sort.getValue()).containsExactly(Sort.Order.asc("name"), Sort.Order.asc("slug"));
    }
}
