package com.bytedance.streamshop.domain.model;

public class OrderItem {
    private String id;
    private String orderId;
    private String productId;
    private int quantity;
    private double unitPrice;
    private double subtotal;
    private Product product;

    public OrderItem() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
}
