/* =========================================================
   RAJHARITA NATURALS — Farmer / Kisan Supply form logic
   ========================================================= */

const FARMER_API = "/api/farmer";

const form = document.getElementById("farmer-form");
const messageBox = document.getElementById("message-box");

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

/* =========================================================
   FORM SUBMIT
   ========================================================= */
form.addEventListener("submit", async (e) => {
    e.preventDefault();
    hideMessage();

    // Gather values
    const payload = {
        name:             document.getElementById("f-name").value.trim(),
        village:          document.getElementById("f-village").value.trim(),
        phone:            document.getElementById("f-phone").value.trim(),
        cropType:         document.getElementById("f-crop").value,
        quantity:         document.getElementById("f-quantity").value.trim(),
        preferredContact: document.getElementById("f-contact").value,
        message:          document.getElementById("f-message").value.trim(),
        language:         document.getElementById("f-language").value || "en"
    };

    // Quick client-side check
    const missing = [];
    if (!payload.name)             missing.push("Name");
    if (!payload.village)          missing.push("Village / District");
    if (!payload.phone)            missing.push("Phone");
    if (!payload.cropType)         missing.push("Crop / Product");
    if (!payload.quantity)         missing.push("Quantity");
    if (!payload.preferredContact) missing.push("Preferred contact");

    if (missing.length > 0) {
        showMessage("Please fill: " + missing.join(", "), "error");
        return;
    }

    // Phone basic validation (10+ digits)
    const digits = payload.phone.replace(/\D/g, "");
    if (digits.length < 10) {
        showMessage("Please enter a valid phone number", "error");
        return;
    }

    // Disable submit button while sending
    const submitBtn = form.querySelector("button[type='submit']");
    const originalText = submitBtn.textContent;
    submitBtn.disabled = true;
    submitBtn.textContent = "Sending…";

    try {
        const res = await fetch(`${FARMER_API}/submit`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        const data = await res.json();

        if (!res.ok) {
            showMessage(data.error || "Submission failed. Please try again.", "error");
            submitBtn.disabled = false;
            submitBtn.textContent = originalText;
            return;
        }

        // Success
        showMessage(data.message || "✅ Thank you! We received your details.", "success");
        form.reset();

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
   PHONE INPUT FORMATTING (keep it farmer-friendly)
   ========================================================= */
const phoneInput = document.getElementById("f-phone");
phoneInput.addEventListener("blur", () => {
    let v = phoneInput.value.trim();
    if (!v) return;

    // Strip everything except digits and +
    v = v.replace(/[^\d+]/g, "");

    // If it's 10 digits, auto-prefix +91
    if (/^\d{10}$/.test(v)) {
        v = "+91" + v;
    }

    phoneInput.value = v;
});