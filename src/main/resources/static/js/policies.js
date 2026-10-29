fetch("/api/store/settings")
    .then(response => {
        if (!response.ok) throw new Error("Store policies are unavailable.");
        return response.json();
    })
    .then(settings => {
        document.getElementById("terms").textContent = settings.termsAndConditions || "Terms and conditions have not been published.";
        document.getElementById("privacy").textContent = settings.privacyPolicy || "Privacy policy has not been published.";
        document.getElementById("returns").textContent = settings.returnPolicy || "Return policy has not been published.";
    })
    .catch(() => {
        document.querySelectorAll(".policy-card p").forEach(paragraph => {
            paragraph.textContent = "Policies could not be loaded. Please contact the store.";
        });
    });
