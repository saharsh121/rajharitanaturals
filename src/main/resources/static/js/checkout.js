const messageBox = document.getElementById("message-box");
let csrfToken = "";
let selectedCoupon = "";
let coinsToUse = 0;
let quote = null;
let onlinePaymentsEnabled = false;
let cashOnDeliveryEnabled = true;
let quoteRequestsPending = 0;
const onlinePaymentUnavailableMessage =
    "Online payment service is unavailable right now. Available soon. Please choose Cash on Delivery.";

function refreshPlaceOrderButton() {
    const cod = document.querySelector('input[name="payment"][value="COD"]');
    document.getElementById("place-order").disabled =
        quoteRequestsPending > 0 || (cod.disabled && !onlinePaymentsEnabled);
}

function showMessage(text, type = "error") {
    messageBox.textContent = text;
    messageBox.className = type;
}

async function request(url, method = "GET", body) {
    if (method !== "GET" && !csrfToken) {
        const csrf = await fetch("/api/auth/csrf");
        csrfToken = (await csrf.json()).token;
    }
    const response = await fetch(url, {
        method,
        headers: method === "GET" ? {} : {
            "Content-Type": "application/json", "X-XSRF-TOKEN": csrfToken
        },
        ...(body ? { body: JSON.stringify(body) } : {})
    });
    const result = await response.json().catch(() => ({}));
    if (response.status === 401 || response.status === 403) {
        window.location.href = "/login";
        throw new Error("Please sign in to continue.");
    }
    if (!response.ok) throw new Error(result.error || "Checkout request failed.");
    return result;
}

async function loadCheckout() {
    const addresses = await request("/api/auth/addresses");
    const addressElement = document.getElementById("addresses");
    if (!addresses.length) {
        addressElement.innerHTML = '<p class="empty">Add a delivery address in <a href="/account">My account</a>.</p>';
    } else {
        addressElement.innerHTML = addresses.map((address, index) => `
            <label class="address-choice"><input type="radio" name="address" value="${escapeHtml(address.id)}" ${address.defaultAddress || index === 0 ? "checked" : ""} />
                <span><strong>${escapeHtml(address.recipientName)} ${address.defaultAddress ? "· Default" : ""}</strong><br>
                ${escapeHtml(address.line1)} ${escapeHtml(address.line2)}, ${escapeHtml(address.city)}, ${escapeHtml(address.state)} ${escapeHtml(address.postalCode)}<br>
                ${escapeHtml(address.phone)}</span></label>`).join("");
    }
    const settings = await request("/api/store/settings");
    const cod = document.querySelector('input[name="payment"][value="COD"]');
    const online = document.querySelector('input[name="payment"][value="RAZORPAY"]');
    cashOnDeliveryEnabled = settings.cashOnDeliveryEnabled;
    onlinePaymentsEnabled = settings.onlinePaymentsEnabled === true;
    cod.disabled = !cashOnDeliveryEnabled;
    document.getElementById("online-payment-notice").hidden = onlinePaymentsEnabled;
    if (!onlinePaymentsEnabled) {
        cod.checked = cashOnDeliveryEnabled;
        online.checked = false;
    } else if (cod.disabled) {
        online.checked = true;
    }
    refreshPlaceOrderButton();
    if (cod.disabled && !onlinePaymentsEnabled) {
        showMessage("Cash on delivery is currently unavailable.", "error");
    }
    await updateQuote();
}

function escapeHtml(value) {
    return String(value || "").replace(/[&<>"']/g, char => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[char]);
}

async function updateQuote() {
    quoteRequestsPending++;
    refreshPlaceOrderButton();
    try {
        const input = document.getElementById("coins-to-use");
        const requestedCoins = Number(input.value);
        if (!Number.isSafeInteger(requestedCoins) || requestedCoins < 0) {
            throw new Error("Enter a whole, non-negative number of coins.");
        }
        quote = await request("/api/orders/quote", "POST", { couponCode: selectedCoupon, coinsToUse: 0 });
        const maximumRedeemable = Math.min(Number(quote.coinBalance), Number(quote.maximumCoinsRedeemable));
        input.max = String(maximumRedeemable);
        input.disabled = maximumRedeemable <= 0;
        coinsToUse = Math.min(requestedCoins, maximumRedeemable);
        input.value = String(coinsToUse);
        if (coinsToUse > 0) {
            quote = await request("/api/orders/quote", "POST", { couponCode: selectedCoupon, coinsToUse });
        }
        document.getElementById("coin-balance").textContent = String(quote.coinBalance);
        document.getElementById("coin-limit").textContent =
            `You can use up to ${maximumRedeemable} coins (₹${maximumRedeemable.toFixed(2)}) on this order.`;
        document.getElementById("subtotal").textContent = `₹${Number(quote.subtotal).toFixed(2)}`;
        document.getElementById("discount").textContent = `−₹${Number(quote.discount).toFixed(2)}`;
        document.getElementById("delivery").textContent = `₹${Number(quote.deliveryCharge).toFixed(2)}`;
        document.getElementById("coin-discount").textContent = `−₹${Number(quote.coinDiscount).toFixed(2)}`;
        document.getElementById("coin-earn-preview").textContent = String(quote.coinsToEarn);
        document.getElementById("total").textContent = `₹${Number(quote.total).toFixed(2)}`;
    } finally {
        quoteRequestsPending--;
        refreshPlaceOrderButton();
    }
}

document.getElementById("coins-to-use").addEventListener("change", async event => {
    try {
        await updateQuote();
        showMessage(coinsToUse ? `${coinsToUse} coins applied to your order.` : "No coins applied.", "success");
    } catch (error) {
        event.target.value = String(quote?.coinsRedeemed || 0);
        showMessage(error.message);
    }
});

document.getElementById("apply-coupon").addEventListener("click", async () => {
    selectedCoupon = document.getElementById("coupon-code").value.trim();
    try {
        await updateQuote();
        showMessage(selectedCoupon ? "Coupon applied." : "Coupon removed.", "success");
    } catch (error) {
        selectedCoupon = "";
        document.getElementById("coupon-code").value = "";
        showMessage(error.message);
    }
});

document.querySelector('input[name="payment"][value="RAZORPAY"]').addEventListener("change", event => {
    if (onlinePaymentsEnabled || !event.target.checked) return;
    showMessage(onlinePaymentUnavailableMessage, "error");
    event.target.checked = false;
    if (cashOnDeliveryEnabled) {
        document.querySelector('input[name="payment"][value="COD"]').checked = true;
    }
});

document.getElementById("place-order").addEventListener("click", async () => {
    const addressId = document.querySelector('input[name="address"]:checked')?.value;
    const paymentMethod = document.querySelector('input[name="payment"]:checked')?.value;
    if (paymentMethod === "RAZORPAY" && !onlinePaymentsEnabled) {
        showMessage(onlinePaymentUnavailableMessage, "error");
        return;
    }
    if (!addressId) {
        showMessage("Please select a delivery address.");
        return;
    }
    const button = document.getElementById("place-order");
    button.disabled = true;
    try {
        if (paymentMethod === "RAZORPAY" && !window.Razorpay) {
            throw new Error("Razorpay checkout did not load. Please retry or choose cash on delivery.");
        }
        const result = await request("/api/orders", "POST", {
            addressId, paymentMethod, couponCode: selectedCoupon, coinsToUse
        });
        if (paymentMethod === "RAZORPAY") {
            const gateway = new Razorpay({
                key: result.paymentKeyId,
                amount: Math.round(Number(result.order.total) * 100),
                currency: "INR",
                name: "Rajharita Naturals",
                description: `Order ${result.order.orderNumber}`,
                order_id: result.order.razorpayOrderId,
                handler: async response => {
                    try {
                        await request(`/api/orders/${encodeURIComponent(result.order.id)}/payment/verify`,
                            "POST", response);
                        window.location.href = "/orders";
                    } catch (error) {
                        showMessage(error.message);
                        button.disabled = false;
                    }
                },
                modal: { ondismiss: () => { button.disabled = false; } }
            });
            gateway.open();
        } else {
            showMessage(`Order ${result.order.orderNumber} placed successfully.`, "success");
            setTimeout(() => { window.location.href = "/orders"; }, 900);
        }
    } catch (error) {
        showMessage(error.message);
        button.disabled = false;
    }
});

loadCheckout().catch(error => showMessage(error.message));
