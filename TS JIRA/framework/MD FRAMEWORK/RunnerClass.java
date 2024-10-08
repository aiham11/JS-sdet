package com.optum.coe.automation.rally;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.rallydev.rest.RallyRestApi;

public class RunnerClass {

    // Logger Initialization for Runner Class
    private static final Logger logger = LogManager.getLogger();

    // Main method
    public static void main(String[] args) throws MalformedURLException, IOException, URISyntaxException {

        /*
         * Main method calls below functionalities from com.optum.coe.automation.rally
         * package 1. Get Jira non migrated testcase keys 2. Get Jira Testcase details
         * for the given testcase key. It is an iterative process 3. Create the testcase
         * in Rally using the Jira testcase details 4. Validate if the testcase is
         * created successfully ; Future implementation is required. US7440061
         */

        JiraTestCase jiraTestCase = new JiraTestCase();
        JiraOperation jiraOperation = new JiraOperation();
        ArrayList<String> testcaseKeys = jiraOperation.getJiraNonMigratedTestcaseKeys();
        RallyRestApi rallyRestApi = new RallyRestApi(new URI(ConfigLoader.getConfigValue("RALLY_BASE_URL")), ConfigLoader.getConfigValue("RALLY_API_KEY"));
        
        for (int i = 0; i < testcaseKeys.size(); i++) {			
            boolean rallyTestcaseCreationStatus = false;
            boolean rallyOverallTestStepAttachmentsStatus = false;
            jiraTestCase.setKey(testcaseKeys.get(i));
            logger.info("Processing " + jiraTestCase.getKey());
            JsonObject jiraTestcaseJson = jiraOperation.getJiraTestCaseDetails(jiraTestCase.getKey()); // Update to return JsonObject
            RallyOperation rallyOperation = new RallyOperation();
            
            // Declaration of rallyTestcaseOID
            String rallyTestcaseOID = rallyOperation.createRallyTestcase(jiraTestcaseJson);
            
            // Validation for Testcase creation
            if (rallyTestcaseOID != null ) {
                rallyTestcaseCreationStatus = true;
            } else {
                logger.error("Testcase is not created in Rally for the key " + jiraTestCase.getKey());
                break;	
            }

            // Download attachments
            List<String> fileAttachmentDownloadPathsTestcaseLevel = jiraOperation.jiraAttachmentsDownload(jiraTestCase.getKey(), "testcase", "file");
            List<String> fileAttachmentDownloadPathsTestStepLevel = jiraOperation.jiraAttachmentsDownload(jiraTestCase.getKey(), "teststep", "file");
            List<String> embeddedAttachmentDownloadPathsTestStepLevel = jiraOperation.jiraAttachmentsDownload(jiraTestCase.getKey(), "teststep", "embedded");

            // Upload test case attachments to Rally
            if (fileAttachmentDownloadPathsTestcaseLevel != null) {
                logger.info("Attachment paths are found in the list.");
                List<String> testcaseAttachmentOIDs = rallyOperation.attachFilestoRallyTestcase(rallyTestcaseOID, fileAttachmentDownloadPathsTestcaseLevel);
                Utils.deleteAttachmentFileFromLocal(fileAttachmentDownloadPathsTestcaseLevel);
                // Validation for Testcase file attachments 
                if (!testcaseAttachmentOIDs.isEmpty()) {
                    rallyOverallTestStepAttachmentsStatus = true;	
                } else {
                    logger.error("The Jira testcase is not created in rally. Jira Testcase key is " + jiraTestCase.getKey()
                    + " is not created in rally");
                    return;
                }

            } else {
                logger.info("No Attachment path found for Testcase level.");
            }

            // Process test steps
            JsonArray testSteps = jiraTestcaseJson.getAsJsonObject("testScript").getAsJsonArray("steps"); // Updated to use JsonArray
            List<JiraTestStep> jiraTestSteps = jiraOperation.getTestStepsForTestCase(jiraTestCase.getKey());

            if (jiraTestSteps != null && !jiraTestSteps.isEmpty()) {
                rallyOperation.migrateTestSteps(rallyTestcaseOID, jiraTestSteps, rallyRestApi);
            } else {
                logger.info("No test steps found for Jira Test Case: " + jiraTestCase.getKey());
            }

            // Upload test step file attachments to Rally
            for (JiraTestStep step : jiraTestSteps) {
                JsonObject gsonTestStepJson = new Gson().toJsonTree(step).getAsJsonObject(); // Convert JiraTestStep to JsonObject
                String rallyTestStepOID = rallyOperation.createRallyTestStep(rallyTestcaseOID, gsonTestStepJson);
                
                if (fileAttachmentDownloadPathsTestStepLevel != null && !fileAttachmentDownloadPathsTestStepLevel.isEmpty()) {
                    List<String> testStepAttachmentOIDs = rallyOperation.attachFilestoRallyTestStep(rallyTestStepOID, fileAttachmentDownloadPathsTestStepLevel);
                    Utils.deleteAttachmentFileFromLocal(fileAttachmentDownloadPathsTestStepLevel);
                    // Validation for TestStep file attachments
                    if (!testStepAttachmentOIDs.isEmpty()) {
                        rallyOverallTestStepAttachmentsStatus = true;	
                    } else {
                        logger.error("The Jira teststep attachments are not created in rally. Jira Testcase key is " + jiraTestCase.getKey()
                        + " is not created in rally");
                        return;
                    }
                }

                // Upload embedded test step attachments to Rally
                if (embeddedAttachmentDownloadPathsTestStepLevel != null && !embeddedAttachmentDownloadPathsTestStepLevel.isEmpty()) {
                    List<String> embeddedTestStepAttachmentOIDs = rallyOperation.attachFilestoRallyTestStep(rallyTestStepOID, embeddedAttachmentDownloadPathsTestStepLevel);
                    Utils.deleteAttachmentFileFromLocal(embeddedAttachmentDownloadPathsTestStepLevel);
                    // Validation for TestStep embedded attachments
                    if (!embeddedTestStepAttachmentOIDs.isEmpty()) {
                        rallyOverallTestStepAttachmentsStatus = true;	
                    } else {
                        logger.error("The Jira embedded teststep attachments are not created in rally. Jira Testcase key is " + jiraTestCase.getKey()
                        + " is not created in rally");
                        return;
                    }
                }
            }

            // Overall validation for Jira Testcase migration to Rally.
            if (rallyTestcaseCreationStatus == true && rallyOverallTestStepAttachmentsStatus == true ) {
                /*
                 * Needs to be added calling method to check "Testcase Migrated" and "Test Folder Migrated" the check box in Jira
                 */
            }
        }

        // Close the RallyRestApi connection
        rallyRestApi.close();
    }
}
