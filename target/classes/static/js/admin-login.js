/* =========================================================
   RAJHARITA NATURALS — Admin Login logic
   ========================================================= */

const ADMIN_API = "/api/admin";

/* ---------- DOM ---------- */
const form = document.getElementById("admin-login-form");
const messageBox = document.getElementById("message-box");
const registrationForm = document.getElementById("admin-registration-form");
const registrationVerifyForm = document.getElementById("admin-registration-verify-form");
let csrfToken = "";
let adminRegistrationId = "";

/* ---------- Message box ---------- */
function showMessage(text, type = "success") {
    messageBox.textContent = text;
    messageBox.className = "message-box show " + type;
}

function hideMessage() {
    messageBox.className = "message-box";
    messageBox.textContent = "";
}

async function loadCsrfToken() {
    const response = await fetch("/api/auth/csrf");
    const data = await response.json();
    csrfToken = data.token;
}

async function postAdminApi(path, body) {
    if (!csrfToken) await loadCsrfToken();
    const response = await fetch(`${ADMIN_API}${path}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrfToken },
        body: JSON.stringify(body)
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.error || "Admin registration request failed.");
    return data;
}

/* =========================================================
   ADMIN LOGIN SUBMIT
   ========================================================= */
form.addEventListener("submit", async (e) => {
    e.preventDefault();
    hideMessage();

    const username = document.getElementById("admin-username").value.trim();
    const password = document.getElementById("admin-password").value;

    if (!username || !password) {
        showMessage("Please enter both username and password", "error");
        return;
    }

    const submitBtn = form.querySelector("button[type='submit']");
    submitBtn.disabled = true;
    submitBtn.textContent = "Signing in…";

    try {
        if (!csrfToken) await loadCsrfToken();
        const res = await fetch(`${ADMIN_API}/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrfToken },
            body: JSON.stringify({ username, password })
        });

        const data = await res.json();

        if (!res.ok) {
            showMessage(data.error || "Invalid admin credentials", "error");
            submitBtn.disabled = false;
            submitBtn.textContent = "Sign In";
            return;
        }

        showMessage(`✅ Welcome, ${data.name || data.username}!`, "success");

        setTimeout(() => {
            window.location.href = "/admin/dashboard";
        }, 1400);

    } catch (err) {
        console.error(err);
        showMessage("Network error. Please try again.", "error");
        submitBtn.disabled = false;
        submitBtn.textContent = "Sign In";
    }
});

registrationForm.addEventListener("submit", async event => {
    event.preventDefault();
    hideMessage();
    const password = document.getElementById("new-admin-password").value;
    if (password !== document.getElementById("new-admin-confirm-password").value) {
        showMessage("The passwords do not match.", "error");
        return;
    }
    const button = registrationForm.querySelector("button[type='submit']");
    button.disabled = true;
    try {
        const result = await postAdminApi("/registration/request", {
            name: document.getElementById("new-admin-name").value.trim(),
            username: document.getElementById("new-admin-username").value.trim(),
            email: document.getElementById("new-admin-email").value.trim(),
            password
        });
        adminRegistrationId = result.registrationId;
        registrationForm.hidden = true;
        registrationVerifyForm.hidden = false;
        document.getElementById("admin-registration-otp").focus();
        showMessage(result.message, "success");
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        button.disabled = false;
    }
});

registrationVerifyForm.addEventListener("submit", async event => {
    event.preventDefault();
    hideMessage();
    const button = registrationVerifyForm.querySelector("button[type='submit']");
    button.disabled = true;
    try {
        const result = await postAdminApi("/registration/verify", {
            registrationId: adminRegistrationId,
            otp: document.getElementById("admin-registration-otp").value.trim()
        });
        registrationVerifyForm.reset();
        registrationForm.reset();
        registrationForm.hidden = false;
        registrationVerifyForm.hidden = true;
        document.querySelector(".admin-registration").open = false;
        adminRegistrationId = "";
        showMessage(`${result.message} Username: ${result.username}`, "success");
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        button.disabled = false;
    }
});

document.getElementById("admin-registration-cancel").addEventListener("click", () => {
    adminRegistrationId = "";
    registrationForm.reset();
    registrationVerifyForm.reset();
    registrationForm.hidden = false;
    registrationVerifyForm.hidden = true;
    hideMessage();
});

loadCsrfToken().catch(error => showMessage(error.message || "Unable to initialize secure login.", "error"));