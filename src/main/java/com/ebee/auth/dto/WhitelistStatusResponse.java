package com.ebee.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WhitelistStatusResponse(@JsonProperty("enabled") boolean enabled) {
}
