package com.ebee.importer;

import com.ebee.category.service.CategoryService;
import com.ebee.importer.entity.ImportJob;
import com.ebee.importer.service.ImportResult;
import com.ebee.importer.service.ImportService;
import com.ebee.importer.service.ImportType;
import com.ebee.inventory.service.InventoryService;
import com.ebee.product.entity.Product;
import com.ebee.report.service.ReportsService;
import com.ebee.stock.dto.StockRequest;
import com.ebee.stock.enums.StockMovementType;
import com.ebee.supplier.dto.SupplierRequest;
import com.ebee.supplier.repository.SupplierRepository;
import com.ebee.supplier.service.SupplierService;
import com.ebee.support.RealDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Imports run end to end against the real schema, starting from the downloadable templates. */
@Transactional
class ImportFlowIntegrationTest extends RealDatabaseTest {

    @Autowired private ImportService imports;
    @Autowired private InventoryService inventory;
    @Autowired private SupplierRepository suppliers;
    @Autowired private SupplierService supplierService;
    @Autowired private CategoryService categories;
    @Autowired private ReportsService reports;
    @Autowired private com.ebee.customer.service.CustomerService customerService;

    private ImportResult upload(ImportType type, String csv, boolean dryRun) throws IOException {
        return imports.run(type, new MockMultipartFile("file", "file.csv", "text/csv", csv.getBytes()), dryRun);
    }

    private ImportResult checkThenApply(ImportType type, String csv) throws IOException {
        ImportResult check = upload(type, csv, true);
        assertThat(check.errors()).as("check %s", type).isEmpty();
        ImportResult applied = upload(type, csv, false);
        assertThat(applied.errors()).as("apply %s", type).isEmpty();
        assertThat(applied.status()).isEqualTo(ImportJob.Status.APPLIED);
        assertThat(check.created()).isEqualTo(applied.created());
        return applied;
    }

    @Test
    void templatesImport_andRepeatImportsDoNotOverwriteOrDoubleAnything() throws IOException {
        checkThenApply(ImportType.PRODUCTS, ImportType.PRODUCTS.templateCsv());
        checkThenApply(ImportType.OPENING_STOCK, ImportType.OPENING_STOCK.templateCsv());
        checkThenApply(ImportType.CUSTOMERS, ImportType.CUSTOMERS.templateCsv());
        checkThenApply(ImportType.SUPPLIERS, ImportType.SUPPLIERS.templateCsv());

        Product product = productRepository.findByItemCodeIgnoreCase("SKU-001").get(0);
        assertThat(product.getCostPrice()).isEqualByComparingTo("900.00");

        var customer = customerRepository.findByPhoneNumber("0771234567").orElseThrow();
        assertThat(reports.getAgingReport().getCustomerBuckets())
                .anyMatch(b -> b.getCustomerId().equals(customer.getId().toString())
                        && b.getTotalOutstanding().compareTo(new BigDecimal("12500.00")) == 0);

        ImportResult again = upload(ImportType.CUSTOMERS,
                "phone,first_name,district,opening_balance\n0771234567,Nimal,,999\n", false);
        assertThat(again.status()).as("%s", again.errors()).isEqualTo(ImportJob.Status.APPLIED);
        assertThat(customer.getLastName()).isEqualTo("Perera");
        assertThat(customer.getOutstandingBalance()).isEqualByComparingTo("12500.00");

        var supplier = suppliers.findFirstByNameIgnoreCase("Lanka Oils (Pvt) Ltd").orElseThrow();
        checkThenApply(ImportType.SUPPLIERS, ImportType.SUPPLIERS.templateCsv());
        assertThat(supplier.getOutstandingBalance()).isEqualByComparingTo("250000.00");

        assertThatThrownBy(() -> supplierService.createSupplier(new SupplierRequest("lanka oils (pvt) ltd", null, null,
                null, "1 Road", null, "Jaffna", null, "", null))).hasMessageContaining("already exists");
    }

    @Test
    void customerCredit_becomesAnAdvance_andADeletedCategoryComesBack() throws IOException {
        String phone = nextPhone();
        checkThenApply(ImportType.CUSTOMERS, "phone,first_name,last_name,district,city,street,opening_balance\n"
                + phone + ",A,B,JF,Jaffna,1 Road,-300\n");
        var customer = customerRepository.findByPhoneNumber(phone).orElseThrow();
        assertThat(customer.getAdvanceBalance()).isEqualByComparingTo("300");
        assertThat(customer.getOutstandingBalance()).isEqualByComparingTo("0");

        var request = new com.ebee.category.dto.CategoryRequest();
        request.setName("Spices " + suffix);
        categories.deleteCategory(categories.createCategory(request).getId());
        checkThenApply(ImportType.PRODUCTS, "item_code,name,category,price\nSP-" + suffix + ",Chilli,spices "
                + suffix + ",10\n");
        assertThat(productRepository.findByItemCodeIgnoreCase("SP-" + suffix).get(0).getCategory())
                .isEqualTo("Spices " + suffix);
    }

    @Test
    void expiryProducts_canBeAddedByStockAdjustment() throws IOException {
        checkThenApply(ImportType.PRODUCTS, "item_code,name,category,price,tracks_expiry\nMILK-" + suffix
                + ",Milk,Dairy " + suffix + ",10,true\n");
        StockRequest request = new StockRequest();
        request.setProductId(productRepository.findByItemCodeIgnoreCase("MILK-" + suffix).get(0).getId());
        request.setQuantityChange(5);
        request.setMovementType(StockMovementType.IN);
        request.setNotes("Count");
        request.setUnitCost(new BigDecimal("7.50"));
        request.setExpiryDate(LocalDate.now().plusMonths(6));

        assertThat(inventory.adjustStock(request).getQuantity()).isEqualTo(5);
    }

    @Test
    void onlyNamesPhoneAndDistrictAreNeeded_andPhoneTwoCanBeSetAndRemoved() throws IOException {
        String phone = nextPhone();
        String second = nextPhone();
        checkThenApply(ImportType.CUSTOMERS, "phone,first_name,last_name,district\n" + phone + ",Solo,Silva,JF\n");
        var customer = customerRepository.findByPhoneNumber(phone).orElseThrow();
        assertThat(customer.getLastName()).isEqualTo("Silva");
        assertThat(customer.getAddress().getStreet()).isNull();
        assertThat(customer.getPhoneNumber2()).isNull();

        checkThenApply(ImportType.CUSTOMERS, "phone,first_name,district,phone_2\n" + phone + ",Solo,," + second + "\n");
        assertThat(customer.getPhoneNumber2()).isEqualTo(second);
        assertThat(customerService.searchCustomers(second)).extracting(c -> c.getPhoneNumber()).containsExactly(phone);

        var request = new com.ebee.customer.dto.CustomerRequest();
        request.setFirstName("Solo");
        request.setLastName("Silva");
        request.setPhoneNumber(phone);
        request.setPhoneNumber2("");
        var address = new com.ebee.customer.dto.AddressRequest();
        address.setDistrict("Jaffna");
        address.setStreet("  ");
        request.setAddress(address);
        var updated = customerService.updateCustomer(customer.getId(), request);
        assertThat(updated.getPhoneNumber2()).isNull();
        assertThat(updated.getAddress().getStreet()).isNull();

        request.setPhoneNumber2(phone);
        assertThatThrownBy(() -> customerService.updateCustomer(customer.getId(), request))
                .hasMessageContaining("different from the main phone");
    }
}
