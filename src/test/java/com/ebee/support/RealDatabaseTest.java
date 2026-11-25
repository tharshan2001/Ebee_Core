package com.ebee.support;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.enums.AgentCommissionType;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.common.enums.District;
import com.ebee.customer.entity.Customer;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderSource;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.repository.OrderRepository;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.enums.UserStatus;
import com.ebee.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Base for tests that run the real services against the real PostgreSQL schema (Flyway
 * migrations, constraints, locks). Locally that's the developer database from application.yml;
 * in CI, the Postgres service container via SPRING_DATASOURCE_*. Subclasses are usually
 * {@code @Transactional} so every test rolls back; concurrency tests can't be and clean up
 * after themselves. Same context configuration as CommissionLockTest, so Spring reuses it.
 */
@SpringBootTest
public abstract class RealDatabaseTest {

    @MockBean
    protected StringRedisTemplate stringRedisTemplate;

    @Autowired protected UserRepository userRepository;
    @Autowired protected CustomerRepository customerRepository;
    @Autowired protected ProductRepository productRepository;
    @Autowired protected AgentRepository agentRepository;
    @Autowired protected OrderRepository orderRepository;

    protected final String suffix = UUID.randomUUID().toString().substring(0, 8);
    protected User admin;

    @BeforeEach
    void signInAsAdmin() {
        admin = userRepository.save(User.builder()
                .email("admin-" + suffix + "@ebee.test")
                .password("encoded")
                .firstName("Admin")
                .lastName("Test")
                .role(RoleType.ADMIN)
                .isActive(true)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                admin.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    protected Customer customer() {
        return customerRepository.save(Customer.builder()
                .firstName("Test")
                .lastName("Customer " + suffix)
                .phoneNumber("077" + String.format("%07d", Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000)))
                .customerCode("CU-" + UUID.randomUUID().toString().substring(0, 8))
                .build());
    }

    protected Agent agent() {
        User agentUser = userRepository.save(User.builder()
                .email("agent-" + UUID.randomUUID().toString().substring(0, 8) + "@ebee.test")
                .password("encoded")
                .firstName("Test")
                .lastName("Agent")
                .role(RoleType.AGENT)
                .isActive(true)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());
        return agentRepository.save(Agent.builder()
                .user(agentUser)
                .agentCode("MN-" + UUID.randomUUID().toString().substring(0, 8))
                .phoneNumber("0771234567")
                .commissionRate(new BigDecimal("7.5"))
                .isActive(true)
                .district(District.MN)
                .commissionType(AgentCommissionType.DEFAULT)
                .country("Sri Lanka")
                .build());
    }

    /** A bare order row (no lines) so ledger entries have something real to point at. */
    protected Order order(Agent agent, Customer customer, String total) {
        return orderRepository.save(Order.builder()
                .agent(agent)
                .customer(customer)
                .createdBy(admin)
                .orderNumber("T-" + UUID.randomUUID().toString().substring(0, 8))
                .totalAmount(new BigDecimal(total))
                .status(OrderStatus.DELIVERED)
                .orderSource(OrderSource.AGENT)
                .build());
    }

    protected Product product(String price) {
        return productRepository.save(Product.builder()
                .name("Test product " + suffix)
                .itemCode("IT-" + UUID.randomUUID().toString().substring(0, 8))
                .category("Test")
                .price(new BigDecimal(price))
                .costPrice(BigDecimal.ZERO)
                .build());
    }
}
