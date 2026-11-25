package com.ebee.pricing.service;

import com.ebee.customer.repository.CustomerRepository;
import com.ebee.pricing.entity.PriceGroup;
import com.ebee.pricing.entity.ProductGroupPrice;
import com.ebee.pricing.entity.ProductUnit;
import com.ebee.pricing.entity.Unit;
import com.ebee.pricing.repository.PriceGroupRepository;
import com.ebee.pricing.repository.ProductGroupPriceRepository;
import com.ebee.pricing.repository.ProductUnitRepository;
import com.ebee.pricing.repository.UnitRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PricingServiceTest {

    private final PriceGroupRepository groups = mock(PriceGroupRepository.class);
    private final ProductGroupPriceRepository groupPrices = mock(ProductGroupPriceRepository.class);
    private final ProductUnitRepository productUnits = mock(ProductUnitRepository.class);
    private final PricingService service = new PricingService(groups, groupPrices, mock(UnitRepository.class),
            productUnits, mock(ProductRepository.class), mock(CustomerRepository.class));

    private Product oil;
    private PriceGroup wholesale;

    private static <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    @BeforeEach
    void setUp() {
        oil = Product.builder().id(UUID.randomUUID()).name("Oil").price(new BigDecimal("100.00")).build();
        wholesale = withId(new PriceGroup("Wholesale", null));
        when(groups.findById(wholesale.getId())).thenReturn(Optional.of(wholesale));
        when(groupPrices.findByProductIdAndPriceGroupId(oil.getId(), wholesale.getId()))
                .thenReturn(Optional.of(new ProductGroupPrice(oil.getId(), wholesale.getId(), new BigDecimal("85.00"))));
    }

    private ProductUnit pack(int units, String price) {
        ProductUnit pu = withId(new ProductUnit(oil.getId(), withId(new Unit("Box")), units, price == null ? null : new BigDecimal(price)));
        when(productUnits.findById(pu.getId())).thenReturn(Optional.of(pu));
        return pu;
    }

    @Test
    void noGroupNoPack_isTheNormalPrice() {
        var q = service.quote(oil, null, null);
        assertThat(q.listPrice()).isEqualByComparingTo("100.00");
        assertThat(q.unitsPerSalesUnit()).isEqualTo(1);
        assertThat(q.groupPrice()).isFalse();
    }

    @Test
    void groupCustomer_getsTheGroupPrice_inactiveGroupIsIgnored() {
        assertThat(service.quote(oil, wholesale.getId(), null).listPrice()).isEqualByComparingTo("85.00");

        wholesale.setActive(false);
        assertThat(service.quote(oil, wholesale.getId(), null).listPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void packWithoutOwnPrice_isUnitPriceTimesPackSize_usingTheGroupPrice() {
        ProductUnit box = pack(12, null);

        var q = service.quote(oil, wholesale.getId(), box.getId());

        assertThat(q.listPrice()).isEqualByComparingTo("1020.00"); // 85 × 12
        assertThat(q.unitsPerSalesUnit()).isEqualTo(12);
        assertThat(q.salesUnitName()).isEqualTo("Box of 12");
    }

    @Test
    void packWithItsOwnPrice_usesItExactly() {
        ProductUnit box = pack(12, "1000.00");
        assertThat(service.quote(oil, wholesale.getId(), box.getId()).listPrice()).isEqualByComparingTo("1000.00");
    }

    @Test
    void packOfAnotherProduct_orInactivePack_isRejected() {
        ProductUnit other = withId(new ProductUnit(UUID.randomUUID(), withId(new Unit("Box")), 6, null));
        when(productUnits.findById(other.getId())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.quote(oil, null, other.getId())).hasMessageContaining("isn't a selling unit");

        ProductUnit retired = pack(6, null);
        retired.setActive(false);
        assertThatThrownBy(() -> service.quote(oil, null, retired.getId())).hasMessageContaining("no longer sold");
    }
}
