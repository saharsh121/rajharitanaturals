const productGrid = document.getElementById("products");
const messageBox = document.getElementById("message-box");
const productDetailDialog = document.getElementById("product-detail-dialog");
const productDetailContent = document.getElementById("product-detail-content");
let productDetailOpener = null;
let availableProducts = [];
let productDetailImages = [];
let currentProductImageIndex = 0;
document.getElementById("search").value = new URLSearchParams(window.location.search).get("q") || "";

function escapeHtml(value) {
    return String(value || "").replace(/[&<>"']/g, char => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[char]);
}

function productImages(product) {
    const homeImages = Array.isArray(product.homeImageUrls) ? product.homeImageUrls : [];
    const catalogImages = Array.isArray(product.imageUrls) ? product.imageUrls : [];
    return [...new Set([...homeImages, ...catalogImages])];
}

async function loadCategories() {
    const categories = await (await fetch("/api/products/categories")).json();
    document.getElementById("category").innerHTML = '<option value="">All categories</option>' +
        categories.map(category => `<option value="${escapeHtml(category.name)}">${escapeHtml(category.name)}</option>`).join("");
}

function queryString() {
    const params = new URLSearchParams();
    const fields = [["q", "search"], ["category", "category"], ["brand", "brand"],
        ["minPrice", "min-price"], ["maxPrice", "max-price"], ["inStock", "availability"], ["sort", "sort"]];
    fields.forEach(([key, id]) => {
        const value = document.getElementById(id).value.trim();
        if (value) params.set(key, value);
    });
    return params.toString();
}

async function loadProducts() {
    messageBox.textContent = "";
    productGrid.innerHTML = '<p class="empty">Loading products…</p>';
    const response = await fetch(`/api/products?${queryString()}`);
    if (!response.ok) {
        productGrid.innerHTML = '<p class="empty">Products could not be loaded.</p>';
        return;
    }
    const products = await response.json();
    availableProducts = products;
    if (!products.length) {
        productGrid.innerHTML = '<p class="empty">No products match your search.</p>';
        return;
    }
    productGrid.innerHTML = products.map(product => {
        const images = productImages(product);
        return `<article class="product-card" data-product-id="${escapeHtml(product.id)}">
        ${images.length ? `<button type="button" class="product-image-button" data-product-view="${escapeHtml(product.id)}" aria-label="View ${escapeHtml(product.name)} details"><img src="${escapeHtml(images[0])}" alt="${escapeHtml(product.name)}"></button>` : `<button type="button" class="no-image product-image-button" data-product-view="${escapeHtml(product.id)}">Rajharita Naturals</button>`}
        <div class="product-info">
            <h2><button type="button" class="product-title-button" data-product-view="${escapeHtml(product.id)}">${escapeHtml(product.name)}</button></h2>
            <p>${escapeHtml(product.description)}</p>
            <p>${escapeHtml(product.category)}${product.brand ? ` · ${escapeHtml(product.brand)}` : ""}</p>
            <p class="price">₹${Number(product.price).toFixed(2)}</p>
            <p>${product.stock > 0 ? `${product.stock} in stock` : "Out of stock"}</p>
            <div class="product-actions">
                <button data-cart="${product.id}" ${product.stock < 1 ? "disabled" : ""}>Add to cart</button>
                <button data-wishlist="${product.id}">♡ Wishlist</button>
            </div>
        </div>
    </article>`;
    }).join("");
    alignProductCardRows();
}

function alignProductCardRows() {
    const cards = [...productGrid.querySelectorAll(".product-card")];
    const rows = new Map();
    cards.forEach(card => {
        card.querySelectorAll(".product-info h2, .product-info > p").forEach(element => {
            element.style.minHeight = "";
        });
        const rowTop = Math.round(card.getBoundingClientRect().top);
        if (!rows.has(rowTop)) rows.set(rowTop, []);
        rows.get(rowTop).push(card);
    });

    const alignedElements = [
        ".product-info h2",
        ".product-info > p:nth-of-type(1)",
        ".product-info > p:nth-of-type(2)",
        ".product-info > p:nth-of-type(3)",
        ".product-info > p:nth-of-type(4)"
    ];
    rows.forEach(rowCards => alignedElements.forEach(selector => {
        const elements = rowCards.map(card => card.querySelector(selector)).filter(Boolean);
        const maxHeight = Math.max(...elements.map(element => element.getBoundingClientRect().height));
        elements.forEach(element => {
            element.style.minHeight = `${maxHeight}px`;
        });
    }));
}

let productAlignmentFrame;
window.addEventListener("resize", () => {
    if (productAlignmentFrame) cancelAnimationFrame(productAlignmentFrame);
    productAlignmentFrame = requestAnimationFrame(() => {
        productAlignmentFrame = null;
        alignProductCardRows();
    });
});

function formatSpecification(value) {
    if (value === null || value === undefined) return "";
    return typeof value === "object" ? JSON.stringify(value) : String(value);
}

function showProductDetails(product, opener) {
    productDetailImages = productImages(product);
    currentProductImageIndex = 0;
    const gallery = productDetailImages.length
        ? `<div class="product-detail-gallery" role="group" aria-label="Product images">
            <button type="button" class="gallery-nav gallery-previous" data-gallery-previous aria-label="Previous image" ${productDetailImages.length < 2 ? "hidden" : ""}>‹</button>
            <img class="product-detail-image" src="${escapeHtml(productDetailImages[0])}" alt="${escapeHtml(product.name)} image 1">
            <button type="button" class="gallery-nav gallery-next" data-gallery-next aria-label="Next image" ${productDetailImages.length < 2 ? "hidden" : ""}>›</button>
            <p class="gallery-counter" aria-live="polite">${productDetailImages.length ? "1" : "0"} / ${productDetailImages.length}</p>
        </div>`
        : '<div class="product-detail-gallery"><div class="product-detail-no-image">Rajharita Naturals</div></div>';
    const specifications = Object.entries(product.specifications || {});
    productDetailContent.innerHTML = `<section class="product-detail-view">
        ${gallery}
        <div class="product-detail-info">
            <p class="eyebrow">${escapeHtml(product.category)}</p>
            <h1>${escapeHtml(product.name)}</h1>
            <p class="product-detail-description">${escapeHtml(product.description || "No description provided.")}</p>
            ${product.brand ? `<p class="product-detail-brand">Brand: ${escapeHtml(product.brand)}</p>` : ""}
            <p class="price">₹${Number(product.price).toFixed(2)}</p>
            <p class="product-detail-stock">${product.stock > 0 ? `${product.stock} in stock` : "Out of stock"}</p>
            <h2>Product specifications</h2>
            ${specifications.length
                ? `<dl class="product-specification-list">${specifications.map(([name, value]) =>
                    `<div><dt>${escapeHtml(name)}</dt><dd>${escapeHtml(formatSpecification(value))}</dd></div>`).join("")}</dl>`
                : '<p class="product-detail-empty-specs">No specifications provided.</p>'}
            <div class="product-actions">
                <button data-detail-cart="${escapeHtml(product.id)}" ${product.stock < 1 ? "disabled" : ""}>Add to cart</button>
                <button class="secondary-action" data-detail-wishlist="${escapeHtml(product.id)}">♡ Wishlist</button>
            </div>
            <p class="product-detail-message" role="status" aria-live="polite"></p>
        </div>
    </section>`;
    productDetailOpener = opener;
    productDetailDialog.showModal();
    document.getElementById("close-product-details").focus();
}

function showProductImage(index) {
    if (!productDetailImages.length) return;
    currentProductImageIndex = (index + productDetailImages.length) % productDetailImages.length;
    const image = productDetailContent.querySelector(".product-detail-image");
    image.src = productDetailImages[currentProductImageIndex];
    image.alt = `${productDetailContent.querySelector(".product-detail-info h1").textContent} image ${currentProductImageIndex + 1}`;
    productDetailContent.querySelector(".gallery-counter").textContent =
        `${currentProductImageIndex + 1} / ${productDetailImages.length}`;
}

document.getElementById("close-product-details").addEventListener("click", () => {
    productDetailDialog.close();
});
productDetailDialog.addEventListener("click", event => {
    if (event.target === productDetailDialog) productDetailDialog.close();
});
productDetailDialog.addEventListener("close", () => {
    productDetailOpener?.focus();
    productDetailOpener = null;
});

async function customerAction(endpoint, method, payload, message) {
    const response = await fetch("/api/auth/csrf");
    const csrf = await response.json();
    const result = await fetch(endpoint, {
        method,
        headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrf.token },
        body: method === "POST" ? JSON.stringify(payload || {}) : undefined
    });
    if (result.status === 401 || result.status === 403) {
        window.location.href = "/login";
        return;
    }
    const data = await result.json().catch(() => ({}));
    if (!result.ok) throw new Error(data.error || "Unable to update your basket.");
    messageBox.textContent = message;
    updateCartCount();
}

async function updateCartCount() {
    const result = await fetch("/api/cart");
    if (!result.ok) return;
    const cart = await result.json();
    document.getElementById("cart-count").textContent = `(${cart.items.reduce((count, item) => count + item.quantity, 0)})`;
}

async function loadCoinBalance() {
    const response = await fetch("/api/auth/me");
    if (response.status === 401 || response.status === 403) return;
    if (!response.ok) throw new Error("Your coin balance could not be loaded.");
    const user = await response.json();
    document.getElementById("shop-coin-balance").textContent = String(user.loyaltyCoins || 0);
    document.getElementById("coin-wallet").hidden = false;
}

productGrid.addEventListener("click", async event => {
    const cartButton = event.target.closest("button[data-cart]");
    const wishlistButton = event.target.closest("button[data-wishlist]");
    const detailButton = event.target.closest("button[data-product-view]");
    if (detailButton) {
        const product = availableProducts.find(item => item.id === detailButton.dataset.productView);
        if (product) showProductDetails(product, detailButton);
        return;
    }
    if (!cartButton && !wishlistButton) return;
    const productId = (cartButton || wishlistButton).dataset.cart || (cartButton || wishlistButton).dataset.wishlist;
    try {
        await customerAction(wishlistButton ? `/api/cart/wishlist/${encodeURIComponent(productId)}` :
            "/api/cart/items", "POST", wishlistButton ? {} : { productId, quantity: 1 },
            wishlistButton ? "Saved to your wishlist." : "Added to your cart.");
    } catch (error) {
        messageBox.textContent = error.message;
    }
});

productDetailDialog.addEventListener("click", async event => {
    if (event.target.closest("[data-gallery-previous]")) {
        showProductImage(currentProductImageIndex - 1);
        return;
    }
    if (event.target.closest("[data-gallery-next]")) {
        showProductImage(currentProductImageIndex + 1);
        return;
    }
    const cartButton = event.target.closest("button[data-detail-cart]");
    const wishlistButton = event.target.closest("button[data-detail-wishlist]");
    if (!cartButton && !wishlistButton) return;

    const productId = cartButton?.dataset.detailCart || wishlistButton.dataset.detailWishlist;
    const status = productDetailContent.querySelector(".product-detail-message");
    try {
        await customerAction(wishlistButton ? `/api/cart/wishlist/${encodeURIComponent(productId)}` :
            "/api/cart/items", "POST", wishlistButton ? {} : { productId, quantity: 1 },
            wishlistButton ? "Saved to your wishlist." : "Added to your cart.");
        status.textContent = messageBox.textContent;
    } catch (error) {
        status.textContent = error.message;
    }
});

productDetailDialog.addEventListener("keydown", event => {
    if (productDetailImages.length < 2) return;
    if (event.key === "ArrowLeft") {
        event.preventDefault();
        showProductImage(currentProductImageIndex - 1);
    } else if (event.key === "ArrowRight") {
        event.preventDefault();
        showProductImage(currentProductImageIndex + 1);
    }
});

document.getElementById("search-button").addEventListener("click", loadProducts);
loadCategories().then(loadProducts).catch(() => {
    productGrid.innerHTML = '<p class="empty">Unable to load the product catalog.</p>';
});

(async () => {
    const response = await fetch("/api/auth/session");
    if (!response.ok) throw new Error("Unable to check customer session.");
    if ((await response.json()).authenticated) {
        await Promise.all([updateCartCount(), loadCoinBalance()]);
    }
})().catch(error => {
    messageBox.textContent = error.message;
});
