package com.ebee.agent.mapper;

import com.ebee.agent.entity.Agent;
import com.ebee.location.entity.Location;
import com.ebee.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMapperTest {

    private final AgentMapper mapper = Mappers.getMapper(AgentMapper.class);

    private static Agent agent(Location home) {
        User user = User.builder().firstName("Mannar").lastName("Agent").email("a@ebee.test").build();
        return Agent.builder().user(user).agentCode("MN-1").location(home).build();
    }

    @Test
    void homeLocation_isInTheResponse() {
        Location shop = new Location("MN", "Mannar shop", Location.Type.SHOP);
        ReflectionTestUtils.setField(shop, "id", UUID.randomUUID());

        var response = mapper.toResponse(agent(shop));

        assertThat(response.getLocationId()).isEqualTo(shop.getId());
        assertThat(response.getLocationName()).isEqualTo("Mannar shop");
    }

    @Test
    void noHomeLocation_isNull() {
        var response = mapper.toResponse(agent(null));
        assertThat(response.getLocationId()).isNull();
        assertThat(response.getLocationName()).isNull();
    }
}
