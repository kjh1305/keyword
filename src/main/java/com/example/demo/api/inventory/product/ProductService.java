package com.example.demo.api.inventory.product;

import com.example.demo.api.inventory.log.ActivityLogService;
import com.example.demo.api.inventory.order.StockOrderRepository;
import com.example.demo.api.inventory.stock.InventoryRepository;
import com.example.demo.api.inventory.stock.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StockOrderRepository stockOrderRepository;
    private final UsageLogRepository usageLogRepository;
    private final ActivityLogService activityLogService;

    public Page<ProductDTO> getProducts(String category, String keyword, Pageable pageable) {
        Page<Product> products;
        if ((category == null || category.isEmpty()) && (keyword == null || keyword.isEmpty())) {
            products = productRepository.findAll(pageable);
        } else {
            products = productRepository.searchProducts(
                    (category == null || category.isEmpty()) ? null : category,
                    (keyword == null || keyword.isEmpty()) ? null : keyword,
                    pageable
            );
        }
        return products.map(ProductDTO::fromEntity);
    }

    public List<ProductDTO> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public ProductDTO getProductById(Long id) {
        return productRepository.findById(id)
                .map(ProductDTO::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다: " + id));
    }

    public List<String> getAllCategories() {
        return productRepository.findAllCategories();
    }

    @Transactional
    public ProductDTO createProduct(ProductDTO dto) {
        if (productRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("이미 존재하는 제품명입니다: " + dto.getName());
        }
        Product product = dto.toEntity();
        Product saved = productRepository.save(product);
        activityLogService.logCreate("PRODUCT", saved.getId(), saved.getName());
        return ProductDTO.fromEntity(saved);
    }

    @Transactional
    public ProductDTO updateProduct(Long id, ProductDTO dto) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다: " + id));

        if (!product.getName().equals(dto.getName()) && productRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("이미 존재하는 제품명입니다: " + dto.getName());
        }

        product.setName(dto.getName());
        product.setCategory(dto.getCategory());
        product.setMinQuantity(dto.getMinQuantity());
        product.setUnit(dto.getUnit());
        product.setNote(dto.getNote());

        Product saved = productRepository.save(product);
        activityLogService.logUpdate("PRODUCT", saved.getId(), saved.getName(), null);
        return ProductDTO.fromEntity(saved);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다: " + id));
        String productName = product.getName();

        // 외래키 제약으로 인해 관련 데이터 먼저 삭제
        usageLogRepository.deleteByProductId(id);
        stockOrderRepository.deleteByProductId(id);
        inventoryRepository.deleteByProductId(id);

        productRepository.deleteById(id);
        activityLogService.logDelete("PRODUCT", id, productName);
    }

    @Transactional
    public Product getOrCreateProduct(String name, String category) {
        return productRepository.findByName(name)
                .orElseGet(() -> {
                    Product newProduct = Product.builder()
                            .name(name)
                            .category(category)
                            .build();
                    return productRepository.save(newProduct);
                });
    }
}
