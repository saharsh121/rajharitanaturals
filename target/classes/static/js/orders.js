const ordersList = document.getElementById("orders-list");
const messageBox = document.getElementById("message-box");
let csrfToken = "";

function escapeHtml(value) {
    return String(value || "").replace(/[&<>"']/g, char => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[char]);
}

async function api(path, method = "GET", body) {
    if (method !== "GET" && !csrfToken) {
        const tokenResponse = await fetch("/api/auth/csrf");
        csrfToken = (await tokenResponse.json()).token;
    }
    const response = await fetch(path, {
        method,
        headers: method === "GET" ? {} : { "Content-Type": "application/json", "X-XSRF-TOKEN": csrfToken },
        ...(body === undefined ? {} : { body: JSON.stringify(body) })
    });
    const result = await response.json().catch(() => ({}));
    if (response.status === 401 || response.status === 403) {
        window.location.href = "/login";
        throw new Error("Please sign in to view your orders.");
    }
    if (!response.ok) throw new Error(result.error || "Order request failed.");
    return result;
}

async function loadOrders() {
    try {
        const orders = await api("/api/orders");
        if (!orders.length) {
            ordersList.innerHTML = '<p class="empty">You have not placed any orders yet. <a href="/shop">Explore products</a>.</p>';
            return;
        }
        ordersList.innerHTML = orders.map(order => `<article class="order-card" data-order="${escapeHtml(order.id)}">
            <div class="order-heading"><div><h2>${escapeHtml(order.orderNumber)}</h2><p>${new Date(order.createdAt).toLocaleString()}</p></div>
                <span class="status">${escapeHtml(order.status.replaceAll("_", " "))}</span></div>
            <div class="order-lines">${order.items.map(item => `<p>${escapeHtml(item.name)} × ${item.quantity} <strong>₹${Number(item.lineTotal).toFixed(2)}</strong></p>`).join("")}</div>
            <p>Payment: ${escapeHtml(order.paymentMethod)} · ${escapeHtml(order.paymentStatus.replaceAll("_", " "))}</p>
            ${order.trackingNumber ? `<p>Tracking: ${escapeHtml(order.trackingNumber)}</p>` : ""}
            ${Number(order.coinsRedeemed) > 0 ? `<p>Rajharita Coins used: ${Number(order.coinsRedeemed)} (−₹${Number(order.coinDiscount || order.coinsRedeemed).toFixed(2)})</p>` : ""}
            ${Number(order.coinsEarned) > 0 ? `<p>${order.coinsAwarded ? "Rajharita Coins earned" : "Coins to earn after delivery"}: ${Number(order.coinsEarned)}</p>` : ""}
            <p class="price">Total ₹${Number(order.total).toFixed(2)}</p>
            <div class="order-actions">
                ${["PLACED", "CONFIRMED"].includes(order.status) ? '<button data-action="cancel">Cancel order</button>' : ""}
                ${order.paymentMethod === "RAZORPAY" && order.paymentStatus === "PENDING" && order.status === "PLACED" ? '<button data-action="pay">Pay now</button>' : ""}
                ${order.status === "DELIVERED" ? '<button data-action="return">Request return</button>' : ""}
                <button data-action="reorder">Reorder items</button>
                <a class="button-link" href="/api/orders/${encodeURIComponent(order.id)}/invoice">Download invoice</a>
            </div>
        </article>`).join("");
    } catch (error) {
        messageBox.textContent = error.message;
    }
}

ordersList.addEventListener("click", async event => {
    const button = event.target.closest("button[data-action]");
    if (!button || button.disabled) return;
    const card = button.closest("[data-order]");
    const action = button.dataset.action;
    const path = action === "cancel" ? "cancel" : action === "return" ? "return" : "reorder";
    const originalText = button.textContent;
    button.disabled = true;
    button.setAttribute("aria-busy", "true");
    button.textContent = action === "pay" ? "Opening payment…" : "Processing…";
    try {
        if (action === "pay") {
            const payment = await api(`/api/orders/${encodeURIComponent(card.dataset.order)}/payment/retry`, "POST", {});
            const order = payment.order;
            const gateway = new Razorpay({
                key: payment.paymentKeyId,
                amount: Math.round(Number(order.total) * 100),
                currency: "INR",
                name: "Rajharita Naturals",
                description: `Order ${order.orderNumber}`,
                order_id: order.razorpayOrderId,
                handler: async result => {
                    try {
                        await api(`/api/orders/${encodeURIComponent(order.id)}/payment/verify`, "POST", result);
                        messageBox.textContent = "Payment confirmed.";
                        await loadOrders();
                    } catch (error) {
                        messageBox.textContent = error.message;
                    }
                }
            });
            gateway.open();
            return;
        }
        await api(`/api/orders/${encodeURIComponent(card.dataset.order)}/${path}`, "POST", {});
        if (action === "reorder") {
            window.location.href = "/cart";
            return;
        }
        messageBox.textContent = action === "cancel" ? "Order cancelled." : "Return request submitted.";
        await loadOrders();
    } catch (error) {
        messageBox.textContent = error.message;
    } finally {
        if (button.isConnected) {
            button.disabled = false;
            button.removeAttribute("aria-busy");
            button.textContent = originalText;
        }
    }
});
loadOrders();
