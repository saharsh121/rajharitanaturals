/* =========================================================
   RAJHARITA NATURALS — B2B Bulk Sourcing form logic
   Handles file upload via FormData
   ========================================================= */

const B2B_API = "/api/b2b";

const form = document.getElementById("b2b-form");
const messageBox = document.getElementById("message-box");
const fileInput = document.getElementById("b-file");
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
    const companyName = document.getElementById("b-company").value.trim();
    const contactPerson = document.getElementById("b-person").value.trim();
    const email = document.getElementById("b-email").value.trim();
    const phone = document.getElementById("b-phone").value.trim();
    const materialRequired = document.getElementById("b-material").value.trim();
    const quantity = document.getElementById("b-quantity").value.trim();
    const frequency = document.getElementById("b-frequency").value;
    const deliveryLocation = document.getElementById("b-location").value.trim();
    const message = document.getElementById("b-message").value.trim();
    const language = document.getElementById("b-language").value || "en";

    // Client-side validation
    const missing = [];
    if (!companyName)        missing.push("Company Name");
    if (!contactPerson)      missing.push("Contact Person");
    if (!email)              missing.push("Email");
    if (!phone)              missing.push("Phone");
    if (!materialRequired)   missing.push("Material Required");
    if (!quantity)           missing.push("Quantity");
    if (!frequency)          missing.push("Frequency");
    if (!deliveryLocation)   missing.push("Delivery Location");

    if (missing.length > 0) {
        showMessage("Please fill: " + missing.join(", "), "error");
        return;
    }

    // Email validation
    if (!/^.+@.+\..+$/.test(email)) {
        showMessage("Please enter a valid email address", "error");
        return;
    }

    // Phone validation
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
    formData.append("companyName", companyName);
    formData.append("contactPerson", contactPerson);
    formData.append("email", email);
    formData.append("phone", phone);
    formData.append("materialRequired", materialRequired);
    formData.append("quantity", quantity);
    formData.append("frequency", frequency);
    formData.append("deliveryLocation", deliveryLocation);
    formData.append("message", message);
    formData.append("language", language);
    if (file) formData.append("attachment", file);

    // Disable button while sending
    const submitBtn = form.querySelector("button[type='submit']");
    const originalText = submitBtn.textContent;
    submitBtn.disabled = true;
    submitBtn.textContent = "Sending…";

    try {
        const res = await fetch(`${B2B_API}/submit`, {
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
        showMessage(data.message || "✅ Thank you! We received your request.", "success");
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
const phoneInput = document.getElementById("b-phone");
phoneInput.addEventListener("blur", () => {
    let v = phoneInput.value.trim();
    if (!v) return;
    v = v.replace(/[^\d+]/g, "");
    if (/^\d{10}$/.test(v)) {
        v = "+91" + v;
    }
    phoneInput.value = v;
});