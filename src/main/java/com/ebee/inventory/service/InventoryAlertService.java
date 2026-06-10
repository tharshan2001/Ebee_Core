package com.ebee.inventory.service;

import com.ebee.alert.enums.AlertType;
import com.ebee.inventory.entity.Inventory;
import com.ebee.notification.NotificationPort;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
        String message = "Stock for %s is below reorder level. Current: %d, Reorder Level: %d".formatted(
                productName, inventory.getQuantity(), inventory.getReorderLevel());
        List<User> admins = userRepository.findByRole(RoleType.ADMIN);
        for (User admin : admins) {
            notificationPort.notifyUser(admin, subject, message);
            notificationPort.createInternalAlert(admin, subject, message,
                    AlertType.LOW_STOCK, inventory.getId(), "inventory");
        }
    }
}
