package io.cloudops.platform.catalog.application;

import io.cloudops.platform.catalog.domain.Team;
import io.cloudops.platform.catalog.persistence.TeamRepository;
import io.cloudops.platform.shared.error.ConflictException;
import io.cloudops.platform.shared.error.ResourceNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TeamService {

    private final TeamRepository teams;

    public TeamService(TeamRepository teams) {
        this.teams = teams;
    }

    @Transactional
    public TeamView create(TeamCommand command) {
        if (teams.existsBySlug(command.slug())) {
            throw new ConflictException("A team with slug '" + command.slug() + "' already exists");
        }
        Team team = teams.save(new Team(command.slug(), command.name(), command.description(),
                command.contactEmail()));
        return TeamView.of(team);
    }

    @Transactional
    public TeamView update(UUID teamId, TeamCommand command) {
        Team team = load(teamId);
        team.rename(command.name(), command.description(), command.contactEmail());
        return TeamView.of(team);
    }

    @Transactional(readOnly = true)
    public TeamView get(UUID teamId) {
        return TeamView.of(load(teamId));
    }

    @Transactional(readOnly = true)
    public List<TeamView> list() {
        return teams.findAll(Sort.by("name")).stream().map(TeamView::of).toList();
    }

    Team load(UUID teamId) {
        return teams.findById(teamId).orElseThrow(() -> new ResourceNotFoundException("Team", teamId));
    }
}
