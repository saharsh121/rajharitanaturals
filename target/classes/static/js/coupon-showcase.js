const couponShowcase = document.getElementById("coupon-showcase");
const couponOfferList = document.getElementById("coupon-offer-list");
const couponShowcaseStatus = document.getElementById("coupon-showcase-status");
const couponOfferIndicators = document.getElementById("coupon-offer-indicators");
let couponSlides = [];
let currentCouponIndex = 0;
let couponRotationTimer;

function formatCouponDiscount(offer) {
    const value = Number(offer.discountValue);
    return offer.discountType === "PERCENT"
        ? `${new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 }).format(value)}% OFF`
        : `₹${new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 }).format(value)} OFF`;
}

function formatCouponExpiry(value) {
    if (!value) return "Limited time offer";
    const expiry = new Date(value);
    return Number.isNaN(expiry.getTime())
        ? "Limited time offer"
        : `Valid until ${expiry.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })}`;
}

function renderCouponOffer(offer) {
    const card = document.createElement("article");
    card.className = "coupon-offer-card";

    const copy = document.createElement("div");
    copy.className = "coupon-offer-copy";
    const offerValue = document.createElement("p");
    offerValue.className = "coupon-offer-value";
    offerValue.textContent = formatCouponDiscount(offer);
    const audience = document.createElement("p");
    audience.className = offer.targetRating === null || offer.targetRating === undefined
        ? "coupon-offer-audience"
        : "coupon-offer-audience coupon-offer-audience-exclusive";
    audience.textContent = offer.targetRating === null || offer.targetRating === undefined
        ? "A special offer for everyone"
        : `A thank-you offer for our ${offer.targetRating}-star customers`;
    const title = document.createElement("h3");
    title.textContent = offer.title || "A special offer for you";
    const description = document.createElement("p");
    description.className = "coupon-offer-description";
    description.textContent = offer.description || "Enjoy this exclusive saving on your next order.";
    const terms = document.createElement("p");
    terms.className = "coupon-offer-terms";
    const termsText = [];
    if (Number(offer.minimumOrder) > 0) {
        termsText.push(`On orders above ₹${new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 }).format(Number(offer.minimumOrder))}`);
    }
    termsText.push(formatCouponExpiry(offer.expiresAt));
    terms.textContent = termsText.join(" · ");
    copy.append(offerValue, audience, title, description, terms);

    const codeBox = document.createElement("div");
    codeBox.className = "coupon-code-box";
    const codeLabel = document.createElement("span");
    codeLabel.textContent = "YOUR COUPON CODE";
    const code = document.createElement("code");
    code.textContent = offer.code;
    const callToAction = document.createElement("a");
    callToAction.href = "/shop";
    callToAction.className = "coupon-shop-link";
    callToAction.textContent = "Shop now";
    codeBox.append(codeLabel, code, callToAction);
    card.append(copy, codeBox);
    return card;
}

function showCoupon(index) {
    currentCouponIndex = (index + couponSlides.length) % couponSlides.length;
    couponSlides.forEach((slide, slideIndex) => {
        const active = slideIndex === currentCouponIndex;
        slide.hidden = !active;
        slide.setAttribute("aria-hidden", String(!active));
    });
    couponOfferIndicators.querySelectorAll("button").forEach((indicator, index) => {
        const active = index === currentCouponIndex;
        indicator.classList.toggle("active", active);
        indicator.setAttribute("aria-current", active ? "true" : "false");
    });
}

function stopCouponRotation() {
    window.clearInterval(couponRotationTimer);
}

function startCouponRotation() {
    stopCouponRotation();
    if (couponSlides.length < 2 || window.matchMedia("(prefers-reduced-motion: reduce)").matches
        || couponShowcase.matches(":hover") || couponShowcase.contains(document.activeElement)) {
        return;
    }
    couponRotationTimer = window.setInterval(() => showCoupon(currentCouponIndex + 1), 3000);
}

async function loadCouponOffers() {
    try {
        const response = await fetch("/api/coupons/available");
        if (!response.ok) throw new Error("Coupon offers could not be loaded.");
        const offers = await response.json();
        if (!Array.isArray(offers)) throw new Error("Coupon offers could not be loaded.");
        if (!offers.length) return;
        couponSlides = offers.map(renderCouponOffer);
        couponOfferList.replaceChildren(...couponSlides);
        couponOfferIndicators.replaceChildren(...offers.map((offer, index) => {
            const indicator = document.createElement("button");
            indicator.type = "button";
            indicator.setAttribute("aria-label", `Show offer ${index + 1}: ${offer.title || offer.code}`);
            indicator.addEventListener("click", () => {
                showCoupon(index);
                startCouponRotation();
            });
            return indicator;
        }));
        showCoupon(0);
        couponShowcase.hidden = false;
        startCouponRotation();
    } catch (error) {
        couponShowcaseStatus.textContent = error instanceof Error
            ? `${error.message} Please refresh to try again.`
            : "Coupon offers could not be loaded. Please refresh to try again.";
        couponShowcase.hidden = false;
    }
}

couponShowcase.addEventListener("mouseenter", stopCouponRotation);
couponShowcase.addEventListener("mouseleave", startCouponRotation);
couponShowcase.addEventListener("focusin", stopCouponRotation);
couponShowcase.addEventListener("focusout", event => {
    if (!couponShowcase.contains(event.relatedTarget)) startCouponRotation();
});

loadCouponOffers();
