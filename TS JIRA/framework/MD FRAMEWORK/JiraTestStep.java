package com.optum.coe.automation.rally;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;








import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JiraTestStep {
    private static final Logger logger = LogManager.getLogger();
    private int id;
    private String description;
    private String expectedResult;
    private int index;
    private String testData;
    private List<JiraAttachment> attachments;

    public List<String> getEmbeddedImageUrls() {
        List<String> imageUrls = new ArrayList<>();
        extractUrlsFromField(description, imageUrls);
        extractUrlsFromField(testData, imageUrls);
        extractUrlsFromField(expectedResult, imageUrls);
        return imageUrls;
    }

    private void extractUrlsFromField(String field, List<String> imageUrls) {
        if (field != null) {
            Pattern pattern = Pattern.compile("\\.\\.\\/rest\\/tests\\/1\\.0\\/attachment\\/image\\/\\d+");
            Matcher matcher = pattern.matcher(field);
            while (matcher.find()) {
                imageUrls.add(matcher.group());
            }
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public void setExpectedResult(String expectedResult) {
        this.expectedResult = expectedResult;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getTestData() {
        return testData;
    }

    public void setTestData(String testData) {
        this.testData = testData;
    }

    public List<JiraAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<JiraAttachment> attachments) {
        this.attachments = attachments;
    }
}
