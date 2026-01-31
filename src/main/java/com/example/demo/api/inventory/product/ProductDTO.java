package com.example.demo.api.inventory.product;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDTO {
    private Long id;
    private String name;
    private String category;
    private Integer minQuantity;
    private String unit;
    private String note;

    public static ProductDTO fromEntity(Product product) {
        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .category(product.getCategory())
                .minQuantity(product.getMinQuantity())
                .unit(product.getUnit())
                .note(product.getNote())
                .build();
    }

    public Product toEntity() {
        return Product.builder()
                .id(this.id)
                .name(this.name)
                .category(this.category)
                .minQuantity(this.minQuantity != null ? this.minQuantity : 0)
                .unit(this.unit != null ? this.unit : "개")
                .note(this.note)
                .build();
    }
}
