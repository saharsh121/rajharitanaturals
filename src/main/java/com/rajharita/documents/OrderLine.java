package com.rajharita.documents;

import java.math.BigDecimal;

public class OrderLine {
    private String productId;
    private String name;
    private String brand;
    private BigDecimal unitPrice;
    private int quantity;
    private BigDecimal lineTotal;

    public OrderLine() { }

    public OrderLine(String productId, String name, String brand, BigDecimal unitPrice, int quantity) {
        this.productId = productId;
        this.name = name;
        this.brand = brand;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public String getProductId() { return productId; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
