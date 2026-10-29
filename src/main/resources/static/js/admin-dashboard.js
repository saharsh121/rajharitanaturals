const messageBox = document.getElementById("message-box");
const usersBody = document.getElementById("users-body");
let users = [];
let currentFilter = "all";
let csrfToken = "";
let products = [];
let categories = [];
let editingImageUrls = [];
let selectedImageFiles = [];
let editingHomeImageUrls = [null, null];
let selectedHomeImageFiles = [null, null];
let orders = [];
let customerRatings = [];
let salesReportData = null;
let showcaseImages = [];
let showcaseMaximumImages = 10;

function showMessage(text, type = "success") {
    messageBox.textContent = text;
    messageBox.className = `show ${type}`;
}

async function loadCsrfToken() {
    const response = await fetch("/api/auth/csrf");
    const result = await response.json();
    csrfToken = result.token;
}

async function api(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (options.method && options.method !== "GET") headers["X-XSRF-TOKEN"] = csrfToken;
    let response;
    try {
        response = await fetch(path, { ...options, headers });
    } catch (error) {
        if (error instanceof TypeError) {
            throw new Error("Could not reach the server. Check your connection and try again.");
        }
        throw error;
    }
    const result = await response.json().catch(() => ({}));
    if (response.status === 401 || response.status === 403) {
        window.location.href = "/admin";
        throw new Error("Your admin session has expired.");
    }
    if (!response.ok) throw new Error(result.error || "Request failed.");
    return result;
}

function renderUsers() {
    const query = document.getElementById("user-search").value.trim().toLocaleLowerCase();
    const visible = users.filter(user => {
        const matchesStatus = currentFilter !== "pending" || !user.approved;
        const searchable = [user.name, user.username, user.email, user.contactNumber]
            .filter(Boolean).join(" ").toLocaleLowerCase();
        return matchesStatus && (!query || searchable.includes(query));
    });
    document.getElementById("user-result-count").textContent =
        `${visible.length} of ${users.filter(user => currentFilter !== "pending" || !user.approved).length} users`;
    if (!visible.length) {
        usersBody.innerHTML = `<tr><td colspan="7" class="empty">${query ? "No users match your search." : "No users to display."}</td></tr>`;
        return;
    }
    usersBody.innerHTML = visible.map(user => {
        const state = !user.active ? "Blocked" : user.approved ? "Approved" : "Pending";
        const statusClass = user.active && user.approved ? "" : "pending";
        return `<tr>
            <td>${escapeHtml(user.name)}</td>
            <td>${escapeHtml(user.username)}</td>
            <td>${escapeHtml(user.email)}</td>
            <td>${escapeHtml(user.contactNumber)}</td>
            <td><span class="status ${statusClass}">${state}</span></td>
            <td>
                <select aria-label="Role for ${escapeHtml(user.username)}" data-role="${escapeHtml(user.id)}">
                    <option value="CUSTOMER" ${user.role === "CUSTOMER" ? "selected" : ""}>Customer</option>
                    <option value="STAFF" ${user.role === "STAFF" ? "selected" : ""}>Staff</option>
                </select>
            </td>
            <td><div class="actions">
                ${!user.approved ? `<button class="approve" data-action="approve" data-id="${escapeHtml(user.id)}">Approve</button><button class="reject" data-action="reject" data-id="${escapeHtml(user.id)}">Reject</button>` : ""}
                ${user.active ? `<button class="block" data-action="block" data-id="${escapeHtml(user.id)}">Block</button>` : `<button class="approve" data-action="activate" data-id="${escapeHtml(user.id)}">Activate</button>`}
                <button class="muted" data-action="orders" data-id="${escapeHtml(user.id)}">Order history</button>
                <button class="reject" data-action="delete" data-id="${escapeHtml(user.id)}">Delete</button>
            </div></td>
        </tr>`;
    }).join("");
}

function renderCustomerRatings() {
    const query = document.getElementById("customer-rating-search").value.trim().toLocaleLowerCase();
    const selectedRating = document.getElementById("customer-rating-filter").value;
    const visible = customerRatings.filter(customer => {
        const searchable = [customer.name, customer.username, customer.email, customer.contactNumber]
            .filter(Boolean).join(" ").toLocaleLowerCase();
        return (selectedRating === "all" || customer.rating === Number(selectedRating))
            && (!query || searchable.includes(query));
    });
    document.getElementById("customer-rating-count").textContent =
        `${visible.length} of ${customerRatings.length} customers`;
    const counts = [5, 4, 3, 2, 1, 0].map(rating => ({
        rating,
        count: customerRatings.filter(customer => customer.rating === rating).length
    }));
    document.getElementById("customer-rating-overview").innerHTML = counts.map(({ rating, count }) => `
        <div class="rating-overview-card">
            <strong>${count}</strong>${rating ? `${rating} ${rating === 1 ? "star" : "stars"}` : "No qualifying orders"}
        </div>`).join("");
    document.getElementById("customer-ratings-body").innerHTML = visible.length ? visible.map(customer => `
        <tr>
            <td><strong>${escapeHtml(customer.name || customer.username)}</strong><br>
                ${escapeHtml(customer.username)}<br>${escapeHtml(customer.email)}<br>${escapeHtml(customer.contactNumber)}</td>
            <td><span class="rating-stars" aria-label="${customer.rating} out of 5 stars">${"★".repeat(customer.rating)}${"☆".repeat(5 - customer.rating)}</span><br>
                <small>${escapeHtml(customer.ratingLabel)}</small></td>
            <td>${customer.deliveredOrders}</td>
            <td>${customer.productUnits}</td>
            <td>₹${Number(customer.spend).toFixed(2)}</td>
            <td>${escapeHtml(orderDateLabel(customer.lastOrderAt))}</td>
        </tr>`).join("") :
        `<tr><td colspan="6" class="empty">${customerRatings.length ? "No customers match these filters." : "No customers to rate."}</td></tr>`;
}

function csvCell(value) {
    let text = value === null || value === undefined ? "" : String(value);
    if (/^[\s]*[=+\-@]/.test(text)) text = `'${text}`;
    return `"${text.replace(/"/g, '""')}"`;
}

function downloadCustomerRatings(ratings, filename) {
    if (!ratings.length) {
        showMessage("There are no customers to download for this selection.", "error");
        return;
    }

    const headers = [
        "Customer ID", "Name", "Username", "Email", "Mobile", "Gender", "Account created",
        "Email verified", "Admin approved", "Account active", "Saved addresses",
        "Purchase rating", "Rating label", "Paid delivered orders (last 3 months)",
        "Product units", "Spend (INR)", "Last order"
    ];
    const rows = ratings.map(customer => {
        const addresses = (customer.addresses || []).map(address =>
            [
                address.recipientName,
                address.phone,
                address.line1,
                address.line2,
                address.city,
                address.state,
                address.postalCode,
                address.defaultAddress ? "Default address" : ""
            ].filter(Boolean).join(", ")
        ).join(" | ");
        return [
            customer.userId,
            customer.name,
            customer.username,
            customer.email,
            customer.contactNumber,
            customer.gender,
            orderDateLabel(customer.createdAt),
            customer.verified ? "Yes" : "No",
            customer.approved ? "Yes" : "No",
            customer.active ? "Yes" : "No",
            addresses,
            customer.rating,
            customer.ratingLabel,
            customer.deliveredOrders,
            customer.productUnits,
            customer.spend,
            orderDateLabel(customer.lastOrderAt)
        ].map(csvCell).join(",");
    });
    const csv = `\uFEFF${[headers.map(csvCell).join(","), ...rows].join("\r\n")}`;
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    showMessage(`Downloaded ${ratings.length} customer record${ratings.length === 1 ? "" : "s"}.`);
}

async function loadCustomerRatings() {
    customerRatings = await api("/api/admin/customers/ratings");
    renderCustomerRatings();
    messageBox.className = "";
}

function renderUserOrders(user, orders) {
    const dialog = document.getElementById("user-orders-dialog");
    document.getElementById("user-orders-title").textContent =
        `Order history — ${user.name || user.username}`;
    document.getElementById("user-orders-body").innerHTML = orders.length ? orders.map(order => `
        <tr>
            <td><strong>${escapeHtml(order.orderNumber || order.id)}</strong></td>
            <td>${escapeHtml(orderDateLabel(order.createdAt))}</td>
            <td>${(order.items || []).map(item =>
                `${escapeHtml(item.name)} × ${escapeHtml(item.quantity)}`).join("<br>") || "No item details"}</td>
            <td>₹${Number(order.total || 0).toFixed(2)}</td>
            <td>${escapeHtml(order.status)}</td>
            <td>${escapeHtml(order.paymentMethod)}<br>${escapeHtml(order.paymentStatus)}</td>
        </tr>`).join("") :
        '<tr><td colspan="6" class="empty">No orders found for this user.</td></tr>';
    dialog.showModal();
}

function escapeHtml(value) {
    return String(value || "").replace(/[&<>"']/g, character => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[character]);
}

const inquiryViews = {
    investments: {
        bodyId: "investment-inquiries-body",
        searchId: "investment-inquiry-search",
        countId: "investment-inquiry-count",
        exportFilename: "rajharita-investment-inquiries.csv",
        exportColumns: [
            ["ID", record => record.id],
            ["Submitted at", record => record.createdAt],
            ["Name", record => record.name],
            ["Company", record => record.companyName],
            ["Email", record => record.email],
            ["Phone", record => record.phone],
            ["Location", record => record.location],
            ["Investment type", record => record.investmentType],
            ["Amount range", record => record.amountRange],
            ["Message", record => record.message],
            ["Attachment name", record => record.attachmentName],
            ["Attachment URL", record => record.attachmentPath],
            ["Status", record => record.status],
            ["Language", record => record.language]
        ],
        columns: record => [
            inquiryDate(record.createdAt),
            [record.name, record.location].filter(Boolean).join("\n"),
            record.companyName,
            [record.email, record.phone].filter(Boolean).join("\n"),
            [record.investmentType, record.amountRange].filter(Boolean).join("\n"),
            record.message,
            inquiryAttachment(record.attachmentPath, record.attachmentName),
            inquiryStatus(record.status)
        ]
    },
    b2b: {
        bodyId: "b2b-inquiries-body",
        searchId: "b2b-inquiry-search",
        countId: "b2b-inquiry-count",
        exportFilename: "rajharita-b2b-requests.csv",
        exportColumns: [
            ["ID", record => record.id],
            ["Submitted at", record => record.createdAt],
            ["Company", record => record.companyName],
            ["Contact person", record => record.contactPerson],
            ["Email", record => record.email],
            ["Phone", record => record.phone],
            ["Material required", record => record.materialRequired],
            ["Quantity", record => record.quantity],
            ["Frequency", record => record.frequency],
            ["Delivery location", record => record.deliveryLocation],
            ["Message", record => record.message],
            ["Attachment name", record => record.attachmentName],
            ["Attachment URL", record => record.attachmentPath],
            ["Request status", record => record.status],
            ["Quote status", record => record.quoteStatus],
            ["Language", record => record.language]
        ],
        columns: record => [
            inquiryDate(record.createdAt),
            [record.companyName, record.contactPerson].filter(Boolean).join("\n"),
            [record.email, record.phone].filter(Boolean).join("\n"),
            [record.materialRequired, record.quantity, record.frequency].filter(Boolean).join("\n"),
            record.deliveryLocation,
            record.message,
            inquiryAttachment(record.attachmentPath, record.attachmentName),
            `${inquiryStatus(record.status)}${record.quoteStatus ? `<br>${escapeHtml(record.quoteStatus)}` : ""}`
        ]
    },
    farmers: {
        bodyId: "farmer-inquiries-body",
        searchId: "farmer-inquiry-search",
        countId: "farmer-inquiry-count",
        exportFilename: "rajharita-farmer-submissions.csv",
        exportColumns: [
            ["ID", record => record.id],
            ["Submitted at", record => record.createdAt],
            ["Farmer name", record => record.name],
            ["Village", record => record.village],
            ["Phone", record => record.phone],
            ["Preferred contact", record => record.preferredContact],
            ["Crop / product", record => record.cropType],
            ["Quantity", record => record.quantity],
            ["Message", record => record.message],
            ["Status", record => record.status],
            ["Language", record => record.language]
        ],
        columns: record => [
            inquiryDate(record.createdAt),
            record.name,
            record.village,
            [record.phone, record.preferredContact].filter(Boolean).join("\n"),
            record.cropType,
            record.quantity,
            record.message,
            inquiryStatus(record.status)
        ]
    },
    odop: {
        bodyId: "odop-inquiries-body",
        searchId: "odop-inquiry-search",
        countId: "odop-inquiry-count",
        exportFilename: "rajharita-odop-partners.csv",
        exportColumns: [
            ["ID", record => record.id],
            ["Submitted at", record => record.createdAt],
            ["Name", record => record.name],
            ["District", record => record.district],
            ["Contact", record => record.contact],
            ["ODOP product", record => record.odopProduct],
            ["Partner role", record => record.role],
            ["Message", record => record.message],
            ["Attachment name", record => record.attachmentName],
            ["Attachment URL", record => record.attachmentPath],
            ["Status", record => record.status],
            ["Language", record => record.language]
        ],
        columns: record => [
            inquiryDate(record.createdAt),
            record.name,
            record.district,
            record.contact,
            [record.odopProduct, record.role].filter(Boolean).join("\n"),
            record.message,
            inquiryAttachment(record.attachmentPath, record.attachmentName),
            inquiryStatus(record.status)
        ]
    }
};
const inquiryRecords = {};
let websiteFeedback = [];
let supportRequests = [];

function inquiryDate(value) {
    if (!value) return "";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function downloadCsv(filename, headers, rows) {
    if (!rows.length) {
        showMessage("There are no records to download.", "error");
        return;
    }
    const csv = `\uFEFF${[headers, ...rows].map(row => row.map(csvCell).join(",")).join("\r\n")}`;
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    showMessage(`Downloaded ${rows.length} record${rows.length === 1 ? "" : "s"} to CSV.`);
}

function downloadInquiries(type) {
    const view = inquiryViews[type];
    const records = inquiryRecords[type];
    if (!Array.isArray(records)) {
        showMessage("Load the inquiry list before downloading it.", "error");
        return;
    }
    downloadCsv(view.exportFilename, view.exportColumns.map(([heading]) => heading),
        records.map(record => view.exportColumns.map(([, value]) => value(record))));
}

function inquiryStatus(value) {
    return value ? `<span class="status">${escapeHtml(value)}</span>` : "";
}

function inquiryAttachment(path, name) {
    if (typeof path !== "string" || !path.startsWith("/uploads/")) {
        return "";
    }
    const segments = path.split("/").slice(1);
    if (segments.some(segment => !/^[\w.-]+$/.test(segment) || segment === "." || segment === "..")) return "";
    return `<a href="${path}" target="_blank" rel="noopener noreferrer">${escapeHtml(name || "View file")}</a>`;
}

function renderInquiries(type) {
    const view = inquiryViews[type];
    const records = inquiryRecords[type] || [];
    const query = document.getElementById(view.searchId).value.trim().toLocaleLowerCase();
    const visible = records.filter(record => view.columns(record).map(value =>
        String(value || "").replace(/<[^>]*>/g, " ")
    ).join(" ").toLocaleLowerCase().includes(query));
    document.getElementById(view.countId).textContent = `${visible.length} of ${records.length} submissions`;
    const emptyMessage = records.length ? "No submissions match your search." : "No submissions received yet.";
    document.getElementById(view.bodyId).innerHTML = visible.length
        ? visible.map(record => `<tr>${view.columns(record).map(value =>
            `<td>${String(value || "").includes("<span") || String(value || "").includes("<a ")
                ? value
                : escapeHtml(value).replace(/\n/g, "<br>")}</td>`
        ).join("")}</tr>`).join("")
        : `<tr><td colspan="8" class="empty">${emptyMessage}</td></tr>`;
}

async function loadInquiries(type) {
    const endpoints = {
        investments: "/api/admin/inquiries/investments",
        b2b: "/api/admin/inquiries/b2b",
        farmers: "/api/admin/inquiries/farmers",
        odop: "/api/admin/inquiries/odop"
    };
    inquiryRecords[type] = await api(endpoints[type]);
    renderInquiries(type);
    messageBox.className = "";
}

function renderWebsiteFeedback() {
    const query = document.getElementById("feedback-search").value.trim().toLocaleLowerCase();
    const visible = websiteFeedback.filter(item =>
        [item.username, item.customerName, item.customerEmail, item.title, item.message]
            .filter(Boolean).join(" ").toLocaleLowerCase().includes(query));
    document.getElementById("feedback-count").textContent =
        `${visible.length} of ${websiteFeedback.length} reviews`;
    document.getElementById("feedback-body").innerHTML = visible.length ? visible.map(item => `
        <tr>
            <td>${escapeHtml(inquiryDate(item.createdAt))}</td>
            <td>${escapeHtml(item.customerName)}<br>${escapeHtml(item.username)}<br>${escapeHtml(item.customerEmail)}</td>
            <td><span class="feedback-stars" aria-label="${escapeHtml(item.rating)} out of 5 stars">${"★".repeat(item.rating)}${"☆".repeat(5 - item.rating)}</span></td>
            <td>${escapeHtml(item.title)}</td>
            <td>${escapeHtml(item.message).replace(/\n/g, "<br>")}</td>
        </tr>`).join("") :
        `<tr><td colspan="5" class="empty">${websiteFeedback.length ? "No feedback matches your search." : "No customer feedback received yet."}</td></tr>`;
}

async function loadWebsiteFeedback() {
    websiteFeedback = await api("/api/admin/feedback");
    renderWebsiteFeedback();
    messageBox.className = "";
}

function renderSupportRequests() {
    const query = document.getElementById("support-search").value.trim().toLocaleLowerCase();
    const fromDate = document.getElementById("support-from-date").value;
    const toDate = document.getElementById("support-to-date").value;
    const visible = supportRequests.filter(item => {
        const submittedDate = String(item.createdAt || "").slice(0, 10);
        const hasSubmittedDate = /^\d{4}-\d{2}-\d{2}$/.test(submittedDate);
        const matchesDate = (!fromDate && !toDate)
            || (hasSubmittedDate && (!fromDate || submittedDate >= fromDate)
                && (!toDate || submittedDate <= toDate));
        const searchable = [item.username, item.customerName, item.customerEmail, item.category,
            item.subject, item.message, item.adminResponse, item.status]
            .filter(Boolean).join(" ").toLocaleLowerCase();
        return matchesDate && searchable.includes(query);
    });
    document.getElementById("support-count").textContent =
        `${visible.length} of ${supportRequests.length} requests`;
    document.getElementById("support-to-date").min = fromDate;
    document.getElementById("support-from-date").max = toDate;
    const statuses = ["OPEN", "IN_PROGRESS", "RESOLVED"];
    document.getElementById("support-body").innerHTML = visible.length ? visible.map(item => `
        <tr data-support-request="${escapeHtml(item.id)}">
            <td>${escapeHtml(inquiryDate(item.createdAt))}</td>
            <td>${escapeHtml(item.customerName)}<br>${escapeHtml(item.username)}<br>${escapeHtml(item.customerEmail)}</td>
            <td>${escapeHtml(item.category)}<br><strong>${escapeHtml(item.subject)}</strong></td>
            <td>${escapeHtml(item.message).replace(/\n/g, "<br>")}</td>
            <td><div class="support-reply-controls">
                <select data-support-status aria-label="Support request status">
                    ${statuses.map(status => `<option value="${status}" ${item.status === status ? "selected" : ""}>${status.replaceAll("_", " ")}</option>`).join("")}
                </select>
                <textarea data-support-response maxlength="3000" aria-label="Reply to customer" placeholder="Write a reply (optional)">${escapeHtml(item.adminResponse)}</textarea>
                ${item.respondedBy ? `<small>Last replied by ${escapeHtml(item.respondedBy)}</small>` : ""}
                <button class="primary" type="button" data-support-save>Save reply & status</button>
            </div></td>
        </tr>`).join("") :
        `<tr><td colspan="5" class="empty">${supportRequests.length ? "No requests match your search." : "No customer support requests received yet."}</td></tr>`;
}

async function loadSupportRequests() {
    supportRequests = await api("/api/admin/support-requests");
    renderSupportRequests();
    messageBox.className = "";
}

async function refreshUsers() {
    try {
        users = await api("/api/admin/users");
        renderUsers();
        messageBox.className = "";
    } catch (error) {
        showMessage(error.message, "error");
    }
}

async function loadCategories() {
    categories = await api("/api/admin/categories");
    const options = categories.filter(category => category.active)
        .map(category => `<option value="${escapeHtml(category.name)}">${escapeHtml(category.name)}</option>`).join("");
    document.getElementById("product-category").innerHTML = options || '<option value="">Create a category first</option>';
    document.getElementById("categories-body").innerHTML = categories.length ? categories.map(category => `
        <tr><td>${escapeHtml(category.name)}</td><td>${escapeHtml(category.description)}</td>
        <td>${category.active ? "Active" : "Inactive"}</td>
        <td><button class="${category.active ? "block" : "approve"}" data-category-active="${category.id}" data-active="${!category.active}">
            ${category.active ? "Deactivate" : "Activate"}</button></td></tr>`).join("") :
        '<tr><td colspan="4" class="empty">No categories created yet.</td></tr>';
}

async function loadProducts() {
    products = await api("/api/admin/products");
    document.getElementById("products-body").innerHTML = products.length ? products.map(product => `
        <tr><td>${escapeHtml(product.name)}</td><td>${escapeHtml(product.category)}</td>
        <td>₹${Number(product.price).toFixed(2)}</td><td>${product.stock}</td>
        <td>${product.published ? "Published" : "Unpublished"}</td>
        <td><div class="actions"><button data-product-edit="${product.id}">Edit</button>
        <button class="${product.published ? "block" : "approve"}" data-product-publish="${product.id}" data-published="${!product.published}">
            ${product.published ? "Unpublish" : "Publish"}</button>
        <button class="reject" data-product-delete="${product.id}">Delete</button></div></td></tr>`).join("") :
        '<tr><td colspan="6" class="empty">No products created yet.</td></tr>';
}

async function loadInventory() {
    const lowStock = await api("/api/admin/inventory/low-stock");
    document.getElementById("inventory-body").innerHTML = lowStock.length ? lowStock.map(product => `
        <tr><td>${escapeHtml(product.name)}</td><td>${escapeHtml(product.category)}</td>
        <td>${product.stock}</td><td>${product.lowStockThreshold}</td></tr>`).join("") :
        '<tr><td colspan="4" class="empty">No low-stock products.</td></tr>';
}

async function loadOrders() {
    orders = await api("/api/admin/orders");
    renderOrders();
}

function renderOrders() {
    const orderNumberQuery = document.getElementById("order-number-search").value.trim().toLocaleLowerCase();
    const query = document.getElementById("order-search").value.trim().toLocaleLowerCase();
    const fromDate = document.getElementById("orders-from-date").value;
    const toDate = document.getElementById("orders-to-date").value;
    const selectedStatus = document.getElementById("order-status-filter").value;
    const visible = orders.filter(order => {
        const searchable = [
            order.orderNumber, order.id, order.username, order.customerEmail, order.customerPhone,
            order.paymentMethod, order.paymentStatus, order.status,
            order.deliveryAddress?.recipientName,
            ...(order.items || []).map(item => item.name)
        ].filter(Boolean).join(" ").toLocaleLowerCase();
        const orderDate = String(order.createdAt || "").slice(0, 10);
        return (!orderNumberQuery || String(order.orderNumber || "").toLocaleLowerCase().includes(orderNumberQuery))
            && (!query || searchable.includes(query))
            && (selectedStatus === "all" || order.status === selectedStatus)
            && (!fromDate || orderDate >= fromDate)
            && (!toDate || orderDate <= toDate);
    });
    document.getElementById("order-result-count").textContent =
        `${visible.length} of ${orders.length} orders`;
    const statuses = ["PLACED", "CONFIRMED", "PACKED", "SHIPPED", "DELIVERED", "CANCELLED", "RETURN_REQUESTED", "RETURNED"];
    document.getElementById("orders-body").innerHTML = visible.length ? visible.map(order => `
        <tr data-order="${escapeHtml(order.id)}">
            <td><strong>${escapeHtml(order.orderNumber)}</strong><br>${escapeHtml(order.username)}<br>${escapeHtml(order.customerEmail)}<br><span class="order-date">${escapeHtml(orderDateLabel(order.createdAt))}</span></td>
            <td>${(order.items || []).map(item => `${escapeHtml(item.name)} × ${item.quantity}`).join("<br>")}</td>
            <td>₹${Number(order.total).toFixed(2)}</td>
            <td>${escapeHtml(order.paymentMethod)}<br>${escapeHtml(order.paymentStatus)}
                ${order.paymentStatus === "REFUND_PENDING" ? `<button class="${order.paymentMethod === "COD" ? "approve" : "block"}" data-refund="${escapeHtml(order.id)}" data-method="${escapeHtml(order.paymentMethod)}">${order.paymentMethod === "COD" ? "Confirm manual refund" : "Refund online"}</button>` : ""}
            </td>
            <td><select data-order-status>${statuses.map(status => `<option value="${status}" ${status === order.status ? "selected" : ""}>${status.replaceAll("_", " ")}</option>`).join("")}</select>
                <input data-tracking placeholder="Tracking number" value="${escapeHtml(order.trackingNumber)}" /></td>
            <td><button class="primary" data-order-save>Save status</button></td>
        </tr>`).join("") : `<tr><td colspan="6" class="empty">${orders.length ? "No orders match these filters." : "No orders placed yet."}</td></tr>`;
}

function orderDateLabel(value) {
    if (!value) return "Date unavailable";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "Date unavailable" : date.toLocaleString();
}

async function loadCoupons() {
    const coupons = await api("/api/admin/coupons");
    document.getElementById("coupons-body").innerHTML = coupons.length ? coupons.map(coupon => `
        <tr><td>${escapeHtml(coupon.code)}</td>
        <td><strong>${escapeHtml(coupon.title || "Special offer")}</strong><br>${coupon.discountType === "PERCENT" ? `${coupon.discountValue}%` : `₹${coupon.discountValue}`}<br>${escapeHtml(coupon.description || "")}<br><small>${coupon.targetRating === null || coupon.targetRating === undefined ? "All customers" : `Exclusive to ${coupon.targetRating}-star customers`}</small></td>
        <td>${escapeHtml(coupon.startsAt || "Now")}<br>${escapeHtml(coupon.expiresAt || "No expiry")}</td>
        <td>${coupon.usageCount}${coupon.usageLimit ? ` / ${coupon.usageLimit}` : " / ∞"}</td>
        <td>${coupon.active ? "Active" : "Inactive"}</td>
        <td><div class="actions">
            <button class="${coupon.active ? "block" : "approve"}" data-coupon="${escapeHtml(coupon.id)}" data-active="${!coupon.active}">${coupon.active ? "Deactivate" : "Activate"}</button>
            <button class="reject" data-coupon-delete="${escapeHtml(coupon.id)}" data-coupon-code="${escapeHtml(coupon.code)}">Delete</button>
        </div></td></tr>`).join("") :
        '<tr><td colspan="6" class="empty">No coupons configured.</td></tr>';
}

function renderShowcaseImages() {
    const container = document.getElementById("showcase-images");
    const count = document.getElementById("showcase-image-count");
    count.textContent = `${showcaseImages.length} of ${showcaseMaximumImages} photos`;
    document.getElementById("upload-showcase-photos").disabled =
        showcaseImages.length >= showcaseMaximumImages;
    container.innerHTML = showcaseImages.length ? showcaseImages.map((image, index) => `
        <article class="showcase-image-card">
            <img src="${escapeHtml(image)}" alt="Home page photo ${index + 1}" loading="lazy" />
            <button type="button" class="reject" data-showcase-delete="${escapeHtml(image.split("/").pop())}">Remove photo</button>
        </article>`).join("") : '<p class="empty">No photos uploaded yet.</p>';
}

async function loadShowcaseImages() {
    const result = await api("/api/admin/showcase/images");
    showcaseImages = result.images;
    showcaseMaximumImages = result.maximumImages;
    document.getElementById("showcase-interval").value = result.intervalSeconds;
    renderShowcaseImages();
}

async function loadAboutUs() {
    const aboutUs = await api("/api/admin/showcase/about-us");
    document.getElementById("about-us-title").value = aboutUs.title;
    document.getElementById("about-us-summary").value = aboutUs.summary;
    document.getElementById("about-us-content").value = aboutUs.content;
    const container = document.getElementById("about-us-blocks");
    container.replaceChildren();
    (aboutUs.blocks || []).forEach(block => addAboutUsBlock(block));
    updateAboutUsBlockControls();
}

function updateAboutUsBlockControls() {
    const container = document.getElementById("about-us-blocks");
    const count = container.querySelectorAll(".about-us-block-row").length;
    document.getElementById("add-about-us-block").disabled = count >= 10;
    if (count === 0) {
        container.innerHTML = '<p class="empty">Add image-and-text blocks to expand the About Us story.</p>';
    }
}

function addAboutUsBlock(block = {}) {
    const container = document.getElementById("about-us-blocks");
    if (container.querySelectorAll(".about-us-block-row").length >= 10) {
        showMessage("The About Us section can contain up to 10 image-and-text blocks.", "error");
        return;
    }
    container.querySelector(".empty")?.remove();

    const row = document.createElement("article");
    row.className = "about-us-block-row";
    row.dataset.imageUrl = block.imageUrl || "";
    const imageField = document.createElement("div");
    imageField.className = "about-us-block-image";
    const label = document.createElement("label");
    label.textContent = "Related photo (JPEG, PNG, or WebP; max 5 MB)";
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "image/jpeg,image/png,image/webp";
    const preview = document.createElement("div");
    preview.className = "about-us-block-preview";
    if (block.imageUrl) {
        const image = document.createElement("img");
        image.src = block.imageUrl;
        image.alt = "About Us related photo";
        preview.append(image);
    } else {
        preview.textContent = "Choose a photo.";
    }
    imageField.append(label, input, preview);

    const content = document.createElement("div");
    content.className = "about-us-block-copy";
    const periodLabel = document.createElement("label");
    periodLabel.textContent = "Era / small label (optional)";
    const periodInput = document.createElement("input");
    periodInput.type = "text";
    periodInput.maxLength = 40;
    periodInput.placeholder = "e.g. Early";
    periodInput.value = block.period || "";
    const headingLabel = document.createElement("label");
    headingLabel.textContent = "Main heading (optional)";
    const headingInput = document.createElement("input");
    headingInput.type = "text";
    headingInput.maxLength = 120;
    headingInput.placeholder = "e.g. 1970s";
    headingInput.value = block.heading || "";
    const subheadingLabel = document.createElement("label");
    subheadingLabel.textContent = "Subheading (optional)";
    const subheadingInput = document.createElement("input");
    subheadingInput.type = "text";
    subheadingInput.maxLength = 160;
    subheadingInput.placeholder = "e.g. Sacrificing the soil";
    subheadingInput.value = block.subheading || "";
    const contentLabel = document.createElement("label");
    contentLabel.textContent = "Description";
    const textarea = document.createElement("textarea");
    textarea.maxLength = 5000;
    textarea.placeholder = "Describe this part of your story.";
    textarea.value = block.content || "";
    content.append(periodLabel, periodInput, headingLabel, headingInput,
        subheadingLabel, subheadingInput, contentLabel, textarea);

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "reject";
    remove.textContent = "Remove";
    row.append(imageField, content, remove);
    container.append(row);
    updateAboutUsBlockControls();
}

async function uploadAboutUsImage(file) {
    const formData = new FormData();
    formData.append("file", file);
    let response;
    try {
        response = await fetch("/api/admin/showcase/about-us/images", {
            method: "POST",
            headers: { "X-XSRF-TOKEN": csrfToken },
            body: formData
        });
    } catch (error) {
        if (error instanceof TypeError) {
            throw new Error(`Could not upload "${file.name}". Check your connection and ensure it is no larger than 5 MB.`);
        }
        throw error;
    }
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.error || `Upload of "${file.name}" failed (HTTP ${response.status}).`);
    return result.url;
}

function homeProductImages(product) {
    const homeImages = Array.isArray(product.homeImageUrls) ? product.homeImageUrls : [];
    const productImages = Array.isArray(product.imageUrls) ? product.imageUrls : [];
    return [homeImages[0] || productImages[0] || "", homeImages[1] || ""];
}

function renderHomeFeaturedPreview(previewId, imageUrl, alt) {
    const preview = document.getElementById(previewId);
    preview.replaceChildren();
    if (!imageUrl) {
        preview.textContent = "No photo selected.";
        return;
    }
    const image = document.createElement("img");
    image.src = imageUrl;
    image.alt = alt;
    preview.append(image);
}

function renderHomeFeaturedProducts() {
    const selector = document.getElementById("home-featured-product");
    const selectedId = selector.value;
    selector.innerHTML = '<option value="">Select a product</option>' +
        products.filter(product => !product.deleted).map(product =>
            `<option value="${escapeHtml(product.id)}">${escapeHtml(product.name)}${product.published ? "" : " (unpublished)"}</option>`
        ).join("");
    if (products.some(product => product.id === selectedId)) selector.value = selectedId;

    const featuredProducts = products.filter(product => product.homeFeatured);
    const container = document.getElementById("home-featured-list");
    container.innerHTML = featuredProducts.length ? featuredProducts.map(product => {
        const [mainImage] = homeProductImages(product);
        return `<article class="home-featured-card" data-home-featured-card="${escapeHtml(product.id)}">
            ${mainImage ? `<img src="${escapeHtml(mainImage)}" alt="${escapeHtml(product.name)}" loading="lazy" />` : ""}
            <div class="home-featured-card-content">
                <strong>${escapeHtml(product.name)}</strong>
                <p>${product.published ? "Published" : "Unpublished"}${homeProductImages(product)[1] ? " · Hover photo set" : ""}</p>
                <button class="secondary" type="button" data-home-featured-edit="${escapeHtml(product.id)}">Edit card</button>
                <button class="reject" type="button" data-home-featured-remove="${escapeHtml(product.id)}">Remove from home page</button>
            </div>
        </article>`;
    }).join("") : '<p class="empty">No Explore products cards added yet.</p>';
}

function selectHomeFeaturedProduct(product) {
    const [mainImage, hoverImage] = homeProductImages(product);
    document.getElementById("home-featured-main-image").value = "";
    document.getElementById("home-featured-hover-image").value = "";
    document.getElementById("home-featured-enabled").checked = true;
    renderHomeFeaturedPreview("home-featured-main-preview", mainImage, `${product.name} main photo`);
    renderHomeFeaturedPreview("home-featured-hover-preview", hoverImage, `${product.name} hover photo`);
}

async function loadSettings() {
    const settings = await api("/api/admin/settings");
    document.getElementById("delivery-charge").value = settings.deliveryCharge;
    document.getElementById("delivery-charge-per-kg").value = settings.deliveryChargePerKg ?? 0;
    document.getElementById("free-delivery").value = settings.freeDeliveryThreshold;
    document.getElementById("return-window").value = settings.returnWindowDays;
    document.getElementById("cod-enabled").checked = settings.cashOnDeliveryEnabled;
    document.getElementById("terms-policy").value = settings.termsAndConditions || "";
    document.getElementById("privacy-policy").value = settings.privacyPolicy || "";
    document.getElementById("return-policy").value = settings.returnPolicy || "";
}

function newInvoiceSection(type = "TEXT") {
    const section = {
        id: crypto.randomUUID(),
        type,
        title: type === "ITEMS" ? "Items" : type === "CUSTOM_TABLE" ? "Custom table" : "New text section",
        enabled: true,
        text: "",
        columns: [],
        rows: []
    };
    if (type === "ITEMS") {
        section.columns = [
            { title: "Product", field: "name" },
            { title: "Qty", field: "quantity" },
            { title: "Unit price", field: "unitPrice" },
            { title: "Total", field: "lineTotal" }
        ];
    } else if (type === "CUSTOM_TABLE") {
        section.columns = [{ title: "Column 1", field: "custom" }];
        section.rows = [{ cells: [""] }];
    }
    return section;
}

async function loadInvoiceTemplate() {
    const template = await api("/api/admin/invoice-template");
    document.getElementById("invoice-business-name").value = template.businessName || "";
    document.getElementById("invoice-title").value = template.invoiceTitle || "";
    document.getElementById("invoice-subtitle").value = template.subtitle || "";
    document.getElementById("invoice-footer").value = template.footer || "";
    const container = document.getElementById("invoice-sections");
    container.replaceChildren();
    (template.sections || []).forEach(section => container.append(renderInvoiceSection(section)));
    if (!template.sections?.length) {
        container.innerHTML = '<p class="empty">No sections yet. Add a section to begin building your invoice.</p>';
    }
}

function renderInvoiceSection(section) {
    const row = document.createElement("article");
    row.className = "invoice-section-editor";
    row.draggable = true;
    row.dataset.sectionId = section.id || crypto.randomUUID();

    const controls = document.createElement("div");
    controls.className = "invoice-section-controls";
    const titleLabel = document.createElement("label");
    titleLabel.textContent = "Section heading";
    const title = document.createElement("input");
    title.dataset.invoiceTitle = "";
    title.maxLength = 120;
    title.value = section.title || "";
    titleLabel.append(title);

    const typeLabel = document.createElement("label");
    typeLabel.textContent = "Section type";
    const type = document.createElement("select");
    type.dataset.invoiceType = "";
    [
        ["TEXT", "Text section"],
        ["ITEMS", "Order items table"],
        ["CUSTOM_TABLE", "Custom table"]
    ].forEach(([value, label]) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = label;
        type.append(option);
    });
    type.value = section.type || "TEXT";
    typeLabel.append(type);

    const enabledLabel = document.createElement("label");
    enabledLabel.className = "invoice-section-enabled";
    const enabled = document.createElement("input");
    enabled.type = "checkbox";
    enabled.dataset.invoiceEnabled = "";
    enabled.checked = section.enabled !== false;
    enabledLabel.append(enabled, document.createTextNode("Show section"));

    const removeSection = document.createElement("button");
    removeSection.type = "button";
    removeSection.className = "reject";
    removeSection.dataset.removeInvoiceSection = "";
    removeSection.textContent = "Remove";
    controls.append(titleLabel, typeLabel, enabledLabel, removeSection);

    const body = document.createElement("div");
    body.className = "invoice-section-body";
    if (type.value === "TEXT") {
        const label = document.createElement("label");
        label.textContent = "Text (placeholders can be used)";
        const textarea = document.createElement("textarea");
        textarea.dataset.invoiceText = "";
        textarea.maxLength = 5000;
        textarea.rows = 5;
        textarea.placeholder = "Add invoice information. Example: Order {{orderNumber}}";
        textarea.value = section.text || "";
        label.append(textarea);
        body.append(label);
    } else if (type.value === "ITEMS") {
        body.append(createInvoiceColumnsEditor(section.columns || [], true));
    } else {
        body.append(createInvoiceCustomTableEditor(section.columns || [], section.rows || []));
    }
    row.append(controls, body);
    return row;
}

function createInvoiceColumnsEditor(columns, itemColumns) {
    const wrapper = document.createElement("div");
    wrapper.className = "invoice-columns-list";
    columns.forEach((column, index) => {
        const editor = document.createElement("div");
        editor.className = "invoice-column-editor";
        const titleLabel = document.createElement("label");
        titleLabel.textContent = "Column heading";
        const title = document.createElement("input");
        title.dataset.invoiceColumnTitle = "";
        title.maxLength = 80;
        title.value = column.title || "";
        titleLabel.append(title);
        editor.append(titleLabel);
        if (itemColumns) {
            const fieldLabel = document.createElement("label");
            fieldLabel.textContent = "Order item data";
            const field = document.createElement("select");
            field.dataset.invoiceColumnField = "";
            [
                ["name", "Product name"],
                ["brand", "Brand"],
                ["quantity", "Quantity"],
                ["unitPrice", "Unit price"],
                ["lineTotal", "Line total"]
            ].forEach(([value, label]) => {
                const option = document.createElement("option");
                option.value = value;
                option.textContent = label;
                field.append(option);
            });
            field.value = column.field || "name";
            fieldLabel.append(field);
            editor.append(fieldLabel);
        }
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "reject";
        remove.dataset.removeInvoiceColumn = String(index);
        remove.textContent = "Remove column";
        editor.append(remove);
        wrapper.append(editor);
    });
    const add = document.createElement("button");
    add.type = "button";
    add.className = "secondary";
    add.dataset.addInvoiceColumn = "";
    add.textContent = "Add column";
    wrapper.append(add);
    return wrapper;
}

function createInvoiceCustomTableEditor(columns, rows) {
    const wrapper = document.createElement("div");
    wrapper.className = "invoice-section-body";
    const tableWrap = document.createElement("div");
    tableWrap.className = "invoice-custom-table-wrap";
    const table = document.createElement("table");
    table.className = "invoice-custom-table";
    const head = document.createElement("thead");
    const headRow = document.createElement("tr");
    columns.forEach((column, index) => {
        const th = document.createElement("th");
        const editor = document.createElement("div");
        editor.className = "invoice-column-editor invoice-custom-column-editor";
        const title = document.createElement("input");
        title.dataset.invoiceColumnTitle = "";
        title.maxLength = 80;
        title.setAttribute("aria-label", `Column ${index + 1} heading`);
        title.placeholder = `Column ${index + 1} heading`;
        title.value = column.title || "";
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "reject";
        remove.dataset.removeInvoiceColumn = String(index);
        remove.textContent = "Remove";
        editor.append(title, remove);
        th.append(editor);
        headRow.append(th);
    });
    head.append(headRow);
    table.append(head);
    const tableBody = document.createElement("tbody");
    rows.forEach((rowData, rowIndex) => {
        const tr = document.createElement("tr");
        columns.forEach((column, columnIndex) => {
            const td = document.createElement("td");
            const input = document.createElement("input");
            input.dataset.invoiceCell = "";
            input.maxLength = 1000;
            input.setAttribute("aria-label", `Row ${rowIndex + 1}, ${column.title || `column ${columnIndex + 1}`}`);
            input.placeholder = "Text or {{placeholder}}";
            input.value = rowData.cells?.[columnIndex] || "";
            td.append(input);
            tr.append(td);
        });
        const actionCell = document.createElement("td");
        const removeRow = document.createElement("button");
        removeRow.type = "button";
        removeRow.className = "reject";
        removeRow.dataset.removeInvoiceRow = String(rowIndex);
        removeRow.textContent = "Remove row";
        actionCell.append(removeRow);
        tr.append(actionCell);
        tableBody.append(tr);
    });
    table.append(tableBody);
    tableWrap.append(table);
    wrapper.append(tableWrap);

    const actions = document.createElement("div");
    actions.className = "invoice-table-controls";
    const addColumn = document.createElement("button");
    addColumn.type = "button";
    addColumn.className = "secondary";
    addColumn.dataset.addInvoiceColumn = "";
    addColumn.textContent = "Add column";
    const addRow = document.createElement("button");
    addRow.type = "button";
    addRow.className = "secondary";
    addRow.dataset.addInvoiceRow = "";
    addRow.textContent = "Add row";
    actions.append(addColumn, addRow);
    wrapper.append(actions);
    return wrapper;
}

function readInvoiceSection(row) {
    const type = row.querySelector("[data-invoice-type]").value;
    const section = {
        id: row.dataset.sectionId,
        type,
        title: row.querySelector("[data-invoice-title]").value.trim(),
        enabled: row.querySelector("[data-invoice-enabled]").checked,
        text: "",
        columns: [],
        rows: []
    };
    if (type === "TEXT") {
        section.text = row.querySelector("[data-invoice-text]").value;
    } else if (type === "ITEMS") {
        section.columns = [...row.querySelectorAll(".invoice-column-editor")].map(editor => ({
            title: editor.querySelector("[data-invoice-column-title]").value.trim(),
            field: editor.querySelector("[data-invoice-column-field]").value
        }));
    } else {
        section.columns = [...row.querySelectorAll("thead [data-invoice-column-title]")].map(input => ({
            title: input.value.trim(),
            field: "custom"
        }));
        section.rows = [...row.querySelectorAll("tbody tr")].map(tr => ({
            cells: [...tr.querySelectorAll("[data-invoice-cell]")].map(input => input.value)
        }));
    }
    return section;
}

function replaceInvoiceSection(row, section) {
    row.replaceWith(renderInvoiceSection(section));
}

function addInvoiceSection(type) {
    const container = document.getElementById("invoice-sections");
    if (container.querySelectorAll(".invoice-section-editor").length >= 20) {
        showMessage("An invoice can contain up to 20 sections.", "error");
        return;
    }
    container.querySelector(".empty")?.remove();
    container.append(renderInvoiceSection(newInvoiceSection(type)));
}

async function loadReport() {
    const fromDate = document.getElementById("sales-from-date").value;
    const toDate = document.getElementById("sales-to-date").value;
    const hasStartDate = Boolean(fromDate);
    const hasEndDate = Boolean(toDate);
    if (hasStartDate !== hasEndDate) {
        throw new Error("Enter both a start date and an end date to calculate period revenue.");
    }
    if (hasStartDate && fromDate > toDate) {
        throw new Error("The start date must be on or before the end date.");
    }
    const query = hasStartDate
        ? `?startDate=${encodeURIComponent(fromDate)}&endDate=${encodeURIComponent(toDate)}`
        : "";
    const report = await api(`/api/admin/reports/sales${query}`);
    salesReportData = { ...report, exportStartDate: fromDate, exportEndDate: toDate };
    const popular = Object.entries(report.popularProducts || {}).sort((a, b) => b[1] - a[1]).slice(0, 5);
    const periodMessage = document.getElementById("sales-period-message");
    periodMessage.textContent = hasStartDate
        ? `Paid revenue from ${fromDate} through ${toDate}, inclusive (by order date).`
        : "Choose a start and end date to calculate paid revenue for that period.";
    periodMessage.classList.remove("error");
    document.getElementById("sales-report").innerHTML = `
        <div class="metric">Orders<strong>${report.orderCount}</strong></div>
        <div class="metric">Paid revenue<strong>₹${Number(report.revenue).toFixed(2)}</strong></div>
        ${hasStartDate ? `<div class="metric">Paid revenue for selected period<strong>₹${Number(report.periodRevenue).toFixed(2)}</strong></div>` : ""}
        ${Object.entries(report.statuses || {}).map(([status, count]) =>
            `<div class="metric">${escapeHtml(status.replaceAll("_", " "))}<strong>${count}</strong></div>`).join("")}
        <div class="metric">Popular products<strong>${popular.length ? popular.map(([name, count]) =>
            `${escapeHtml(name)} (${count})`).join("<br>") : "—"}</strong></div>`;
}

async function downloadSalesReport() {
    if (!salesReportData) {
        showMessage("Load the sales report before downloading it.", "error");
        return;
    }
    const fromDate = salesReportData.exportStartDate;
    const toDate = salesReportData.exportEndDate;
    const ordersForExport = await api("/api/admin/orders");
    const reportHeaders = [
        "Record type", "Metric", "Value", "Order number", "Customer username", "Customer email",
        "Customer phone", "Order date", "Order status", "Payment method", "Payment status",
        "Items", "Coupon code", "Subtotal (INR)", "Discount (INR)", "Delivery charge (INR)",
        "Total (INR)", "Delivery address"
    ];
    const rows = [];
    const addSummary = (metric, value) => {
        const row = Array(reportHeaders.length).fill("");
        row[0] = "Sales summary";
        row[1] = metric;
        row[2] = value;
        rows.push(row);
    };
    addSummary("Report period", fromDate && toDate ? `${fromDate} to ${toDate}` : "All dates");
    addSummary("All-time orders", salesReportData.orderCount);
    addSummary("All-time paid revenue (INR)", salesReportData.revenue);
    if (salesReportData.periodRevenue !== undefined) {
        addSummary(`Paid revenue for ${fromDate} to ${toDate} (INR)`, salesReportData.periodRevenue);
    }
    Object.entries(salesReportData.statuses || {}).forEach(([status, count]) =>
        addSummary(`Orders — ${status.replaceAll("_", " ")}`, count));
    Object.entries(salesReportData.popularProducts || {})
        .sort((left, right) => right[1] - left[1])
        .forEach(([name, quantity]) => addSummary(`Units sold — ${name}`, quantity));

    const includedOrders = ordersForExport.filter(order => {
        if (!fromDate || !toDate) return true;
        const orderDate = String(order.createdAt || "").slice(0, 10);
        return orderDate >= fromDate && orderDate <= toDate;
    });
    includedOrders.forEach(order => {
        const address = order.deliveryAddress || {};
        const addressText = [
            address.recipientName, address.line1, address.line2, address.city,
            address.state, address.postalCode
        ].filter(Boolean).join(", ");
        const items = (order.items || [])
            .map(item => `${item.name || "Item"} × ${item.quantity || 0}`)
            .join(" | ");
        rows.push([
            "Order detail", "", "", order.orderNumber, order.username, order.customerEmail,
            order.customerPhone, order.createdAt, order.status, order.paymentMethod,
            order.paymentStatus, items, order.couponCode, order.subtotal, order.discount,
            order.deliveryCharge, order.total, addressText
        ]);
    });
    downloadCsv("rajharita-sales-report.csv", reportHeaders, rows);
}

function resetProductForm() {
    document.getElementById("product-form").reset();
    document.getElementById("product-id").value = "";
    document.getElementById("product-threshold").value = "5";
    document.getElementById("product-specification-rows").replaceChildren();
    document.getElementById("product-form-title").textContent = "Add product";
    editingImageUrls = [];
    selectedImageFiles = [];
    editingHomeImageUrls = [null, null];
    selectedHomeImageFiles = [null, null];
    document.getElementById("product-home-image-1").value = "";
    document.getElementById("product-home-image-2").value = "";
    document.getElementById("product-home-featured").checked = false;
    renderProductImagePreview();
    renderHomeImagePreviews();
}

function renderProductImagePreview() {
    const preview = document.getElementById("product-image-preview");
    preview.replaceChildren();
    editingImageUrls.forEach((url, index) => {
        const item = document.createElement("div");
        item.className = "product-image-preview-item";
        const image = document.createElement("img");
        image.src = url;
        image.alt = `Product image ${index + 1}`;
        const remove = document.createElement("button");
        remove.type = "button";
        remove.textContent = "×";
        remove.setAttribute("aria-label", `Remove product image ${index + 1}`);
        remove.addEventListener("click", () => {
            editingImageUrls.splice(index, 1);
            renderProductImagePreview();
        });
        item.append(image, remove);
        preview.append(item);
    });
    selectedImageFiles.forEach((file, index) => {
        const item = document.createElement("div");
        item.className = "product-image-preview-item";
        const image = document.createElement("img");
        const imageUrl = URL.createObjectURL(file);
        image.src = imageUrl;
        image.alt = file.name;
        const revokeImageUrl = () => URL.revokeObjectURL(imageUrl);
        image.addEventListener("load", revokeImageUrl, { once: true });
        image.addEventListener("error", revokeImageUrl, { once: true });
        const remove = document.createElement("button");
        remove.type = "button";
        remove.textContent = "×";
        remove.setAttribute("aria-label", `Remove selected image ${file.name}`);
        remove.addEventListener("click", () => {
            selectedImageFiles.splice(index, 1);
            document.getElementById("product-image").value = "";
            renderProductImagePreview();
        });
        item.append(image, remove);
        preview.append(item);
    });
    if (!editingImageUrls.length && !selectedImageFiles.length) {
        preview.textContent = "No product images selected.";
    }
}

function renderHomeImagePreviews() {
    for (let index = 0; index < 2; index += 1) {
        const preview = document.getElementById(`product-home-image-preview-${index + 1}`);
        const file = selectedHomeImageFiles[index];
        const url = file ? URL.createObjectURL(file) : editingHomeImageUrls[index];
        preview.replaceChildren();
        if (!url) {
            preview.textContent = index === 0 ? "No main photo selected." : "No hover photo selected.";
            continue;
        }

        const image = document.createElement("img");
        image.src = url;
        image.alt = index === 0 ? "Main home card photo preview" : "Hover home card photo preview";
        const remove = document.createElement("button");
        remove.type = "button";
        remove.textContent = "Remove photo";
        remove.addEventListener("click", () => {
            editingHomeImageUrls[index] = null;
            selectedHomeImageFiles[index] = null;
            document.getElementById(`product-home-image-${index + 1}`).value = "";
            renderHomeImagePreviews();
        });
        preview.append(image);
        preview.append(remove);
        if (file) {
            image.addEventListener("load", () => URL.revokeObjectURL(url), { once: true });
            image.addEventListener("error", () => URL.revokeObjectURL(url), { once: true });
        }
    }
}

renderProductImagePreview();
renderHomeImagePreviews();

document.getElementById("product-image").addEventListener("change", event => {
    selectedImageFiles = Array.from(event.currentTarget.files);
    renderProductImagePreview();
});

for (let index = 0; index < 2; index += 1) {
    document.getElementById(`product-home-image-${index + 1}`).addEventListener("change", event => {
        selectedHomeImageFiles[index] = event.currentTarget.files[0] || null;
        renderHomeImagePreviews();
    });
}

function addProductSpecification(name = "", value = "", valueType = "string") {
    const row = document.createElement("div");
    row.className = "specification-row";
    row.dataset.valueType = valueType;

    const nameInput = document.createElement("input");
    nameInput.type = "text";
    nameInput.className = "specification-name";
    nameInput.placeholder = "Specification name";
    nameInput.setAttribute("aria-label", "Specification name");
    nameInput.value = name;
    nameInput.maxLength = 100;

    const valueInput = document.createElement("input");
    valueInput.type = "text";
    valueInput.className = "specification-value";
    valueInput.placeholder = "Value";
    valueInput.setAttribute("aria-label", name ? `${name} value` : "Specification value");
    valueInput.value = typeof value === "string" ? value : JSON.stringify(value);

    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.className = "remove-specification";
    removeButton.textContent = "Remove";
    removeButton.setAttribute("aria-label", name ? `Remove ${name} specification` : "Remove specification");
    removeButton.addEventListener("click", () => row.remove());

    nameInput.addEventListener("input", () => {
        valueInput.setAttribute("aria-label", nameInput.value.trim()
            ? `${nameInput.value.trim()} value`
            : "Specification value");
        removeButton.setAttribute("aria-label", nameInput.value.trim()
            ? `Remove ${nameInput.value.trim()} specification`
            : "Remove specification");
    });

    row.append(nameInput, valueInput, removeButton);
    document.getElementById("product-specification-rows").append(row);
}

function readProductSpecifications() {
    const specifications = Object.create(null);
    for (const row of document.querySelectorAll("#product-specification-rows .specification-row")) {
        const name = row.querySelector(".specification-name").value.trim();
        const value = row.querySelector(".specification-value").value.trim();
        if (!name && !value) continue;
        if (!name) throw new Error("Enter a name for each product specification.");
        if (Object.hasOwn(specifications, name)) {
            throw new Error(`The specification "${name}" is listed more than once.`);
        }
        const valueType = row.dataset.valueType;
        if (valueType && valueType !== "string") {
            try {
                specifications[name] = JSON.parse(value);
            } catch {
                throw new Error(`The value for "${name}" must remain valid ${valueType} data.`);
            }
        } else {
            specifications[name] = value;
        }
    }
    return specifications;
}

async function uploadProductImage(file) {
    const formData = new FormData();
    formData.append("file", file);
    let response;
    try {
        response = await fetch("/api/admin/products/images", {
            method: "POST",
            headers: { "X-XSRF-TOKEN": csrfToken },
            body: formData
        });
    } catch (error) {
        if (error instanceof TypeError) {
            throw new Error(`Could not upload "${file.name}". Check your connection and ensure the file is no larger than 5 MB.`);
        }
        throw error;
    }
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.error || `Upload of "${file.name}" failed (HTTP ${response.status}).`);
    return result.url;
}

document.querySelectorAll(".filter").forEach(button => {
    button.addEventListener("click", () => {
        document.querySelectorAll(".filter").forEach(item => item.classList.remove("active"));
        button.classList.add("active");
        currentFilter = button.dataset.filter;
        renderUsers();
    });
});

document.getElementById("user-search").addEventListener("input", renderUsers);
document.getElementById("order-number-search").addEventListener("input", renderOrders);
document.getElementById("order-search").addEventListener("input", renderOrders);
document.getElementById("orders-from-date").addEventListener("change", renderOrders);
document.getElementById("orders-to-date").addEventListener("change", renderOrders);
document.getElementById("clear-order-filters").addEventListener("click", () => {
    document.getElementById("order-number-search").value = "";
    document.getElementById("order-search").value = "";
    document.getElementById("orders-from-date").value = "";
    document.getElementById("orders-to-date").value = "";
    document.getElementById("order-status-filter").value = "all";
    renderOrders();
});
document.getElementById("order-status-filter").addEventListener("change", renderOrders);

usersBody.addEventListener("click", async event => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const action = button.dataset.action;
    try {
        if (action === "orders") {
            const user = users.find(item => item.id === button.dataset.id);
            if (!user) throw new Error("User not found in the current list.");
            const history = await api(`/api/admin/users/${encodeURIComponent(user.id)}/orders`);
            renderUserOrders(user, history);
            return;
        }
        if (action === "delete" && !window.confirm(
            "Delete this user account? Existing order records will be retained."
        )) return;
        if (action === "delete") {
            await api(`/api/admin/users/${encodeURIComponent(button.dataset.id)}`, { method: "DELETE" });
            await refreshUsers();
            showMessage("User deleted. Existing order history was retained.");
            return;
        }
        await api(`/api/admin/users/${encodeURIComponent(button.dataset.id)}/${action}`, { method: "POST" });
        await refreshUsers();
        showMessage("User account updated.");
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("close-user-orders").addEventListener("click", () =>
    document.getElementById("user-orders-dialog").close());

usersBody.addEventListener("change", async event => {
    const select = event.target.closest("select[data-role]");
    if (!select) return;
    try {
        await api(`/api/admin/users/${encodeURIComponent(select.dataset.role)}/role`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ role: select.value })
        });
        await refreshUsers();
        showMessage("User role updated.");
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.querySelectorAll(".section-tab").forEach(button => {
    button.addEventListener("click", async () => {
        document.querySelectorAll(".section-tab").forEach(tab => tab.classList.remove("active"));
        button.classList.add("active");
        document.querySelectorAll(".managed-panel").forEach(panel => {
            panel.hidden = panel.id !== button.dataset.panel;
        });
        try {
            if (button.dataset.panel === "products-panel") {
                await loadCategories();
                await loadProducts();
            } else if (button.dataset.panel === "customer-ratings-panel") {
                await loadCustomerRatings();
            } else if (button.dataset.panel === "categories-panel") {
                await loadCategories();
            } else if (button.dataset.panel === "inventory-panel") {
                await loadInventory();
            } else if (button.dataset.panel === "orders-panel") {
                await loadOrders();
            } else if (button.dataset.panel === "investment-inquiries-panel") {
                await loadInquiries("investments");
            } else if (button.dataset.panel === "b2b-inquiries-panel") {
                await loadInquiries("b2b");
            } else if (button.dataset.panel === "farmer-inquiries-panel") {
                await loadInquiries("farmers");
            } else if (button.dataset.panel === "odop-inquiries-panel") {
                await loadInquiries("odop");
            } else if (button.dataset.panel === "feedback-panel") {
                await loadWebsiteFeedback();
            } else if (button.dataset.panel === "support-requests-panel") {
                await loadSupportRequests();
            } else if (button.dataset.panel === "coupons-panel") {
                await loadCoupons();
            } else if (button.dataset.panel === "settings-panel") {
                await loadSettings();
            } else if (button.dataset.panel === "invoice-panel") {
                await loadInvoiceTemplate();
            } else if (button.dataset.panel === "showcase-panel") {
                await loadShowcaseImages();
                await loadAboutUs();
                await loadProducts();
                renderHomeFeaturedProducts();
            } else if (button.dataset.panel === "reports-panel") {
                await loadReport();
            }
        } catch (error) {
            showMessage(error.message, "error");
        }
    });
});

document.querySelectorAll("[data-add-invoice-section]").forEach(button => {
    button.addEventListener("click", () => addInvoiceSection(button.dataset.addInvoiceSection));
});

document.getElementById("load-invoice-template").addEventListener("click", () =>
    loadInvoiceTemplate().catch(error => showMessage(error.message, "error")));

const invoiceSections = document.getElementById("invoice-sections");
invoiceSections.addEventListener("change", event => {
    const select = event.target.closest("[data-invoice-type]");
    if (!select) return;
    const previous = readInvoiceSection(select.closest(".invoice-section-editor"));
    const replacement = newInvoiceSection(select.value);
    replacement.id = previous.id;
    replacement.title = previous.title || replacement.title;
    replacement.enabled = previous.enabled;
    replaceInvoiceSection(select.closest(".invoice-section-editor"), replacement);
});

invoiceSections.addEventListener("click", event => {
    const sectionRow = event.target.closest(".invoice-section-editor");
    if (!sectionRow) return;
    if (event.target.closest("[data-remove-invoice-section]")) {
        sectionRow.remove();
        return;
    }
    const section = readInvoiceSection(sectionRow);
    if (event.target.closest("[data-add-invoice-column]")) {
        if (section.columns.length >= 8) {
            showMessage("A table can contain up to 8 columns.", "error");
            return;
        }
        if (section.type === "ITEMS") {
            section.columns.push({ title: `Column ${section.columns.length + 1}`, field: "name" });
        } else {
            section.columns.push({ title: `Column ${section.columns.length + 1}`, field: "custom" });
            section.rows.forEach(row => row.cells.push(""));
        }
        replaceInvoiceSection(sectionRow, section);
    } else if (event.target.closest("[data-add-invoice-row]")) {
        if (section.rows.length >= 30) {
            showMessage("A custom table can contain up to 30 rows.", "error");
            return;
        }
        section.rows.push({ cells: section.columns.map(() => "") });
        replaceInvoiceSection(sectionRow, section);
    } else if (event.target.closest("[data-remove-invoice-column]")) {
        if (section.columns.length <= 1) {
            showMessage("A table must contain at least one column.", "error");
            return;
        }
        const index = Number(event.target.closest("[data-remove-invoice-column]").dataset.removeInvoiceColumn);
        section.columns.splice(index, 1);
        if (section.type === "CUSTOM_TABLE") {
            section.rows.forEach(row => row.cells.splice(index, 1));
        }
        replaceInvoiceSection(sectionRow, section);
    } else if (event.target.closest("[data-remove-invoice-row]")) {
        const index = Number(event.target.closest("[data-remove-invoice-row]").dataset.removeInvoiceRow);
        section.rows.splice(index, 1);
        replaceInvoiceSection(sectionRow, section);
    }
});

invoiceSections.addEventListener("dragstart", event => {
    const section = event.target.closest(".invoice-section-editor");
    if (!section) return;
    event.dataTransfer.setData("text/plain", section.dataset.sectionId);
    event.dataTransfer.effectAllowed = "move";
});
invoiceSections.addEventListener("dragover", event => {
    if (event.target.closest(".invoice-section-editor")) event.preventDefault();
});
invoiceSections.addEventListener("drop", event => {
    const target = event.target.closest(".invoice-section-editor");
    if (!target) return;
    event.preventDefault();
    const dragged = [...invoiceSections.querySelectorAll(".invoice-section-editor")]
        .find(section => section.dataset.sectionId === event.dataTransfer.getData("text/plain"));
    if (dragged && dragged !== target) invoiceSections.insertBefore(dragged, target);
});

document.getElementById("invoice-template-form").addEventListener("submit", async event => {
    event.preventDefault();
    const button = document.getElementById("save-invoice-template");
    button.disabled = true;
    try {
        const sections = [...invoiceSections.querySelectorAll(".invoice-section-editor")].map(readInvoiceSection);
        if (sections.length > 20) throw new Error("An invoice can contain up to 20 sections.");
        await api("/api/admin/invoice-template", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                businessName: document.getElementById("invoice-business-name").value.trim(),
                invoiceTitle: document.getElementById("invoice-title").value.trim(),
                subtitle: document.getElementById("invoice-subtitle").value.trim(),
                footer: document.getElementById("invoice-footer").value.trim(),
                sections
            })
        });
        await loadInvoiceTemplate();
        showMessage("Invoice design saved. New invoice downloads will use this layout.");
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        button.disabled = false;
    }
});

document.getElementById("product-form").addEventListener("submit", async event => {
    event.preventDefault();
    try {
        if ((selectedHomeImageFiles[1] || editingHomeImageUrls[1])
                && !(selectedHomeImageFiles[0] || editingHomeImageUrls[0])) {
            showMessage("Choose a main photo before adding a hover photo.", "error");
            return;
        }
        const specifications = readProductSpecifications();
        const uploadedImageUrls = await Promise.all(selectedImageFiles.map(uploadProductImage));
        const homeImageUrls = await Promise.all(editingHomeImageUrls.map(async (url, index) => {
            const file = selectedHomeImageFiles[index];
            return file ? uploadProductImage(file) : url;
        }));
        const id = document.getElementById("product-id").value;
        const product = {
            name: document.getElementById("product-name").value.trim(),
            description: document.getElementById("product-description").value.trim(),
            category: document.getElementById("product-category").value,
            brand: document.getElementById("product-brand").value.trim(),
            price: Number(document.getElementById("product-price").value),
            weightKg: Number(document.getElementById("product-weight").value),
            stock: Number(document.getElementById("product-stock").value),
            lowStockThreshold: Number(document.getElementById("product-threshold").value),
            specifications,
            imageUrls: [...editingImageUrls, ...uploadedImageUrls],
            homeImageUrls,
            homeFeatured: document.getElementById("product-home-featured").checked,
            published: document.getElementById("product-published").checked
        };
        await api(id ? `/api/admin/products/${encodeURIComponent(id)}` : "/api/admin/products", {
            method: id ? "PUT" : "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(product)
        });
        resetProductForm();
        await loadProducts();
        showMessage("Product saved.");
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("products-body").addEventListener("click", async event => {
    const edit = event.target.closest("button[data-product-edit]");
    const publish = event.target.closest("button[data-product-publish]");
    const remove = event.target.closest("button[data-product-delete]");
    if (!edit && !publish && !remove) return;
    try {
        if (edit) {
            const product = products.find(item => item.id === edit.dataset.productEdit);
            if (!product) return;
            document.getElementById("product-id").value = product.id;
            document.getElementById("product-name").value = product.name;
            document.getElementById("product-description").value = product.description || "";
            document.getElementById("product-category").value = product.category;
            document.getElementById("product-brand").value = product.brand || "";
            document.getElementById("product-price").value = product.price;
            document.getElementById("product-weight").value = product.weightKg || 0;
            document.getElementById("product-stock").value = product.stock;
            document.getElementById("product-threshold").value = product.lowStockThreshold;
            const specificationRows = document.getElementById("product-specification-rows");
            specificationRows.replaceChildren();
            Object.entries(product.specifications || {}).forEach(([name, value]) => {
                addProductSpecification(name, value, value === null ? "null" : typeof value);
            });
            document.getElementById("product-published").checked = product.published;
            editingHomeImageUrls = [...(product.homeImageUrls || []), null, null].slice(0, 2);
            selectedHomeImageFiles = [null, null];
            document.getElementById("product-home-image-1").value = "";
            document.getElementById("product-home-image-2").value = "";
            document.getElementById("product-home-featured").checked = Boolean(product.homeFeatured);
            renderHomeImagePreviews();
            editingImageUrls = product.imageUrls || [];
            selectedImageFiles = [];
            document.getElementById("product-image").value = "";
            renderProductImagePreview();
            document.getElementById("product-form-title").textContent = "Edit product";
            document.getElementById("product-form").scrollIntoView({ behavior: "smooth" });
        } else if (publish) {
            await api(`/api/admin/products/${encodeURIComponent(publish.dataset.productPublish)}/published`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ published: publish.dataset.published === "true" })
            });
            await loadProducts();
            showMessage("Product visibility updated.");
        } else {
            await api(`/api/admin/products/${encodeURIComponent(remove.dataset.productDelete)}`, { method: "DELETE" });
            await loadProducts();
            showMessage("Product archived.");
        }
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("add-product-specification").addEventListener("click", () => {
    addProductSpecification();
});

document.getElementById("product-cancel").addEventListener("click", resetProductForm);

document.getElementById("category-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
        await api("/api/admin/categories", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                name: document.getElementById("category-name").value.trim(),
                description: document.getElementById("category-description").value.trim()
            })
        });
        form.reset();
        await loadCategories();
        showMessage("Category created.");
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("refresh-showcase").addEventListener("click", () => {
    loadShowcaseImages().catch(error => showMessage(error.message, "error"));
});

document.getElementById("about-us-form").addEventListener("submit", async event => {
    event.preventDefault();
    const button = document.getElementById("save-about-us");
    button.disabled = true;
    try {
        const blockRows = [...document.querySelectorAll("#about-us-blocks .about-us-block-row")];
        if (blockRows.length > 10) {
            throw new Error("The About Us section can contain up to 10 image-and-text blocks.");
        }
        const blocks = [];
        for (const [index, row] of blockRows.entries()) {
            const file = row.querySelector('input[type="file"]').files[0];
            const imageUrl = file ? await uploadAboutUsImage(file) : row.dataset.imageUrl;
            const textInputs = row.querySelectorAll('input[type="text"]');
            const period = textInputs[0].value.trim();
            const heading = textInputs[1].value.trim();
            const subheading = textInputs[2].value.trim();
            const content = row.querySelector("textarea").value.trim();
            if (!imageUrl || !content) {
                throw new Error(`About Us block ${index + 1} needs both a photo and related information.`);
            }
            blocks.push({ imageUrl, period, heading, subheading, content });
        }
        const result = await api("/api/admin/showcase/about-us", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                title: document.getElementById("about-us-title").value.trim(),
                summary: document.getElementById("about-us-summary").value.trim(),
                content: document.getElementById("about-us-content").value.trim(),
                blocks
            })
        });
        document.getElementById("about-us-title").value = result.title;
        document.getElementById("about-us-summary").value = result.summary;
        document.getElementById("about-us-content").value = result.content;
        await loadAboutUs();
        showMessage("Home page About Us section saved.");
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        button.disabled = false;
    }
});

document.getElementById("add-about-us-block").addEventListener("click", () => addAboutUsBlock());

document.getElementById("about-us-blocks").addEventListener("change", event => {
    const input = event.target.closest('input[type="file"]');
    if (!input) return;
    const file = input.files[0];
    if (!file) return;
    const preview = input.closest(".about-us-block-image").querySelector(".about-us-block-preview");
    preview.replaceChildren();
    const image = document.createElement("img");
    const previewUrl = URL.createObjectURL(file);
    image.src = previewUrl;
    image.alt = file.name;
    image.addEventListener("load", () => URL.revokeObjectURL(previewUrl), { once: true });
    image.addEventListener("error", () => URL.revokeObjectURL(previewUrl), { once: true });
    preview.append(image);
});

document.getElementById("about-us-blocks").addEventListener("click", event => {
    const button = event.target.closest("button.reject");
    if (!button) return;
    button.closest(".about-us-block-row").remove();
    updateAboutUsBlockControls();
});

document.getElementById("home-featured-product").addEventListener("change", event => {
    const product = products.find(item => item.id === event.currentTarget.value);
    if (product) {
        selectHomeFeaturedProduct(product);
        return;
    }
    document.getElementById("home-featured-enabled").checked = false;
    renderHomeFeaturedPreview("home-featured-main-preview", "", "");
    renderHomeFeaturedPreview("home-featured-hover-preview", "", "");
});

for (const [inputId, previewId, alt] of [
    ["home-featured-main-image", "home-featured-main-preview", "Main photo preview"],
    ["home-featured-hover-image", "home-featured-hover-preview", "Hover photo preview"]
]) {
    document.getElementById(inputId).addEventListener("change", event => {
        const file = event.currentTarget.files[0];
        if (!file) return;
        const previewUrl = URL.createObjectURL(file);
        renderHomeFeaturedPreview(previewId, previewUrl, alt);
        const image = document.querySelector(`#${previewId} img`);
        image.addEventListener("load", () => URL.revokeObjectURL(previewUrl), { once: true });
        image.addEventListener("error", () => URL.revokeObjectURL(previewUrl), { once: true });
    });
}

document.getElementById("home-featured-form").addEventListener("submit", async event => {
    event.preventDefault();
    const productId = document.getElementById("home-featured-product").value;
    const product = products.find(item => item.id === productId);
    if (!product) {
        showMessage("Select a product to add to Explore products.", "error");
        return;
    }
    const mainFile = document.getElementById("home-featured-main-image").files[0];
    const hoverFile = document.getElementById("home-featured-hover-image").files[0];
    const currentImages = homeProductImages(product);
    if (hoverFile && !mainFile && !currentImages[0]) {
        showMessage("Upload a main photo before adding a hover photo.", "error");
        return;
    }
    const enabled = document.getElementById("home-featured-enabled").checked;
    if (enabled && !mainFile && !currentImages[0]) {
        showMessage("Add a main photo before showing this product on the home page.", "error");
        return;
    }

    const saveButton = document.getElementById("save-home-featured-product");
    saveButton.disabled = true;
    try {
        const mainImage = mainFile ? await uploadProductImage(mainFile) : currentImages[0];
        const hoverImage = hoverFile ? await uploadProductImage(hoverFile) : currentImages[1];
        const homeImageUrls = [mainImage, hoverImage].filter(Boolean);
        await api(`/api/admin/products/${encodeURIComponent(product.id)}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                ...product,
                homeImageUrls,
                homeFeatured: enabled
            })
        });
        await loadProducts();
        renderHomeFeaturedProducts();
        document.getElementById("home-featured-product").value = "";
        document.getElementById("home-featured-form").reset();
        renderHomeFeaturedPreview("home-featured-main-preview", "", "");
        renderHomeFeaturedPreview("home-featured-hover-preview", "", "");
        document.getElementById("home-featured-main-image").value = "";
        document.getElementById("home-featured-hover-image").value = "";
        showMessage(enabled
            ? product.published
                ? `${product.name} added to the home page Explore products section. Select another product to add another card.`
                : `${product.name} card was saved, but it is unpublished and will not appear to customers until you publish it. Select another product to add another card.`
            : `${product.name} home page card updated.`);
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        saveButton.disabled = false;
    }
});

document.getElementById("home-featured-list").addEventListener("click", async event => {
    const editButton = event.target.closest("button[data-home-featured-edit]");
    const removeButton = event.target.closest("button[data-home-featured-remove]");
    const productId = editButton?.dataset.homeFeaturedEdit || removeButton?.dataset.homeFeaturedRemove;
    if (!productId) return;
    const product = products.find(item => item.id === productId);
    if (!product) {
        showMessage("This product could not be found. Refresh the products list and try again.", "error");
        return;
    }
    if (editButton) {
        document.getElementById("home-featured-product").value = product.id;
        selectHomeFeaturedProduct(product);
        document.getElementById("home-featured-form").scrollIntoView({ behavior: "smooth", block: "center" });
        return;
    }
    try {
        await api(`/api/admin/products/${encodeURIComponent(product.id)}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ ...product, homeFeatured: false })
        });
        await loadProducts();
        renderHomeFeaturedProducts();
        showMessage("Product removed from the home page Explore products section.");
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("showcase-interval-form").addEventListener("submit", async event => {
    event.preventDefault();
    const input = document.getElementById("showcase-interval");
    const intervalSeconds = Number(input.value);
    if (!Number.isInteger(intervalSeconds) || intervalSeconds < 1 || intervalSeconds > 60) {
        showMessage("Enter a whole number between 1 and 60 seconds.", "error");
        return;
    }

    const button = document.getElementById("save-showcase-interval");
    button.disabled = true;
    try {
        const result = await api("/api/admin/showcase/interval", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ intervalSeconds })
        });
        input.value = result.intervalSeconds;
        showMessage(`Photo change interval saved to ${result.intervalSeconds} second(s).`);
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        button.disabled = false;
    }
});

document.getElementById("showcase-form").addEventListener("submit", async event => {
    event.preventDefault();
    const filesInput = document.getElementById("showcase-files");
    const files = Array.from(filesInput.files);
    if (!files.length) {
        showMessage("Choose at least one photo to upload.", "error");
        return;
    }
    if (showcaseImages.length + files.length > showcaseMaximumImages) {
        showMessage(`You can upload ${showcaseMaximumImages - showcaseImages.length} more photo(s).`, "error");
        return;
    }

    let uploadedCount = 0;
    const uploadButton = document.getElementById("upload-showcase-photos");
    uploadButton.disabled = true;
    try {
        for (const file of files) {
            const formData = new FormData();
            formData.append("file", file);
            const result = await api("/api/admin/showcase/images", {
                method: "POST",
                body: formData
            });
            showcaseImages = result.images;
            showcaseMaximumImages = result.maximumImages;
            uploadedCount += 1;
            renderShowcaseImages();
        }
        filesInput.value = "";
        showMessage(`${uploadedCount} photo(s) uploaded.`);
    } catch (error) {
        try {
            await loadShowcaseImages();
        } catch (refreshError) {
            showMessage(`${error.message} The gallery could not be refreshed: ${refreshError.message}`, "error");
            return;
        }
        showMessage(uploadedCount
            ? `${uploadedCount} photo(s) uploaded before the error: ${error.message}`
            : error.message, "error");
    } finally {
        uploadButton.disabled = showcaseImages.length >= showcaseMaximumImages;
    }
});

document.getElementById("showcase-images").addEventListener("click", async event => {
    const button = event.target.closest("button[data-showcase-delete]");
    if (!button) return;
    if (!window.confirm("Remove this photo from the shared home page showcase?")) return;
    button.disabled = true;
    try {
        const filename = encodeURIComponent(button.dataset.showcaseDelete);
        const result = await api(`/api/admin/showcase/images/${filename}`, { method: "DELETE" });
        showcaseImages = result.images;
        showcaseMaximumImages = result.maximumImages;
        renderShowcaseImages();
        showMessage("Photo removed from the showcase.");
    } catch (error) {
        try {
            await loadShowcaseImages();
        } catch (refreshError) {
            button.disabled = false;
            showMessage(`${error.message} The gallery could not be refreshed: ${refreshError.message}`, "error");
            return;
        }
        showMessage(error.message, "error");
    }
});

document.getElementById("categories-body").addEventListener("click", async event => {
    const button = event.target.closest("button[data-category-active]");
    if (!button) return;
    try {
        await api(`/api/admin/categories/${encodeURIComponent(button.dataset.categoryActive)}/active`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ active: button.dataset.active === "true" })
        });
        await loadCategories();
        showMessage("Category status updated.");
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("refresh-inventory").addEventListener("click", () => {
    loadInventory().catch(error => showMessage(error.message, "error"));
});

Object.entries(inquiryViews).forEach(([type, view]) => {
    document.getElementById(view.searchId).addEventListener("input", () => renderInquiries(type));
});
document.getElementById("feedback-search").addEventListener("input", renderWebsiteFeedback);
document.getElementById("support-search").addEventListener("input", renderSupportRequests);
document.getElementById("support-from-date").addEventListener("change", renderSupportRequests);
document.getElementById("support-to-date").addEventListener("change", renderSupportRequests);
document.getElementById("clear-support-filters").addEventListener("click", () => {
    document.getElementById("support-search").value = "";
    document.getElementById("support-from-date").value = "";
    document.getElementById("support-to-date").value = "";
    renderSupportRequests();
});
document.querySelectorAll("[data-engagement-refresh]").forEach(button => {
    button.addEventListener("click", () => {
        const load = button.dataset.engagementRefresh === "feedback"
            ? loadWebsiteFeedback
            : loadSupportRequests;
        load().catch(error => showMessage(error.message, "error"));
    });
});
document.getElementById("support-body").addEventListener("click", async event => {
    const button = event.target.closest("button[data-support-save]");
    if (!button) return;
    const row = button.closest("tr[data-support-request]");
    if (!row) return;
    try {
        await api(`/api/admin/support-requests/${encodeURIComponent(row.dataset.supportRequest)}`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                status: row.querySelector("[data-support-status]").value,
                adminResponse: row.querySelector("[data-support-response]").value
            })
        });
        await loadSupportRequests();
        showMessage("Support request updated. The customer can see your reply in their account.");
    } catch (error) {
        showMessage(error.message, "error");
    }
});
document.querySelectorAll("[data-inquiry-refresh]").forEach(button => {
    button.addEventListener("click", () => {
        loadInquiries(button.dataset.inquiryRefresh).catch(error => showMessage(error.message, "error"));
    });
});
document.querySelectorAll("[data-inquiry-download]").forEach(button => {
    button.addEventListener("click", () => downloadInquiries(button.dataset.inquiryDownload));
});

document.getElementById("orders-body").addEventListener("click", async event => {
    const row = event.target.closest("tr[data-order]");
    if (!row) return;
    const refundButton = event.target.closest("button[data-refund]");
    const saveButton = event.target.closest("button[data-order-save]");
    try {
        if (refundButton) {
            const endpoint = refundButton.dataset.method === "COD" ? "confirm-manual-refund" : "refund";
            await api(`/api/admin/orders/${encodeURIComponent(refundButton.dataset.refund)}/${endpoint}`, { method: "POST" });
            showMessage("Refund status updated.");
            await loadOrders();
        } else if (saveButton) {
            await api(`/api/admin/orders/${encodeURIComponent(row.dataset.order)}/status`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    status: row.querySelector("[data-order-status]").value,
                    trackingNumber: row.querySelector("[data-tracking]").value.trim()
                })
            });
            showMessage("Order updated.");
            await loadOrders();
        }
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("coupon-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
        await api("/api/admin/coupons", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                code: document.getElementById("coupon-code").value.trim(),
                title: document.getElementById("coupon-title").value.trim(),
                description: document.getElementById("coupon-description").value.trim(),
                targetRating: document.getElementById("coupon-target-rating").value === ""
                    ? null
                    : Number(document.getElementById("coupon-target-rating").value),
                discountType: document.getElementById("coupon-type").value,
                discountValue: Number(document.getElementById("coupon-value").value),
                minimumOrder: Number(document.getElementById("coupon-minimum").value || 0),
                startsAt: document.getElementById("coupon-start").value || null,
                expiresAt: document.getElementById("coupon-expiry").value || null,
                usageLimit: Number(document.getElementById("coupon-limit").value || 0),
                active: document.getElementById("coupon-active").checked
            })
        });
        form.reset();
        document.getElementById("coupon-active").checked = true;
        document.getElementById("coupon-limit").value = "0";
        await loadCoupons();
        showMessage("Coupon created.");
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("coupons-body").addEventListener("click", async event => {
    const button = event.target.closest("button[data-coupon]");
    const deleteButton = event.target.closest("button[data-coupon-delete]");
    if (!button && !deleteButton) return;
    if (deleteButton && !window.confirm(
        `Delete coupon "${deleteButton.dataset.couponCode}"? This cannot be undone.`
    )) return;
    try {
        if (deleteButton) {
            await api(`/api/admin/coupons/${encodeURIComponent(deleteButton.dataset.couponDelete)}`, {
                method: "DELETE"
            });
        } else {
            await api(`/api/admin/coupons/${encodeURIComponent(button.dataset.coupon)}/active`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ active: button.dataset.active === "true" })
            });
        }
        await loadCoupons();
        showMessage(deleteButton ? "Coupon deleted." : "Coupon status updated.");
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("settings-form").addEventListener("submit", async event => {
    event.preventDefault();
    try {
        await api("/api/admin/settings", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                deliveryCharge: Number(document.getElementById("delivery-charge").value),
                deliveryChargePerKg: Number(document.getElementById("delivery-charge-per-kg").value),
                freeDeliveryThreshold: Number(document.getElementById("free-delivery").value),
                returnWindowDays: Number(document.getElementById("return-window").value),
                cashOnDeliveryEnabled: document.getElementById("cod-enabled").checked,
                termsAndConditions: document.getElementById("terms-policy").value,
                privacyPolicy: document.getElementById("privacy-policy").value,
                returnPolicy: document.getElementById("return-policy").value
            })
        });
        showMessage("Store settings saved.");
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("refresh-orders").addEventListener("click", () =>
    loadOrders().catch(error => showMessage(error.message, "error")));
document.getElementById("refresh-report").addEventListener("click", () =>
    loadReport().catch(error => {
        document.getElementById("sales-period-message").textContent = error.message;
        document.getElementById("sales-period-message").classList.add("error");
        showMessage(error.message, "error");
    }));
document.getElementById("download-sales-report").addEventListener("click", () =>
    downloadSalesReport().catch(error => showMessage(error.message, "error")));
document.getElementById("sales-period-submit").addEventListener("click", () =>
    loadReport().catch(error => {
        document.getElementById("sales-period-message").textContent = error.message;
        document.getElementById("sales-period-message").classList.add("error");
        showMessage(error.message, "error");
    }));
document.getElementById("sales-period-clear").addEventListener("click", () => {
    document.getElementById("sales-from-date").value = "";
    document.getElementById("sales-to-date").value = "";
    loadReport().catch(error => showMessage(error.message, "error"));
});

document.getElementById("refresh-button").addEventListener("click", refreshUsers);
document.getElementById("download-all-customer-ratings").addEventListener("click", () =>
    downloadCustomerRatings(customerRatings, "rajharita-all-customer-details.csv"));
document.getElementById("download-filtered-customer-ratings").addEventListener("click", () => {
    const selectedRating = document.getElementById("customer-rating-filter").value;
    const selected = selectedRating === "all"
        ? customerRatings
        : customerRatings.filter(customer => customer.rating === Number(selectedRating));
    const ratingName = selectedRating === "all" ? "all-ratings" : `${selectedRating}-stars`;
    downloadCustomerRatings(selected, `rajharita-customers-${ratingName}.csv`);
});
document.getElementById("refresh-customer-ratings").addEventListener("click", () =>
    loadCustomerRatings().catch(error => showMessage(error.message, "error")));
document.getElementById("customer-rating-search").addEventListener("input", renderCustomerRatings);
document.getElementById("customer-rating-filter").addEventListener("change", renderCustomerRatings);
document.getElementById("invitation-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
        const email = document.getElementById("invitation-email").value.trim();
        const result = await api("/api/admin/invitations", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email })
        });
        form.reset();
        showMessage(result.message);
    } catch (error) {
        showMessage(error.message, "error");
    }
});
document.getElementById("logout-button").addEventListener("click", async () => {
    try {
        await api("/api/auth/logout", { method: "POST" });
        window.location.href = "/admin";
    } catch (error) {
        showMessage(error.message, "error");
    }
});

loadCsrfToken().then(refreshUsers).catch(error => showMessage(error.message, "error"));
