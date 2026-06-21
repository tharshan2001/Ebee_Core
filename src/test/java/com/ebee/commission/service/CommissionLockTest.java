package com.ebee.commission.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.enums.AgentCommissionType;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.enums.District;
import com.ebee.customer.entity.Address;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderSource;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.repository.OrderRepository;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.enums.UserStatus;
import com.ebee.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CommissionLockTest {

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired private UserRepository userRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CommissionRepository commissionRepository;
    @Autowired private CommissionLifecycleService commissionLifecycleService;

    private Agent agent;
    private Order order;

    @BeforeEach
    void setUp() {
        User adminUser = userRepository.save(User.builder()
                .email("admin@ebee.com")
                .password("encoded")
                .firstName("Admin")
                .lastName("User")
                .role(RoleType.ADMIN)
                .isActive(true)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        adminUser.getEmail(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        User agentUser = userRepository.save(User.builder()
                .email("agent-mn001@ebee.com")
                .password("encoded")
                .firstName("Mannar")
                .lastName("Agent")
                .role(RoleType.AGENT)
                .isActive(true)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        agent = agentRepository.save(Agent.builder()
                .user(agentUser)
                .agentCode("MN-001")
                .phoneNumber("0771234567")
                .commissionRate(BigDecimal.valueOf(7.5))
                .isActive(true)
                .district(District.MN)
                .commissionType(AgentCommissionType.DEFAULT)
                .build());

        Customer customer = customerRepository.save(Customer.builder()
                .firstName("Test")
                .lastName("Customer")
                .phoneNumber("0771234567")
                .build());

        Address address = Address.builder()
                .customer(customer)
                .district(District.MN)
                .city("Mannar")
                .street("Main Street")
                .build();
        customer.setAddress(address);
        customerRepository.save(customer);

        order = orderRepository.save(Order.builder()
                .agent(agent)
                .customer(customer)
                .createdBy(adminUser)
                .orderNumber("MN-A-20250101-001")
                .totalAmount(new BigDecimal("10000.00"))
                .status(OrderStatus.DELIVERED)
                .orderSource(OrderSource.AGENT)
                .build());
    }

    @Test
    void commission_approved5DaysAgo_cannotBeProcessed() {
        Commission commission = commissionRepository.save(Commission.builder()
                .agent(agent)
                .order(order)
                .orderAmount(order.getTotalAmount())
                .commissionRate(new BigDecimal("7.5"))
                .commissionAmount(new BigDecimal("750.00"))
                .status(CommissionStatus.APPROVED)
                .approvedAt(LocalDateTime.now().minusDays(5))
                .build());

        assertThatThrownBy(() -> commissionLifecycleService.markAsProcessing(commission.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("holding period");
    }

    @Test
    void commission_approved15DaysAgo_canBeProcessed() {
        Commission commission = commissionRepository.save(Commission.builder()
                .agent(agent)
                .order(order)
                .orderAmount(order.getTotalAmount())
                .commissionRate(new BigDecimal("7.5"))
                .commissionAmount(new BigDecimal("750.00"))
                .status(CommissionStatus.APPROVED)
                .approvedAt(LocalDateTime.now().minusDays(15))
                .build());

        commissionLifecycleService.markAsProcessing(commission.getId());

        Commission updated = commissionRepository.findById(commission.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(CommissionStatus.PROCESSING);
    }
}
