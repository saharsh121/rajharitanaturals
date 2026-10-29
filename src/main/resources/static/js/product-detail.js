const container = document.getElementById("product-detail");
const id = decodeURIComponent(window.location.pathname.split("/").pop());

function escapeHtml(value) {
    return String(value || "").replace(/[&<>"']/g, char => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[char]);
}

async function loadProduct() {
    const response = await fetch(`/api/products/${encodeURIComponent(id)}`);
    const product = await response.json().catch(() => ({}));
    if (!response.ok) {
        container.innerHTML = `<p class="empty">${escapeHtml(product.error || "Product not found.")} <a href="/shop">Return to shop</a></p>`;
        return;
    }
    container.innerHTML = `<section class="detail-layout">
        <div class="detail-images">${product.imageUrls?.length ? product.imageUrls.map(url =>
            `<img src="${escapeHtml(url)}" alt="${escapeHtml(product.name)}">`).join("") :
            '<div class="no-image">Rajharita Naturals</div>'}</div>
        <div class="detail-copy"><p class="eyebrow">${escapeHtml(product.category)}</p>
            <h1>${escapeHtml(product.name)}</h1><p>${escapeHtml(product.description)}</p>
            <p>${product.brand ? `Brand: ${escapeHtml(product.brand)}` : ""}</p>
            <p class="price">₹${Number(product.price).toFixed(2)}</p>
            <p>${product.stock > 0 ? `${product.stock} in stock` : "Currently out of stock"}</p>
            <div class="product-actions"><button id="detail-add" ${product.stock < 1 ? "disabled" : ""}>Add to cart</button>
            <button id="detail-wishlist">♡ Add to wishlist</button></div>
            <h2>Specifications</h2><dl>${Object.entries(product.specifications || {}).map(([key, value]) =>
                `<dt>${escapeHtml(key)}</dt><dd>${escapeHtml(value)}</dd>`).join("") || "<dd>No specifications provided.</dd>"}</dl>
        </div></section><p id="message-box" role="status" aria-live="polite"></p>`;
    document.getElementById("detail-add").addEventListener("click", () => addToCart(false));
    document.getElementById("detail-wishlist").addEventListener("click", () => addToCart(true));
}

async function addToCart(wishlist) {
    const csrfResponse = await fetch("/api/auth/csrf");
    const csrf = await csrfResponse.json();
    const response = await fetch(wishlist ? `/api/cart/wishlist/${encodeURIComponent(id)}` : "/api/cart/items", {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrf.token },
        body: JSON.stringify(wishlist ? {} : { productId: id, quantity: 1 })
    });
    if (response.status === 401 || response.status === 403) {
        window.location.href = "/login";
        return;
    }
    const result = await response.json().catch(() => ({}));
    document.getElementById("message-box").textContent =
        response.ok ? (wishlist ? "Saved to your wishlist." : "Added to your cart.") :
            result.error || "Unable to update your basket.";
}

loadProduct().catch(() => {
    container.innerHTML = '<p class="empty">Unable to load product details.</p>';
});
