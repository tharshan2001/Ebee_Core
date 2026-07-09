package com.ebee.inventory.service;

import com.ebee.alert.enums.AlertType;
import com.ebee.inventory.entity.Inventory;
import com.ebee.notification.NotificationPort;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ebee.user.enums.RoleType.ADMIN;
import static com.ebee.user.enums.RoleType.SUPER_ADMIN;

@Service
@RequiredArgsConstructor
public class InventoryAlertService {

    private final NotificationPort notificationPort;
    private final UserRepository userRepository;

    public void checkAndNotify(Inventory inventory) {
        if (inventory.getReorderLevel() == null) {
            return;
        }
        if (inventory.getQuantity() > inventory.getReorderLevel()) {
            return;
        }
        String productName = inventory.getProduct().getName();
        String subject = "Low Stock Alert – " + productName;
        Map<String, String> p = new HashMap<>();
        p.put("productName", productName);
        p.put("currentQty", String.valueOf(inventory.getQuantity()));
        p.put("reorderLevel", String.valueOf(inventory.getReorderLevel()));
        List<User> admins = userRepository.findByRoleIn(List.of(ADMIN, SUPER_ADMIN));
        for (User admin : admins) {
            notificationPort.notifyUser(admin, subject, "low-stock-alert", p, Set.of());
            notificationPort.createInternalAlert(admin, subject,
                    "Stock for %s is below reorder level. Current: %d, Reorder Level: %d".formatted(
                            productName, inventory.getQuantity(), inventory.getReorderLevel()),
                    AlertType.LOW_STOCK, inventory.getId(), "inventory");
        }
    }
}
