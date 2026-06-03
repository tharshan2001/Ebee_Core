package com.ebee.category.service;

import com.ebee.category.dto.CategoryRequest;
import com.ebee.category.dto.CategoryResponse;
import com.ebee.category.entity.Category;
import com.ebee.category.repository.CategoryRepository;
import com.ebee.category.dto.CategoryResponse;
import com.ebee.category.entity.Category;
import com.ebee.category.repository.CategoryRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = request.getName().trim();

        return categoryRepository.findByNameIgnoreCase(name)
                .map(existing -> {
                    if (Boolean.FALSE.equals(existing.getIsActive())) {
                        existing.setIsActive(true);
                        existing.setName(name);
                        return toResponse(categoryRepository.save(existing));
                    }
                    throw new IllegalArgumentException("Category already exists: " + name);
                })
                .orElseGet(() -> {
                    Category category = Category.builder().name(name).build();
                    return toResponse(categoryRepository.save(category));
                });
    }

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findByIsActiveTrueOrderByName().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public CategoryResponse getCategoryById(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        return toResponse(category);
    }

    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        String newName = request.getName().trim();
        if (!category.getName().equalsIgnoreCase(newName) &&
            categoryRepository.existsByNameIgnoreCaseAndIsActiveTrue(newName)) {
            throw new IllegalArgumentException("Category already exists: " + newName);
        }

        category.setName(newName);
        category.setIsActive(true);
        category = categoryRepository.save(category);
        return toResponse(category);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        if (Boolean.FALSE.equals(category.getIsActive())) {
            throw new IllegalArgumentException("Category is already deleted");
        }

        category.setIsActive(false);
        categoryRepository.save(category);
    }

    public Category findByNameOrThrow(String name) {
        return categoryRepository.findByNameIgnoreCaseAndIsActiveTrue(name.trim())
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + name));
    }

    private CategoryResponse toResponse(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setActive(Boolean.TRUE.equals(category.getIsActive()));
        return response;
    }
}
