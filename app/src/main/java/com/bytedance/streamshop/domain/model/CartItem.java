package com.bytedance.streamshop.domain.model;

public class CartItem {
    private String id;
    private String userId;
    private String productId;
    private int quantity;
    private boolean selected;
    private String createdAt;
    private Product product;

    public CartItem() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
}
