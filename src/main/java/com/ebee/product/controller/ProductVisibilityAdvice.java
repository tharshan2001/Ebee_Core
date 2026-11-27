package com.ebee.product.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.common.response.PageResponse;
import com.ebee.product.dto.ProductResponse;
import com.ebee.product.dto.ProductSearchResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Product reads are public, so every product leaving {@link ProductController} is trimmed to what the
 * caller may see. Admins see everything. Other signed-in users never see cost prices. Anonymous
 * visitors get what a shopper needs: no costs, stock levels, discount limits or sales figures.
 */
@RestControllerAdvice(assignableTypes = ProductController.class)
public class ProductVisibilityAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> wrapper) {
            trim(wrapper.getData(), SecurityContextHolder.getContext().getAuthentication());
        }
        return body;
    }

    private void trim(Object data, Authentication caller) {
        if (data instanceof ProductResponse product) {
            trimProduct(product, caller);
        } else if (data instanceof PageResponse<?> page) {
            page.getContent().forEach(item -> trim(item, caller));
        } else if (data instanceof ProductSearchResponse search) {
            search.getProducts().forEach(item -> trim(item, caller));
        } else if (data instanceof Iterable<?> items) {
            items.forEach(item -> trim(item, caller));
        }
    }

    private void trimProduct(ProductResponse product, Authentication caller) {
        var inventory = product.getInventory();
        product.setInStock(inventory != null && inventory.getAvailableQuantity() != null
                && inventory.getAvailableQuantity() > 0);

        boolean signedIn = caller != null && caller.isAuthenticated()
                && !(caller instanceof AnonymousAuthenticationToken);
        boolean admin = signedIn && caller.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        if (admin) {
            return;
        }

        product.setCostPrice(null);
        if (inventory != null) {
            inventory.setAverageCost(null);
        }
        if (!signedIn) {
            product.setInventory(null);
            product.setMaxDiscountType(null);
            product.setMaxDiscountValue(null);
            product.setTotalSold(null);
            product.setTaxRateId(null);
        }
    }
}
