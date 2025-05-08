    package com.qa.nal;
    
    // import javax.swing.text.html.parser.Element;
    
    import static io.restassured.RestAssured.given;
    
    // import com.qa.nal.utils.ExcelReader;
    import io.github.bonigarcia.wdm.WebDriverManager;
    import io.github.cdimascio.dotenv.Dotenv;
    import io.restassured.RestAssured;
    import io.restassured.path.json.JsonPath;
    import io.restassured.response.Response;
    import java.time.Duration;
    import java.util.ArrayList;
    import java.util.List;
    import java.util.Random;
    // import org.apache.xmlbeans.impl.xb.xsdschema.Public;
    import org.openqa.selenium.*;
    import org.openqa.selenium.chrome.ChromeDriver;
    import org.openqa.selenium.interactions.Actions;
    import org.openqa.selenium.support.ui.ExpectedConditions;
    import org.openqa.selenium.support.ui.FluentWait;
    import org.openqa.selenium.support.ui.Wait;
    import org.openqa.selenium.support.ui.WebDriverWait;
    import org.testng.Assert;
    import org.testng.Reporter;
    import org.testng.annotations.*;
    
    @Listeners({ com.qa.nal.listeners.ExtentTestNGListener.class })
    public class DiagnosticTests {

    private static boolean shouldFillDescription = false;
    private WebDriver driver;
    private Wait<WebDriver> wait;
    private String authToken = "";

    Dotenv dotenv = Dotenv.load();
    String username = dotenv.get("APP_USERNAME");
    String password = dotenv.get("PASSWORD");

    private void generateAuthToken() {
        try {
            if (username == null || password == null) {
                throw new IllegalStateException("USERNAME or PASSWORD not found in .env file");
            }

            RestAssured.baseURI = "https://dev-demo.neuron7.ai:8889";
            String loginPayload = String.format("{\"userName\": \"%s\", \"password\": \"%s\"}", username, password);

            Response response = given()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(loginPayload)
                .when()
                .post("/security/user/authenticate");

            if (response.getStatusCode() == 200) {
                // Try extracting token from header first
                String token = response.header("authorization");
                if (token == null || token.isEmpty()) {
                    // Fallback to JSON body
                    JsonPath json = response.jsonPath();
                    token = json.getString("token");
                    if (token == null || token.isEmpty()) {
                        token = json.getString("jwtToken");
                    }
                }
                if (token != null && !token.isEmpty()) {
                    authToken = "Bearer " + token;
                    System.out.println("✅ Auth token generated");
                } else {
                    System.out.println(
                        "⚠️ Auth token not found. Response body length: " + response.getBody().asString().length()
                    );
                }
            } else {
                System.out.println("❌ Failed to authenticate. Status: " + response.getStatusCode());
                System.out.println("Response Body: " + response.getBody().asString());
            }
        } catch (Exception e) {
            System.out.println("❌ Error generating auth token: " + e.getMessage());
        }
    }

    private boolean validateApiStatus(String baseUri, String endpoint, String methodType) {
        String apiName = endpoint.substring(endpoint.lastIndexOf('/') + 1);
        boolean isAuthRequest = endpoint.contains("/authenticate");
        try {
            if (isAuthRequest) {
                // re-call authenticate so we can measure timing & status
                long startTime = System.currentTimeMillis();
                generateAuthToken();
                long endTime = System.currentTimeMillis();

                String formattedDuration = String.format("%.3f", (endTime - startTime) / 1000.0);
                int statusCode = (authToken != null && !authToken.isEmpty()) ? 200 : 400;

                Reporter.log(
                    "API: " +
                    apiName +
                    " | Status Code: " +
                    statusCode +
                    " | Processing Time: " +
                    formattedDuration +
                    "s<br>",
                    true
                );

                JavascriptExecutor js = (JavascriptExecutor) driver;
                if (statusCode >= 200 && statusCode < 300) {
                    js.executeScript(
                        "alert('✅ ' + arguments[0] + ' API Passed! Processing Time: '+ arguments[1] + ' Second');",
                        apiName,
                        formattedDuration
                    );
                } else {
                    js.executeScript(
                        "alert('❌ ' + arguments[0] + ' API Failed! Status: " +
                        statusCode +
                        " Processing Time: '+ arguments[1] + ' Second');",
                        apiName,
                        formattedDuration
                    );
                }
                Thread.sleep(2000);
                driver.switchTo().alert().accept();

                return statusCode >= 200 && statusCode < 300;
            }

            if (authToken == null || authToken.isEmpty()) {
                generateAuthToken();
            }
            RestAssured.baseURI = baseUri;
            Response response;
            long startTime = System.currentTimeMillis();

            io.restassured.specification.RequestSpecification request = given()
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");

            if (!isAuthRequest && authToken != null && !authToken.isEmpty()) {
                request
                    .header("Authorization", authToken)
                    .header("n7-client-locale", "en")
                    .header("n7-client-type", "web");
            }
            System.out.println("Using token: " + authToken);

            switch (methodType.toUpperCase()) {
                case "GET":
                    response = request.when().get(endpoint);
                    break;
                case "POST":
                    if (endpoint.contains("/v2/service_request")) {
                        // service_request expects no JSON body
                        response = request.when().post(endpoint);
                    } else {
                        response = request.body("{}").when().post(endpoint);
                    }
                    break;
                case "PUT":
                    response = request.body("{}").when().put(endpoint);
                    break;
                case "DELETE":
                    response = request.when().delete(endpoint);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid HTTP method: " + methodType);
            }
            long endTime = System.currentTimeMillis();
            double durationInSeconds = (endTime - startTime) / 1000.0;
            String formattedDuration = String.format("%.3f", durationInSeconds);
            int statusCode = response.getStatusCode();

            Reporter.log(
                "API: " +
                apiName +
                " | Status Code: " +
                statusCode +
                " | Processing Time: " +
                formattedDuration +
                "s<br>",
                true
            );

            JavascriptExecutor js = (JavascriptExecutor) driver;
            if (statusCode >= 200 && statusCode < 300) {
                js.executeScript(
                    "alert('✅ ' + arguments[0] + ' API Passed! Processing Time: '+ arguments[1] + ' Second');",
                    apiName,
                    formattedDuration
                );
                Thread.sleep(2000);
                driver.switchTo().alert().accept();
                return true;
            } else {
                js.executeScript(
                    "alert('❌ ' + arguments[0] + ' API Failed! Status: " +
                    statusCode +
                    " Processing Time: '+ arguments[1] + ' Second');",
                    apiName,
                    formattedDuration
                );
                Thread.sleep(2000);
                driver.switchTo().alert().accept();
                return false;
            }
        } catch (Exception e) {
            System.out.println("❌ Error validating API: " + apiName + " - " + e.getMessage());
            return false;
        }
    }

    @BeforeClass
    public void setupAll() {
        WebDriverManager.chromedriver().setup();
        driver = new ChromeDriver();
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(20));
        wait = new FluentWait<>(driver)
            .withTimeout(Duration.ofSeconds(45))
            .pollingEvery(Duration.ofSeconds(3))
            .ignoring(NoSuchElementException.class);
    }

    private static boolean UIVerified = false;
    private static boolean ENVerified = false;

    @Test(priority = 1)
    public void navigateToLoginPage() {
        driver.get("https://dev-demo.neuron7.ai/#/account/login");

        UIVerified = validateApiStatus("https://dev-demo.neuron7.ai:8889", "/api/v1/config/ui", "Get");
        ENVerified = validateApiStatus("https://dev-demo.neuron7.ai", "/assets/i18n/en.json", "Get");

        driver.manage().window().maximize();

        Assert.assertTrue(driver.getCurrentUrl().contains("login"));
    }

    @Test(priority = 2, dependsOnMethods = "navigateToLoginPage")
    public void verifyUIAPI() {
        Assert.assertTrue(UIVerified, "UI API verification failed");
    }

    @Test(priority = 3, dependsOnMethods = "navigateToLoginPage")
    public void verifyENAPI() {
        Assert.assertTrue(ENVerified, "EN API verification failed");
    }

    @Test(priority = 4)
    public void performLogin() {
        WebElement usernameField = wait.until(
            ExpectedConditions.visibilityOfElementLocated(
                By.xpath(
                    "/html/body/app/account-layout/ng-component/body/div[2]/div/div[3]/div/ng-component/div/div/form/div[1]/input"
                )
            )
        );

        usernameField.sendKeys(username);

        WebElement passwordField = wait.until(
            ExpectedConditions.visibilityOfElementLocated(
                By.xpath(
                    "/html/body/app/account-layout/ng-component/body/div[2]/div/div[3]/div/ng-component/div/div/form/div[2]/input"
                )
            )
        );

        passwordField.sendKeys(password);

        WebElement loginButton = wait.until(
            ExpectedConditions.elementToBeClickable(
                By.xpath(
                    "/html/body/app/account-layout/ng-component/body/div[2]/div/div[3]/div/ng-component/div/div/form/div[3]/div/button"
                )
            )
        );

        loginButton.click();

        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

        wait.until(ExpectedConditions.urlContains("/app/new-home"));

        new WebDriverWait(driver, Duration.ofSeconds(30)).until(webDriver ->
                ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete")
            );

        Assert.assertTrue(driver.getCurrentUrl().contains("new-home"));
    }

    @Test(priority = 5)
    public void verifyAuthenticateAPI() {
        boolean authVerified = validateApiStatus(
            "https://dev-demo.neuron7.ai:8889",
            "/security/user/authenticate",
            "Post"
        );
        Assert.assertTrue(authVerified, "Authenticate API verification failed");
    }

    @Test(priority = 6)
    public void handleInitialPopup() {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id='modalCenter']/div/div")));

            WebElement cancelButton = wait.until(
                ExpectedConditions.elementToBeClickable(By.xpath("//*[@id='modalCenter']/div/div/div[2]/button"))
            );

            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", cancelButton);
            Assert.assertTrue(true);

            System.out.println("Modal Pop-UP handled successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.print("Exception :");
            Assert.fail("Assert Fail: ", e);
        }
    }

    @Test(priority = 7)
    public void navigateToIntelligentDiagnostics() {
        try {
            Actions actions = new Actions(driver);

            // 1. Hover over the collapsed sidebar to expand it.
            WebElement sidebar = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("/html/body/app/default-layout/div/aside/div[2]") // Update with actual
                    // locator
                )
            );
            Thread.sleep(2000);

            actions.moveToElement(sidebar).perform();
            Thread.sleep(2000); // Wait for the sidebar to expand

            // 2. Click on "Intelligent Diagnostics" using JavascriptExecutor.
            WebElement diagnosticsOption = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath("/html/body/app/default-layout/div/aside/div[2]/nav/ul[2]/li/a")
                )
            );
            // Update with the accurate locator

            Thread.sleep(2000);
            diagnosticsOption.click();

            Thread.sleep(2000); // Allow time for the dropdown to render
        } catch (Exception e) {
            e.printStackTrace();
            System.out.print("Exception :");
            Assert.fail("Assert Fail: ", e);
        }
    }

    private static boolean serviceRequestVerified = false;
    private static boolean getattributesVerified = false;
    private static boolean createVerified = false;

    @Test(priority = 9)
    public void createNewServiceRequest() {
        try {
            // 3. Click on "Service Request" from the dropdown.
            WebElement serviceRequest = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath("/html/body/app/default-layout/div/aside/div[2]/nav/ul[2]/li/ul[1]/li/a")
                )
            );
            // Update with the correct locator if needed

            Thread.sleep(2000);
            serviceRequest.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            serviceRequestVerified = validateApiStatus(
                "https://dev-demo.neuron7.ai:8889",
                "/v2/service_request",
                "Post"
            );

            wait.until(ExpectedConditions.urlContains("caseobject"));

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            Assert.assertTrue(
                driver.getCurrentUrl().contains("caseobject"),
                "Service Request page did not load as expected."
            );

            System.out.println("Successfully navigated to the Service Request page.");

            Thread.sleep(2000);

            Actions actions = new Actions(driver);

            WebElement createNew = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath(
                        "/html/body/app/default-layout/div/div/div/section/div[1]/app-list/mat-drawer-container/mat-drawer-content/div[2]/div/div/div/div[1]/div/div[2]/button[2]"
                    )
                )
            );

            actions.moveToElement(createNew).perform();

            Thread.sleep(2000);

            createNew.click();

            getattributesVerified = validateApiStatus(
                "https://dev-demo.neuron7.ai:8889",
                "/data/caseobject/v2/getattributes",
                "Get"
            );

            Thread.sleep(2000);

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"modalCenter\"]/div/div")));

            Thread.sleep(2000);

            WebElement picklist = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath("//*[@id='modalCenter']/div/div/div[1]/div/div/div[2]/div[1]/ng-select")
                )
            );
            picklist.click();

            Thread.sleep(2000);

            List<WebElement> picklistOptions = wait.until(
                ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath("//ng-dropdown-panel//div[@role='option']"))
            );

            WebElement descriptionField = driver.findElement(
                By.xpath("//*[@id=\"modalCenter\"]/div/div/div[1]/div/div/div[2]/div[2]/div/textarea")
            );

            boolean selected = false;
            for (WebElement picklistOption : picklistOptions) {
                if (picklistOption.getText().trim().equalsIgnoreCase("SDM2") && !shouldFillDescription) {
                    picklistOption.click();
                    shouldFillDescription = true;
                    selected = true;
                    break;
                } else if (picklistOption.getText().trim().equalsIgnoreCase("ALINITY_S") && shouldFillDescription) {
                    picklistOption.click();

                    Thread.sleep(2000);

                    descriptionField.sendKeys("Test Description.");

                    selected = true;
                    break;
                }
            }
            if (!selected && !picklistOptions.isEmpty()) {
                Random random = new Random();
                int randomIndex = random.nextInt(picklistOptions.size());

                WebElement picklistOption = picklistOptions.get(randomIndex);
                Thread.sleep(2000);

                picklistOption.click();

                shouldFillDescription = true;
            }

            Thread.sleep(2000);

            WebElement startDiagnosis = wait.until(
                ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[@id=\"modalCenter\"]/div/div/div[2]/btn-spinner/button")
                )
            );

            // replace with actual option
            Thread.sleep(2000);

            startDiagnosis.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));
            Thread.sleep(2000);

            createVerified = validateApiStatus("https://dev-demo.neuron7.ai:8889", "/data/caseobject/create", "Post");

            Thread.sleep(2000);
        } catch (Exception e) {
            System.out.println("Testt Failed: " + e.getMessage());
        }
    }

    @Test(priority = 8, dependsOnMethods = "createNewServiceRequest")
    public void serviceRequestAPI() {
        Assert.assertTrue(serviceRequestVerified, "service_request API verification failed");
    }

    @Test(priority = 10, dependsOnMethods = "createNewServiceRequest")
    public void getattributesAPI() {
        Assert.assertTrue(getattributesVerified, "getattributes API verification failed");
    }

    @Test(priority = 11, dependsOnMethods = "createNewServiceRequest")
    public void createAPI() {
        Assert.assertTrue(createVerified, "create API verification failed");
    }

    @Test(priority = 12)
    public void ExObsExInf() {
        try {
            Thread.sleep(2000);
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(webDriver ->
                ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete")
            );

            Thread.sleep(2000);

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("observation-card")));

            Thread.sleep(2000);

            List<WebElement> problemList = driver.findElements(By.className("observation-card"));

            String problemText = problemList.get(0).getText().trim();

            if (!problemText.contains("Are you seeing something else?")) {
                boolean newSolutionCreated = false;

                Thread.sleep(2000);

                List<WebElement> solutionList = driver.findElements(By.className("resolution"));

                if (!solutionList.isEmpty()) {
                    System.out.println("Solutions size: " + solutionList.size());

                    WebElement checkBox = wait.until(
                        ExpectedConditions.elementToBeClickable(
                            By.xpath(
                                "/html/body/app/default-layout/div/div/div/section/div[2]/app-new-diagnostic/mat-drawer-container/mat-drawer-content/div/div[2]/form/div[2]/div[2]/mat-card[1]/div/div/mat-card-header/i[2]"
                            )
                        )
                    );

                    Thread.sleep(2000);

                    checkBox.click();
                    WebElement btnSaveContinue = wait.until(
                        ExpectedConditions.elementToBeClickable(
                            By.xpath("//button//span[contains(text(),'Save & Continue')]")
                        )
                    );

                    Thread.sleep(2000);

                    btnSaveContinue.click();

                    wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                } else if (!newSolutionCreated) {
                    newSolutionCreated = true;

                    System.out.println("No solution found!");

                    Thread.sleep(2000);

                    WebElement newSolution = wait.until(
                        ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//h4[contains(text(),'Do you want to resolve with another solution')]")
                        )
                    );

                    newSolution.click();

                    Thread.sleep(2000);

                    WebElement txtSolution = wait.until(
                        ExpectedConditions.visibilityOfElementLocated(By.xpath("//ng-select//input"))
                    );

                    Thread.sleep(2000);

                    txtSolution.sendKeys("Solution");

                    WebElement btnSolutionCreate = wait.until(
                        ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(),'Create New')]"))
                    );

                    Thread.sleep(2000);

                    btnSolutionCreate.click();

                    wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                    Thread.sleep(2000);

                    WebElement btnSaveContinue = wait.until(
                        ExpectedConditions.elementToBeClickable(
                            By.xpath("//button//span[contains(text(),'Save & Continue')]")
                        )
                    );

                    Thread.sleep(2000);

                    btnSaveContinue.click();

                    wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                    Thread.sleep(2000);
                }
            } else {
                WebElement btnSomethingElse = wait.until(
                    ExpectedConditions.elementToBeClickable(
                        By.xpath(
                            "//mat-card//mat-card-header//div//mat-card-subtitle[contains(.,'Are you seeing something else?')]"
                        )
                    )
                );

                Thread.sleep(2000);

                btnSomethingElse.click();

                WebElement txtSomethingElse = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.xpath("//ng-select//input"))
                );

                Thread.sleep(2000);

                txtSomethingElse.sendKeys("Other problem");

                WebElement btnCreate = wait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(),'Create')]"))
                );

                Thread.sleep(2000);

                btnCreate.click();

                wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                WebElement txtSolution = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.xpath("//ng-select//input"))
                );

                Thread.sleep(2000);

                txtSolution.sendKeys("Solution");

                WebElement btnSolutionCreate = wait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(),'Create New')]"))
                );

                Thread.sleep(2000);

                btnSolutionCreate.click();

                wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                Thread.sleep(2000);

                WebElement btnSaveContinue = wait.until(
                    ExpectedConditions.elementToBeClickable(
                        By.xpath("//button//span[contains(text(),'Save & Continue')]")
                    )
                );

                Thread.sleep(2000);

                btnSaveContinue.click();

                wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                Thread.sleep(2000);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.print("Exception :");
            Assert.fail("Assert Fail: ", e);
        }
    }

    @Test(priority = 13)
    public void nwObsNwInf() {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("investigation-card")));

            WebElement delInvestigation = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath(
                        "/html/body/app/default-layout/div/div/div/section/div[2]/app-new-diagnostic/mat-drawer-container/mat-drawer-content/div/div[2]/form/div[1]/div[2]/div/mat-card/mat-card-header/div/mat-card-title/i"
                    )
                )
            );

            delInvestigation.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            Thread.sleep(2000);

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("/html/body/div[3]/div")));

            WebElement confirmDel = wait.until(
                ExpectedConditions.elementToBeClickable(By.xpath("/html/body/div[3]/div/div[3]/button[1]"))
            );

            confirmDel.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            Thread.sleep(2000);

            Thread.sleep(2000);
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(webDriver ->
                ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete")
            );

            Thread.sleep(2000);

            WebElement expandProblems = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath(
                        "/html/body/app/default-layout/div/div/div/section/div[2]/app-new-diagnostic/mat-drawer-container/mat-drawer-content/div/div[2]/form/div[1]/div[4]/button"
                    )
                )
            );

            Thread.sleep(2000);

            expandProblems.click();

            Thread.sleep(2000);

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("observation-card")));

            Thread.sleep(2000);

            List<WebElement> problemList = driver.findElements(By.className("observation-card"));

            String problemText = problemList.get(0).getText().trim();

            if (!problemText.contains("Are you seeing something else?")) {
                WebElement btnSomethingElse = wait.until(
                    ExpectedConditions.elementToBeClickable(
                        By.xpath(
                            "//mat-card//mat-card-header//div//mat-card-subtitle[contains(.,'Are you seeing something else?')]"
                        )
                    )
                );

                Thread.sleep(2000);

                btnSomethingElse.click();

                WebElement txtSomethingElse = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.xpath("//ng-select//input"))
                );

                Thread.sleep(2000);

                txtSomethingElse.sendKeys("Other problem");

                WebElement btnCreate = wait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(),'Create')]"))
                );

                Thread.sleep(2000);

                btnCreate.click();

                wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                WebElement txtSolution = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.xpath("//ng-select//input"))
                );

                Thread.sleep(2000);

                txtSolution.sendKeys("Solution");

                WebElement btnSolutionCreate = wait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(),'Create New')]"))
                );

                Thread.sleep(2000);

                btnSolutionCreate.click();

                wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                Thread.sleep(2000);

                WebElement btnSaveContinue = wait.until(
                    ExpectedConditions.elementToBeClickable(
                        By.xpath("//button//span[contains(text(),'Save & Continue')]")
                    )
                );

                Thread.sleep(2000);

                btnSaveContinue.click();

                wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                Thread.sleep(2000);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.print("Exception :");
            Assert.fail("Assert Fail: ", e);
        }
    }

    @Test(priority = 14)
    public void ExObsNwInf() {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("investigation-card")));

            WebElement delInvestigation = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath(
                        "/html/body/app/default-layout/div/div/div/section/div[2]/app-new-diagnostic/mat-drawer-container/mat-drawer-content/div/div[2]/form/div[1]/div[2]/div/mat-card/mat-card-header/div/mat-card-title/i"
                    )
                )
            );

            delInvestigation.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            Thread.sleep(2000);

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("/html/body/div[3]/div")));

            WebElement confirmDel = wait.until(
                ExpectedConditions.elementToBeClickable(By.xpath("/html/body/div[3]/div/div[3]/button[1]"))
            );

            confirmDel.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            Thread.sleep(2000);

            Thread.sleep(2000);
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(webDriver ->
                ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete")
            );

            Thread.sleep(2000);

            WebElement expandProblems = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath(
                        "/html/body/app/default-layout/div/div/div/section/div[2]/app-new-diagnostic/mat-drawer-container/mat-drawer-content/div/div[2]/form/div[1]/div[4]/button"
                    )
                )
            );

            Thread.sleep(2000);

            expandProblems.click();

            Thread.sleep(2000);

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("observation-card")));

            Thread.sleep(2000);

            List<WebElement> problemList = driver.findElements(By.className("observation-card"));

            String problemText = problemList.get(0).getText().trim();

            if (!problemText.contains("Are you seeing something else?")) {
                Thread.sleep(2000);

                Thread.sleep(2000);

                WebElement newSolution = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                        By.xpath("//h4[contains(text(),'Do you want to resolve with another solution')]")
                    )
                );

                newSolution.click();

                Thread.sleep(2000);

                WebElement txtSolution = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.xpath("//ng-select//input"))
                );

                Thread.sleep(2000);

                txtSolution.sendKeys("Solution");

                WebElement btnSolutionCreate = wait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(),'Create New')]"))
                );

                Thread.sleep(2000);

                btnSolutionCreate.click();

                wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                Thread.sleep(2000);

                WebElement btnSaveContinue = wait.until(
                    ExpectedConditions.elementToBeClickable(
                        By.xpath("//button//span[contains(text(),'Save & Continue')]")
                    )
                );

                Thread.sleep(2000);

                btnSaveContinue.click();

                wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                Thread.sleep(2000);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.print("Exception :");
            Assert.fail("Assert Fail: ", e);
        }
    }

    @Test(priority = 15)
    public void nwObsExInf() {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("investigation-card")));

            WebElement delInvestigation = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath(
                        "/html/body/app/default-layout/div/div/div/section/div[2]/app-new-diagnostic/mat-drawer-container/mat-drawer-content/div/div[2]/form/div[1]/div[2]/div/mat-card/mat-card-header/div/mat-card-title/i"
                    )
                )
            );

            delInvestigation.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            Thread.sleep(2000);

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("/html/body/div[3]/div")));

            WebElement confirmDel = wait.until(
                ExpectedConditions.elementToBeClickable(By.xpath("/html/body/div[3]/div/div[3]/button[1]"))
            );

            confirmDel.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            Thread.sleep(2000);

            Thread.sleep(2000);
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(webDriver ->
                ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete")
            );

            Thread.sleep(2000);

            WebElement expandProblems = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath(
                        "/html/body/app/default-layout/div/div/div/section/div[2]/app-new-diagnostic/mat-drawer-container/mat-drawer-content/div/div[2]/form/div[1]/div[4]/button"
                    )
                )
            );

            Thread.sleep(2000);

            expandProblems.click();

            Thread.sleep(2000);

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("observation-card")));

            Thread.sleep(2000);

            List<WebElement> problemList = driver.findElements(By.className("observation-card"));

            String problemText = problemList.get(0).getText().trim();

            if (!problemText.contains("Are you seeing something else?")) {
                WebElement btnSomethingElse = wait.until(
                    ExpectedConditions.elementToBeClickable(
                        By.xpath(
                            "//mat-card//mat-card-header//div//mat-card-subtitle[contains(.,'Are you seeing something else?')]"
                        )
                    )
                );

                Thread.sleep(2000);

                btnSomethingElse.click();

                WebElement txtSomethingElse = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.xpath("//ng-select//input"))
                );

                Thread.sleep(2000);

                txtSomethingElse.sendKeys("Other Problems");

                WebElement btnCreate = wait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(),'Create')]"))
                );

                Thread.sleep(2000);

                btnCreate.click();

                wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                Thread.sleep(2000);

                WebElement txtSolution = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.xpath("//ng-select//input"))
                );

                Thread.sleep(2000);

                txtSolution.sendKeys("Solution");

                Thread.sleep(2000);

                List<WebElement> existingInferences;
                try {
                    existingInferences = new WebDriverWait(driver, Duration.ofSeconds(5)).until(
                        ExpectedConditions.presenceOfAllElementsLocatedBy(
                            By.xpath("//ng-dropdown-panel//div//div//div[@role='option']")
                        )
                    );
                } catch (TimeoutException te) {
                    existingInferences = new ArrayList<>();
                }

                if (!existingInferences.isEmpty()) {
                    Random random = new Random();
                    int randomIndex = random.nextInt(existingInferences.size());

                    WebElement existingInference = existingInferences.get(randomIndex);
                    Thread.sleep(2000);

                    existingInference.click();

                    Thread.sleep(2000);

                    WebElement btnSaveClose = wait.until(
                        ExpectedConditions.elementToBeClickable(
                            By.xpath("//button//span[contains(text(),'Save & Close')]")
                        )
                    );

                    btnSaveClose.click();

                    wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                    Thread.sleep(2000);

                    WebElement btnCopy = wait.until(
                        ExpectedConditions.elementToBeClickable(
                            By.xpath("//mat-dialog-container//div//button[contains(text(),'Copy')]")
                        )
                    );

                    btnCopy.click();

                    wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                } else {
                    Thread.sleep(2000);

                    WebElement btnSolutionCreate = wait.until(
                        ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(),'Create New')]"))
                    );

                    Thread.sleep(2000);

                    btnSolutionCreate.click();

                    wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                    Thread.sleep(2000);

                    WebElement btnSaveClose = wait.until(
                        ExpectedConditions.elementToBeClickable(
                            By.xpath("//button//span[contains(text(),'Save & Close')]")
                        )
                    );

                    btnSaveClose.click();

                    wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                    Thread.sleep(2000);

                    WebElement btnCopy = wait.until(
                        ExpectedConditions.elementToBeClickable(
                            By.xpath("//mat-dialog-container//div//button[contains(text(),'Copy')]")
                        )
                    );

                    btnCopy.click();

                    wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));
                }

                Thread.sleep(2000);

                wait.until(ExpectedConditions.urlContains("/app/new-home"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.print("Exception :");
            Assert.fail("Assert Fail: ", e);
        }
    }

    @Test(priority = 16)
    public void withDescription() {
        try {
            Thread.sleep(2000);

            handleInitialPopup();

            Actions actions = new Actions(driver);

            WebElement sidebar = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("/html/body/app/default-layout/div/aside/div[2]")
                )
            );

            Thread.sleep(2000);

            actions.moveToElement(sidebar).perform();
            Thread.sleep(2000); // Wait for the sidebar to expand

            createNewServiceRequest();

            Thread.sleep(2000);

            ExObsExInf();

            Thread.sleep(2000);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.print("Exception :");
            Assert.fail("Assert Fail: ", e);
        }
    }

    @Test(priority = 17)
    public void obsInbox() {
        try {
            // 1st

            Thread.sleep(2000);

            driver.get("https://dev-demo.neuron7.ai/#/app/new-home");

            new WebDriverWait(driver, Duration.ofSeconds(30)).until(ExpectedConditions.urlContains("/app/new-home"));

            Thread.sleep(2000);

            handleInitialPopup();

            System.out.println("Pop-up Canceled.");

            Thread.sleep(2000);

            Actions actions = new Actions(driver);

            WebElement sidebar = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("/html/body/app/default-layout/div/aside/div[2]")
                )
            );
            
            Thread.sleep(2000);

            actions.moveToElement(sidebar).perform();
            Thread.sleep(2000);
            System.out.println("Opened Side Bar.");

            // 3. Click on "Observation Inbox" from the dropdown.
            WebElement observationInbox = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath("/html/body/app/default-layout/div/aside/div[2]/nav/ul[2]/li/ul[5]/li/a")
                )
            );
            // Update with the correct locator if needed

            Thread.sleep(2000);
            observationInbox.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            System.out.println("Clicked Observation Inbox.");

            wait.until(ExpectedConditions.urlContains("observation-inbox"));
            Assert.assertTrue(
                driver.getCurrentUrl().contains("observation-inbox"),
                "Observation Inbox page did not load as expected."
            );

            System.out.println("Successfully navigated to the Observation Inbox page.");

            Thread.sleep(2000);

            WebElement createNew = wait.until(
                ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(),'Create New')]"))
            );

            actions.moveToElement(createNew).perform();

            Thread.sleep(2000);

            createNew.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            System.out.println("clicked create new.");

            Thread.sleep(2000);

            wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"modalCenter\"]/div/div/div[1]"))
            );

            Thread.sleep(2000);

            WebElement picklist = wait.until(
                ExpectedConditions.elementToBeClickable(By.xpath("//ng-select//div[@role='combobox']"))
            );
            picklist.click();

            System.out.println("Clicked Picklist");

            Thread.sleep(2000);

            List<WebElement> picklistOptions = wait.until(
                ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath("//ng-dropdown-panel//div[@role='option']"))
            );

            Random random = new Random();
            int randomIndex = random.nextInt(picklistOptions.size());

            WebElement picklistOption = picklistOptions.get(randomIndex);
            Thread.sleep(2000);

            picklistOption.click();

            System.out.println("clicked option.");

            Thread.sleep(2000);

            WebElement nextBtn = wait.until(
                ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(),'Next')]"))
            );

            nextBtn.click();
            System.out.println("clicked next");

            Thread.sleep(2000);

            WebElement inputObservation = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.xpath("//ng-select[@bindlabel='observation']//input"))
            );

            inputObservation.sendKeys("Other Observation.");

            Thread.sleep(2000);

            WebElement createObservationBtn = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath("//ng-dropdown-panel//div//button[contains(text(),'Create New')]")
                )
            );

            Thread.sleep(2000);

            createObservationBtn.click();
            System.out.println("clicked create observaation");

            Thread.sleep(2000);

            WebElement inputInference = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.xpath("//ng-select[@bindlabel='inference']//input"))
            );

            inputInference.sendKeys("Other Inference.");

            Thread.sleep(2000);

            WebElement createInferenceBtn = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath("//ng-dropdown-panel//div//button[contains(text(),'Create New')]")
                )
            );

            createInferenceBtn.click();
            System.out.println("clicked create inference");

            Thread.sleep(2000);

            WebElement saveInferenceBtn = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath("//app-rich-text-editor//div//button[contains(text(),'Save')]")
                )
            );
            saveInferenceBtn.click();
            System.out.println("clicked save inference.");

            Thread.sleep(2000);

            WebElement nextBtn2 = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath("//*[@id=\"cdk-step-content-0-1\"]/form/div[2]/button[2]")
                )
            );

            nextBtn2.click();
            System.out.println("clicked next2");

            Thread.sleep(2000);

            WebElement saveObservation = wait.until(
                ExpectedConditions.elementToBeClickable(By.xpath("//*[@id=\"cdk-step-content-0-2\"]/div[2]/button"))
            );

            saveObservation.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            System.out.println("clicked save observstion");

            // 2nd

            Thread.sleep(2000);

            new WebDriverWait(driver, Duration.ofSeconds(30)).until(webDriver ->
                ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete")
            );

            Thread.sleep(2000);

            wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.xpath(
                        "/html/body/app/default-layout/div/div/div/section/div[1]/app-list/div[3]/div/div/div/div[2]/ag-grid-angular/div/div[1]/div[2]/div[3]/div[2]/div/div"
                    )
                )
            );

            Thread.sleep(2000);

            WebElement approveObservation = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath(
                        "/html/body/app/default-layout/div/div/div/section/div[1]/app-list/div[3]/div/div/div/div[2]/ag-grid-angular/div/div[1]/div[2]/div[3]/div[2]/div/div/div[2]/div[9]/div/span/app-icon-renderer/i[2]"
                    )
                )
            );

            approveObservation.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            System.out.println("clicked approve");

            Thread.sleep(2000);

            WebElement rejectObservation = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath(
                        "/html/body/app/default-layout/div/div/div/section/div[1]/app-list/div[3]/div/div/div/div[2]/ag-grid-angular/div/div[1]/div[2]/div[3]/div[2]/div/div/div[3]/div[9]/div/span/app-icon-renderer/i[3]"
                    )
                )
            );

            rejectObservation.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            System.out.println("clicked reject");

            Thread.sleep(2000);

            // approve

            WebElement bulbIcon = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath(
                        "/html/body/app/default-layout/div/div/div/section/div[1]/app-list/div[3]/div/div/div/div[2]/ag-grid-angular/div/div[1]/div[2]/div[3]/div[2]/div/div/div[1]/div[9]/div/span/app-icon-renderer/i[5]"
                    )
                )
            );

            bulbIcon.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            System.out.println("clicked bulbicon");

            Thread.sleep(2000);

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"modalCenter\"]/div")));

            Thread.sleep(2000);

            List<WebElement> listOptions = wait.until(
                ExpectedConditions.presenceOfAllElementsLocatedBy(
                    By.xpath(
                        "//div[@class='ag-center-cols-viewport']//div"
                    )
                )
            );

            System.out.println("List Options size: " + listOptions.size());

            if (!listOptions.isEmpty()) {
                for (int i = 0; i < listOptions.size(); i++) {
                    WebElement inference = listOptions.get(i);
                    System.out.println("Inference:" + inference.getText());

                    inference.click();
                    System.out.println("clicked inference.");

                    Thread.sleep(2000);

                    if (i == (listOptions.size() - 1)) {
                        // Approve
                        WebElement approveInference = wait.until(
                            ExpectedConditions.elementToBeClickable(
                                By.xpath(
                                    "//*[@id=\"modalCenter\"]/div/div/div[2]/div/mat-drawer-container/mat-drawer-content/div/div[2]/button[2]"
                                )
                            )
                        );

                        approveInference.click();
                        System.out.println("clicked approve");

                        Thread.sleep(2000);

                        WebElement confirtmApprove = wait.until(
                            ExpectedConditions.elementToBeClickable(By.className("swal2-confirm"))
                        );

                        confirtmApprove.click();

                        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                        System.out.println("clicked yes.");

                        Thread.sleep(2000);

                        // Delete

                        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                        inference.click();
                        System.out.println("clicked inference.");
    
                        Thread.sleep(2000);

                        WebElement deleteInference = wait.until(
                            ExpectedConditions.elementToBeClickable(
                                By.xpath(
                                    "//*[@id=\"modalCenter\"]/div/div/div[2]/div/mat-drawer-container/mat-drawer-content/div/div[2]/button[1]"
                                )
                            )
                        );

                        Thread.sleep(1000);
                        deleteInference.click();
                        System.out.println("clicked delete");

                        Thread.sleep(2000);

                        WebElement confirtmDelete = wait.until(
                            ExpectedConditions.elementToBeClickable(By.className("swal2-confirm"))
                        );

                        confirtmDelete.click();

                        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

                        System.out.println("clicked yes.");

                        Thread.sleep(2000);
                    }
                }

                Thread.sleep(2000);
            }

            WebElement closeBtn = wait.until(
                ExpectedConditions.elementToBeClickable(By.cssSelector("#modalCenter > div > div > div.modal-header.ng-star-inserted > button"))
            );

            closeBtn.click();
            System.out.println("clicked close.");

            Thread.sleep(2000);

            WebElement editObservation = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath(
                        "(/html/body/app/default-layout/div/div/div/section/div[1]/app-list/div[3]/div/div/div/div[2]/ag-grid-angular/div/div[1]/div[2]/div[3]/div[2]/div/div/div[1]/div[9]/div/span/app-icon-renderer/i[1])[1]"
                    )
                )
            );

            editObservation.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            System.out.println("clicked edit.");

            Thread.sleep(2000);

            wait.until(ExpectedConditions.urlContains("edit"));

            Thread.sleep(2000);

            WebElement saveBtn = wait.until(
                ExpectedConditions.elementToBeClickable(By.xpath("//div//button[contains(text(),'Save')]"))
            );

            saveBtn.click();

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));

            System.out.println("clicked save and approve.");

            Thread.sleep(2000);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.print("Exception :");
            Assert.fail("Assert Fail: ", e);
        }
    }

    @AfterClass
    public void tearDown() {
        if (driver != null) {
            try {
                Thread.sleep(2000);
            } catch (Exception e) {
                Thread.currentThread().interrupt();

                e.printStackTrace();
            System.out.print("Exception :");
            Assert.fail("Assert Fail: ", e);
            }

            driver.quit();

            System.out.println("Test execution completed.");
        }
    }
}
