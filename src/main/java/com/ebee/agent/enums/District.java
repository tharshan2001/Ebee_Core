package com.ebee.agent.enums;

import java.util.Arrays;

public enum District {

    JF("Jaffna"),
    KN("Kilinochchi"),
    MN("Mannar"),
    ML("Mullaitivu"),
    VN("Vavuniya"),
    TM("Trincomalee"),
    BC("Batticaloa"),
    AP("Ampara");

    private final String displayName;

    District(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static District fromDisplayName(String name) {
        return Arrays.stream(values())
                .filter(d -> d.displayName.equalsIgnoreCase(name)
                        || d.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid district: '" + name + "'. Supported districts (North & East): "
                                + Arrays.stream(values()).map(District::getDisplayName).toList()));
    }

    public static boolean isValid(String name) {
        return Arrays.stream(values())
                .anyMatch(d -> d.displayName.equalsIgnoreCase(name)
                        || d.name().equalsIgnoreCase(name));
    }
}
