/* =========================================================
   RAJHARITA NATURALS — Login / Signup / OTP logic (Email OTP)
   ========================================================= */

const API_BASE = "/api/auth";

/* ---------- DOM HELPERS ---------- */
const views = {
    login:  document.getElementById("view-login"),
    signup: document.getElementById("view-signup"),
    otp:    document.getElementById("view-otp"),
    reset:  document.getElementById("view-reset")
};

const messageBox = document.getElementById("message-box");
let csrfToken = "";
let pendingEmail = "";

async function loadCsrfToken() {
    const response = await fetch(`${API_BASE}/csrf`);
    if (!response.ok) throw new Error("Unable to initialize secure session.");
    const result = await response.json();
    csrfToken = result.token;
}

async function post(path, payload) {
    if (!csrfToken) await loadCsrfToken();
    return fetch(`${API_BASE}/${path}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrfToken },
        body: JSON.stringify(payload)
    });
}

/* ---------- Show a specific view ---------- */
function showView(name) {
    Object.values(views).forEach(v => v.classList.remove("active"));
    views[name].classList.add("active");
    hideMessage();
    window.scrollTo({ top: 0, behavior: "smooth" });
}

/* ---------- Message box ---------- */
function showMessage(text, type = "success") {
    messageBox.textContent = text;
    messageBox.className = "message-box show " + type;
}

function hideMessage() {
    messageBox.className = "message-box";
    messageBox.textContent = "";
}

/* =========================================================
   LOGIN
   ========================================================= */
document.getElementById("login-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    hideMessage();

    const username = document.getElementById("login-username").value.trim();
    const password = document.getElementById("login-password").value;

    try {
        const res = await post("login", { username, password });

        const data = await res.json();

        if (!res.ok) {
            showMessage(data.error || "Login failed", "error");
            return;
        }

        showMessage(`✅ Welcome back, ${data.name || data.username}!`, "success");

        setTimeout(() => { window.location.href = "/shop"; }, 900);

    } catch (err) {
        console.error(err);
        showMessage("Network error. Please try again.", "error");
    }
});

/* =========================================================
   SIGNUP
   ========================================================= */
document.getElementById("signup-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    hideMessage();

    const payload = {
        name:          document.getElementById("su-name").value.trim(),
        username:      document.getElementById("su-username").value.trim(),
        email:         document.getElementById("su-email").value.trim(),
        contactNumber: document.getElementById("su-contact").value.trim(),
        gender:        document.getElementById("su-gender").value,
        password:      document.getElementById("su-password").value,
        invitationCode: document.getElementById("su-invitation").value.trim()
    };

    try {
        const res = await post("register", payload);

        const data = await res.json();

        if (!res.ok) {
            showMessage(data.error || "Registration failed", "error");
            return;
        }

        showMessage(data.emailSent === false
            ? "Your account was created, but the verification email could not be sent right now. Please try again later."
            : "✅ OTP sent to your email! Please check your inbox.", "success");

        // Save email for the OTP step
        pendingEmail = data.email || payload.email;
        document.getElementById("otp-target-email").textContent = pendingEmail;

        // Go to OTP view after a short delay
        setTimeout(() => { showView("otp"); }, 1200);

    } catch (err) {
        console.error(err);
        showMessage("Network error. Please try again.", "error");
    }
});

/* =========================================================
   OTP VERIFY (via EMAIL)
   ========================================================= */
document.getElementById("otp-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    hideMessage();

    const otp = document.getElementById("otp-input").value.trim();
    const email = pendingEmail;

    if (!email) {
        showMessage("Session expired. Please sign up again.", "error");
        showView("signup");
        return;
    }

    if (otp.length !== 6) {
        showMessage("Please enter a valid 6-digit OTP", "error");
        return;
    }

    try {
        const res = await post("verify-otp", { email, otp });

        const data = await res.json();

        if (!res.ok) {
            showMessage(data.error || "OTP verification failed", "error");
            return;
        }

        showMessage("✅ Account verified successfully! Redirecting to login…", "success");

        pendingEmail = "";

        setTimeout(() => {
            showView("login");
        }, 1600);

    } catch (err) {
        console.error(err);
        showMessage("Network error. Please try again.", "error");
    }
});

document.getElementById("resend-otp").addEventListener("click", async event => {
    event.preventDefault();
    if (!pendingEmail) {
        showMessage("Please return to sign up and enter your email address.", "error");
        return;
    }
    try {
        const response = await post("resend-otp", { email: pendingEmail });
        const data = await response.json();
        if (!response.ok) {
            showMessage(data.error || "Unable to resend the verification code.", "error");
            return;
        }
        showMessage(data.message, "success");
    } catch (error) {
        showMessage(error.message || "Network error. Please try again.", "error");
    }
});

document.getElementById("request-reset-form").addEventListener("submit", async event => {
    event.preventDefault();
    hideMessage();
    const email = document.getElementById("reset-email").value.trim();
    try {
        const res = await post("request-password-reset", { email });
        const data = await res.json();
        if (!res.ok) {
            showMessage(data.error || "Unable to send password reset OTP.", "error");
            return;
        }
        document.getElementById("complete-reset-form").hidden = false;
        showMessage(data.message, "success");
    } catch (error) {
        showMessage(error.message || "Network error. Please try again.", "error");
    }
});

document.getElementById("complete-reset-form").addEventListener("submit", async event => {
    event.preventDefault();
    hideMessage();
    const payload = {
        email: document.getElementById("reset-email").value.trim(),
        otp: document.getElementById("reset-otp").value.trim(),
        newPassword: document.getElementById("reset-password").value
    };
    try {
        const res = await post("reset-password", payload);
        const data = await res.json();
        if (!res.ok) {
            showMessage(data.error || "Password reset failed.", "error");
            return;
        }
        document.getElementById("complete-reset-form").reset();
        document.getElementById("complete-reset-form").hidden = true;
        showView("login");
        showMessage("Password reset successfully. You can now sign in.", "success");
    } catch (error) {
        showMessage(error.message || "Network error. Please try again.", "error");
    }
});

/* =========================================================
   VIEW SWITCHING
   ========================================================= */
document.getElementById("go-signup").addEventListener("click", (e) => {
    e.preventDefault();
    showView("signup");
});

document.getElementById("go-login").addEventListener("click", (e) => {
    e.preventDefault();
    showView("login");
});

document.getElementById("go-back-signup").addEventListener("click", (e) => {
    e.preventDefault();
    showView("signup");
});

document.getElementById("go-reset").addEventListener("click", event => {
    event.preventDefault();
    document.getElementById("complete-reset-form").hidden = true;
    showView("reset");
});

document.getElementById("go-login-reset").addEventListener("click", event => {
    event.preventDefault();
    showView("login");
});

/* =========================================================
   INIT — start on login view
   ========================================================= */
showView("login");
loadCsrfToken().catch(error => showMessage(error.message, "error"));
if (new URLSearchParams(window.location.search).has("accountDeleted")) {
    showMessage("Your account and associated data were permanently deleted.", "success");
}