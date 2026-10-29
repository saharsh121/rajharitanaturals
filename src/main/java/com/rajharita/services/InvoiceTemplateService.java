package com.rajharita.services;

import com.rajharita.documents.Address;
import com.rajharita.documents.InvoiceTemplateSettings;
import com.rajharita.documents.InvoiceTemplateSettings.Column;
import com.rajharita.documents.InvoiceTemplateSettings.Section;
import com.rajharita.documents.InvoiceTemplateSettings.TableRow;
import com.rajharita.documents.Order;
import com.rajharita.documents.OrderLine;
import com.rajharita.repositories.InvoiceTemplateSettingsRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class InvoiceTemplateService {
    private static final String SETTINGS_ID = "invoice-template";
    private static final Set<String> SECTION_TYPES = Set.of("TEXT", "ITEMS", "CUSTOM_TABLE");
    private static final Set<String> ITEM_FIELDS = Set.of("name", "brand", "quantity", "unitPrice", "lineTotal");
    private static final int MAX_SECTIONS = 20;
    private static final int MAX_COLUMNS = 8;
    private static final int MAX_ROWS = 30;

    private final InvoiceTemplateSettingsRepository repository;

    public InvoiceTemplateService(InvoiceTemplateSettingsRepository repository) {
        this.repository = repository;
    }

    public InvoiceTemplateSettings get() {
        return repository.findById(SETTINGS_ID)
                .orElseGet(() -> repository.save(defaultSettings()));
    }

    public InvoiceTemplateSettings save(InvoiceTemplateSettings submitted) {
        if (submitted == null || blankOrLong(submitted.getBusinessName(), 120)
                || blankOrLong(submitted.getInvoiceTitle(), 120)
                || tooLong(submitted.getSubtitle(), 300) || tooLong(submitted.getFooter(), 1000)) {
            throw new IllegalArgumentException("Enter a business name and invoice title (up to 120 characters each); subtitle and footer can be up to 300 and 1,000 characters.");
        }
        List<Section> sections = submitted.getSections();
        if (sections == null || sections.size() > MAX_SECTIONS) {
            throw new IllegalArgumentException("An invoice can contain up to 20 sections.");
        }
        for (Section section : sections) validate(section);

        submitted.setId(SETTINGS_ID);
        submitted.setBusinessName(submitted.getBusinessName().trim());
        submitted.setInvoiceTitle(submitted.getInvoiceTitle().trim());
        submitted.setSubtitle(clean(submitted.getSubtitle()));
        submitted.setFooter(clean(submitted.getFooter()));
        return repository.save(submitted);
    }

    public String render(Order order) {
        InvoiceTemplateSettings settings = get();
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Invoice</title><style>
                *{box-sizing:border-box}body{margin:0;padding:32px;color:#24311c;font:14px/1.6 Arial,sans-serif;background:#f4f6f1}
                .invoice{max-width:900px;margin:auto;padding:42px;background:#fff;border:1px solid #e1e8dc;border-radius:12px}
                header{display:flex;justify-content:space-between;gap:20px;align-items:flex-start;padding-bottom:22px;border-bottom:2px solid #326338}
                h1{margin:0;color:#244b2e;font:700 28px Georgia,serif}header p{margin:6px 0;color:#61705b}
                .invoice-title{text-align:right;color:#326338;font-size:13px;font-weight:700;letter-spacing:1.6px}
                section{margin-top:24px;break-inside:avoid}h2{margin:0 0 10px;color:#244b2e;font:700 17px Georgia,serif}
                .invoice-copy{padding:14px 16px;border:1px solid #e3eadd;border-radius:8px;background:#fbfcf9;white-space:pre-line}
                table{width:100%;border-collapse:collapse}th,td{padding:10px 12px;border:1px solid #dfe6d9;text-align:left;vertical-align:top}
                th{color:#244b2e;background:#edf3e8}footer{margin-top:30px;padding-top:16px;border-top:1px solid #e1e8dc;color:#61705b;text-align:center;white-space:pre-line}
                @media print{body{padding:0;background:#fff}.invoice{max-width:none;padding:0;border:0;border-radius:0}}
                </style></head><body><main class="invoice"><header><div><h1>""")
                .append(escape(settings.getBusinessName())).append("</h1><p>")
                .append(escape(settings.getSubtitle())).append("</p></div><div class=\"invoice-title\">")
                .append(escape(settings.getInvoiceTitle())).append("</div></header>");

        Map<String, String> values = invoiceValues(order);
        for (Section section : settings.getSections()) {
            if (!section.isEnabled()) continue;
            String title = escape(section.getTitle());
            html.append("<section>");
            if (!title.isBlank()) html.append("<h2>").append(title).append("</h2>");
            switch (section.getType()) {
                case "TEXT" -> html.append("<div class=\"invoice-copy\">")
                        .append(escape(replaceTokens(section.getText(), values))).append("</div>");
                case "ITEMS" -> appendItemsTable(html, section, order);
                case "CUSTOM_TABLE" -> appendCustomTable(html, section, values);
                default -> throw new IllegalStateException("Unsupported invoice section type.");
            }
            html.append("</section>");
        }
        html.append("<footer>").append(escape(settings.getFooter()))
                .append("</footer></main></body></html>");
        return html.toString();
    }

    private void validate(Section section) {
        if (section == null || section.getId() == null || section.getId().length() > 80
                || section.getTitle() == null || section.getTitle().length() > 120
                || !SECTION_TYPES.contains(section.getType())) {
            throw new IllegalArgumentException("Each invoice section needs a valid type and a title up to 120 characters.");
        }
        section.setTitle(section.getTitle().trim());
        section.setText(clean(section.getText()));
        if ("TEXT".equals(section.getType()) && section.getText().length() > 5000) {
            throw new IllegalArgumentException("Invoice text sections can contain up to 5,000 characters.");
        }
        List<Column> columns = section.getColumns();
        if ("ITEMS".equals(section.getType())) {
            validateColumns(columns, true);
        } else if ("CUSTOM_TABLE".equals(section.getType())) {
            validateColumns(columns, false);
            List<TableRow> rows = section.getRows();
            if (rows == null || rows.size() > MAX_ROWS) {
                throw new IllegalArgumentException("Custom invoice tables can contain up to 30 rows.");
            }
            for (TableRow row : rows) {
                if (row == null || row.getCells() == null || row.getCells().size() != columns.size()
                        || row.getCells().stream().anyMatch(cell -> cell == null || cell.length() > 1000)) {
                    throw new IllegalArgumentException("Every custom table row must have a value for each column (up to 1,000 characters per cell).");
                }
            }
        }
    }

    private void validateColumns(List<Column> columns, boolean itemColumns) {
        if (columns == null || columns.isEmpty() || columns.size() > MAX_COLUMNS) {
            throw new IllegalArgumentException("Each invoice table needs between 1 and 8 columns.");
        }
        for (Column column : columns) {
            if (column == null || blankOrLong(column.getTitle(), 80)
                    || column.getField() == null || column.getField().length() > 40
                    || itemColumns && !ITEM_FIELDS.contains(column.getField())) {
                throw new IllegalArgumentException("Invoice table columns need a heading and a valid field.");
            }
            column.setTitle(column.getTitle().trim());
            column.setField(column.getField().trim());
        }
    }

    private void appendItemsTable(StringBuilder html, Section section, Order order) {
        html.append("<table><thead><tr>");
        for (Column column : section.getColumns()) html.append("<th>").append(escape(column.getTitle())).append("</th>");
        html.append("</tr></thead><tbody>");
        if (order.getItems() != null) {
            for (OrderLine item : order.getItems()) {
                html.append("<tr>");
                for (Column column : section.getColumns()) {
                    html.append("<td>").append(escape(itemField(item, column.getField()))).append("</td>");
                }
                html.append("</tr>");
            }
        }
        html.append("</tbody></table>");
    }

    private void appendCustomTable(StringBuilder html, Section section, Map<String, String> values) {
        html.append("<table><thead><tr>");
        for (Column column : section.getColumns()) html.append("<th>").append(escape(column.getTitle())).append("</th>");
        html.append("</tr></thead><tbody>");
        for (TableRow row : section.getRows()) {
            html.append("<tr>");
            for (String cell : row.getCells()) {
                html.append("<td>").append(escape(replaceTokens(cell, values))).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");
    }

    private Map<String, String> invoiceValues(Order order) {
        Address address = order.getDeliveryAddress();
        return Map.ofEntries(
                Map.entry("orderNumber", value(order.getOrderNumber())),
                Map.entry("date", order.getCreatedAt() == null ? "" : order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))),
                Map.entry("status", value(order.getStatus())),
                Map.entry("paymentMethod", value(order.getPaymentMethod())),
                Map.entry("paymentStatus", value(order.getPaymentStatus())),
                Map.entry("customerName", address == null || address.getRecipientName() == null
                        || address.getRecipientName().isBlank() ? value(order.getUsername()) : address.getRecipientName()),
                Map.entry("customerEmail", value(order.getCustomerEmail())),
                Map.entry("customerPhone", value(order.getCustomerPhone())),
                Map.entry("trackingNumber", value(order.getTrackingNumber())),
                Map.entry("subtotal", money(order.getSubtotal())),
                Map.entry("discount", money(order.getDiscount())),
                Map.entry("deliveryCharge", money(order.getDeliveryCharge())),
                Map.entry("couponCode", value(order.getCouponCode())),
                Map.entry("coinsRedeemed", Long.toString(order.getCoinsRedeemed())),
                Map.entry("coinDiscount", money(order.getCoinDiscount())),
                Map.entry("coinsEarned", Long.toString(order.getCoinsEarned())),
                Map.entry("total", money(order.getTotal())),
                Map.entry("recipientName", address == null ? "" : value(address.getRecipientName())),
                Map.entry("addressLine1", address == null ? "" : value(address.getLine1())),
                Map.entry("addressLine2", address == null ? "" : value(address.getLine2())),
                Map.entry("city", address == null ? "" : value(address.getCity())),
                Map.entry("state", address == null ? "" : value(address.getState())),
                Map.entry("postalCode", address == null ? "" : value(address.getPostalCode())),
                Map.entry("addressPhone", address == null ? "" : value(address.getPhone()))
        );
    }

    private String itemField(OrderLine item, String field) {
        return switch (field) {
            case "name" -> value(item.getName());
            case "brand" -> value(item.getBrand());
            case "quantity" -> Integer.toString(item.getQuantity());
            case "unitPrice" -> money(item.getUnitPrice());
            case "lineTotal" -> money(item.getLineTotal());
            default -> "";
        };
    }

    private String replaceTokens(String text, Map<String, String> values) {
        String result = clean(text);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private InvoiceTemplateSettings defaultSettings() {
        InvoiceTemplateSettings settings = new InvoiceTemplateSettings();
        settings.setId(SETTINGS_ID);
        settings.setSections(InvoiceTemplateSettings.defaultSections());
        return settings;
    }

    private String money(BigDecimal amount) {
        return amount == null ? "₹0.00" : "₹" + amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String value(String text) {
        return text == null ? "" : text;
    }

    private String clean(String text) {
        return text == null ? "" : text;
    }

    private boolean blankOrLong(String text, int maxLength) {
        return text == null || text.isBlank() || text.length() > maxLength;
    }

    private boolean tooLong(String text, int maxLength) {
        return text != null && text.length() > maxLength;
    }

    private String escape(String text) {
        return value(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
