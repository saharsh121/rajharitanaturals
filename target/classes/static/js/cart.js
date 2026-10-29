const cartContainer = document.getElementById("cart-items");
const wishlistContainer = document.getElementById("wishlist-items");
const messageBox = document.getElementById("message-box");
let csrfToken = "";

async function ensureCsrf() {
    if (csrfToken) return;
    const response = await fetch("/api/auth/csrf");
    csrfToken = (await response.json()).token;
}

function escapeHtml(value) {
    return String(value || "").replace(/[&<>"']/g, character => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[character]);
}

async function api(url, method = "GET", body) {
    if (method !== "GET") await ensureCsrf();
    const response = await fetch(url, {
        method,
        headers: method === "GET" ? {} : { "Content-Type": "application/json", "X-XSRF-TOKEN": csrfToken },
        ...(body === undefined ? {} : { body: JSON.stringify(body) })
    });
    const data = await response.json().catch(() => ({}));
    if (response.status === 401 || response.status === 403) {
        window.location.href = "/login";
        throw new Error("Please sign in to access your cart.");
    }
    if (!response.ok) throw new Error(data.error || "Cart update failed.");
    return data;
}

async function loadCart() {
    try {
        const cart = await api("/api/cart");
        document.getElementById("subtotal").textContent = `₹${Number(cart.subtotal).toFixed(2)}`;
        if (!cart.items.length) {
            cartContainer.innerHTML = '<p class="empty">Your cart is empty. <a href="/shop">Browse products</a></p>';
        } else {
            cartContainer.innerHTML = cart.items.map(item => `<article class="cart-item" data-id="${escapeHtml(item.productId)}">
                ${item.imageUrl ? `<img src="${escapeHtml(item.imageUrl)}" alt="">` : '<div class="no-image"></div>'}
                <div><strong>${escapeHtml(item.name)}</strong><p>₹${Number(item.unitPrice).toFixed(2)} each · ${item.available ? `${item.stock} in stock` : "Unavailable"}</p>
                    <div class="cart-controls">${item.available ? `<input type="number" min="1" max="${item.stock}" value="${item.quantity}" aria-label="Quantity">` : ""}
                    <strong>₹${Number(item.lineTotal).toFixed(2)}</strong></div>
                </div><div class="cart-item-actions">
                    <button class="wishlist-item" type="button">♡ Wishlist</button>
                    <button class="remove-item" type="button">Remove</button>
                </div>
            </article>`).join("");
        }
        document.getElementById("checkout-button").disabled = !cart.items.length ||
            cart.items.some(item => !item.available);
        await loadWishlist();
        loadCoinBalance().catch(error => {
            messageBox.textContent = error.message;
        });
    } catch (error) {
        messageBox.textContent = error.message;
    }
}

async function loadCoinBalance() {
    const response = await fetch("/api/auth/me");
    if (!response.ok) throw new Error("Your coin balance could not be loaded.");
    const user = await response.json();
    document.getElementById("cart-coin-balance").textContent = String(user.loyaltyCoins || 0);
    document.getElementById("coin-wallet").hidden = false;
}

async function loadWishlist() {
    const products = await api("/api/cart/wishlist");
    if (!products.length) {
        wishlistContainer.innerHTML = '<p class="empty">Your wishlist is empty.</p>';
        return;
    }
    wishlistContainer.innerHTML = products.map(product => `<article class="product-card">
        ${product.imageUrls?.length ? `<img src="${escapeHtml(product.imageUrls[0])}" alt="${escapeHtml(product.name)}">` : '<div class="no-image">Rajharita Naturals</div>'}
        <div class="product-info"><h2>${escapeHtml(product.name)}</h2><p class="price">₹${Number(product.price).toFixed(2)}</p>
        <div class="product-actions"><button data-move="${product.id}">Move to cart</button><button data-remove="${product.id}">Remove</button></div></div>
    </article>`).join("");
}

cartContainer.addEventListener("change", async event => {
    const input = event.target.closest("input[type=number]");
    if (!input) return;
    const id = input.closest("[data-id]").dataset.id;
    try {
        await api(`/api/cart/items/${encodeURIComponent(id)}`, "PUT", { quantity: Number(input.value) });
        await loadCart();
    } catch (error) {
        messageBox.textContent = error.message;
    }
});

cartContainer.addEventListener("click", async event => {
    const wishlistButton = event.target.closest(".wishlist-item");
    const removeButton = event.target.closest(".remove-item");
    if (!wishlistButton && !removeButton) return;
    const id = (wishlistButton || removeButton).closest("[data-id]").dataset.id;
    try {
        if (wishlistButton) {
            await api(`/api/cart/wishlist/${encodeURIComponent(id)}`, "POST", {});
            messageBox.textContent = "Product added to your wishlist.";
            await loadWishlist();
        } else {
            await api(`/api/cart/items/${encodeURIComponent(id)}`, "DELETE");
            await loadCart();
        }
    } catch (error) {
        messageBox.textContent = error.message;
    }
});

wishlistContainer.addEventListener("click", async event => {
    const move = event.target.closest("button[data-move]");
    const remove = event.target.closest("button[data-remove]");
    if (!move && !remove) return;
    const id = (move || remove).dataset.move || (move || remove).dataset.remove;
    try {
        await api(move ? `/api/cart/wishlist/${encodeURIComponent(id)}/move-to-cart` :
            `/api/cart/wishlist/${encodeURIComponent(id)}`, move ? "POST" : "DELETE", move ? {} : undefined);
        await loadCart();
    } catch (error) {
        messageBox.textContent = error.message;
    }
});

document.getElementById("checkout-button").addEventListener("click", () => {
    window.location.href = "/checkout";
});
loadCart();
