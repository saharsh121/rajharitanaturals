const messageBox = document.getElementById("message-box");
let csrfToken = "";
let savedAddresses = [];

function showMessage(text, type = "success") {
    messageBox.textContent = text;
    messageBox.className = `show ${type}`;
    messageBox.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

async function request(path, method = "GET", payload) {
    const response = await fetch(`/api/auth/${path}`, {
        method,
        headers: {
            ...(payload ? { "Content-Type": "application/json" } : {}),
            ...(method === "GET" ? {} : { "X-XSRF-TOKEN": csrfToken })
        },
        ...(payload ? { body: JSON.stringify(payload) } : {})
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.error || "Request failed.");
    return result;
}

async function initialize() {
    const csrfResponse = await fetch("/api/auth/csrf");
    if (!csrfResponse.ok) throw new Error("Unable to initialize secure session.");
    csrfToken = (await csrfResponse.json()).token;
    const response = await fetch("/api/auth/me");
    if (response.status === 401 || response.status === 403) {
        window.location.href = "/login";
        return;
    }
    if (!response.ok) throw new Error("Unable to load your account.");
    const user = await response.json();
    document.getElementById("account-identity").textContent = `Signed in as ${user.username}`;
    document.getElementById("loyalty-balance").textContent = String(user.loyaltyCoins || 0);
    document.getElementById("profile-name").value = user.name || "";
    document.getElementById("profile-email").value = user.email || "";
    document.getElementById("profile-contact").value = user.contactNumber || "";
    document.getElementById("profile-gender").value = user.gender || "Other";
    await loadAddresses();
    await loadEngagementHistory();
}

async function loadAddresses() {
    savedAddresses = await request("addresses");
    const list = document.getElementById("address-list");
    if (!savedAddresses.length) {
        list.innerHTML = '<p class="empty-address">No saved addresses yet. Add one below for quicker checkout.</p>';
        return;
    }
    list.innerHTML = savedAddresses.map(address => `<article class="address-card">
        <div><strong>${escapeHtml(address.recipientName)}${address.defaultAddress ? " · Default" : ""}</strong>
        <p>${escapeHtml(address.line1)} ${escapeHtml(address.line2)}</p>
        <p>${escapeHtml(address.city)}, ${escapeHtml(address.state)} ${escapeHtml(address.postalCode)}</p>
        <p>${escapeHtml(address.phone)}</p></div>
        <div class="address-actions">
            <button type="button" data-edit="${escapeHtml(address.id)}">Edit</button>
            <button type="button" data-delete="${escapeHtml(address.id)}">Delete</button>
        </div>
    </article>`).join("");
}

async function loadEngagementHistory() {
    const [feedback, supportRequests] = await Promise.all([
        request("feedback"),
        request("support-requests")
    ]);
    renderFeedbackHistory(feedback);
    renderSupportHistory(supportRequests);
}

function renderFeedbackHistory(feedback) {
    const history = document.getElementById("feedback-history");
    const cards = feedback.map(item => `
        <article class="engagement-item">
            <p class="feedback-stars" aria-label="${escapeHtml(item.rating)} out of 5 stars">${"★".repeat(item.rating)}${"☆".repeat(5 - item.rating)}</p>
            <h3>${escapeHtml(item.title)}</h3>
            <p>${escapeHtml(item.message)}</p>
            <p class="engagement-meta">${formatEngagementDate(item.createdAt)}</p>
        </article>`);
    renderEngagementCarousel(history, cards, "Your feedback and reviews will appear here.", "feedback");
}

function renderSupportHistory(requests) {
    const history = document.getElementById("support-history");
    const cards = requests.map(item => `
        <article class="engagement-item">
            <p class="engagement-meta">${escapeHtml(item.category)} · ${formatEngagementDate(item.createdAt)}</p>
            <h3>${escapeHtml(item.subject)}</h3>
            <p>${escapeHtml(item.message)}</p>
            <span class="support-status">${escapeHtml((item.status || "OPEN").replaceAll("_", " "))}</span>
            ${item.adminResponse ? `<div class="support-response">
                <strong class="support-response-title">Reply from Rajharita Naturals Admin</strong>
                <span class="support-response-sender">Sent by Admin</span>
                <p>${escapeHtml(item.adminResponse)}</p>
            </div>` : ""}
        </article>`);
    renderEngagementCarousel(history, cards, "You have no support requests yet.", "support");
}

function renderEngagementCarousel(container, cards, emptyMessage, name) {
    if (!cards.length) {
        container.innerHTML = `<p class="empty-address">${emptyMessage}</p>`;
        return;
    }
    container.innerHTML = `
        <div class="engagement-carousel-controls">
            <span class="engagement-carousel-count" aria-live="polite"></span>
            <div>
                <button type="button" class="engagement-carousel-button" data-carousel-previous aria-label="Previous ${name}">‹</button>
                <button type="button" class="engagement-carousel-button" data-carousel-next aria-label="Next ${name}">›</button>
            </div>
        </div>
        <div class="engagement-carousel-track" tabindex="0" aria-label="${name} history">
            ${cards.join("")}
        </div>`;
    updateEngagementCarousel(container);
}

function updateEngagementCarousel(container) {
    const track = container.querySelector(".engagement-carousel-track");
    if (!track) return;
    const cards = [...track.querySelectorAll(".engagement-item")];
    const currentIndex = cards.findIndex(card =>
        card.offsetLeft - track.offsetLeft + card.offsetWidth / 2 >= track.scrollLeft + track.clientWidth / 2);
    const current = currentIndex < 0 ? cards.length : currentIndex + 1;
    container.querySelector(".engagement-carousel-count").textContent = `${current} of ${cards.length}`;
    container.querySelector("[data-carousel-previous]").disabled = current <= 1;
    container.querySelector("[data-carousel-next]").disabled = current >= cards.length;
}

function formatEngagementDate(value) {
    if (!value) return "Date unavailable";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "Date unavailable" : date.toLocaleString();
}

function escapeHtml(value) {
    return String(value || "").replace(/[&<>"']/g, character => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[character]);
}

document.getElementById("feedback-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
        await request("feedback", "POST", {
            rating: Number(document.getElementById("feedback-rating").value),
            title: document.getElementById("feedback-title").value.trim(),
            message: document.getElementById("feedback-message").value.trim()
        });
        form.reset();
        await loadEngagementHistory();
        showMessage("Thank you for sharing your feedback.");
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("support-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
        await request("support-requests", "POST", {
            category: document.getElementById("support-category").value,
            subject: document.getElementById("support-subject").value.trim(),
            message: document.getElementById("support-message").value.trim()
        });
        form.reset();
        await loadEngagementHistory();
        showMessage("Your support request was sent. You can follow its status here.");
    } catch (error) {
        showMessage(error.message, "error");
    }
});

["feedback-history", "support-history"].forEach(id => {
    const container = document.getElementById(id);
    container.addEventListener("click", event => {
        const previous = event.target.closest("[data-carousel-previous]");
        const next = event.target.closest("[data-carousel-next]");
        if (!previous && !next) return;
        const track = container.querySelector(".engagement-carousel-track");
        const cards = [...track.querySelectorAll(".engagement-item")];
        const currentIndex = cards.findIndex(card =>
            card.offsetLeft - track.offsetLeft + card.offsetWidth / 2 >= track.scrollLeft + track.clientWidth / 2);
        const targetIndex = Math.max(0, Math.min(cards.length - 1, currentIndex + (next ? 1 : -1)));
        track.scrollTo({
            left: cards[targetIndex].offsetLeft - track.offsetLeft,
            behavior: "smooth"
        });
    });
    container.addEventListener("scroll", () => updateEngagementCarousel(container), true);
});

document.getElementById("profile-form").addEventListener("submit", async event => {
    event.preventDefault();
    try {
        await request("profile", "PUT", {
            name: document.getElementById("profile-name").value.trim(),
            contactNumber: document.getElementById("profile-contact").value.trim(),
            gender: document.getElementById("profile-gender").value
        });
        showMessage("Your details have been saved.");
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("password-form").addEventListener("submit", async event => {
    event.preventDefault();
    try {
        const result = await request("password", "PUT", {
            currentPassword: document.getElementById("current-password").value,
            newPassword: document.getElementById("new-password").value
        });
        event.currentTarget.reset();
        showMessage(result.message);
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("request-password-reset").addEventListener("click", async event => {
    const button = event.currentTarget;
    button.disabled = true;
    try {
        const result = await request("account/password-reset/request", "POST", {});
        document.getElementById("password-reset-form").hidden = false;
        button.textContent = "Send another verification code";
        showMessage(result.message);
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        button.disabled = false;
    }
});

document.getElementById("password-reset-form").addEventListener("submit", async event => {
    event.preventDefault();
    try {
        const result = await request("account/password-reset/complete", "POST", {
            otp: document.getElementById("account-reset-otp").value.trim(),
            newPassword: document.getElementById("account-reset-password").value
        });
        event.currentTarget.reset();
        event.currentTarget.hidden = true;
        document.getElementById("password-form").reset();
        showMessage(result.message);
    } catch (error) {
        showMessage(error.message, "error");
    }
});

const addressList = document.getElementById("address-list");
addressList.addEventListener("click", async event => {
    const edit = event.target.closest("button[data-edit]");
    const remove = event.target.closest("button[data-delete]");
    try {
        if (remove) {
            await request(`addresses/${encodeURIComponent(remove.dataset.delete)}`, "DELETE");
            await loadAddresses();
            showMessage("Address deleted.");
        } else if (edit) {
            const address = savedAddresses.find(item => item.id === edit.dataset.edit);
            if (!address) return;
            document.getElementById("address-id").value = address.id;
            document.getElementById("address-recipient").value = address.recipientName;
            document.getElementById("address-phone").value = address.phone;
            document.getElementById("address-line1").value = address.line1;
            document.getElementById("address-line2").value = address.line2 || "";
            document.getElementById("address-city").value = address.city;
            document.getElementById("address-state").value = address.state;
            document.getElementById("address-postal").value = address.postalCode;
            document.getElementById("address-default").checked = address.defaultAddress;
            document.getElementById("address-editor-title").textContent = "Edit delivery address";
            document.querySelector(".address-editor").open = true;
            document.getElementById("address-recipient").focus();
        }
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("address-form").addEventListener("submit", async event => {
    event.preventDefault();
    const id = document.getElementById("address-id").value;
    const payload = {
        recipientName: document.getElementById("address-recipient").value.trim(),
        phone: document.getElementById("address-phone").value.trim(),
        line1: document.getElementById("address-line1").value.trim(),
        line2: document.getElementById("address-line2").value.trim(),
        city: document.getElementById("address-city").value.trim(),
        state: document.getElementById("address-state").value.trim(),
        postalCode: document.getElementById("address-postal").value.trim(),
        defaultAddress: document.getElementById("address-default").checked
    };
    try {
        await request(id ? `addresses/${encodeURIComponent(id)}` : "addresses",
            id ? "PUT" : "POST", payload);
        event.currentTarget.reset();
        document.getElementById("address-id").value = "";
        document.getElementById("address-editor-title").textContent = "Add a delivery address";
        document.querySelector(".address-editor").open = false;
        await loadAddresses();
        showMessage("Address saved.");
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("address-cancel").addEventListener("click", () => {
    document.getElementById("address-form").reset();
    document.getElementById("address-id").value = "";
    document.getElementById("address-editor-title").textContent = "Add a delivery address";
    document.querySelector(".address-editor").open = false;
});

document.getElementById("delete-account-form").addEventListener("submit", async event => {
    event.preventDefault();
    if (!window.confirm("Permanently delete your account and all associated data? This cannot be undone.")) return;
    try {
        await request("account", "DELETE", {
            currentPassword: document.getElementById("delete-account-password").value
        });
        window.location.href = "/login?accountDeleted";
    } catch (error) {
        showMessage(error.message, "error");
    }
});

document.getElementById("logout-button").addEventListener("click", async () => {
    try {
        await request("logout", "POST", {});
        window.location.href = "/login";
    } catch (error) {
        showMessage(error.message || "Unable to sign out. Please try again.", "error");
    }
});

initialize().catch(error => {
    showMessage(error.message || "Unable to load your account.", "error");
    if (error.message.includes("session")) window.location.href = "/login";
});
