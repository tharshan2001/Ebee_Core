package com.ebee.location.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.location.dto.LocationRequest;
import com.ebee.location.entity.Location;
import com.ebee.location.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final com.ebee.agent.repository.AgentRepository agentRepository;

    public LocationService(LocationRepository locationRepository,
                           com.ebee.agent.repository.AgentRepository agentRepository) {
        this.locationRepository = locationRepository;
        this.agentRepository = agentRepository;
    }

    /** Set (or clear, with null) the location an agent's orders ship from by default. */
    @Transactional
    public void setAgentHomeLocation(UUID agentId, UUID locationId) {
        var agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", agentId));
        agent.setLocation(locationId != null ? requireActive(locationId) : null);
        agentRepository.save(agent);
    }

    /** The default location (V127 creates "Main"). */
    @Transactional(readOnly = true)
    public Location defaultLocation() {
        return locationRepository.findDefault()
                .orElseThrow(() -> new IllegalStateException("No default location is configured"));
    }

    @Transactional(readOnly = true)
    public Location require(UUID id) {
        return locationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Location", "id", id));
    }

    @Transactional(readOnly = true)
    public Location requireActive(UUID id) {
        Location l = require(id);
        if (!l.isActive()) {
            throw new IllegalArgumentException("Location '" + l.getName() + "' is inactive");
        }
        return l;
    }

    /** First non-null of: explicit id, a fallback id (e.g. an agent's home), the default location. */
    @Transactional(readOnly = true)
    public Location resolve(UUID explicitId, UUID fallbackId) {
        if (explicitId != null) {
            return requireActive(explicitId);
        }
        if (fallbackId != null) {
            Location l = require(fallbackId);
            if (l.isActive()) {
                return l;
            }
        }
        return defaultLocation();
    }

    @Transactional(readOnly = true)
    public List<Location> list() {
        return locationRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public Location create(LocationRequest r) {
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        if (locationRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("A location with code '" + code + "' already exists");
        }
        Location l = new Location(code, r.name().trim(), r.type());
        l.setDistrict(r.district());
        l.setAddress(r.address());
        Location saved = locationRepository.save(l);
        if (Boolean.TRUE.equals(r.makeDefault())) {
            makeDefault(saved);
        }
        return saved;
    }

    @Transactional
    public Location update(UUID id, LocationRequest r) {
        Location l = require(id);
        l.setName(r.name().trim());
        l.setType(r.type());
        l.setDistrict(r.district());
        l.setAddress(r.address());
        if (r.active() != null) {
            if (!r.active() && l.isDefaultLocation()) {
                throw new IllegalArgumentException("The default location can't be deactivated; make another location the default first");
            }
            l.setActive(r.active());
        }
        if (Boolean.TRUE.equals(r.makeDefault()) && !l.isDefaultLocation()) {
            makeDefault(l);
        }
        return locationRepository.save(l);
    }

    private void makeDefault(Location l) {
        if (!l.isActive()) {
            throw new IllegalArgumentException("An inactive location can't be the default");
        }
        locationRepository.clearDefault();
        locationRepository.flush();
        l.setDefaultLocation(true);
        locationRepository.save(l);
    }
}
