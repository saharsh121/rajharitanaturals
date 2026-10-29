document.querySelectorAll("[data-home-featured-products]").forEach(async grid => {
    try {
        const response = await fetch("/api/products/featured", { cache: "no-store" });
        if (!response.ok) throw new Error(`Featured products request failed (${response.status}).`);
        const products = await response.json();
        const cards = [];

        products.forEach(product => {
            const uploadedImages = Array.isArray(product.homeImageUrls)
                ? product.homeImageUrls.filter(image => typeof image === "string" && image)
                : [];
            const catalogImages = Array.isArray(product.imageUrls)
                ? product.imageUrls.filter(image => typeof image === "string" && image)
                : [];
            const images = uploadedImages.length ? uploadedImages : catalogImages;
            if (!images.length) return;

            const card = document.createElement("a");
            card.className = "ad-product";
            card.href = "/shop";
            card.setAttribute("aria-label", `Explore ${product.name} in the shop`);

            const imageContainer = document.createElement("span");
            imageContainer.className = "ad-product-image";
            const mainImage = document.createElement("img");
            mainImage.className = "ad-product-main-image";
            mainImage.src = images[0];
            mainImage.alt = product.name;
            mainImage.loading = "lazy";
            imageContainer.append(mainImage);

            if (images[1]) {
                const hoverImage = document.createElement("img");
                hoverImage.className = "ad-product-hover-image";
                hoverImage.src = images[1];
                hoverImage.alt = `${product.name}, alternate view`;
                hoverImage.loading = "lazy";
                hoverImage.setAttribute("aria-hidden", "true");
                imageContainer.append(hoverImage);
            }

            const name = document.createElement("span");
            name.className = "ad-product-name";
            name.textContent = product.name;
            const arrow = document.createElement("span");
            arrow.className = "ad-product-arrow";
            arrow.setAttribute("aria-hidden", "true");
            arrow.textContent = "→";

            const caption = document.createElement("span");
            caption.className = "ad-product-caption";
            caption.append(name, arrow);

            card.append(imageContainer, caption);
            cards.push(card);
        });

        if (!cards.length) {
            grid.textContent = "Explore products will appear here when products are added in the Admin dashboard.";
            grid.classList.add("is-empty");
            return;
        }

        const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        grid.classList.toggle("is-static", reducedMotion);
        const track = document.createElement("div");
        track.className = "ad-products-track";
        const firstSet = document.createElement("div");
        firstSet.className = "ad-products-set";
        cards.forEach(card => firstSet.append(card));
        track.append(firstSet);

        if (!reducedMotion) {
            const duplicateSet = firstSet.cloneNode(true);
            duplicateSet.setAttribute("aria-hidden", "true");
            duplicateSet.querySelectorAll("a").forEach(link => link.tabIndex = -1);
            track.append(duplicateSet);
            track.style.setProperty("--ad-marquee-duration", `${Math.max(20, cards.length * 8) / 1.5}s`);
            track.classList.add("is-animated");
        }

        grid.replaceChildren(track);
    } catch (error) {
        console.error("Featured products could not be loaded for the home page.", error);
    }
});

document.querySelectorAll("[data-home-about]").forEach(async section => {
    try {
        const response = await fetch("/api/showcase/settings", { cache: "no-store" });
        if (!response.ok) throw new Error(`About Us settings request failed (${response.status}).`);
        const settings = await response.json();
        const aboutUs = settings.aboutUs;
        if (!aboutUs) return;

        section.querySelector("#home-about-title").textContent = aboutUs.title;
        section.querySelector(".home-about-summary").textContent = aboutUs.summary;
        const fullContent = section.querySelector("#home-about-full");
        const paragraphs = aboutUs.content.split(/\r?\n\s*\r?\n/).map(text => text.trim()).filter(Boolean);
        paragraphs.forEach(text => {
            const paragraph = document.createElement("p");
            paragraph.textContent = text;
            fullContent.append(paragraph);
        });

        const blocksContainer = section.querySelector("#home-about-blocks");
        const blocks = Array.isArray(aboutUs.blocks) ? aboutUs.blocks.slice(0, 10) : [];
        blocks.forEach((block, index) => {
            if (!block.imageUrl || !block.content) return;
            const row = document.createElement("article");
            row.className = "home-about-story-block";
            const image = document.createElement("img");
            image.src = block.imageUrl;
            image.alt = `${aboutUs.title} — image ${index + 1}`;
            image.loading = "lazy";
            const copy = document.createElement("div");
            copy.className = "home-about-story-copy";
            if (block.period && block.period.trim()) {
                const period = document.createElement("span");
                period.className = "home-about-story-period";
                period.textContent = block.period;
                copy.append(period);
            }
            if (block.heading && block.heading.trim()) {
                const heading = document.createElement("h3");
                heading.textContent = block.heading;
                copy.append(heading);
            }
            if (block.subheading && block.subheading.trim()) {
                const subheading = document.createElement("h4");
                subheading.textContent = block.subheading;
                copy.append(subheading);
            }
            const text = document.createElement("p");
            text.textContent = block.content;
            copy.append(text);
            row.append(image, copy);
            blocksContainer.append(row);
        });

        const button = section.querySelector(".home-about-read-more");
        const hasDetails = paragraphs.length > 0 || blocksContainer.childElementCount > 0;
        fullContent.hidden = true;
        blocksContainer.hidden = true;
        button.hidden = !hasDetails;
        button.addEventListener("click", () => {
            const expanded = button.getAttribute("aria-expanded") === "true";
            button.setAttribute("aria-expanded", String(!expanded));
            fullContent.hidden = expanded || paragraphs.length === 0;
            blocksContainer.hidden = expanded || blocksContainer.childElementCount === 0;
            section.classList.toggle("is-expanded", !expanded);
            button.textContent = expanded ? "Read On" : "Read Less";
        });
        section.hidden = !aboutUs.summary && !hasDetails;
    } catch (error) {
        console.error("Home page About Us content could not be loaded.", error);
    }
});

document.querySelectorAll("[data-ad-carousel]").forEach(async carousel => {
    let intervalSeconds = 1;
    function replaceSlides(images) {
        const slideFragment = document.createDocumentFragment();
        const indicatorFragment = document.createDocumentFragment();
        images.forEach((imageUrl, index) => {
            const slide = document.createElement("a");
            slide.className = `ad-slide${index === 0 ? " active" : ""}`;
            slide.href = "/shop";
            slide.setAttribute("aria-label", "Explore the Rajharita Naturals collection");
            slide.hidden = index !== 0;

            const image = document.createElement("img");
            image.src = imageUrl;
            image.alt = `Rajharita Naturals featured photo ${index + 1}`;
            image.loading = index === 0 ? "eager" : "lazy";
            slide.append(image);
            slideFragment.append(slide);

            const indicator = document.createElement("button");
            indicator.type = "button";
            indicator.dataset.adIndicator = String(index);
            indicator.classList.toggle("active", index === 0);
            indicator.setAttribute("aria-label", `Show featured photo ${index + 1}`);
            indicator.setAttribute("aria-current", String(index === 0));
            indicatorFragment.append(indicator);
        });

        carousel.querySelectorAll(".ad-slide").forEach(slide => slide.remove());
        carousel.querySelector(".ad-indicators")?.remove();
        carousel.prepend(slideFragment);

        const indicators = document.createElement("div");
        indicators.className = "ad-indicators";
        indicators.setAttribute("aria-label", "Choose featured photo");
        indicators.append(indicatorFragment);
        carousel.append(indicators);
    }

    try {
        const response = await fetch("/api/showcase/settings", { cache: "no-store" });
        if (!response.ok) throw new Error(`Showcase request failed (${response.status}).`);
        const settings = await response.json();
        if (Number.isInteger(settings.intervalSeconds)
                && settings.intervalSeconds >= 1 && settings.intervalSeconds <= 60) {
            intervalSeconds = settings.intervalSeconds;
        }
        const images = settings.images;
        const validImages = Array.isArray(images)
            ? images.filter(image => typeof image === "string" && image.startsWith("/showcase/"))
            : [];
        if (validImages.length) replaceSlides(validImages);
    } catch (error) {
        console.warn("Uploaded showcase photos could not be loaded.", error);
    }

    const slides = Array.from(carousel.querySelectorAll(".ad-slide"));
    const indicators = Array.from(carousel.querySelectorAll("[data-ad-indicator]"));
    const previous = carousel.querySelector("[data-ad-previous]");
    const next = carousel.querySelector("[data-ad-next]");
    if (!slides.length) return;

    let current = Math.max(0, slides.findIndex(slide => slide.classList.contains("active")));
    let timer;
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    function showSlide(index) {
        current = (index + slides.length) % slides.length;
        slides.forEach((slide, slideIndex) => {
            const active = slideIndex === current;
            slide.hidden = !active;
            slide.classList.toggle("active", active);
            slide.setAttribute("aria-hidden", String(!active));
        });
        indicators.forEach((indicator, indicatorIndex) => {
            const active = indicatorIndex === current;
            indicator.classList.toggle("active", active);
            indicator.setAttribute("aria-current", String(active));
        });
    }

    function showRandomSlide() {
        const offset = Math.floor(Math.random() * (slides.length - 1)) + 1;
        showSlide(current + offset);
    }

    function restartTimer() {
        window.clearInterval(timer);
        if (!reducedMotion && slides.length > 1) {
            timer = window.setInterval(showRandomSlide, intervalSeconds * 1000);
        }
    }

    if (slides.length > 1) {
        previous.hidden = false;
        next.hidden = false;
        previous.addEventListener("click", () => {
            showSlide(current - 1);
            restartTimer();
        });
        next.addEventListener("click", () => {
            showSlide(current + 1);
            restartTimer();
        });
        indicators.forEach((indicator, index) => indicator.addEventListener("click", () => {
            showSlide(index);
            restartTimer();
        }));
        carousel.addEventListener("mouseenter", () => window.clearInterval(timer));
        carousel.addEventListener("mouseleave", restartTimer);
        carousel.addEventListener("focusin", () => window.clearInterval(timer));
        carousel.addEventListener("focusout", event => {
            if (!carousel.contains(event.relatedTarget)) restartTimer();
        });
    } else {
        previous.hidden = true;
        next.hidden = true;
        carousel.querySelector(".ad-indicators")?.setAttribute("hidden", "");
    }

    showSlide(current);
    restartTimer();
});
