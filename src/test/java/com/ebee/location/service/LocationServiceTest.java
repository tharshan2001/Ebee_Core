package com.ebee.location.service;

import com.ebee.agent.repository.AgentRepository;
import com.ebee.location.dto.LocationRequest;
import com.ebee.location.entity.Location;
import com.ebee.location.repository.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocationServiceTest {

    private final LocationRepository repo = mock(LocationRepository.class);
    private final LocationService service = new LocationService(repo, mock(AgentRepository.class));
    private Location main;
    private Location agentHome;

    private Location location(String code, boolean isDefault) {
        Location l = new Location(code, code, Location.Type.WAREHOUSE);
        ReflectionTestUtils.setField(l, "id", UUID.randomUUID());
        l.setDefaultLocation(isDefault);
        when(repo.findById(l.getId())).thenReturn(Optional.of(l));
        return l;
    }

    @BeforeEach
    void setUp() {
        main = location("MAIN", true);
        agentHome = location("JAFFNA", false);
        when(repo.findDefault()).thenReturn(Optional.of(main));
    }

    @Test
    void resolve_explicitThenAgentHomeThenDefault() {
        Location chosen = location("SHOP", false);
        assertThat(service.resolve(chosen.getId(), agentHome.getId())).isSameAs(chosen);
        assertThat(service.resolve(null, agentHome.getId())).isSameAs(agentHome);
        assertThat(service.resolve(null, null)).isSameAs(main);
    }

    @Test
    void inactiveAgentHome_fallsBackToDefault_butInactiveExplicitIsRejected() {
        agentHome.setActive(false);
        assertThat(service.resolve(null, agentHome.getId())).isSameAs(main);
        assertThatThrownBy(() -> service.resolve(agentHome.getId(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inactive");
    }

    @Test
    void defaultLocation_cantBeDeactivated() {
        assertThatThrownBy(() -> service.update(main.getId(),
                new LocationRequest("MAIN", "Main", Location.Type.WAREHOUSE, null, null, false, null)))
                .hasMessageContaining("default location can't be deactivated");
    }
}
