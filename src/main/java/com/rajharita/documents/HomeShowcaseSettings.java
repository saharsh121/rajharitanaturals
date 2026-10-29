package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "home_showcase_settings")
public class HomeShowcaseSettings {

    @Id
    private String id = "home-showcase";
    private List<String> images = new ArrayList<>();
    private Integer intervalSeconds = 1;
    private String aboutUsTitle = "About Rajharita Naturals";
    private String aboutUsSummary = "";
    private String aboutUsContent = "";
    private List<AboutUsBlock> aboutUsBlocks = new ArrayList<>();

    public static class AboutUsBlock {
        private String imageUrl;
        private String period;
        private String heading;
        private String subheading;
        private String content;

        public String getImageUrl() { return imageUrl; }
        public String getPeriod() { return period; }
        public String getHeading() { return heading; }
        public String getSubheading() { return subheading; }
        public String getContent() { return content; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public void setPeriod(String period) { this.period = period; }
        public void setHeading(String heading) { this.heading = heading; }
        public void setSubheading(String subheading) { this.subheading = subheading; }
        public void setContent(String content) { this.content = content; }
    }

    public String getId() { return id; }
    public List<String> getImages() { return images; }
    public Integer getIntervalSeconds() { return intervalSeconds; }
    public String getAboutUsTitle() { return aboutUsTitle; }
    public String getAboutUsSummary() { return aboutUsSummary; }
    public String getAboutUsContent() { return aboutUsContent; }
    public List<AboutUsBlock> getAboutUsBlocks() { return aboutUsBlocks; }

    public void setId(String id) { this.id = id; }
    public void setImages(List<String> images) { this.images = images; }
    public void setIntervalSeconds(Integer intervalSeconds) { this.intervalSeconds = intervalSeconds; }
    public void setAboutUsTitle(String aboutUsTitle) { this.aboutUsTitle = aboutUsTitle; }
    public void setAboutUsSummary(String aboutUsSummary) { this.aboutUsSummary = aboutUsSummary; }
    public void setAboutUsContent(String aboutUsContent) { this.aboutUsContent = aboutUsContent; }
    public void setAboutUsBlocks(List<AboutUsBlock> aboutUsBlocks) { this.aboutUsBlocks = aboutUsBlocks; }
}
