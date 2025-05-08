	package com.qa.nal;
	import static io.restassured.RestAssured.given;
	import com.qa.nal.listeners.ExtentTestNGListener;
	import com.qa.nal.utils.ExcelReader;
	import io.github.bonigarcia.wdm.WebDriverManager;
	import io.github.cdimascio.dotenv.Dotenv;
	import io.restassured.RestAssured;
	import io.restassured.path.json.JsonPath;
	import io.restassured.response.Response;
	import java.time.Duration;
	import java.util.HashMap;
	import java.util.List;
	import java.util.Map;
	import java.util.Random;
	import org.openqa.selenium.*;
	import org.openqa.selenium.chrome.ChromeDriver;
	import org.openqa.selenium.interactions.Actions;
	import org.openqa.selenium.support.ui.ExpectedConditions;
	import org.openqa.selenium.support.ui.FluentWait;
	import org.openqa.selenium.support.ui.Wait;
	// import org.openqa.selenium.support.ui.WebDriverWait;
	import org.testng.Assert;
	import org.testng.Reporter;
	import org.testng.annotations.*;
	
	@Listeners({ com.qa.nal.listeners.ExtentTestNGListener.class })
	public class SearchTests {private WebDriver driver;
	private Wait<WebDriver> wait;
	
	private String authToken = "";
	
	/**
	 * Generates or refreshes the auth token.
	 */
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
							.body(loginPayload)
							.when()
							.post("/security/user/authenticate");
	
					if (response.statusCode() == 200) {
							String token = response.header("authorization");
							if (token == null || token.isEmpty()) {
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
									System.err.println("⚠️ Auth token not found. Body: " + response.getBody().asString());
							}
					} else {
							System.err.println("❌ Failed to authenticate. Status: " + response.statusCode());
							System.err.println("Body: " + response.getBody().asString());
					}
			} catch (Exception e) {
					System.err.println("❌ Error generating auth token: " + e.getMessage());
			}
	}
	
	/**
	 * Validates an API's status code and logs + shows popup.
	 *
	 * @param baseUri  Base URI (including port if needed)
	 * @param endpoint Path or full URL endpoint
	 * @param method   HTTP method (GET, POST, PUT, DELETE)
	 * @param bodyJson JSON body for POST/PUT, or null for none
	 * @return true if status is 2xx
	 */
	private boolean validateApiStatus(String baseUri, String endpoint, String method, String bodyJson) {
			String apiName = endpoint.substring(endpoint.lastIndexOf('/') + 1);
			try {
					// Auth endpoint special handling
					if (endpoint.contains("/authenticate")) {
							long t0 = System.currentTimeMillis();
							generateAuthToken();
							long t1 = System.currentTimeMillis();
							int code = (authToken != null && !authToken.isEmpty()) ? 200 : 400;
							String dur = String.format("%.3f", (t1 - t0) / 1000.0);
							Reporter.log(
									String.format("API: %s | Status Code: %d | Processing Time: %ss<br>", apiName, code, dur),
									true
							);
	
							ExtentTestNGListener.getTest()
									.info("API: " + apiName + " | Status Code: " + code + " | Processing Time: " + dur + "s");
	
							showAlert(apiName, code, dur);
							return code >= 200 && code < 300;
					}
	
					if (authToken == null || authToken.isEmpty()) generateAuthToken();
					RestAssured.baseURI = baseUri;
					long start = System.currentTimeMillis();
					io.restassured.specification.RequestSpecification req = given()
							.header("Accept", "application/json")
							.header("Content-Type", "application/json")
							.header("Authorization", authToken)
							.header("n7-client-locale", "en")
							.header("n7-client-type", "web");
	
					Response r;
					switch (method.toUpperCase()) {
							case "GET":
									r = req.get(endpoint);
									break;
							case "POST":
									r = req.body(bodyJson != null ? bodyJson : "{}").post(endpoint);
									break;
							case "PUT":
									r = req.body(bodyJson != null ? bodyJson : "{}").put(endpoint);
									break;
							case "DELETE":
									r = req.delete(endpoint);
									break;
							default:
									throw new IllegalArgumentException("Invalid HTTP method: " + method);
					}
					long end = System.currentTimeMillis();
					String dur = String.format("%.3f", (end - start) / 1000.0);
					int status = r.getStatusCode();
	
					Reporter.log(
							String.format("API: %s | Status Code: %d | Processing Time: %ss<br>", apiName, status, dur),
							true
					);
	
					ExtentTestNGListener.getTest()
							.info("API: " + apiName + " | Status Code: " + status + " | Processing Time: " + dur + "s");
	
					showAlert(apiName, status, dur);
					return status >= 200 && status < 300;
			} catch (Exception e) {
					System.err.println("❌ Error validating API " + apiName + " - " + e.getMessage());
					return false;
			}
	}
	
	private boolean validateApiStatus(String baseUri, String endpoint, String method) {
			return validateApiStatus(baseUri, endpoint, method, null);
	}
	
	/**
	 * Injects a non-blocking toast message into the page, auto-dismissed after 3s.
	 */
	private void showAlert(String apiName, int status, String duration) {
			try {
					String icon = (status >= 200 && status < 300) ? "✅" : "❌";
					JavascriptExecutor js = (JavascriptExecutor) driver;
					// Build a JS string that uses single-quotes inside Java's double-quoted literal
					String script =
							"alert('" +
							icon +
							" ' + arguments[0] + ' API ' + " +
							"(arguments[1]>=200&&arguments[1]<300?'Passed!':'Failed! Status: '+arguments[1]) + " +
							"' Processing Time: ' + arguments[2] + ' Second');";
					js.executeScript(script, apiName, status, duration);
					Thread.sleep(2000);
					driver.switchTo().alert().accept();
			} catch (InterruptedException ignored) {}
	}
	
	@BeforeClass
	public void setupAll() {
			WebDriverManager.chromedriver().setup();
			driver = new ChromeDriver();
			driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
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
	
			ExtentTestNGListener.getTest().info("Navigated to login page");
	
			UIVerified = validateApiStatus("https://dev-demo.neuron7.ai:8889", "/api/v1/config/ui", "Get");
			ENVerified = validateApiStatus("https://dev-demo.neuron7.ai", "/assets/i18n/en.json", "Get");
	
			driver.manage().window().maximize();
	
			Assert.assertTrue(driver.getCurrentUrl().contains("login"));
	}
	
	@Test(priority = 2, dependsOnMethods = "navigateToLoginPage")
	public void verifyUIAPI() {
			ExtentTestNGListener.getTest().info("Verifying UI API status");
	
			Assert.assertTrue(UIVerified, "UI API verification failed");
	}
	
	@Test(priority = 3, dependsOnMethods = "navigateToLoginPage")
	public void verifyENAPI() {
			ExtentTestNGListener.getTest().info("Verifying EN API status");
			Assert.assertTrue(ENVerified, "EN API verification failed");
	}
	
	@Test(priority = 4)
	public void performLogin() {
			ExtentTestNGListener.getTest().info("Performing login using UI");
	
			WebElement usernameField = wait.until(
					ExpectedConditions.visibilityOfElementLocated(
							By.cssSelector(
									"body > div.login-page > div > div:nth-child(3) > div > ng-component > div > div > form > div:nth-child(1) > input"
							)
					)
			);
	
			usernameField.sendKeys(username);
	
			WebElement passwordField = wait.until(
					ExpectedConditions.visibilityOfElementLocated(
							By.cssSelector(
									"body > div.login-page > div > div:nth-child(3) > div > ng-component > div > div > form > div:nth-child(2) > input"
							)
					)
			);
	
			passwordField.sendKeys(password);
	
			WebElement loginButton = wait.until(
					ExpectedConditions.elementToBeClickable(
							By.cssSelector(
									"body > div.login-page > div > div:nth-child(3) > div > ng-component > div > div > form > div:nth-child(3) > div > button"
							)
					)
			);
	
			loginButton.click();
	
			wait.until(ExpectedConditions.urlContains("/app/new-home"));
	
			Assert.assertTrue(driver.getCurrentUrl().contains("new-home"));
	}
	
	@Test(priority = 5)
	public void verifyAuthenticateAPI() {
			ExtentTestNGListener.getTest().info("Verifying /authenticate API");
	
			validateApiStatus("https://dev-demo.neuron7.ai:8889", "/security/user/authenticate", "Post");
	}
	
	@Test(priority = 6)
	public void handleInitialPopup() {
			try {
					ExtentTestNGListener.getTest().info("Handling initial modal pop-up if present");
	
					wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("modal-content")));
	
					System.out.println("Modal Pop-Up found");
	
					WebElement cancelButton = wait.until(
							ExpectedConditions.elementToBeClickable(By.className("btn-link"))
					);
					System.out.println("Modal Pop-UP handled successfully.");
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", cancelButton);
					Assert.assertTrue(wait.until(ExpectedConditions.invisibilityOfElementLocated(By.className("modal-content"))), "Modal pop-up is still visible.");
	
			} catch (Exception e) {
					Assert.fail("Popup handling failed: " + e.getMessage());
			}
	}
	
	@Test(priority = 7)
	public void navigateToIntelligentSearch() {
			try {
					ExtentTestNGListener.getTest().info("Navigating to Intelligent Search section");
	
					// Wait for card to be clickable using text content
					WebElement searchCard = wait.until(
							ExpectedConditions.elementToBeClickable(
									By.cssSelector("body > app > default-layout > div > div > div > section > div.container-fluid.main-router-outlet.p-0 > app-layout > div > div:nth-child(1)")
								 
								 
							)
					);
	
					searchCard.click();
	
					// Verify URL with explicit wait
					wait.until(ExpectedConditions.urlContains("int-answer"));
	
					Assert.assertTrue(driver.getCurrentUrl().contains("int-answer"));
			} catch (Exception e) {
					Assert.fail();
			}
	}
	
	@Test(priority = 8)
	public void verifyIntelligent_AnswerAPI() {
			ExtentTestNGListener.getTest().info("Validating /INTELLIGENT_ANSWER API");
	
			validateApiStatus("https://dev-demo.neuron7.ai:8889", "/data/app-config/module/INTELLIGENT_ANSWER", "Get");
	}
	
	private static boolean pushPositiveFeedbackVerified = false;
	private static boolean pushNegativeFeedbackVerified = false;
	private static boolean pushSpecificFeedbackVerified = false;
	private static boolean queryResultsVerified = false;
	
	@Test(priority = 9)
	public void testSearchQueries() {
			try {
					Thread.sleep(2000);
	
					ExtentTestNGListener.getTest().info("Executing search queries from Excel");
	
					List<String> searchQueries = ExcelReader.readQueriesFromExcel(
							"src/test/resources/SearchQueries.xlsx",
							"Search Query data"
					);
	
					if (searchQueries.isEmpty()) {
							Assert.fail("No queries found in Excel file.");
					}
	
					for (String query : searchQueries) {
							try {
									Thread.sleep(2000);
	
									WebElement searchField = wait.until(
											ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#searchTxt"))
										 
									);
									searchField.clear();
									searchField.sendKeys(query);
									searchField.sendKeys(Keys.ENTER);
	
									queryResultsVerified = validateApiStatus(
											"https://dev-demo.neuron7.ai:8889",
											"/intelligence/getQueryResults",
											"Post",
											"{\"text\":\"" + query + "\", \"tenant_id\":\"Ashwin\", \"is_rspl\":false}"
									);
	
									System.out.println("Searching for query: " + query);
	
									List<WebElement> searchResults = driver.findElements(
											By.className("btn-link")
										 
									);
	
									System.out.println("searchResults: " + searchResults.size());
	
									if (searchResults.isEmpty()) {
											System.out.println("No search results found.");
	
											ExtentTestNGListener.getTest().info("No Result Found In " + query);
									} else if (searchResults.size() >= 2) {
											Response qrResp = given()
													.header("Authorization", authToken)
													.header("Content-Type", "application/json")
													.body("{\"text\":\"" + query + "\",\"tenant_id\":\"Ashwin\",\"is_rspl\":false}")
													.post("https://dev-demo.neuron7.ai:8889/intelligence/getQueryResults");
	
											System.out.println("API JSON Response length: " + qrResp.getBody().asString().length());
	
											Reporter.log("getQueryResults Status: " + qrResp.getStatusCode(), true);
	
											ExtentTestNGListener.getTest().info("getQueryResults Status: " + qrResp.getStatusCode());
	
											JsonPath jp = qrResp.jsonPath();
											List<Map<String, Object>> resultData = jp.getList("data.QueryResponse");
	
											System.out.println("Parsed resultData size: " + jp.getList("data.QueryResponse").size());
	
											Random random = new Random();
											int randomIndex = random.nextInt(searchResults.size());
	
											Map<String, Object> selectedResultData = resultData.get(randomIndex);
											String feedbackId = (String) selectedResultData.get("feedback_id");
	
											for (int i = 0; i < 2; i++) {
													if (i == 0) {
															Random random1 = new Random();
															int randomIndex1 = random1.nextInt(searchResults.size());
	
															WebElement selectedResult = searchResults.get(randomIndex1);
	
															if (!selectedResult.getText().toLowerCase().contains(".html")) {
																	selectedResult.click();
	
																	wait.until(
																			ExpectedConditions.invisibilityOfElementLocated(
																					By.cssSelector(".loading-screen-wrapper")
																			)
																	);
	
																	Thread.sleep(2000);
	
																	wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
	
																	System.out.println("Opened a search result.");
	
																	Thread.sleep(2000);
	
																	if (selectedResult.getText().toLowerCase().contains(".pdf")) {
																			Thread.sleep(2000);
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			// search page number
																			WebElement pageCountElement = wait.until(
																					ExpectedConditions.visibilityOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-8.d-flex.justify-content-end.mt-3 > span"
																							)
																					)
																			);
	
																			// Get the text from the span
																			String pageText = pageCountElement.getText(); // Example:
																			// "2 of 4"
	
																			// Split and extract the total page
																			// count
																			String[] parts = pageText.split("of");
																			int totalPages = Integer.parseInt(parts[1].trim());
	
																			Random random2 = new Random();
																			int randomPageNumber = random2.nextInt(totalPages) + 1;
	
																			System.out.println(totalPages);
	
																			WebElement searchPageField = wait.until(
																					ExpectedConditions.visibilityOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-4.d-flex.justify-content-end.mt-2 > div > input"
																							)
																					)
																			);
																			searchPageField.clear();
																			searchPageField.sendKeys("" + randomPageNumber);
	
																			Thread.sleep(2000);
	
																			WebElement goButton = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-4.d-flex.justify-content-end.mt-2 > div > button"
																							)
																					)
																			);
	
																			goButton.click();
																			System.out.println("Go clicked");
																			Thread.sleep(2000);
	
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			WebElement download = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-4.d-flex.justify-content-end.mt-2 > button"
																							)
																					)
																			);
	
																			download.click();
																			System.out.println("Download clicked");
	
																			Thread.sleep(2000);
	
																			WebElement first = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-8.d-flex.justify-content-end.mt-3 > button:nth-child(1)"
																							)
																					)
																			);
	
																			first.click();
																			System.out.println("first clicked");
																			Thread.sleep(2000);
	
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			WebElement last = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-8.d-flex.justify-content-end.mt-3 > button:nth-child(5)"
																							)
																					)
																			);
	
																			last.click();
																			System.out.println("last clicked");
	
																			Thread.sleep(2000);
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			// clicking additional result
																			//xpath change leave for later
																			List<WebElement> additionalResultsButton = driver.findElements(
																					By.xpath("//div//mat-chip[contains(@role,'option')]")
																			);
	
																			System.out.println("Additional Result checked");
	
																			if (!additionalResultsButton.isEmpty()) {
																					Random random3 = new Random();
																					int randomIndex2 = random3.nextInt(additionalResultsButton.size());
	
																					System.out.println("Additional Result found");
	
																					System.out.println(
																							"Clicking 'Additional Results' for: " + selectedResult.getText()
																					);
																					additionalResultsButton.get(randomIndex2).click();
	
																					System.out.println("Additional Result clicked");
	
																					// Optionally wait for the
																					// additional results to expand
																					Thread.sleep(2000);
	
																					wait.until(
																							ExpectedConditions.presenceOfElementLocated(
																									By.cssSelector(
																											"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																									)
																							)
																					);
																			}
																	}
	
																	// allow modal to appear if it's going to
	
																	WebElement cancelButton = wait.until(
																			ExpectedConditions.elementToBeClickable(
																					By.cssSelector("#modalCenter > div > div > div.modal-header.ng-star-inserted > button > span")
																			)
																	);
	
																	cancelButton.click();
																	Thread.sleep(2000);
																	// Handle feedback form
																	WebElement thumbsDown = wait.until(
																			ExpectedConditions.elementToBeClickable(
																					By.cssSelector("#modalCenter > div > div > div.modal-body > div > i.fas.fa-thumbs-down.selected-down.fa-3x.ml-4")
																			)
																	);
	
																	Thread.sleep(2000);
																	thumbsDown.click();
	
																	String negPayload = String.format(
																			"{\"text\":\"%s\",\"feedback_id\":\"%s\",\"tenant_id\":\"Ashwin\"}",
																			query,
																			feedbackId
																	);
	
																	pushNegativeFeedbackVerified = validateApiStatus(
																			"https://dev-demo.neuron7.ai:8889",
																			"/intelligence/pushNegativeFeedback",
																			"Post",
																			negPayload
																	);
	
																	Thread.sleep(2000);
	
																	WebElement feedbackField = wait.until(
																			ExpectedConditions.visibilityOfElementLocated(
																					By.cssSelector("#feedback-div > form > div > div:nth-child(1) > textarea")
																			)
																	);
																	String comment = "Issue in this result!";
	
																	feedbackField.sendKeys(comment);
	
																	Thread.sleep(2000);
	
																	WebElement submit = driver.findElement(
																			By.cssSelector("#feedback-div > form > div > div.ml-2 > button")
																	);
	
																	submit.click();
	
																	String specPayload = String.format(
																			"{\"feedback_id\":\"%s\",\"comment\":\"%s\"}",
																			feedbackId,
																			comment
																	);
	
																	pushSpecificFeedbackVerified = validateApiStatus(
																			"https://dev-demo.neuron7.ai:8889",
																			"/intelligence/pushSpecificFeedback",
																			"Post",
																			specPayload
																	);
	
																	Thread.sleep(2000);
	
																	System.out.println("Submitted feedback.");
																	// Jump to next result
															} else {
																	/*
																	 * selectedResult.click();
																	 *
																	 * Thread.sleep(2000);
																	 *
																	 * wait.until(ExpectedConditions.
																	 * presenceOfElementLocated(By.tagName("body")))
																	 * ;
																	 *
																	 * System.out.println("Opened a search result."
																	 * );
																	 *
																	 * Thread.sleep(2000);
																	 *
																	 * WebElement cancelButton = wait.until(
																	 * ExpectedConditions.elementToBeClickable(
																	 * By.cssSelector(
																	 * "#modalCenter > div > div > div.modal-header.ng-star-inserted > button > span")
																	 * )
																	 * );
																	 *
																	 * cancelButton.click();
																	 */
	
																	System.out.println("Html found Skipping result.");
																	Thread.sleep(2000);
															}
													} else {
															Random random1 = new Random();
															int randomIndex1 = random1.nextInt(searchResults.size());
	
															WebElement selectedResult = searchResults.get(randomIndex1);
	
															if (!selectedResult.getText().toLowerCase().contains(".html")) {
																	selectedResult.click();
	
																	wait.until(
																			ExpectedConditions.invisibilityOfElementLocated(
																					By.cssSelector(".loading-screen-wrapper")
																			)
																	);
	
																	Thread.sleep(2000);
	
																	wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
																	System.out.println("Opened a search result.");
	
																	Thread.sleep(2000); // allow modal to appear if
																	// it's going to
	
																	if (selectedResult.getText().toLowerCase().contains(".pdf")) {
																			Thread.sleep(2000);
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			// search page number
																			WebElement pageCountElement = wait.until(
																					ExpectedConditions.visibilityOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-8.d-flex.justify-content-end.mt-3 > span"
																							)
																					)
																			);
	
																			// Get the text from the span
																			String pageText = pageCountElement.getText(); // Example:
																			// "2 of 4"
	
																			// Split and extract the total page
																			// count
																			String[] parts = pageText.split("of");
																			int totalPages = Integer.parseInt(parts[1].trim());
	
																			Random random2 = new Random();
																			int randomPageNumber = random2.nextInt(totalPages) + 1;
	
																			System.out.println(totalPages);
	
																			WebElement searchPageField = wait.until(
																					ExpectedConditions.visibilityOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-4.d-flex.justify-content-end.mt-2 > div > input"
																							)
																					)
																			);
																			searchPageField.clear();
																			searchPageField.sendKeys("" + randomPageNumber);
	
																			Thread.sleep(2000);
	
																			WebElement goButton = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-4.d-flex.justify-content-end.mt-2 > div > button"
																							)
																					)
																			);
	
																			goButton.click();
																			System.out.println("Go clicked");
																			Thread.sleep(2000);
	
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			WebElement download = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-4.d-flex.justify-content-end.mt-2 > button"
																							)
																					)
																			);
	
																			download.click();
																			System.out.println("Download clicked");
	
																			Thread.sleep(2000);
	
																			WebElement first = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-8.d-flex.justify-content-end.mt-3 > button:nth-child(1)"
																							)
																					)
																			);
	
																			first.click();
																			System.out.println("first clicked");
																			Thread.sleep(2000);
	
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			WebElement last = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-8.d-flex.justify-content-end.mt-3 > button:nth-child(5)"
																							)
																					)
																			);
	
																			last.click();
																			System.out.println("last clicked");
	
																			Thread.sleep(2000);
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			// clicking additional result
																			List<WebElement> additionalResultsButton = driver.findElements(
																					By.xpath("//div//mat-chip[contains(@role,'option')]")
																			);
	
																			System.out.println("Additional Result checked");
	
																			if (!additionalResultsButton.isEmpty()) {
																					Random random3 = new Random();
																					int randomIndex2 = random3.nextInt(additionalResultsButton.size());
	
																					System.out.println("Additional Result found");
	
																					System.out.println(
																							"Clicking 'Additional Results' for: " + selectedResult.getText()
																					);
																					additionalResultsButton.get(randomIndex2).click();
	
																					System.out.println("Additional Result clicked");
	
																					// Optionally wait for the
																					// additional results to expand
																					Thread.sleep(2000);
	
																					wait.until(
																							ExpectedConditions.presenceOfElementLocated(
																									By.cssSelector(
																											"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																									)
																							)
																					);
																			}
																	}
	
																	WebElement cancelButton = wait.until(
																			ExpectedConditions.elementToBeClickable(
																					By.cssSelector("#modalCenter > div > div > div.modal-header.ng-star-inserted > button > span")
																			)
																	);
	
																	cancelButton.click();
	
																	Thread.sleep(2000);
																	// Handle feedback form
																	WebElement thumbsUp = driver.findElement(
																			By.cssSelector("#modalCenter > div > div > div.modal-body > div.feedback-action.user-thums-selection.text-center.ng-star-inserted > i.fas.fa-thumbs-up.selected-up.fa-3x")
																	);
	
																	thumbsUp.click();
	
																	// serialize it back to JSON
																	String posPayload = new com.google.gson.Gson().toJson(selectedResultData);
	
																	pushPositiveFeedbackVerified = validateApiStatus(
																			"https://dev-demo.neuron7.ai:8889",
																			"/intelligence/pushPositiveFeedback",
																			"Post",
																			posPayload
																	);
																	Thread.sleep(2000);
															} else {
																	/*
																	 * selectedResult.click();
																	 *
																	 * Thread.sleep(2000);
																	 *
																	 * wait.until(ExpectedConditions.
																	 * presenceOfElementLocated(By.tagName("body")))
																	 * ;
																	 *
																	 * System.out.println("Opened a search result."
																	 * );
																	 *
																	 * Thread.sleep(2000);
																	 *
																	 * WebElement cancelButton = wait.until(
																	 * ExpectedConditions.elementToBeClickable(
																	 * By.cssSelector(
																	 * "#modalCenter > div > div > div.modal-header.ng-star-inserted > button > span")
																	 * )
																	 * );
																	 *
																	 * cancelButton.click();
																	 */
	
																	System.out.println("Html found Skipping result.");
																	Thread.sleep(2000);
															}
													}
											}
									} else {
											Response qrResp = given()
													.header("Authorization", authToken)
													.header("Content-Type", "application/json")
													.body("{\"text\":\"" + query + "\",\"tenant_id\":\"Ashwin\",\"is_rspl\":false}")
													.post("https://dev-demo.neuron7.ai:8889/intelligence/getQueryResults");
	
											System.out.println("API JSON Response length: " + qrResp.getBody().asString().length());
	
											Reporter.log("getQueryResults Status: " + qrResp.getStatusCode(), true);
	
											ExtentTestNGListener.getTest().info("getQueryResults Status: " + qrResp.getStatusCode());
	
											JsonPath jp = qrResp.jsonPath();
											List<Map<String, Object>> resultData = jp.getList("data.QueryResponse");
	
											System.out.println("Parsed resultData size: " + jp.getList("data.QueryResponse").size());
	
											Random random = new Random();
											int randomIndex = random.nextInt(searchResults.size());
	
											WebElement selectedResult = searchResults.get(randomIndex);
	
											Map<String, Object> selectedResultData = resultData.get(randomIndex);
											String feedbackId = (String) selectedResultData.get("feedback_id");
	
											for (int i = 0; i < 2; i++) {
													if (i == 0) {
															if (!selectedResult.getText().toLowerCase().contains(".html")) {
																	selectedResult.click();
																	wait.until(
																			ExpectedConditions.invisibilityOfElementLocated(
																					By.cssSelector(".loading-screen-wrapper")
																			)
																	);
	
																	Thread.sleep(2000);
	
																	wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
	
																	System.out.println("Opened a search result.");
	
																	Thread.sleep(2000); // allow modal to appear if
																	// it's going to
	
																	if (selectedResult.getText().toLowerCase().contains(".pdf")) {
																			Thread.sleep(2000);
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			// search page number
																			WebElement pageCountElement = wait.until(
																					ExpectedConditions.visibilityOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-8.d-flex.justify-content-end.mt-3 > span"
																							)
																					)
																			);
	
																			// Get the text from the span
																			String pageText = pageCountElement.getText(); // Example:
																			// "2 of 4"
	
																			// Split and extract the total page
																			// count
																			String[] parts = pageText.split("of");
																			int totalPages = Integer.parseInt(parts[1].trim());
	
																			Random random2 = new Random();
																			int randomPageNumber = random2.nextInt(totalPages) + 1;
	
																			System.out.println(totalPages);
	
																			WebElement searchPageField = wait.until(
																					ExpectedConditions.visibilityOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-4.d-flex.justify-content-end.mt-2 > div > input"
																							)
																					)
																			);
																			searchPageField.clear();
																			searchPageField.sendKeys("" + randomPageNumber);
	
																			Thread.sleep(2000);
	
																			WebElement goButton = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-4.d-flex.justify-content-end.mt-2 > div > button"
																							)
																					)
																			);
	
																			goButton.click();
																			System.out.println("Go clicked");
																			Thread.sleep(2000);
	
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			WebElement download = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-4.d-flex.justify-content-end.mt-2 > button"
																							)
																					)
																			);
	
																			download.click();
																			System.out.println("Download clicked");
	
																			Thread.sleep(2000);
	
																			WebElement first = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-8.d-flex.justify-content-end.mt-3 > button:nth-child(1)"
																							)
																					)
																			);
	
																			first.click();
																			System.out.println("first clicked");
																			Thread.sleep(2000);
	
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			WebElement last = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-8.d-flex.justify-content-end.mt-3 > button:nth-child(5)"
																							)
																					)
																			);
	
																			last.click();
																			System.out.println("last clicked");
	
																			Thread.sleep(2000);
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			// clicking additional result
																			List<WebElement> additionalResultsButton = driver.findElements(
																					By.xpath("//div//mat-chip[contains(@role,'option')]")
																			);
	
																			System.out.println("Additional Result checked");
	
																			if (!additionalResultsButton.isEmpty()) {
																					Random random3 = new Random();
																					int randomIndex2 = random3.nextInt(additionalResultsButton.size());
	
																					System.out.println("Additional Result found");
	
																					System.out.println(
																							"Clicking 'Additional Results' for: " + selectedResult.getText()
																					);
																					additionalResultsButton.get(randomIndex2).click();
	
																					System.out.println("Additional Result clicked");
	
																					// Optionally wait for the
																					// additional results to expand
																					Thread.sleep(2000);
	
																					wait.until(
																							ExpectedConditions.presenceOfElementLocated(
																									By.cssSelector(
																											"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																									)
																							)
																					);
																			}
																	}
	
																	WebElement cancelButton = wait.until(
																			ExpectedConditions.elementToBeClickable(
																					By.cssSelector("#modalCenter > div > div > div.modal-header.ng-star-inserted > button > span")
																			)
																	);
	
																	cancelButton.click();
	
																	// Handle feedback form
																	WebElement thumbsDown = wait.until(
																			ExpectedConditions.elementToBeClickable(
																					By.cssSelector("#modalCenter > div > div > div.modal-body > div > i.fas.fa-thumbs-down.selected-down.fa-3x.ml-4")
																			)
																	);
	
																	Thread.sleep(2000);
																	thumbsDown.click();
	
																	String negPayload = String.format(
																			"{\"text\":\"%s\",\"feedback_id\":\"%s\",\"tenant_id\":\"Ashwin\"}",
																			query,
																			feedbackId
																	);
	
																	pushNegativeFeedbackVerified = validateApiStatus(
																			"https://dev-demo.neuron7.ai:8889",
																			"/intelligence/pushNegativeFeedback",
																			"Post",
																			negPayload
																	);
	
																	Thread.sleep(2000);
	
																	WebElement feedbackField = wait.until(
																			ExpectedConditions.visibilityOfElementLocated(
																					By.cssSelector("#feedback-div > form > div > div:nth-child(1) > textarea")
																			)
																	);
	
																	String comment = "Issue in this result!";
	
																	feedbackField.sendKeys(comment);
	
																	Thread.sleep(2000);
	
																	WebElement submit = driver.findElement(
																			By.cssSelector("#feedback-div > form > div > div.ml-2 > button")
																	);
	
																	submit.click();
	
																	String specPayload = String.format(
																			"{\"feedback_id\":\"%s\",\"comment\":\"%s\"}",
																			feedbackId,
																			comment
																	);
	
																	pushSpecificFeedbackVerified = validateApiStatus(
																			"https://dev-demo.neuron7.ai:8889",
																			"/intelligence/pushSpecificFeedback",
																			"Post",
																			specPayload
																	);
	
																	Thread.sleep(2000);
	
																	System.out.println("Submitted feedback.");
															} else {
																	/*
																	 * selectedResult.click();
																	 *
																	 * Thread.sleep(2000);
																	 *
																	 * wait.until(ExpectedConditions.
																	 * presenceOfElementLocated(By.tagName("body")))
																	 * ;
																	 *
																	 * System.out.println("Opened a search result."
																	 * );
																	 *
																	 * Thread.sleep(2000);
																	 *
																	 * WebElement cancelButton = wait.until(
																	 * ExpectedConditions.elementToBeClickable(
																	 * By.cssSelector(
																	 * "#modalCenter > div > div > div.modal-header.ng-star-inserted > button > span")
																	 * )
																	 * );
																	 *
																	 * cancelButton.click();
																	 */
	
																	System.out.println("Html found Skipping result.");
																	Thread.sleep(2000);
															}
													} else {
															if (!selectedResult.getText().toLowerCase().contains(".html")) {
																	selectedResult.click();
	
																	wait.until(
																			ExpectedConditions.invisibilityOfElementLocated(
																					By.cssSelector(".loading-screen-wrapper")
																			)
																	);
	
																	Thread.sleep(2000);
	
																	wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
	
																	System.out.println("Opened a search result.");
	
																	Thread.sleep(2000); // allow modal to appear if
																	// it's going to
	
																	if (selectedResult.getText().toLowerCase().contains(".pdf")) {
																			Thread.sleep(2000);
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			// search page number
																			WebElement pageCountElement = wait.until(
																					ExpectedConditions.visibilityOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-8.d-flex.justify-content-end.mt-3 > span"
																							)
																					)
																			);
	
																			// Get the text from the span
																			String pageText = pageCountElement.getText(); // Example:
																			// "2 of 4"
	
																			// Split and extract the total page
																			// count
																			String[] parts = pageText.split("of");
																			int totalPages = Integer.parseInt(parts[1].trim());
	
																			Random random2 = new Random();
																			int randomPageNumber = random2.nextInt(totalPages) + 1;
	
																			System.out.println(totalPages);
	
																			WebElement searchPageField = wait.until(
																					ExpectedConditions.visibilityOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-4.d-flex.justify-content-end.mt-2 > div > input"
																							)
																					)
																			);
																			searchPageField.clear();
																			searchPageField.sendKeys("" + randomPageNumber);
	
																			Thread.sleep(2000);
	
																			WebElement goButton = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-4.d-flex.justify-content-end.mt-2 > div > button"
																							)
																					)
																			);
	
																			goButton.click();
																			System.out.println("Go clicked");
																			Thread.sleep(2000);
	
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			WebElement download = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-4.d-flex.justify-content-end.mt-2 > button"
																							)
																					)
																			);
	
																			download.click();
																			System.out.println("Download clicked");
	
																			Thread.sleep(2000);
	
																			WebElement first = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-8.d-flex.justify-content-end.mt-3 > button:nth-child(1)"
																							)
																					)
																			);
	
																			first.click();
																			System.out.println("first clicked");
																			Thread.sleep(2000);
	
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			WebElement last = wait.until(
																					ExpectedConditions.elementToBeClickable(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.row.navigation-pannel.ng-star-inserted > div.col-8.d-flex.justify-content-end.mt-3 > button:nth-child(5)"
																							)
																					)
																			);
	
																			last.click();
																			System.out.println("last clicked");
	
																			Thread.sleep(2000);
																			wait.until(
																					ExpectedConditions.presenceOfElementLocated(
																							By.cssSelector(
																									"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																							)
																					)
																			);
	
																			System.out.println("Wait");
																			Thread.sleep(2000);
	
																			// clicking additional result
																			List<WebElement> additionalResultsButton = driver.findElements(
																					By.xpath("//div//mat-chip[contains(@role,'option')]")
																			);
	
																			System.out.println("Additional Result checked");
	
																			if (!additionalResultsButton.isEmpty()) {
																					Random random3 = new Random();
																					int randomIndex2 = random3.nextInt(additionalResultsButton.size());
	
																					System.out.println("Additional Result found");
	
																					System.out.println(
																							"Clicking 'Additional Results' for: " + selectedResult.getText()
																					);
																					additionalResultsButton.get(randomIndex2).click();
	
																					System.out.println("Additional Result clicked");
	
																					// Optionally wait for the
																					// additional results to expand
																					Thread.sleep(2000);
	
																					wait.until(
																							ExpectedConditions.presenceOfElementLocated(
																									By.cssSelector(
																											"#modalCenter > div > div > div.modal-body > div.col-sm-12.row.p-0.ng-star-inserted > div > div > pdf-viewer > div > div > div"
																									)
																							)
																					);
																			}
																	}
	
																	WebElement cancelButton = wait.until(
																			ExpectedConditions.elementToBeClickable(
																					By.cssSelector("#modalCenter > div > div > div.modal-header.ng-star-inserted > button > span")
																			)
																	);
	
																	cancelButton.click();
	
																	Thread.sleep(2000);
	
																	// Handle feedback form
																	WebElement thumbsUp = driver.findElement(
																			By.cssSelector("#modalCenter > div > div > div.modal-body > div.feedback-action.user-thums-selection.text-center.ng-star-inserted > i.fas.fa-thumbs-up.selected-up.fa-3x")
																	);
	
																	thumbsUp.click();
	
																	String posPayload = new com.google.gson.Gson().toJson(selectedResultData);
	
																	pushPositiveFeedbackVerified = validateApiStatus(
																			"https://dev-demo.neuron7.ai:8889",
																			"/intelligence/pushPositiveFeedback",
																			"Post",
																			posPayload
																	);
	
																	Thread.sleep(2000);
															} else {
																	/*
																	 * selectedResult.click();
																	 *
																	 * Thread.sleep(2000);
																	 *
																	 * wait.until(ExpectedConditions.
																	 * presenceOfElementLocated(By.tagName("body")))
																	 * ;
																	 *
																	 * System.out.println("Opened a search result."
																	 * );
																	 *
																	 * Thread.sleep(2000);
																	 *
																	 * WebElement cancelButton = wait.until(
																	 * ExpectedConditions.elementToBeClickable(
																	 * By.cssSelector(
																	 * "#modalCenter > div > div > div.modal-header.ng-star-inserted > button > span")
																	 * )
																	 * );
																	 *
																	 * cancelButton.click();
																	 */
	
																	System.out.println("Html found Skipping result.");
																	Thread.sleep(2000);
															}
													}
											}
									}
	
									Thread.sleep(2000);
							} catch (Exception e) {
									e.printStackTrace();
	
									System.out.println("Error executing query: " + query + " - " + e.getMessage());
	
									Assert.fail("Test failed for query: " + query + " - " + e.getMessage());
							}
					}
	
					Thread.sleep(2000);
	
					WebElement searchField = wait.until(
							ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#searchTxt"))
					);
					searchField.clear();
					searchField.sendKeys("Circuit Pack Failed");
					searchField.sendKeys(Keys.ENTER);
	
					queryResultsVerified = validateApiStatus(
							"https://dev-demo.neuron7.ai:8889",
							"/intelligence/getQueryResults",
							"Post",
							"{\"text\":\"" + "Circuit Pack Failed" + "\", \"tenant_id\":\"Ashwin\", \"is_rspl\":false}"
					);
	
					System.out.println("Searching for query: Circuit Pack Failed");
	
					List<WebElement> searchResults = driver.findElements(By.className("btn-link"));
	
					System.out.println("searchResults size: " + searchResults.size());
	
					if (searchResults.isEmpty()) {
							System.out.println("No search results found.");
	
							ExtentTestNGListener.getTest().info("No Result Found In Circuit Pack Failed");
					} else {
							Response qrResp = given()
									.header("Authorization", authToken)
									.header("Content-Type", "application/json")
									.body("{\"text\":\"Circuit Pack Failed\",\"tenant_id\":\"Ashwin\",\"is_rspl\":false}")
									.post("https://dev-demo.neuron7.ai:8889/intelligence/getQueryResults");
	
							System.out.println("API JSON Response length: " + qrResp.getBody().asString().length());
	
							Reporter.log("getQueryResults Status: " + qrResp.getStatusCode(), true);
	
							ExtentTestNGListener.getTest().info("getQueryResults Status: " + qrResp.getStatusCode());
	
							JsonPath jp = qrResp.jsonPath();
							List<Map<String, Object>> resultData = jp.getList("data.QueryResponse");
	
							System.out.println("Parsed resultData size: " + jp.getList("data.QueryResponse").size());
	
							Random random1 = new Random();
							int randomIndex1 = random1.nextInt(searchResults.size());
	
							WebElement selectedResult = searchResults.get(randomIndex1);
	
							Map<String, Object> selectedResultData = resultData.get(randomIndex1);
							String feedbackId = (String) selectedResultData.get("feedback_id");
	
							if (selectedResult.getText().toLowerCase().contains(".html")) {
									// thumbs UP & down both
									WebElement thumbsUp = wait.until(
											ExpectedConditions.visibilityOfElementLocated(By.className("up-action"))
									);
	
									thumbsUp.click();
	
									Thread.sleep(2000);
	
									String posPayload = new com.google.gson.Gson().toJson(selectedResultData);
	
									pushPositiveFeedbackVerified = validateApiStatus(
											"https://dev-demo.neuron7.ai:8889",
											"/intelligence/pushPositiveFeedback",
											"Post",
											posPayload
									);
	
									WebElement thumbsDown = wait.until(
											ExpectedConditions.visibilityOfElementLocated(By.className("down-action"))
									);
	
									thumbsDown.click();
	
									Thread.sleep(2000);
	
									String negPayload = String.format(
											"{\"text\":\"Circuit Pack Failed\",\"feedback_id\":\"%s\",\"tenant_id\":\"Ashwin\"}",
											feedbackId
									);
	
									pushNegativeFeedbackVerified = validateApiStatus(
											"https://dev-demo.neuron7.ai:8889",
											"/intelligence/pushNegativeFeedback",
											"Post",
											negPayload
									);
	
									Thread.sleep(2000);
	
									WebElement feedbackField = wait.until(
											ExpectedConditions.visibilityOfElementLocated(By.className("feedback-text-area"))
									);
									String comment = "Issue in this result!";
	
									feedbackField.sendKeys(comment);
	
									Thread.sleep(2000);
	
									WebElement submit = driver.findElement(
											By.cssSelector(
													"#feedback-div > form > div > div.col-12.col-md-2.text-md-left.text-center.mt-2.mt-md-0 > button"
											)
									);
	
									submit.click();
	
									String specPayload = String.format(
											"{\"feedback_id\":\"%s\",\"comment\":\"%s\"}",
											feedbackId,
											comment
									);
	
									pushSpecificFeedbackVerified = validateApiStatus(
											"https://dev-demo.neuron7.ai:8889",
											"/intelligence/pushSpecificFeedback",
											"Post",
											specPayload
									);
	
									Thread.sleep(2000);
	
									System.out.println("Submitted feedback.");
							}
					}
			} catch (Exception e) {
					Thread.currentThread().interrupt();
	
					Assert.fail("Alert handling failed: " + e.getMessage());
			}
	}
	
	// Add these new test methods that depend on testSearchQueries
	@Test(priority = 10, dependsOnMethods = "testSearchQueries")
	public void verifyPushPositiveFeedbackApi() {
			ExtentTestNGListener.getTest().info("Verifying pushPositiveFeedback API");
	
			Assert.assertTrue(pushPositiveFeedbackVerified, "pushPositiveFeedback API verification failed");
	}
	
	@Test(priority = 11, dependsOnMethods = "testSearchQueries")
	public void verifyPushNegativeFeedbackApi() {
			ExtentTestNGListener.getTest().info("Verifying pushNegativeFeedback API");
	
			Assert.assertTrue(pushNegativeFeedbackVerified, "pushNegativeFeedback API verification failed");
	}
	
	@Test(priority = 12, dependsOnMethods = "testSearchQueries")
	public void verifyPushSpecificFeedbackApi() {
			ExtentTestNGListener.getTest().info("Verifying pushSpecificFeedback API");
	
			Assert.assertTrue(pushSpecificFeedbackVerified, "pushSpecificFeedback API verification failed");
	}
	
	@Test(priority = 13, dependsOnMethods = "testSearchQueries")
	public void verifyQueryResultAPI() {
			ExtentTestNGListener.getTest().info("Verifying getQueryResults API");
	
			Assert.assertTrue(queryResultsVerified, "getQueryResults API verification failed");
	}
	
	@Test(priority = 14)
	public void navigateToLogmanagenemt() {
			try {
					Actions actions = new Actions(driver);
	
					// 1. Hover over the collapsed sidebar to expand it.
					WebElement sidebar = wait.until(
							ExpectedConditions.visibilityOfElementLocated(
									By.cssSelector("body > app > default-layout > div > aside > div.sidebar.collapsed") // Update
									// with
									// actual
									// locator
							)
					);
					Thread.sleep(2000);
	
					actions.moveToElement(sidebar).perform();
					Thread.sleep(2000); // Wait for the sidebar to expand
	
					// 2. Click on "Intelligent Diagnostics" using JavascriptExecutor.
					WebElement intelligentSearch = wait.until(
							ExpectedConditions.elementToBeClickable(
									By.cssSelector(
											"body > app > default-layout > div > aside > div.sidebar.collapsed > nav > ul:nth-child(3) > li > a"
									)
							)
					);
					// Update with the accurate locator
	
					Thread.sleep(2000);
					intelligentSearch.click();
	
					Thread.sleep(2000); // Allow time for the dropdown to render
	
					WebElement logManagement = wait.until(
							ExpectedConditions.elementToBeClickable(
									By.cssSelector(
											"body > app > default-layout > div > aside > div.sidebar.collapsed > nav > ul:nth-child(3) > li > ul:nth-child(2) > li > a"
									)
							)
					);
					// Update with the accurate locator
	
					Thread.sleep(2000);
					logManagement.click();
	
					wait.until(ExpectedConditions.urlContains("intelligent-mgmt"));
					Thread.sleep(2000); // Allow time for the page to load
					ExtentTestNGListener.getTest().info("Log Management page is displayed successfully.");
			} catch (Exception e) {
					Assert.fail("Test failed: " + e.getMessage());
					System.out.println("Testt Failed: " + e.getMessage());
			}
	}
	
	// for scrolling into view
	private void scrollIntoView(WebElement element) {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
	}
	
	@Test(priority = 15)
	public void verifyLogs() {
			try {
					// 1) wait for loader to vanish
					wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));
					Thread.sleep(2000);
	
					// 2) click the edit‐icon
					WebElement editIcon = driver.findElement(
							By.cssSelector(
									"body > app > default-layout > div > div > div > section > " +
									"div.container-fluid.main-router-outlet.p-0 > app-list > div:nth-child(2) " +
									"> div > div > div > div.card-body.table-responsive.p-0 " +
									"> ag-grid-angular > div > div.ag-root-wrapper-body > div.ag-root " +
									"> div.ag-body-viewport > div.ag-center-cols-clipper > div > div " +
									"> div.ag-row-even > div:nth-child(9) > div > span > app-icon-renderer > i"
							)
					);
					editIcon.click();
					System.out.println("Edit icon clicked.");
					Thread.sleep(2000);
	
					// 3) labels to fetch
					String[] labels = { "negativeFeedback", "positiveFeedback", "comment", "username" };
					Map<String, String> values = new HashMap<>();
	
					for (String lbl : labels) {
							String xpathInput = String.format(
									"//label[contains(@class,'observstion-item-title') and normalize-space(text())='%s']" +
									"/following-sibling::div[contains(@class,'col-sm-8')]//input",
									lbl
							);
	
							try {
									WebElement input = driver.findElement(By.xpath(xpathInput));
									scrollIntoView(input);
	
									// first try the input's value attribute
									String val = input.getAttribute("value").trim();
									if (val.isEmpty()) {
											// fallback to any text inside the append‐div
											WebElement appendDiv = driver.findElement(
													By.xpath(xpathInput + "/following-sibling::div[contains(@class,'input-group-append')]")
											);
											val = appendDiv.getText().trim();
									}
	
									values.put(lbl, val);
							} catch (NoSuchElementException ex) {
									values.put(lbl, "");
							}
					}
	
					// 4) pull them out
					String negativeFeedback = values.get("negativeFeedback");
					String positiveFeedback = values.get("positiveFeedback");
					String comment = values.get("comment");
					String username2 = values.get("username");
	
					System.out.println("negativeFeedback = " + negativeFeedback);
					System.out.println("positiveFeedback = " + positiveFeedback);
					System.out.println("comment          = " + comment);
					System.out.println("username         = " + username2);
	
					// 5) your pass/fail logic
					if (
							"true".equals(negativeFeedback) &&
							"true".equals(positiveFeedback) &&
							!comment.isEmpty() &&
							username2.equals(username)
					) {
							Assert.assertTrue(true, "Log verification passed.");
							System.out.println("Log verification passed.");
	
							wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-screen-wrapper")));
							// click “Create New KB”
							WebElement checkBox = wait.until(ExpectedConditions.elementToBeClickable(By.id("createKB")));
							checkBox.click();
							Thread.sleep(2000);
	
							// click Save
							WebElement save = wait.until(
									ExpectedConditions.elementToBeClickable(
											By.cssSelector("#mat-tab-content-1-0 > div > div > div:nth-child(41) > div > button")
									)
							);
							save.click();
							Thread.sleep(2000);
							System.out.println("Save clicked.");
							ExtentTestNGListener.getTest().info("Log updated successfully.");
					} else {
							Assert.fail(
									"Log Verification failed: " +
									"negativeFeedback=" +
									negativeFeedback +
									", " +
									"positiveFeedback=" +
									positiveFeedback +
									", " +
									"comment=" +
									comment +
									", " +
									"username=" +
									username2
							);
					}
			} catch (Exception e) {
					Assert.fail("Log Verification failed: " + e.getMessage());
			}
	}
	
	@AfterClass
	public void tearDown() {
			if (driver != null) {
					try {
							Thread.sleep(2000);
					} catch (Exception e) {
							Thread.currentThread().interrupt();
	
							Assert.fail("Alert handling failed: " + e.getMessage());
					}
	
					driver.quit();
	
					ExtentTestNGListener.getTest().info("Closing browser and cleaning up test");
					System.out.println("Browser closed.\nTest execution completed.");
			}
	}
	}
