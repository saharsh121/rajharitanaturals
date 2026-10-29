/* =========================================================
   RAJHARITA NATURALS — Investment Inquiry form logic
   Handles file upload via FormData
   ========================================================= */

const INVEST_API = "/api/investment";

const form = document.getElementById("investment-form");
const messageBox = document.getElementById("message-box");
const fileInput = document.getElementById("i-file");
const fileHint = document.getElementById("file-hint");

const MAX_FILE_MB = 10;
const ALLOWED_EXT = ["pdf", "doc", "docx"];

/* ---------- Message box helpers ---------- */
function showMessage(text, type = "success") {
    messageBox.textContent = text;
    messageBox.className = "message-box show " + type;
    messageBox.scrollIntoView({ behavior: "smooth", block: "center" });
}

function hideMessage() {
    messageBox.className = "message-box";
    messageBox.textContent = "";
}

/* ---------- File hint preview ---------- */
fileInput.addEventListener("change", () => {
    const f = fileInput.files[0];
    if (!f) {
        fileHint.textContent = "No file selected";
        return;
    }
    const sizeMB = (f.size / (1024 * 1024)).toFixed(2);
    fileHint.textContent = `Selected: ${f.name} (${sizeMB} MB)`;
});

/* =========================================================
   FORM SUBMIT
   ========================================================= */
form.addEventListener("submit", async (e) => {
    e.preventDefault();
    hideMessage();

    // Gather values
    const name = document.getElementById("i-name").value.trim();
    const companyName = document.getElementById("i-company").value.trim();
    const email = document.getElementById("i-email").value.trim();
    const phone = document.getElementById("i-phone").value.trim();
    const location = document.getElementById("i-location").value.trim();
    const investmentType = document.getElementById("i-type").value;
    const amountRange = document.getElementById("i-amount").value;
    const message = document.getElementById("i-message").value.trim();
    const language = document.getElementById("i-language").value || "en";

    // Client-side validation
    const missing = [];
    if (!name)           missing.push("Name");
    if (!companyName)    missing.push("Company Name");
    if (!email)          missing.push("Email");
    if (!phone)          missing.push("Phone");
    if (!location)       missing.push("Location");
    if (!investmentType) missing.push("Investment Type");
    if (!amountRange)    missing.push("Amount Range");

    if (missing.length > 0) {
        showMessage("Please fill: " + missing.join(", "), "error");
        return;
    }

    if (!/^.+@.+\..+$/.test(email)) {
        showMessage("Please enter a valid email address", "error");
        return;
    }

    // Phone digit check
    const digits = phone.replace(/\D/g, "");
    if (digits.length < 10) {
        showMessage("Please enter a valid phone number", "error");
        return;
    }

    // File validation (if any)
    const file = fileInput.files[0];
    if (file) {
        const ext = (file.name.split(".").pop() || "").toLowerCase();
        if (!ALLOWED_EXT.includes(ext)) {
            showMessage("Attachment must be PDF, DOC or DOCX", "error");
            return;
        }
        if (file.size > MAX_FILE_MB * 1024 * 1024) {
            showMessage(`Attachment too large (max ${MAX_FILE_MB} MB)`, "error");
            return;
        }
    }

    // Build multipart form data
    const formData = new FormData();
    formData.append("name", name);
    formData.append("companyName", companyName);
    formData.append("email", email);
    formData.append("phone", phone);
    formData.append("location", location);
    formData.append("investmentType", investmentType);
    formData.append("amountRange", amountRange);
    formData.append("message", message);
    formData.append("language", language);
    if (file) formData.append("attachment", file);

    // Disable button while sending
    const submitBtn = form.querySelector("button[type='submit']");
    const originalText = submitBtn.textContent;
    submitBtn.disabled = true;
    submitBtn.textContent = "Sending…";

    try {
        const res = await fetch(`${INVEST_API}/submit`, {
            method: "POST",
            body: formData
            // ⚠️ Don't set Content-Type — browser sets it with boundary automatically
        });

        const data = await res.json();

        if (!res.ok) {
            showMessage(data.error || "Submission failed. Please try again.", "error");
            submitBtn.disabled = false;
            submitBtn.textContent = originalText;
            return;
        }

        // Success
        showMessage(data.message || "✅ Thank you! We received your inquiry.", "success");
        form.reset();
        fileHint.textContent = "No file selected";

        submitBtn.disabled = false;
        submitBtn.textContent = originalText;

    } catch (err) {
        console.error(err);
        showMessage("Network error. Please check your connection and try again.", "error");
        submitBtn.disabled = false;
        submitBtn.textContent = originalText;
    }
});

/* =========================================================
   PHONE AUTO-FORMAT (+91)
   ========================================================= */
const phoneInput = document.getElementById("i-phone");
phoneInput.addEventListener("blur", () => {
    let v = phoneInput.value.trim();
    if (!v) return;
    v = v.replace(/[^\d+]/g, "");
    if (/^\d{10}$/.test(v)) {
        v = "+91" + v;
    }
    phoneInput.value = v;
});