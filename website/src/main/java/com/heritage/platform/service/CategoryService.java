package com.heritage.platform.service;

import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.common.ResourceNotFoundException;
import com.heritage.platform.dto.request.CategoryCreateRequest;
import com.heritage.platform.dto.response.CategoryResponse;
import com.heritage.platform.entity.Category;
import com.heritage.platform.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private static final String PUBLICATION_CATEGORY_NAME = "Publication";

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new BadRequestException("This collection name already exists.");
        }

        Category category = categoryRepository.save(Category.create(request.name(), request.description()));
        return toResponse(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Category getById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("The collection could not be found."));
    }

    @Transactional
    public Category getPublicationCategory() {
        return categoryRepository.findByName(PUBLICATION_CATEGORY_NAME)
                .orElseGet(() -> categoryRepository.save(Category.create(
                        PUBLICATION_CATEGORY_NAME,
                        "Academic publications, papers, technical reports, and research outputs."
                )));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getCreatedAt()
        );
    }
}
