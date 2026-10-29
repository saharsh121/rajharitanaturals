package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "invoice_template_settings")
public class InvoiceTemplateSettings {

    @Id
    private String id = "invoice-template";
    private String businessName = "Rajharita Naturals";
    private String invoiceTitle = "TAX INVOICE";
    private String subtitle = "Pure by Nature. Made for Wellness.";
    private String footer = "Thank you for choosing Rajharita Naturals.";
    private List<Section> sections = defaultSections();

    public static List<Section> defaultSections() {
        List<Section> sections = new ArrayList<>();
        sections.add(section("ORDER DETAILS", "TEXT",
                "Order number: {{orderNumber}}\nOrder date: {{date}}\nOrder status: {{status}}\nPayment: {{paymentMethod}} — {{paymentStatus}}\nCustomer: {{customerName}}\nEmail: {{customerEmail}}\nPhone: {{customerPhone}}\nTracking: {{trackingNumber}}"));

        Section items = section("ITEMS", "ITEMS", "");
        items.setColumns(new ArrayList<>(List.of(
                column("Product", "name"),
                column("Brand", "brand"),
                column("Qty", "quantity"),
                column("Unit price", "unitPrice"),
                column("Line total", "lineTotal"))));
        sections.add(items);

        Section summary = section("ORDER SUMMARY", "TEXT",
                "Subtotal: {{subtotal}}\nDiscount: {{discount}}\nDelivery charge: {{deliveryCharge}}\nCoupon: {{couponCode}}\nCoins used: {{coinsRedeemed}} ({{coinDiscount}})\nCoins earned after delivery: {{coinsEarned}}\nTOTAL PAID / PAYABLE: {{total}}");
        sections.add(summary);
        sections.add(section("DELIVERY ADDRESS", "TEXT",
                "{{recipientName}}\n{{addressLine1}}\n{{addressLine2}}\n{{city}}, {{state}} {{postalCode}}\nPhone: {{addressPhone}}"));
        return sections;
    }

    private static Section section(String title, String type, String text) {
        Section section = new Section();
        section.setId(java.util.UUID.randomUUID().toString());
        section.setTitle(title);
        section.setType(type);
        section.setText(text);
        return section;
    }

    private static Column column(String title, String field) {
        Column column = new Column();
        column.setTitle(title);
        column.setField(field);
        return column;
    }

    public static class Section {
        private String id;
        private String type;
        private String title;
        private boolean enabled = true;
        private String text = "";
        private List<Column> columns = new ArrayList<>();
        private List<TableRow> rows = new ArrayList<>();

        public String getId() { return id; }
        public String getType() { return type; }
        public String getTitle() { return title; }
        public boolean isEnabled() { return enabled; }
        public String getText() { return text; }
        public List<Column> getColumns() { return columns; }
        public List<TableRow> getRows() { return rows; }

        public void setId(String id) { this.id = id; }
        public void setType(String type) { this.type = type; }
        public void setTitle(String title) { this.title = title; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setText(String text) { this.text = text; }
        public void setColumns(List<Column> columns) { this.columns = columns; }
        public void setRows(List<TableRow> rows) { this.rows = rows; }
    }

    public static class Column {
        private String title;
        private String field;

        public String getTitle() { return title; }
        public String getField() { return field; }
        public void setTitle(String title) { this.title = title; }
        public void setField(String field) { this.field = field; }
    }

    public static class TableRow {
        private List<String> cells = new ArrayList<>();

        public List<String> getCells() { return cells; }
        public void setCells(List<String> cells) { this.cells = cells; }
    }

    public String getId() { return id; }
    public String getBusinessName() { return businessName; }
    public String getInvoiceTitle() { return invoiceTitle; }
    public String getSubtitle() { return subtitle; }
    public String getFooter() { return footer; }
    public List<Section> getSections() { return sections; }

    public void setId(String id) { this.id = id; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public void setInvoiceTitle(String invoiceTitle) { this.invoiceTitle = invoiceTitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public void setFooter(String footer) { this.footer = footer; }
    public void setSections(List<Section> sections) { this.sections = sections; }
}
