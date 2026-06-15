package com.ebee.commission.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.enums.AgentCommissionType;
import com.ebee.commission.dto.CommissionCalculateRequest;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.entity.CommissionConfig;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.enums.CommissionType;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionConfigRepository;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.notification.NotificationPort;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.repository.OrderRepository;
import com.ebee.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommissionCalculationServiceTest {

    @Mock private CommissionRepository commissionRepository;
    @Mock private CommissionConfigRepository configRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CommissionMapper mapper;
    @Mock private NotificationPort notificationPort;

    @InjectMocks private CommissionCalculationService commissionCalculationService;

    private Order order;
    private Agent agent;

    @BeforeEach
    void setUp() {
        User agentUser = User.builder()
                .id(UUID.randomUUID())
                .firstName("Agent")
                .lastName("User")
                .build();

        agent = Agent.builder()
                .id(UUID.randomUUID())
                .user(agentUser)
                .agentCode("JF-008")
                .commissionRate(7.5)
                .commissionType(AgentCommissionType.DEFAULT)
                .isActive(true)
                .build();

        order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("COL-A-20250601-001")
                .agent(agent)
                .totalAmount(new BigDecimal("10000.00"))
                .status(OrderStatus.PENDING)
                .build();
    }

    @Test
    void calculateCommissionForOrder_withDefaultConfig_calculatesCorrectly() {
        CommissionConfig config = CommissionConfig.builder()
                .commissionRate(10.0)
                .type(CommissionType.DEFAULT)
                .isActive(true)
                .build();

        when(configRepository.findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT))
                .thenReturn(Optional.of(config));

        Commission commission = Commission.builder()
                .id(UUID.randomUUID())
                .agent(agent)
                .order(order)
                .config(config)
                .orderAmount(order.getTotalAmount())
                .commissionRate(BigDecimal.valueOf(10.0))
                .commissionAmount(new BigDecimal("1000.00"))
                .status(CommissionStatus.CALCULATED)
                .build();
        when(commissionRepository.save(any())).thenReturn(commission);

        Commission result = commissionCalculationService.calculateCommissionForOrder(order);

        assertThat(result.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.getCommissionRate()).isEqualByComparingTo(new BigDecimal("10.0"));
        assertThat(result.getStatus()).isEqualTo(CommissionStatus.CALCULATED);
        verify(configRepository).findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT);
    }

    @Test
    void calculateCommissionForOrder_noConfig_usesAgentRate() {
        when(configRepository.findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT))
                .thenReturn(Optional.empty());

        Commission commission = Commission.builder()
                .id(UUID.randomUUID())
                .agent(agent)
                .order(order)
                .orderAmount(order.getTotalAmount())
                .commissionRate(BigDecimal.valueOf(agent.getCommissionRate()))
                .commissionAmount(new BigDecimal("750.00"))
                .status(CommissionStatus.CALCULATED)
                .build();
        when(commissionRepository.save(any())).thenReturn(commission);

        Commission result = commissionCalculationService.calculateCommissionForOrder(order);

        assertThat(result.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("750.00"));
        assertThat(result.getCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(7.5));
    }

    @Test
    void calculateCommissionForOrder_performanceAgent_usesAgentRate() {
        agent.setCommissionType(AgentCommissionType.PERFORMANCE);

        Commission commission = Commission.builder()
                .id(UUID.randomUUID())
                .agent(agent)
                .order(order)
                .orderAmount(order.getTotalAmount())
                .commissionRate(BigDecimal.valueOf(7.5))
                .commissionAmount(new BigDecimal("750.00"))
                .status(CommissionStatus.CALCULATED)
                .build();
        when(commissionRepository.save(any())).thenReturn(commission);

        Commission result = commissionCalculationService.calculateCommissionForOrder(order);

        assertThat(result.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("750.00"));
        verify(configRepository, never()).findFirstByTypeAndIsActiveTrue(any());
    }

    @Test
    void calculateCommissionForOrder_noAgent_throws() {
        order.setAgent(null);

        assertThatThrownBy(() -> commissionCalculationService.calculateCommissionForOrder(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order has no agent");
    }

    @Test
    void calculateCommissionForOrder_inactiveAgent_throws() {
        agent.setIsActive(false);

        assertThatThrownBy(() -> commissionCalculationService.calculateCommissionForOrder(order))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Agent is not active");
    }

    @Test
    void calculateCommission_existingCommission_throws() {
        CommissionCalculateRequest request = new CommissionCalculateRequest();
        request.setOrderId(order.getId());

        when(commissionRepository.existsByOrderId(order.getId())).thenReturn(true);

        assertThatThrownBy(() -> commissionCalculationService.calculateCommission(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Commission already exists");
    }

    @Test
    void calculateCommission_orderNotFound_throws() {
        CommissionCalculateRequest request = new CommissionCalculateRequest();
        request.setOrderId(UUID.randomUUID());

        when(commissionRepository.existsByOrderId(request.getOrderId())).thenReturn(false);
        when(orderRepository.findById(request.getOrderId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commissionCalculationService.calculateCommission(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void calculateCommission_sendsNotifications() {
        CommissionCalculateRequest request = new CommissionCalculateRequest();
        request.setOrderId(order.getId());

        when(commissionRepository.existsByOrderId(order.getId())).thenReturn(false);
        when(orderRepository.findById(request.getOrderId())).thenReturn(Optional.of(order));
        when(configRepository.findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT)).thenReturn(Optional.empty());

        Commission commission = Commission.builder()
                .id(UUID.randomUUID())
                .agent(agent)
                .order(order)
                .orderAmount(order.getTotalAmount())
                .commissionRate(BigDecimal.valueOf(7.5))
                .commissionAmount(new BigDecimal("750.00"))
                .status(CommissionStatus.CALCULATED)
                .build();
        when(commissionRepository.save(any())).thenReturn(commission);
        when(mapper.toResponse(commission)).thenReturn(new CommissionResponse());

        commissionCalculationService.calculateCommission(request);

        verify(notificationPort).notifyAgent(eq(agent), anyString(), anyString());
        verify(notificationPort).createInternalAlert(any(), anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void calculateCommissionOnDiscountedOrder_usesDiscountedTotalAmount() {
        // Scenario: Admin creates an order with a product that has 20% discount.
        // Order total is the discounted total. Commission must be calculated
        // on this discounted total, not on the original undiscounted price.

        // Original undiscounted order would be: 100.00 * 5 = 500.00
        // After 20% discount: 80.00 * 5 = 400.00
        // Commission at 10%: 400.00 * 10% = 40.00
        Order discountedOrder = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("COL-A-20250601-002")
                .agent(agent)
                .totalAmount(new BigDecimal("400.00"))
                .status(OrderStatus.PENDING)
                .build();

        CommissionConfig config = CommissionConfig.builder()
                .commissionRate(10.0)
                .type(CommissionType.DEFAULT)
                .isActive(true)
                .build();

        when(configRepository.findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT))
                .thenReturn(Optional.of(config));

        Commission savedCommission = Commission.builder()
                .id(UUID.randomUUID())
                .agent(agent)
                .order(discountedOrder)
                .config(config)
                .orderAmount(new BigDecimal("400.00"))
                .commissionRate(BigDecimal.valueOf(10.0))
                .commissionAmount(new BigDecimal("40.00"))
                .status(CommissionStatus.CALCULATED)
                .build();
        when(commissionRepository.save(any())).thenReturn(savedCommission);

        Commission result = commissionCalculationService.calculateCommissionForOrder(discountedOrder);

        // Verify commission is calculated on the discounted amount, not original
        assertThat(result.getOrderAmount()).isEqualByComparingTo(new BigDecimal("400.00"));
        assertThat(result.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    void calculateCommissionOnFreeOrder_zeroCommission() {
        // Scenario: Product has 100% discount (free). Order total is 0.
        // Commission should be 0.

        Order freeOrder = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("COL-A-20250601-003")
                .agent(agent)
                .totalAmount(BigDecimal.ZERO)
                .status(OrderStatus.PENDING)
                .build();

        CommissionConfig config = CommissionConfig.builder()
                .commissionRate(10.0)
                .type(CommissionType.DEFAULT)
                .isActive(true)
                .build();

        when(configRepository.findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT))
                .thenReturn(Optional.of(config));

        Commission savedCommission = Commission.builder()
                .id(UUID.randomUUID())
                .agent(agent)
                .order(freeOrder)
                .config(config)
                .orderAmount(BigDecimal.ZERO)
                .commissionRate(BigDecimal.valueOf(10.0))
                .commissionAmount(BigDecimal.ZERO)
                .status(CommissionStatus.CALCULATED)
                .build();
        when(commissionRepository.save(any())).thenReturn(savedCommission);

        Commission result = commissionCalculationService.calculateCommissionForOrder(freeOrder);

        assertThat(result.getCommissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
