package com.rajharita.services;

import com.rajharita.documents.*;
import com.rajharita.repositories.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final String ONLINE_PAYMENT_UNAVAILABLE =
            "Online payment service is unavailable right now. Available soon. Please choose Cash on Delivery.";
    private static final BigDecimal COIN_EARN_RATE = new BigDecimal("0.01");
    private static final BigDecimal MAX_COIN_REDEMPTION_RATE = new BigDecimal("0.50");

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CustomerCartRepository cartRepository;
    private final StoreSettingsService settingsService;
    private final CouponService couponService;
    private final RazorpayPaymentService paymentService;
    private final CartService cartService;
    private final MongoTemplate mongoTemplate;
    private final NotificationEmailService emailService;
    private final InvoiceTemplateService invoiceTemplateService;
    private final boolean onlinePaymentsEnabled;

    public OrderService(OrderRepository orderRepository,
                        UserRepository userRepository,
                        ProductRepository productRepository,
                        CustomerCartRepository cartRepository,
                        StoreSettingsService settingsService,
                        CouponService couponService,
                        RazorpayPaymentService paymentService,
                        CartService cartService,
                        MongoTemplate mongoTemplate,
                        NotificationEmailService emailService,
                        InvoiceTemplateService invoiceTemplateService,
                        @Value("${app.payments.online-enabled:false}") boolean onlinePaymentsEnabled) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.settingsService = settingsService;
        this.couponService = couponService;
        this.paymentService = paymentService;
        this.cartService = cartService;
        this.mongoTemplate = mongoTemplate;
        this.emailService = emailService;
        this.invoiceTemplateService = invoiceTemplateService;
        this.onlinePaymentsEnabled = onlinePaymentsEnabled;
    }

    public OrderQuote quote(String username, String couponCode) {
        return quote(username, couponCode, 0);
    }

    public OrderQuote quote(String username, String couponCode, long coinsToUse) {
        CartTotals totals = cartTotals(username);
        BigDecimal discount = couponService.discount(couponCode, totals.subtotal(), username);
        return quote(totals.subtotal(), discount, settingsService.get(), totals.user(),
                totals.totalWeightKg(), coinsToUse);
    }

    public CheckoutResult checkout(String username, String addressId, String paymentMethod,
                                   String couponCode, long coinsToUse) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        Address selectedAddress = user.getAddresses() == null ? null : user.getAddresses().stream()
                .filter(address -> Objects.equals(address.getId(), addressId))
                .findFirst().orElse(null);
        if (selectedAddress == null) throw new IllegalArgumentException("Select a saved delivery address");

        CartTotals totals = cartTotals(username);
        StoreSettings settings = settingsService.get();
        String method = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase(Locale.ROOT);
        if (!"COD".equals(method) && !"RAZORPAY".equals(method)) {
            throw new IllegalArgumentException("Choose cash on delivery or UPI/card payment");
        }
        if ("RAZORPAY".equals(method) && !onlinePaymentsEnabled) {
            throw new IllegalArgumentException(ONLINE_PAYMENT_UNAVAILABLE);
        }
        if ("COD".equals(method) && !settings.isCashOnDeliveryEnabled()) {
            throw new IllegalArgumentException("Cash on delivery is not available");
        }

        Coupon coupon = couponService.reserve(couponCode, totals.subtotal(), username);
        BigDecimal discount = coupon == null ? BigDecimal.ZERO
                : couponService.calculateDiscount(coupon, totals.subtotal());
        OrderQuote quote = quote(totals.subtotal(), discount, settings, user,
                totals.totalWeightKg(), coinsToUse);
        List<OrderLine> lines = totals.items();
        List<OrderLine> reserved = new ArrayList<>();
        String gatewayOrderId = null;
        String orderId = UUID.randomUUID().toString();
        boolean orderSaved = false;
        boolean coinsDebited = false;
        try {
            reserveInventory(lines, reserved);
            String orderNumber = "RN-" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase(Locale.ROOT);
            if ("RAZORPAY".equals(method)) {
                gatewayOrderId = paymentService.createOrder(quote.total(), orderNumber);
            }
            Order order = new Order();
            order.setId(orderId);
            order.setOrderNumber(orderNumber);
            order.setUserId(user.getId());
            order.setUsername(user.getUsername());
            order.setCustomerEmail(user.getEmail());
            order.setCustomerPhone(selectedAddress.getPhone());
            order.setDeliveryAddress(copyAddress(selectedAddress));
            order.setItems(lines);
            order.setSubtotal(quote.subtotal());
            order.setDiscount(quote.discount());
            order.setDeliveryCharge(quote.deliveryCharge());
            order.setTotal(quote.total());
            order.setCoinsRedeemed(quote.coinsRedeemed());
            order.setCoinDiscount(quote.coinDiscount());
            order.setCoinsEarned(quote.coinsToEarn());
            order.setCouponCode(coupon == null ? null : coupon.getCode());
            order.setStatus("PLACED");
            order.setPaymentMethod(method);
            order.setPaymentStatus("PENDING");
            order.setRazorpayOrderId(gatewayOrderId);
            order.setCreatedAt(LocalDateTime.now());
            if ("RAZORPAY".equals(method)) order.setPaymentExpiresAt(LocalDateTime.now().plusMinutes(30));
            order.setUpdatedAt(LocalDateTime.now());
            spendLoyaltyCoins(user.getId(), orderId, quote.coinsRedeemed());
            coinsDebited = quote.coinsRedeemed() > 0;
            Order saved = orderRepository.save(order);
            orderSaved = true;
            cartRepository.findById(user.getId()).ifPresent(cart -> {
                cart.setItems(new ArrayList<>());
                cart.setUpdatedAt(LocalDateTime.now());
                cartRepository.save(cart);
            });
            emailService.sendOrderConfirmation(saved);
            return new CheckoutResult(saved, "RAZORPAY".equals(method) ? paymentService.getKeyId() : null);
        } catch (RuntimeException e) {
            if (!orderSaved) {
                restoreInventory(reserved);
                couponService.release(coupon);
                if (coinsDebited) refundSpentCoins(user.getId(), orderId, quote.coinsRedeemed());
            }
            throw e;
        }
    }

    public List<Order> userOrders(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    public List<Order> ordersForUser(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found");
        }
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Order userOrder(String username, String id) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        return orderRepository.findById(id).filter(order -> order.getUserId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
    }

    public Order verifyPayment(String username, String id, String gatewayOrderId,
                               String paymentId, String signature) {
        Order order = userOrder(username, id);
        requireOnlinePaymentsEnabled();
        if ("PAID".equals(order.getPaymentStatus())
                && Objects.equals(order.getRazorpayPaymentId(), paymentId)) return order;
        if (!"RAZORPAY".equals(order.getPaymentMethod()) || !"PENDING".equals(order.getPaymentStatus())
                || !"PLACED".equals(order.getStatus())
                || !Objects.equals(order.getRazorpayOrderId(), gatewayOrderId)
                || !paymentService.verifySignature(gatewayOrderId, paymentId, signature)) {
            throw new IllegalArgumentException("Payment verification failed");
        }
        order.setRazorpayPaymentId(paymentId);
        order.setPaymentStatus("PAID");
        order.setStatus("CONFIRMED");
        order.setPaymentExpiresAt(null);
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    public CheckoutResult retryPayment(String username, String id) {
        Order order = userOrder(username, id);
        requireOnlinePaymentsEnabled();
        if (!"RAZORPAY".equals(order.getPaymentMethod()) || !"PENDING".equals(order.getPaymentStatus())
                || !"PLACED".equals(order.getStatus())) {
            throw new IllegalArgumentException("This order is not awaiting online payment");
        }
        String gatewayOrderId = paymentService.createOrder(order.getTotal(), order.getOrderNumber());
        order.setRazorpayOrderId(gatewayOrderId);
        order.setPaymentExpiresAt(LocalDateTime.now().plusMinutes(30));
        order.setUpdatedAt(LocalDateTime.now());
        return new CheckoutResult(orderRepository.save(order), paymentService.getKeyId());
    }

    private void requireOnlinePaymentsEnabled() {
        if (!onlinePaymentsEnabled) throw new IllegalArgumentException(ONLINE_PAYMENT_UNAVAILABLE);
    }

    public Order cancelByCustomer(String username, String id) {
        Order order = userOrder(username, id);
        if (!List.of("PLACED", "CONFIRMED").contains(order.getStatus())) {
            throw new IllegalArgumentException("This order can no longer be cancelled");
        }
        return cancel(order);
    }

    public Order requestReturn(String username, String id) {
        Order order = userOrder(username, id);
        StoreSettings settings = settingsService.get();
        LocalDateTime deliveredAt = order.getDeliveredAt() == null ? order.getUpdatedAt() : order.getDeliveredAt();
        if (!"DELIVERED".equals(order.getStatus())
                || deliveredAt.plusDays(settings.getReturnWindowDays()).isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("This order is outside the return window");
        }
        order.setStatus("RETURN_REQUESTED");
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    public CartService.CartView reorder(String username, String id) {
        Order order = userOrder(username, id);
        CartService.CartView cart = cartService.getCart(username);
        for (OrderLine line : order.getItems()) {
            cart = cartService.addItem(username, line.getProductId(), line.getQuantity());
        }
        return cart;
    }

    public List<Order> allOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public Order setStatus(String id, String status, String trackingNumber) {
        Order order = getAdminOrder(id);
        String next = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (next.equals(order.getStatus()) && "DELIVERED".equals(next) && !order.isCoinsAwarded()) {
            awardLoyaltyCoins(order);
            return getAdminOrder(id);
        }
        if (!validTransition(order.getStatus(), next)) {
            throw new IllegalArgumentException("Order cannot move from " + order.getStatus() + " to " + next);
        }
        if ("SHIPPED".equals(next) && (trackingNumber == null || trackingNumber.isBlank())) {
            throw new IllegalArgumentException("A tracking number is required before shipping");
        }
        if ("CONFIRMED".equals(next) && "RAZORPAY".equals(order.getPaymentMethod())
                && !"PAID".equals(order.getPaymentStatus())) {
            throw new IllegalArgumentException("Online payment must be verified before confirming the order");
        }
        LocalDateTime now = LocalDateTime.now();
        Update update = new Update().set("status", next).set("updatedAt", now);
        if (trackingNumber != null && !trackingNumber.isBlank()) update.set("trackingNumber", trackingNumber.trim());
        if ("DELIVERED".equals(next)) {
            update.set("deliveredAt", now);
            if ("COD".equals(order.getPaymentMethod())) update.set("paymentStatus", "PAID");
        }
        if (("CANCELLED".equals(next) || "RETURNED".equals(next))
                && "PAID".equals(order.getPaymentStatus())) {
            update.set("paymentStatus", "REFUND_PENDING");
        }
        Order updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(id).and("status").is(order.getStatus())),
                update, FindAndModifyOptions.options().returnNew(true), Order.class);
        if (updated == null) throw new IllegalArgumentException("Order changed; refresh and try again");
        if ("CANCELLED".equals(next) || "RETURNED".equals(next)) {
            restoreInventory(updated.getItems());
            reverseLoyaltyCoins(updated);
        }
        if ("DELIVERED".equals(next)) {
            updated = awardLoyaltyCoins(updated);
            emailService.sendDeliveryThankYou(updated);
        }
        return updated;
    }

    public Order refundOnlinePayment(String id) {
        Order order = getAdminOrder(id);
        if (!"RAZORPAY".equals(order.getPaymentMethod()) || !"REFUND_PENDING".equals(order.getPaymentStatus())
                || order.getRazorpayPaymentId() == null) {
            throw new IllegalArgumentException("This order is not eligible for a Razorpay refund");
        }
        String refundReference = paymentService.refund(order.getRazorpayPaymentId(), order.getTotal());
        order.setRefundReference(refundReference);
        order.setPaymentStatus("REFUNDED");
        order.setUpdatedAt(LocalDateTime.now());
        Order refunded = orderRepository.save(order);
        reverseLoyaltyCoins(refunded);
        return refunded;
    }

    public Order confirmManualRefund(String id) {
        Order order = getAdminOrder(id);
        if (!"COD".equals(order.getPaymentMethod()) || !"REFUND_PENDING".equals(order.getPaymentStatus())) {
            throw new IllegalArgumentException("Only pending cash-on-delivery refunds can be confirmed manually");
        }
        order.setPaymentStatus("REFUNDED");
        order.setUpdatedAt(LocalDateTime.now());
        Order refunded = orderRepository.save(order);
        reverseLoyaltyCoins(refunded);
        return refunded;
    }

    public Order cancelAsAdmin(String id) {
        Order order = getAdminOrder(id);
        if (!List.of("PLACED", "CONFIRMED").contains(order.getStatus())) {
            throw new IllegalArgumentException("This order can no longer be cancelled");
        }
        return cancel(order);
    }

    public String invoiceHtml(String username, String id) {
        Order order = userOrder(username, id);
        return invoiceTemplateService.render(order);
    }

    public Map<String, Object> salesReport() {
        return salesReport(null, null);
    }

    public Map<String, Object> salesReport(LocalDate startDate, LocalDate endDate) {
        if ((startDate == null) != (endDate == null)) {
            throw new IllegalArgumentException("Both start date and end date are required.");
        }
        if (startDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be on or before end date.");
        }

        List<Order> orders = allOrders();
        BigDecimal revenue = orders.stream().filter(order -> "PAID".equals(order.getPaymentStatus()))
                .map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Long> statusCounts = orders.stream().collect(Collectors.groupingBy(
                Order::getStatus, Collectors.counting()));
        Map<String, Long> popularProducts = orders.stream().flatMap(order -> order.getItems().stream())
                .collect(Collectors.groupingBy(OrderLine::getName,
                        Collectors.summingLong(OrderLine::getQuantity)));
        Map<String, Object> report = new HashMap<>();
        report.put("orderCount", orders.size());
        report.put("revenue", revenue);
        report.put("statuses", statusCounts);
        report.put("popularProducts", popularProducts);
        if (startDate != null) {
            BigDecimal periodRevenue = orders.stream()
                    .filter(order -> "PAID".equals(order.getPaymentStatus()))
                    .filter(order -> order.getCreatedAt() != null)
                    .filter(order -> {
                        LocalDate orderDate = order.getCreatedAt().toLocalDate();
                        return !orderDate.isBefore(startDate) && !orderDate.isAfter(endDate);
                    })
                    .map(Order::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            report.put("periodRevenue", periodRevenue);
            report.put("startDate", startDate);
            report.put("endDate", endDate);
        }
        return report;
    }

    private Order cancel(Order order) {
        Update update = new Update().set("status", "CANCELLED").set("updatedAt", LocalDateTime.now());
        if ("PAID".equals(order.getPaymentStatus())) update.set("paymentStatus", "REFUND_PENDING");
        Order cancelled = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(order.getId()).and("status").is(order.getStatus())),
                update, FindAndModifyOptions.options().returnNew(true), Order.class);
        if (cancelled == null) throw new IllegalArgumentException("Order changed; refresh and try again");
        restoreInventory(cancelled.getItems());
        reverseLoyaltyCoins(cancelled);
        return cancelled;
    }

    @Scheduled(fixedDelayString = "${app.orders.payment-timeout-scan-ms:60000}")
    public void cancelExpiredPayments() {
        List<Order> expired = orderRepository.findByPaymentStatusAndStatusAndPaymentExpiresAtBefore(
                "PENDING", "PLACED", LocalDateTime.now());
        for (Order order : expired) {
            Update update = new Update().set("status", "CANCELLED")
                    .set("updatedAt", LocalDateTime.now());
            Order cancelled = mongoTemplate.findAndModify(
                    Query.query(Criteria.where("_id").is(order.getId())
                            .and("status").is("PLACED")
                            .and("paymentStatus").is("PENDING")
                            .and("paymentExpiresAt").lte(LocalDateTime.now())),
                    update, FindAndModifyOptions.options().returnNew(true), Order.class);
            if (cancelled != null) {
                restoreInventory(cancelled.getItems());
                reverseLoyaltyCoins(cancelled);
            }
        }
    }

    private boolean validTransition(String current, String next) {
        return switch (current) {
            case "PLACED" -> List.of("CONFIRMED", "CANCELLED").contains(next);
            case "CONFIRMED" -> List.of("PACKED", "CANCELLED").contains(next);
            case "PACKED" -> "SHIPPED".equals(next);
            case "SHIPPED" -> "DELIVERED".equals(next);
            case "DELIVERED" -> "RETURN_REQUESTED".equals(next);
            case "RETURN_REQUESTED" -> List.of("RETURNED", "DELIVERED").contains(next);
            default -> false;
        };
    }

    private CartTotals cartTotals(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        CustomerCart cart = cartRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Your cart is empty"));
        if (cart.getItems().isEmpty()) throw new IllegalArgumentException("Your cart is empty");
        List<OrderLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalWeightKg = BigDecimal.ZERO;
        for (CartLine line : cart.getItems()) {
            Product product = productRepository.findById(line.getProductId())
                    .filter(item -> item.isPublished() && !item.isDeleted())
                    .orElseThrow(() -> new IllegalArgumentException("A cart item is no longer available"));
            if (line.getQuantity() < 1 || product.getStock() < line.getQuantity()) {
                throw new IllegalArgumentException("Not enough stock is available for " + product.getName());
            }
            OrderLine snapshot = new OrderLine(product.getId(), product.getName(), product.getBrand(),
                    product.getPrice(), line.getQuantity());
            lines.add(snapshot);
            subtotal = subtotal.add(snapshot.getLineTotal());
            totalWeightKg = totalWeightKg.add(product.getWeightKg()
                    .multiply(BigDecimal.valueOf(line.getQuantity())));
        }
        return new CartTotals(user, lines, subtotal.setScale(2, RoundingMode.HALF_UP), totalWeightKg);
    }

    static OrderQuote quote(BigDecimal subtotal, BigDecimal discount, StoreSettings settings,
                            User user, long coinsToUse) {
        return quote(subtotal, discount, settings, user, BigDecimal.ZERO, coinsToUse);
    }

    static OrderQuote quote(BigDecimal subtotal, BigDecimal discount, StoreSettings settings,
                            User user, BigDecimal totalWeightKg, long coinsToUse) {
        BigDecimal afterDiscount = subtotal.subtract(discount);
        BigDecimal delivery = BigDecimal.ZERO;
        if (afterDiscount.compareTo(settings.getFreeDeliveryThreshold()) < 0) {
            BigDecimal startedKilograms = totalWeightKg.signum() <= 0
                    ? BigDecimal.ZERO : totalWeightKg.setScale(0, RoundingMode.CEILING);
            delivery = settings.getDeliveryCharge().add(
                    settings.getDeliveryChargePerKg().multiply(startedKilograms))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal beforeCoins = afterDiscount.add(delivery);
        long coinBalance = Math.max(0, user.getLoyaltyCoins());
        long maximumCoins = beforeCoins.multiply(MAX_COIN_REDEMPTION_RATE)
                .setScale(0, RoundingMode.DOWN).longValueExact();
        long maximumRedeemable = Math.min(coinBalance, maximumCoins);
        if (coinsToUse < 0 || coinsToUse > maximumRedeemable) {
            throw new IllegalArgumentException("You can use up to " + maximumRedeemable + " coins on this order.");
        }
        long coinsRedeemed = coinsToUse;
        BigDecimal coinDiscount = BigDecimal.valueOf(coinsRedeemed).setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal payable = beforeCoins.subtract(coinDiscount).setScale(2, RoundingMode.HALF_UP);
        long coinsToEarn = payable.multiply(COIN_EARN_RATE)
                .setScale(0, RoundingMode.DOWN).longValueExact();
        return new OrderQuote(subtotal, discount, delivery, payable, coinBalance,
                maximumCoins, coinsRedeemed, coinDiscount, coinsToEarn);
    }

    private void spendLoyaltyCoins(String userId, String orderId, long coins) {
        if (coins <= 0) return;
        String spendKey = "SPEND:" + orderId;
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(userId),
                Criteria.where("loyaltyCoins").gte(coins),
                Criteria.where("loyaltyOperations").ne(spendKey)));
        User updated = mongoTemplate.findAndModify(query,
                new Update().inc("loyaltyCoins", -coins).addToSet("loyaltyOperations", spendKey),
                FindAndModifyOptions.options().returnNew(true), User.class);
        if (updated != null) return;
        User current = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (current.getLoyaltyOperations() != null && current.getLoyaltyOperations().contains(spendKey)) return;
        throw new IllegalArgumentException("Your coin balance changed. Refresh checkout and try again.");
    }

    private void refundSpentCoins(String userId, String orderId, long coins) {
        if (coins <= 0) return;
        String spendKey = "SPEND:" + orderId;
        String refundKey = "SPEND_REFUND:" + orderId;
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(userId),
                Criteria.where("loyaltyOperations").is(spendKey),
                Criteria.where("loyaltyOperations").ne(refundKey)));
        mongoTemplate.findAndModify(query,
                new Update().inc("loyaltyCoins", coins).addToSet("loyaltyOperations", refundKey),
                FindAndModifyOptions.options().returnNew(true), User.class);
    }

    private Order awardLoyaltyCoins(Order order) {
        if (!order.isCoinsAwarded()) {
            creditLoyaltyCoins(order.getUserId(), "EARN:" + order.getId(), order.getCoinsEarned());
            order.setCoinsAwarded(true);
            return orderRepository.save(order);
        }
        return order;
    }

    private void creditLoyaltyCoins(String userId, String operationKey, long coins) {
        if (coins <= 0) return;
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(userId),
                Criteria.where("loyaltyOperations").ne(operationKey)));
        User updated = mongoTemplate.findAndModify(query,
                new Update().inc("loyaltyCoins", coins).addToSet("loyaltyOperations", operationKey),
                FindAndModifyOptions.options().returnNew(true), User.class);
        if (updated == null && !userRepository.existsById(userId)) {
            throw new IllegalStateException("Unable to update loyalty coin balance for missing account");
        }
    }

    private void reverseLoyaltyCoins(Order order) {
        refundSpentCoins(order.getUserId(), order.getId(), order.getCoinsRedeemed());
        long earned = order.getCoinsEarned();
        if (earned <= 0) return;
        String earnKey = "EARN:" + order.getId();
        String reversalKey = "EARN_REVERSAL:" + order.getId();
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(order.getUserId()),
                Criteria.where("loyaltyOperations").is(earnKey),
                Criteria.where("loyaltyOperations").ne(reversalKey)));
        mongoTemplate.findAndModify(query,
                new Update().inc("loyaltyCoins", -earned).addToSet("loyaltyOperations", reversalKey),
                FindAndModifyOptions.options().returnNew(true), User.class);
    }

    private void reserveInventory(List<OrderLine> lines, List<OrderLine> reserved) {
        for (OrderLine line : lines) {
            Query query = Query.query(new Criteria().andOperator(
                    Criteria.where("_id").is(line.getProductId()),
                    Criteria.where("stock").gte(line.getQuantity()),
                    Criteria.where("published").is(true),
                    Criteria.where("deleted").is(false)));
            Product updated = mongoTemplate.findAndModify(query, new Update().inc("stock", -line.getQuantity()),
                    FindAndModifyOptions.options().returnNew(true), Product.class);
            if (updated == null) throw new IllegalArgumentException("Stock changed; review your cart and try again");
            reserved.add(line);
        }
    }

    private void restoreInventory(List<OrderLine> lines) {
        for (OrderLine line : lines) {
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(line.getProductId())),
                    new Update().inc("stock", line.getQuantity()), Product.class);
        }
    }

    private Address copyAddress(Address source) {
        Address copy = new Address();
        copy.setId(source.getId());
        copy.setRecipientName(source.getRecipientName());
        copy.setPhone(source.getPhone());
        copy.setLine1(source.getLine1());
        copy.setLine2(source.getLine2());
        copy.setCity(source.getCity());
        copy.setState(source.getState());
        copy.setPostalCode(source.getPostalCode());
        copy.setDefaultAddress(source.isDefaultAddress());
        return copy;
    }

    private Order getAdminOrder(String id) {
        return orderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Order not found"));
    }

    private record CartTotals(User user, List<OrderLine> items, BigDecimal subtotal,
                              BigDecimal totalWeightKg) { }
    public record OrderQuote(BigDecimal subtotal, BigDecimal discount, BigDecimal deliveryCharge,
                             BigDecimal total, long coinBalance, long maximumCoinsRedeemable,
                             long coinsRedeemed, BigDecimal coinDiscount, long coinsToEarn) { }
    public record CheckoutResult(Order order, String paymentKeyId) { }
}
