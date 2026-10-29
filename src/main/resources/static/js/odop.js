/* =========================================================
   RAJHARITA NATURALS — ODOP Partnership form logic
   Handles file upload via FormData
   ========================================================= */

const ODOP_API = "/api/odop";

const form = document.getElementById("odop-form");
const messageBox = document.getElementById("message-box");
const fileInput = document.getElementById("o-file");
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
    const name = document.getElementById("o-name").value.trim();
    const district = document.getElementById("o-district").value.trim();
    const contact = document.getElementById("o-contact").value.trim();
    const odopProduct = document.getElementById("o-product").value.trim();
    const role = document.getElementById("o-role").value;
    const message = document.getElementById("o-message").value.trim();
    const language = document.getElementById("o-language").value || "en";

    // Client-side validation
    const missing = [];
    if (!name)         missing.push("Name");
    if (!district)     missing.push("District");
    if (!contact)      missing.push("Phone / Email");
    if (!odopProduct)  missing.push("ODOP Product");
    if (!role)         missing.push("Proposed Role");

    if (missing.length > 0) {
        showMessage("Please fill: " + missing.join(", "), "error");
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
    formData.append("district", district);
    formData.append("contact", contact);
    formData.append("odopProduct", odopProduct);
    formData.append("role", role);
    formData.append("message", message);
    formData.append("language", language);
    if (file) formData.append("attachment", file);

    // Disable button while sending
    const submitBtn = form.querySelector("button[type='submit']");
    const originalText = submitBtn.textContent;
    submitBtn.disabled = true;
    submitBtn.textContent = "Sending…";

    try {
        const res = await fetch(`${ODOP_API}/submit`, {
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