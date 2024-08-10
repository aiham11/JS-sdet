 public String createRallyTestStep(String rallyTestcaseOID, JsonObject jiraTestStepJson) {
        String rallyTestStepOID = null;
        try (RallyRestApi restApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey)) {
            restApi.setApplicationName("CreateRallyTestStepApp");

            JsonObject newTestStep = new JsonObject();
            newTestStep.addProperty("TestCase", rallyTestcaseOID);
            newTestStep.addProperty("Index", jiraTestStepJson.get("index").getAsInt());
            newTestStep.addProperty("Input", jiraTestStepJson.get("description").getAsString());
            newTestStep.addProperty("ExpectedResult", jiraTestStepJson.get("expectedResult").getAsString());

            CreateRequest createRequest = new CreateRequest("teststep", newTestStep);
            CreateResponse createResponse = restApi.create(createRequest);

            if (createResponse.wasSuccessful()) {
                rallyTestStepOID = createResponse.getObject().get("_ref").getAsString();
                logger.info("Successfully created Rally test step: " + rallyTestStepOID);
            } else {
                logger.error("Error occurred creating Rally test step:");
                for (String error : createResponse.getErrors()) {
                    logger.error(error);
                }
            }
        } catch (URISyntaxException | IOException e) {
            logger.error("Exception while creating Rally test step", e);
        }
        return rallyTestStepOID;
    }
