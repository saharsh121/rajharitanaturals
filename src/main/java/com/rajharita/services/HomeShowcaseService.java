package com.rajharita.services;

import com.rajharita.documents.HomeShowcaseSettings;
import com.rajharita.documents.HomeShowcaseSettings.AboutUsBlock;
import com.rajharita.repositories.HomeShowcaseSettingsRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class HomeShowcaseService {

    private static final String SETTINGS_ID = "home-showcase";
    private static final int MAX_IMAGES = 10;
    private static final int DEFAULT_INTERVAL_SECONDS = 1;
    private static final int MAX_INTERVAL_SECONDS = 60;

    private final HomeShowcaseSettingsRepository repository;
    private final MongoTemplate mongoTemplate;

    public HomeShowcaseService(HomeShowcaseSettingsRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<String> images() {
        return repository.findById(SETTINGS_ID)
                .map(settings -> settings.getImages() == null ? List.<String>of() : List.copyOf(settings.getImages()))
                .orElseGet(List::of);
    }

    public int intervalSeconds() {
        return repository.findById(SETTINGS_ID)
                .map(settings -> settings.getIntervalSeconds() == null
                        ? DEFAULT_INTERVAL_SECONDS : settings.getIntervalSeconds())
                .orElse(DEFAULT_INTERVAL_SECONDS);
    }

    public int updateIntervalSeconds(Integer intervalSeconds) {
        if (intervalSeconds == null || intervalSeconds < 1 || intervalSeconds > MAX_INTERVAL_SECONDS) {
            throw new IllegalArgumentException("Photo change interval must be between 1 and 60 seconds.");
        }
        HomeShowcaseSettings settings = repository.findById(SETTINGS_ID)
                .orElseGet(HomeShowcaseSettings::new);
        settings.setIntervalSeconds(intervalSeconds);
        return repository.save(settings).getIntervalSeconds();
    }

    public Map<String, Object> aboutUs() {
        return repository.findById(SETTINGS_ID)
                .map(settings -> {
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("title", valueOrDefault(settings.getAboutUsTitle(), "About Rajharita Naturals"));
                    result.put("summary", valueOrDefault(settings.getAboutUsSummary(), ""));
                    result.put("content", valueOrDefault(settings.getAboutUsContent(), ""));
                    result.put("blocks", settings.getAboutUsBlocks() == null ? List.of() : settings.getAboutUsBlocks());
                    return result;
                })
                .orElseGet(() -> Map.of("title", "About Rajharita Naturals",
                        "summary", "", "content", "", "blocks", List.of()));
    }

    public Map<String, Object> updateAboutUs(String title, String summary, String content,
                                             List<AboutUsBlockRequest> submittedBlocks) {
        if (title == null || title.isBlank() || title.length() > 120
                || summary == null || summary.length() > 500
                || content == null || content.length() > 10000) {
            throw new IllegalArgumentException("Enter a title (up to 120 characters), a short introduction (up to 500 characters), and About Us details (up to 10,000 characters).");
        }
        List<AboutUsBlockRequest> blocks = submittedBlocks == null ? List.of() : submittedBlocks;
        if (blocks.size() > 10) {
            throw new IllegalArgumentException("The About Us section can contain up to 10 image-and-text blocks.");
        }
        List<AboutUsBlock> aboutUsBlocks = blocks.stream().map(block -> {
            if (block == null || block.imageUrl() == null
                    || !block.imageUrl().matches("/about/[0-9a-fA-F-]{36}\\.(jpg|png|webp)")
                    || block.period() != null && block.period().length() > 40
                    || block.heading() != null && block.heading().length() > 120
                    || block.subheading() != null && block.subheading().length() > 160
                    || block.content() == null || block.content().isBlank() || block.content().length() > 5000) {
                throw new IllegalArgumentException("Each About Us block needs an uploaded image and text (up to 5,000 characters).");
            }
            AboutUsBlock savedBlock = new AboutUsBlock();
            savedBlock.setImageUrl(block.imageUrl());
            savedBlock.setPeriod(block.period() == null ? "" : block.period().trim());
            savedBlock.setHeading(block.heading() == null ? "" : block.heading().trim());
            savedBlock.setSubheading(block.subheading() == null ? "" : block.subheading().trim());
            savedBlock.setContent(block.content().trim());
            return savedBlock;
        }).toList();
        repository.findById(SETTINGS_ID).orElseGet(() -> repository.save(new HomeShowcaseSettings()));
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(SETTINGS_ID)),
                new Update().set("aboutUsTitle", title.trim())
                        .set("aboutUsSummary", summary.trim())
                        .set("aboutUsContent", content.trim())
                        .set("aboutUsBlocks", aboutUsBlocks),
                HomeShowcaseSettings.class);
        return aboutUs();
    }

    public void addImage(String imageUrl) {
        repository.findById(SETTINGS_ID).orElseGet(() -> repository.save(new HomeShowcaseSettings()));
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(SETTINGS_ID),
                new Criteria().orOperator(
                        Criteria.where("images").exists(false),
                        Criteria.where("images").size(0),
                        Criteria.where("images.9").exists(false))));
        HomeShowcaseSettings updated = mongoTemplate.findAndModify(query,
                new Update().addToSet("images", imageUrl),
                FindAndModifyOptions.options().returnNew(true), HomeShowcaseSettings.class);
        if (updated == null) {
            if (images().contains(imageUrl)) return;
            throw new IllegalArgumentException("The showcase already has 10 photos. Remove a photo before adding another.");
        }
    }

    public void removeImage(String imageUrl) {
        HomeShowcaseSettings settings = repository.findById(SETTINGS_ID)
                .orElseThrow(() -> new IllegalArgumentException("Showcase photo was not found."));
        if (settings.getImages() == null || !settings.getImages().contains(imageUrl)) {
            throw new IllegalArgumentException("Showcase photo was not found.");
        }
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(SETTINGS_ID)),
                new Update().pull("images", imageUrl), HomeShowcaseSettings.class);
    }

    public int maximumImages() {
        return MAX_IMAGES;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    public record AboutUsBlockRequest(String imageUrl, String period, String heading,
                                     String subheading, String content) { }
}
