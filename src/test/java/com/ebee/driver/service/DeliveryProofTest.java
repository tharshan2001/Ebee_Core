package com.ebee.driver.service;

import com.ebee.common.port.StoragePort;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.driver.dto.DriverDeliveryStatusRequest;
import com.ebee.order.entity.Order;
import com.ebee.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/** A driver can't mark a delivery delivered without a photo and the customer's signature. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveryProofTest {

    @Mock private OrderRepository orderRepository;
    @Mock private DriverContext driverContext;
    @Mock private StoragePort storagePort;
    @InjectMocks private DriverDeliveryService service;

    private final UUID orderId = UUID.randomUUID();
    private final Order order = Order.builder().id(orderId).deliveryStatus(DeliveryStatus.IN_TRANSIT).build();
    private final MultipartFile file = new MockMultipartFile("f", "f.png", "image/png", new byte[] {1});
    private final MultipartFile empty = new MockMultipartFile("f", "f.png", "image/png", new byte[0]);

    @BeforeEach
    void setUp() {
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
    }

    @Test
    void completion_withoutBothFiles_isRefused_andNothingIsUploadedOrSaved() {
        for (MultipartFile[] pair : new MultipartFile[][] {{null, null}, {file, null}, {null, file}, {file, empty}, {empty, file}}) {
            assertThatThrownBy(() -> service.completeDelivery(orderId, pair[0], pair[1], "note"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("photo and the customer's signature");
        }
        verifyNoInteractions(storagePort);
        verify(orderRepository, never()).save(any());
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
    }

    @Test
    void statusUpdate_toDelivered_isRefused() {
        DriverDeliveryStatusRequest request = new DriverDeliveryStatusRequest();
        request.setStatus(DeliveryStatus.DELIVERED);

        assertThatThrownBy(() -> service.updateDeliveryStatus(orderId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("photo and the customer's signature");
        verify(orderRepository, never()).save(any());
    }
}
