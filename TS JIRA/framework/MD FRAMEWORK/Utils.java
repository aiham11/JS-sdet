package com.optum.coe.automation.rally;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.CreateRequest;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.response.CreateResponse;
import com.rallydev.rest.response.QueryResponse;
import com.rallydev.rest.util.Fetch;
import com.rallydev.rest.util.QueryFilter;
import org.apache.http.HttpEntity;

public class Utils {

	// Logger Initialization for Utils Class

	private static final Logger logger = LogManager.getLogger();

	/*
	 * Create a method to establish a Jira connection.This method have two String
	 * arguments as "url" and "apiKey" ClosableHttpClient class is used to perform
	 * this operation instead HttpClient, so that a separate method is not required
	 * to close the connection each time. This method returns CloseableHttpClient's
	 * object instance once connection is established
	 */

	public static HttpEntity getJiraResponse(String url, String apiKey) {

		CloseableHttpClient connection = HttpClients.createDefault();
		HttpGet request = new HttpGet(url);
		request.setHeader("Authorization", "Bearer " + apiKey);
		// request.setHeader("Accept", "application/json");
		CloseableHttpResponse response = null;
		try {
			response = connection.execute(request);
		} catch (ClientProtocolException e) {
			logger.error("Error occurred in Jira connection while connecting " + url, e);
		} catch (IOException e) {
			logger.error("Error occurred in Jira connection while connecting " + url, e);
		}
		HttpEntity entity = response.getEntity();
		if (entity != null) {
			logger.info("Successfully returned HttpEntity response for the URL " + url);
			return entity;
		} else {

			logger.error("Error occurred. HttpEntity is null and no respone is recevied for the URL." + url);
			return null;

		}

	}
    public static JsonObject findOrCreateTag(RallyRestApi restApi, String tagName) throws IOException {
        // Check if the tag already exists
        QueryRequest tagRequest = new QueryRequest("Tag");
        tagRequest.setQueryFilter(new QueryFilter("Name", "=", tagName));
        QueryResponse tagResponse = restApi.query(tagRequest);

        if (tagResponse.getResults().size() > 0) {
            return tagResponse.getResults().get(0).getAsJsonObject();
        }

        // If the tag doesn't exist, create it
        JsonObject newTag = new JsonObject();
        newTag.addProperty("Name", tagName);

        CreateRequest createTagRequest = new CreateRequest("Tag", newTag);
        CreateResponse createTagResponse = restApi.create(createTagRequest);

        if (createTagResponse.wasSuccessful()) {
            return createTagResponse.getObject();
        } else {
            System.out.println("Error occurred creating tag:");
            for (String error : createTagResponse.getErrors()) {
                System.out.println(error);
            }
            return null;
        }
    }
	

	/*
	 * Check if the Jira folder structure is available in Rally a. If folder
	 * structure is not available in rally, then create the same Jira folder
	 * structure in rally for the testcase b. If folder structure is available in
	 * rally, no action is required
	 */

	public static JsonObject createTestFolder(String[] folderHierarchy, String projectRef, String rallyBaseUrl,
			String rallyApiKey) {
		JsonObject lastFolder = null;
		String lastFolderRef = null;

		RallyRestApi restApi = null;
		try {
			restApi = new RallyRestApi(new URI(rallyBaseUrl), rallyApiKey);
			restApi.setApplicationName("CreateTestCaseApp");

			for (int i = 0; i < folderHierarchy.length; i++) {
				String folderName = folderHierarchy[i];
				if (folderName == null || folderName.trim().isEmpty()) {
					logger.info("Invalid folder name encountered: '" + folderName + "'");
					continue;
				}

				// If it's the top-level folder, ensure it is created as a parent folder
				if (i == 0) {
					// Check if the folder exists as a parent folder
					QueryRequest parentFolderExistenceRequest = new QueryRequest("testfolder");
					parentFolderExistenceRequest.setQueryFilter(new QueryFilter("Name", "=", folderName.trim())
							.and(new QueryFilter("Parent", "=", "null")));
					parentFolderExistenceRequest.setFetch(new Fetch("_ref", "Name", "Parent"));

					QueryResponse parentQueryResponse = restApi.query(parentFolderExistenceRequest);

					if (parentQueryResponse.wasSuccessful() && parentQueryResponse.getTotalResultCount() > 0) {
						// Folder exists as a parent folder
						lastFolder = parentQueryResponse.getResults().get(0).getAsJsonObject();
						lastFolderRef = lastFolder.get("_ref").getAsString();
						logger.info("Parent folder already exists: " + lastFolderRef);
					} else {
						// Folder does not exist as a parent folder, create it
						JsonObject newFolder = new JsonObject();
						newFolder.addProperty("Name", folderName.trim());
						newFolder.addProperty("Project", projectRef);

						CreateRequest createFolderRequest = new CreateRequest("testfolder", newFolder);
						CreateResponse createFolderResponse = restApi.create(createFolderRequest);

						if (createFolderResponse.wasSuccessful()) {
							lastFolderRef = createFolderResponse.getObject().get("_ref").getAsString();
							newFolder.addProperty("_ref", lastFolderRef);
							lastFolder = newFolder;
							logger.info("Successfully created parent folder: " + lastFolderRef);
						} else {
							logger.error("Error occurred creating parent folder.");
							for (String error : createFolderResponse.getErrors()) {
								System.out.println(error);
							}
							break;
						}
					}
				} else {
					// For subfolders, check and create under the last folder
					QueryRequest subFolderExistenceRequest = new QueryRequest("testfolder");
					subFolderExistenceRequest.setQueryFilter(new QueryFilter("Name", "=", folderName.trim())
							.and(new QueryFilter("Parent", "=", lastFolderRef)));
					subFolderExistenceRequest.setFetch(new Fetch("_ref", "Name", "Parent"));

					QueryResponse subQueryResponse = restApi.query(subFolderExistenceRequest);

					if (subQueryResponse.wasSuccessful() && subQueryResponse.getTotalResultCount() > 0) {
						// Folder exists as a subfolder
						lastFolder = subQueryResponse.getResults().get(0).getAsJsonObject();
						lastFolderRef = lastFolder.get("_ref").getAsString();
						logger.info("Subfolder already exists: " + lastFolderRef);
					} else {
						// Folder does not exist, create it as a subfolder
						JsonObject newFolder = new JsonObject();
						newFolder.addProperty("Name", folderName.trim());
						newFolder.addProperty("Project", projectRef);
						newFolder.addProperty("Parent", lastFolderRef);

						CreateRequest createFolderRequest = new CreateRequest("testfolder", newFolder);
						CreateResponse createFolderResponse = restApi.create(createFolderRequest);

						if (createFolderResponse.wasSuccessful()) {
							lastFolderRef = createFolderResponse.getObject().get("_ref").getAsString();
							newFolder.addProperty("_ref", lastFolderRef);
							lastFolder = newFolder;
							logger.info("Successfully created subfolder: " + lastFolderRef);
						} else {
							logger.error("Error occurred creating subfolder");
							for (String error : createFolderResponse.getErrors()) {
								System.out.println(error);
							}
							break;
						}
					}
				}
			}

			return lastFolder;

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			if (restApi != null) {
				try {
					restApi.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

// Implementation to update the TestCase Migrated in Jira to "true". User story US7382197

	public void updateTestCaseMigratedStatusinJira(boolean status) {

	}

	/*
	 * Implementation to get the GetAttachment URL and its Name This method will
	 * accept the Jira JSON response of a testcase key and store the attachment
	 * url:attachment filename in a Map The Map is <String,String> Generic
	 */

	public static Map<String, String> pharseJsonGetAttachmentUrlAndName(String jsonResponse) {

		Map<String, String> attachmentMap = new HashMap<String, String>(); // Initialize the Map object to store the Attachment URL and its Name
		JSONArray jArrayResponse = new JSONArray(jsonResponse); // Load the Jira Json response to JsonArray
		for (int i = 0; i < jArrayResponse.length(); i++) { // Loop through the Json Array and get the attachment URL and its name. And then, put them into the Map
			JSONObject jsonObject = jArrayResponse.getJSONObject(i);
			String url = jsonObject.getString("url");
			String name = jsonObject.getString("filename");
			attachmentMap.put(url, name);

		}

		return attachmentMap;

	}

	public static List<String> downloadFileAttachmentFromJiraTestCase(Map<String, String> attachmentMap,
			String tcAttachmentDownloadLocation, String jiraApiKey, String testcaseKey) throws IOException {

		List<String> filePaths = new ArrayList<String>();
		Path path = Paths.get(tcAttachmentDownloadLocation);
		if (!Files.exists(path)) {
			try {
				Files.createDirectories(path);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		for (Map.Entry<String, String> entry : attachmentMap.entrySet()) {

			String fileUrl = entry.getKey();
			String fileName = entry.getValue();
			HttpEntity response = Utils.getJiraResponse(fileUrl, jiraApiKey);

			if (response != null) {

				try (InputStream in = response.getContent()) {
					try {
						Files.copy(in, Paths.get(tcAttachmentDownloadLocation + "/" + fileName));
						filePaths.add(tcAttachmentDownloadLocation + "/" + fileName);
						EntityUtils.consume(response);
					} catch (IOException e) {
						logger.error("Failed to download the file attachments from Jira for Testcase level", e);
					}
					logger.info("File downloaded from Jira to tcAttachmentDownloadLocation. File Name:" + fileName);
				} catch (UnsupportedOperationException | IOException e1) {

					logger.error("Failed to download the file attachment " + fileName + " from Jira for Testcase level",
							e1);
				}
			} else {
				logger.error("Failed to download the file attachment " + fileName
						+ " from Jira for Testcase level. No Entity response found");

			}

		}
		return filePaths;

	}

	public static List<String> downloadFileAttachmentFromTestStep(String jsonResponse, String apiToken,
	        String testStepFileAttachmentLocationToBeSaved, String tC_Id, String baseURL) throws IOException {
	    List<String> filePaths = new ArrayList<String>();
	    JSONObject jsonObject = new JSONObject(jsonResponse);
	    JSONArray stepsArray = jsonObject.getJSONObject("testScript").getJSONArray("steps");
	    Path path = Paths.get(testStepFileAttachmentLocationToBeSaved);
	    if (!Files.exists(path)) {
	        try {
	            Files.createDirectories(path);
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	    }
	    for (int i = 0; i < stepsArray.length(); i++) {
	        JSONObject stepObject = stepsArray.getJSONObject(i);
	        int index = stepObject.getInt("index");
	        int stepNumber = index + 1;
	        if (stepObject.has("attachments")) {
	            JSONArray attachmentsArray = stepObject.getJSONArray("attachments");
	            for (int j = 0; j < attachmentsArray.length(); j++) {
	                JSONObject attachmentObject = attachmentsArray.getJSONObject(j);
	                int attachmentID = attachmentObject.getInt("id");
	                int attachmentNumber = j + 1;
	                String attachmentFileName = attachmentObject.getString("name");
	                String testStepAttachmentUrl = baseURL + "/rest/tests/1.0/attachment/" + attachmentID;
	                HttpEntity response = Utils.getJiraResponse(testStepAttachmentUrl, apiToken);
	                if (response != null) {
	                    try (InputStream in = response.getContent()) {
	                        Path filePath = Paths.get(testStepFileAttachmentLocationToBeSaved + "/" + stepNumber
	                                + "_" + attachmentNumber + "_" + attachmentFileName);
	                        if (Files.exists(filePath)) {
	                            filePath = Paths.get(testStepFileAttachmentLocationToBeSaved + "/" + stepNumber
	                                    + "_" + attachmentNumber + "_" + System.currentTimeMillis() + "_" + attachmentFileName);
	                        }
	                        try {
	                            Files.copy(in, filePath);
	                            filePaths.add(filePath.toString());
	                            EntityUtils.consume(response);
	                        } catch (IOException e) {
	                            logger.error("Failed to download the file attachments from Jira for Testcase level", e);
	                        }
	                        logger.info("File downloaded from Jira to tcAttachmentDownloadLocation. File Name:"
	                                + attachmentFileName);
	                    } catch (UnsupportedOperationException | IOException e1) {
	                        logger.error("Failed to download the file attachment " + attachmentFileName
	                                + " from Jira for Testcase level", e1);
	                    }
	                } else {
	                    logger.error("Failed to download the file attachment " + attachmentFileName
	                            + " from Jira for Testcase level. No Entity response found");
	                }
	            }
	        }
	    }
	    return filePaths;
	}

	public static List<String> downloadTestStepEmbeddedAttachments(String jsonResponse, String apiToken,
	        String testStepAttachmentLocationToBeSaved, String tC_Id, String baseURL, String columnName)
	        throws IOException {
	    List<String> filePaths = new ArrayList<String>();
	    JSONObject jsonObject = new JSONObject(jsonResponse);
	    JSONArray stepsArray = jsonObject.getJSONObject("testScript").getJSONArray("steps");
	    Path path = Paths.get(testStepAttachmentLocationToBeSaved);
	    if (!Files.exists(path)) {
	        try {
	            Files.createDirectories(path);
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	    }
	    for (int i = 0; i < stepsArray.length(); i++) {
	        JSONObject stepObject = stepsArray.getJSONObject(i);
	        int index = stepObject.getInt("index");
	        int stepNumber = index + 1;
	        if ((columnName.equals("description")) || (columnName.equals("testData")) || (columnName.equals("expectedResult"))) {
	            if (stepObject.has(columnName)) {
	                String htmlContent = stepObject.getString(columnName);
	                Document doc = Jsoup.parse(htmlContent);
	                Elements imgElements = doc.select("img");
	                if (!imgElements.isEmpty()) {
	                    int imageCount = 0;
	                    for (Element img : imgElements) {
	                        String imageUrl = img.attr("src");
	                        if (!imageUrl.isEmpty()) {
	                            imageCount++;
	                            String extractUrl = imageUrl.substring(2);
	                            String absoluteUrl = baseURL + extractUrl;
	                            HttpEntity response = Utils.getJiraResponse(absoluteUrl, apiToken);
	                            if (response != null) {
	                                try (InputStream in = response.getContent()) {
	                                    Path filePath = Paths.get(testStepAttachmentLocationToBeSaved + "/" + stepNumber
	                                            + "_" + imageCount + "_" + "_EmbbededFile_" + columnName + ".png");
	                                    if (Files.exists(filePath)) {
	                                        filePath = Paths.get(testStepAttachmentLocationToBeSaved + "/" + stepNumber
	                                                + "_" + imageCount + "_" + System.currentTimeMillis() + "_EmbbededFile_" + columnName + ".png");
	                                    }
	                                    try {
	                                        Files.copy(in, filePath);
	                                        filePaths.add(filePath.toString());
	                                        EntityUtils.consume(response);
	                                    } catch (IOException e) {
	                                        logger.error(
	                                                "Failed to download the file attachments from Jira for test step level",
	                                                e);
	                                    }
	                                    logger.info(
	                                            "File downloaded from Jira to tcAttachmentDownloadLocation. File Name:"
	                                                    + stepNumber + "_" + imageCount + "_" + "_EmbbededFile_"
	                                                    + columnName + ".png");
	                                } catch (UnsupportedOperationException | IOException e1) {
	                                    logger.error("Failed to download the file attachment " + stepNumber + "_"
	                                            + imageCount + "_" + "_EmbbededFile_" + columnName
	                                            + " from Jira for Testcase level", e1);
	                                }
	                            } else {
	                                logger.error("Failed to download the file attachment " + stepNumber + "_"
	                                        + imageCount + "_" + "_EmbbededFile_" + columnName
	                                        + " from Jira for test step level. No Entity response found");
	                            }
	                        }
	                    }
	                }
	            }
	        } else {
	            logger.error(
	                    "No Valid arugument is passed the method. it should be either description OR testData OR expectedResult");
	        }
	    }
	    return filePaths;
	}
	
	
    public static String getJsonString(JsonObject jsonObject, String key) {
        JsonElement element = jsonObject.get(key);
        if (element != null && !element.isJsonNull()) {
            if (element.isJsonArray()) {
                StringBuilder dataBuilder = new StringBuilder();
                JsonArray dataArray = element.getAsJsonArray();
                for (JsonElement jsonElement : dataArray) {
                    if (dataBuilder.length() > 0) {
                        dataBuilder.append(", ");
                    }
                    dataBuilder.append(jsonElement.getAsString());
                }
                return dataBuilder.toString();
            } else {
                return element.getAsString();
            }
        } else {
            logger.warn("Key {} not found or is null in JsonObject", key);
            return "";
        }
    }
	
	
	
	
	
	
	// Delete File Attachments

	public static void deleteAttachmentFileFromLocal(List<String> filePaths) {

		for (String filePath : filePaths) {

			File file = new File(filePath);
			if (file.exists()) {
				file.delete();
				logger.info("The file " + filePath + " is deleted for next attachment download run.");

			}

		}

	}

}
