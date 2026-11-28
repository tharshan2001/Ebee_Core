package com.ebee.importer;

import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.accounting.repository.SupplierLedgerRepository;
import com.ebee.accounting.service.AccountingPeriodService;
import com.ebee.common.enums.District;
import com.ebee.customer.dto.AdjustAdvanceRequest;
import com.ebee.customer.repository.CustomerAdvanceTransactionRepository;
import com.ebee.importer.service.ImportType;
import com.ebee.importer.service.RowImporter;
import com.ebee.accounting.service.LedgerService;
import com.ebee.common.security.SecurityService;
import com.ebee.customer.dto.CustomerResponse;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.customer.service.CustomerService;
import com.ebee.importer.service.ImportRow;
import com.ebee.importer.service.RowError;
import com.ebee.importer.service.importers.CustomerImporter;
import com.ebee.importer.service.importers.OpeningStockImporter;
import com.ebee.importer.service.importers.SupplierImporter;
import com.ebee.inventory.service.InventoryService;
import com.ebee.location.entity.Location;
import com.ebee.location.repository.LocationRepository;
import com.ebee.location.service.LocationService;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.supplier.dto.SupplierResponse;
import com.ebee.supplier.entity.Supplier;
import com.ebee.supplier.repository.SupplierRepository;
import com.ebee.supplier.service.SupplierService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportersTest {

    private static ImportRow row(int n, String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return new ImportRow(n, m);
    }

    private static List<String> messages(List<RowError> errors) {
        return errors.stream().map(e -> e.column() + ": " + e.message()).toList();
    }

    // ── opening stock ──────────────────────────────────────────────────

    @Test
    void openingStock_refusesExistingStock_unknownProducts_andMissingExpiry() {
        ProductRepository products = mock(ProductRepository.class);
        LocationService locations = mock(LocationService.class);
        InventoryService inventory = mock(InventoryService.class);
        Location main = new Location("MAIN", "Main", Location.Type.WAREHOUSE);
        ReflectionTestUtils.setField(main, "id", UUID.randomUUID());
        when(locations.defaultLocation()).thenReturn(main);
        Product stocked = Product.builder().id(UUID.randomUUID()).name("Oil").build();
        Product milk = Product.builder().id(UUID.randomUUID()).name("Milk").tracksExpiry(true).build();
        when(products.findByItemCodeIgnoreCase("OIL")).thenReturn(List.of(stocked));
        when(products.findByItemCodeIgnoreCase("MILK")).thenReturn(List.of(milk));
        when(inventory.hasStockHistory(stocked.getId(), main.getId())).thenReturn(true);
        var importer = new OpeningStockImporter(products, mock(LocationRepository.class), locations, inventory);
        List<RowError> errors = new ArrayList<>();

        importer.validate(List.of(
                row(2, "item_code", "OIL", "quantity", "10", "unit_cost", "100"),
                row(3, "item_code", "MILK", "quantity", "5", "unit_cost", "80"),
                row(4, "item_code", "NOPE", "quantity", "1.5", "unit_cost", "-1")), errors, new ArrayList<>());

        assertThat(messages(errors)).anyMatch(m -> m.contains("already has stock at Main"))
                .anyMatch(m -> m.startsWith("expiry_date") && m.contains("tracks expiry"))
                .anyMatch(m -> m.contains("no product with item code 'NOPE'"))
                .anyMatch(m -> m.startsWith("quantity") && m.contains("whole number"))
                .anyMatch(m -> m.startsWith("unit_cost") && m.contains("negative"));
    }

    // ── customers ──────────────────────────────────────────────────────

    private record CustomerFixture(CustomerImporter importer, CustomerService service, LedgerService ledger,
                                   CustomerRepository repo, UUID customerId) {
    }

    private CustomerFixture customers(boolean exists, boolean hasHistory) {
        CustomerRepository repo = mock(CustomerRepository.class);
        CustomerService service = mock(CustomerService.class);
        CustomerLedgerRepository ledgerRepo = mock(CustomerLedgerRepository.class);
        LedgerService ledger = mock(LedgerService.class);
        UUID id = UUID.randomUUID();
        Customer c = Customer.builder().id(id).firstName("Nimal").lastName("Perera").build();
        when(repo.findByPhoneNumber("0771234567")).thenReturn(exists ? Optional.of(c) : Optional.empty());
        when(repo.findById(id)).thenReturn(Optional.of(c));
        CustomerResponse created = new CustomerResponse();
        created.setId(id);
        when(service.createCustomer(any(), eq(false))).thenReturn(created);
        when(ledgerRepo.existsByCustomerId(id)).thenReturn(hasHistory);
        return new CustomerFixture(new CustomerImporter(repo, service, ledgerRepo,
                mock(CustomerAdvanceTransactionRepository.class), ledger, mock(SecurityService.class),
                mock(AccountingPeriodService.class)), service, ledger, repo, id);
    }

    @Test
    void customers_validation() {
        List<RowError> errors = new ArrayList<>();
        customers(false, false).importer().validate(List.of(
                row(2, "phone", "077 123 4567", "first_name", "A", "district", "JF", "city", "J", "street", "S"),
                row(3, "phone", "+94771234567", "first_name", "B", "district", "Colombo", "city", "C", "street", "S",
                        "email", "not-an-email")), errors, new ArrayList<>());

        assertThat(messages(errors)).anyMatch(m -> m.startsWith("phone") && m.contains("more than once"))
                .anyMatch(m -> m.startsWith("district") && m.contains("Colombo"))
                .anyMatch(m -> m.startsWith("email"));
    }

    @Test
    void newCustomer_isCreatedWithoutWelcomeMessage_andOpeningBalancePostedAsDebit() {
        var f = customers(false, false);

        var counts = f.importer().apply(List.of(row(2, "phone", "0771234567", "first_name", "Nimal", "district", "JF",
                "city", "Jaffna", "street", "1 Rd", "last_name", "P", "opening_balance", "12500.00")));

        assertThat(counts.created()).isEqualTo(1);
        verify(f.service()).createCustomer(any(), eq(false));
        verify(f.ledger()).recordCustomerAdjustment(eq(f.customerId()), eq(new BigDecimal("12500.00")), any(),
                eq("Opening balance (import)"), eq("OPENING_BALANCE"), any());
    }

    @Test
    void reimport_updatesTheCustomer_andDoesNotPostTheOpeningBalanceTwice() {
        var f = customers(true, true);

        var counts = f.importer().apply(List.of(row(2, "phone", "0771234567", "first_name", "Nimal", "district", "JF",
                "city", "Jaffna", "street", "1 Rd", "opening_balance", "12500.00")));

        assertThat(counts.updated()).isEqualTo(1);
        assertThat(counts.skipped()).isEqualTo(1);
        verify(f.service()).updateCustomer(eq(f.customerId()), any());
        verify(f.ledger(), never()).recordCustomerAdjustment(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reimportWithoutASurname_leavesTheStoredOneAndTheAddress() {
        var f = customers(true, false);

        f.importer().apply(List.of(row(2, "phone", "0771234567", "first_name", "Nimal", "street", "9 New Rd")));

        verify(f.service()).updateCustomer(eq(f.customerId()),
                argThat(r -> r.getLastName() == null && r.getAddress() == null));
    }

    @Test
    void customerPreview_countsUpdates_andNewCustomersNeedWhatTheFormNeeds() {
        var f = customers(true, true);
        List<RowError> errors = new ArrayList<>();
        List<RowError> notices = new ArrayList<>();

        var preview = f.importer().validate(List.of(
                row(2, "phone", "0771234567", "first_name", "Nimal", "opening_balance", "10"),
                row(3, "phone", "0770000000", "first_name", "New")), errors, notices);

        assertThat(preview).isEqualTo(new RowImporter.Counts(1, 1, 1));
        assertThat(errors).extracting(RowError::column).containsExactly("district", "city", "street", "last_name");
        assertThat(messages(notices)).anyMatch(m -> m.contains("will update the existing customer Nimal Perera"))
                .anyMatch(m -> m.startsWith("opening_balance: not posted"));
    }

    @Test
    void customerInCredit_getsAnAdvance() {
        var f = customers(false, false);

        f.importer().apply(List.of(row(2, "phone", "0771234567", "first_name", "Nimal", "district", "JF",
                "city", "Jaffna", "street", "1 Rd", "opening_balance", "-300")));

        verify(f.service()).manualAdjustAdvance(f.customerId(),
                new AdjustAdvanceRequest(new BigDecimal("300"), "Opening balance (import)"));
        verify(f.ledger(), never()).recordCustomerCreditAdjustment(any(), any(), any(), any(), any());
    }

    // ── suppliers ──────────────────────────────────────────────────────

    @Test
    void supplierOpeningBalance_isPostedOnlyForSuppliersWithoutHistory() {
        SupplierRepository repo = mock(SupplierRepository.class);
        SupplierService service = mock(SupplierService.class);
        SupplierLedgerRepository ledgerRepo = mock(SupplierLedgerRepository.class);
        LedgerService ledger = mock(LedgerService.class);
        UUID newId = UUID.randomUUID();
        UUID oldId = UUID.randomUUID();
        when(repo.findFirstByNameIgnoreCase("New Co")).thenReturn(Optional.empty());
        Supplier existing = new Supplier();
        ReflectionTestUtils.setField(existing, "id", oldId);
        when(repo.findFirstByNameIgnoreCase("Old Co")).thenReturn(Optional.of(existing));
        SupplierResponse created = mock(SupplierResponse.class);
        when(created.id()).thenReturn(newId);
        when(service.createSupplier(any())).thenReturn(created);
        when(repo.findById(any())).thenReturn(Optional.of(new Supplier()));
        when(ledgerRepo.calculateBalance(any())).thenReturn(BigDecimal.ZERO);
        when(ledgerRepo.existsBySupplierId(oldId)).thenReturn(true);
        var importer = new SupplierImporter(repo, service, ledgerRepo, ledger, mock(SecurityService.class),
                mock(AccountingPeriodService.class));

        var counts = importer.apply(List.of(
                row(2, "name", "New Co", "address_line1", "1 Rd", "city", "C", "opening_balance", "250000"),
                row(3, "name", "Old Co", "address_line1", "2 Rd", "city", "C", "opening_balance", "-1000")));

        assertThat(counts.created()).isEqualTo(1);
        assertThat(counts.skipped()).isEqualTo(1);
        verify(ledger).recordSupplierInvoice(eq(newId), any(), eq(new BigDecimal("250000")), anyString(), any(), anyString(), any());
        verify(ledger, never()).recordSupplierAdjustment(any(), any(), any(), any(), any(), any());
        verify(service).createSupplier(any());
    }

    @Test
    void templateSampleRows_useSupportedDistricts() {
        for (ImportType type : List.of(ImportType.CUSTOMERS, ImportType.SUPPLIERS)) {
            String[] lines = type.templateCsv().split("\n");
            int i = List.of(lines[0].split(",")).indexOf("district");
            assertThat(District.isValid(lines[1].split(",")[i])).as(type.name()).isTrue();
        }
    }

    // ── products ───────────────────────────────────────────────────────

    @Test
    void products_unknownVatRateAndDuplicateCodes_areErrors_missingCategoryIsCreatedOnApply() {
        var productRepo = mock(ProductRepository.class);
        var productService = mock(com.ebee.product.service.ProductService.class);
        var categoryRepo = mock(com.ebee.category.repository.CategoryRepository.class);
        var categoryService = mock(com.ebee.category.service.CategoryService.class);
        var taxService = mock(com.ebee.tax.service.TaxService.class);
        var vat = new com.ebee.tax.entity.TaxRate("VAT 18%", new BigDecimal("18"));
        ReflectionTestUtils.setField(vat, "id", UUID.randomUUID());
        when(taxService.listRates()).thenReturn(List.of(vat));
        var importer = new com.ebee.importer.service.importers.ProductImporter(productRepo, productService, categoryRepo,
                categoryService, taxService, mock(InventoryService.class));
        List<RowError> errors = new ArrayList<>();

        importer.validate(List.of(
                row(2, "item_code", "A-1", "name", "Oil", "category", "Grocery", "price", "100", "vat_rate", "VAT 15%"),
                row(3, "item_code", "a-1", "name", "Oil 2", "category", "Grocery", "price", "100")), errors, new ArrayList<>());
        assertThat(messages(errors)).anyMatch(m -> m.startsWith("vat_rate"))
                .anyMatch(m -> m.startsWith("item_code") && m.contains("more than once"));

        when(categoryRepo.findByNameIgnoreCase("New Cat")).thenReturn(Optional.empty());
        var counts = importer.apply(List.of(row(2, "item_code", "B-1", "name", "Rice", "category", "New Cat",
                "price", "300", "vat_rate", "vat 18%")));

        assertThat(counts.created()).isEqualTo(1);
        verify(categoryService).createCategory(org.mockito.ArgumentMatchers.argThat(c -> "New Cat".equals(c.getName())));
        verify(productService).createProduct(org.mockito.ArgumentMatchers.argThat(r -> vat.getId().equals(r.getTaxRateId())
                && new BigDecimal("300").compareTo(r.getPrice()) == 0));
    }

    @Test
    void productImport_matchesCodeAndCategoryIgnoringCase_andKeepsTheCostOfStockedProducts() {
        var productRepo = mock(ProductRepository.class);
        var productService = mock(com.ebee.product.service.ProductService.class);
        var categoryRepo = mock(com.ebee.category.repository.CategoryRepository.class);
        var inventory = mock(InventoryService.class);
        Product oil = Product.builder().id(UUID.randomUUID()).name("Oil").itemCode("SKU-1").build();
        when(productRepo.findByItemCodeIgnoreCase("sku-1")).thenReturn(List.of(oil));
        when(categoryRepo.findByNameIgnoreCase("grocery")).thenReturn(Optional.of(
                com.ebee.category.entity.Category.builder().name("Grocery").build()));
        when(inventory.hasAnyStockHistory(oil.getId())).thenReturn(true);
        var importer = new com.ebee.importer.service.importers.ProductImporter(productRepo, productService, categoryRepo,
                mock(com.ebee.category.service.CategoryService.class), mock(com.ebee.tax.service.TaxService.class),
                inventory);

        importer.apply(List.of(row(2, "item_code", "sku-1", "name", "Oil", "category", "grocery", "price", "100",
                "cost_price", "5")));

        verify(productService).updateProduct(eq(oil.getId()), argThat(r -> "SKU-1".equals(r.getItemCode())
                && "Grocery".equals(r.getCategory()) && r.getCostPrice() == null));
        verify(productService, never()).createProduct(any());
    }
}
