    package com.qa.nal;
    
    import com.microsoft.playwright.*;
    import com.microsoft.playwright.options.*;
    import com.qa.nal.utils.ExcelReader;
    import io.github.cdimascio.dotenv.Dotenv;
    import io.qase.commons.annotation.*;
    import java.util.*;
    import java.util.regex.Pattern;
    import org.junit.jupiter.api.*;
    import org.slf4j.*;
    
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("My Application - Core User Flows")
    public class SearchTests extends BaseTest {
    
        Dotenv dotenv = Dotenv.load();
        private final String username = dotenv.get("APP_USERNAME");
        private final String password = dotenv.get("PASSWORD");
        private final String loginUrl = dotenv.get("DEVDEMO_URL");
        private static final Logger log = LoggerFactory.getLogger(SearchTests.class);
        public static List<Locator> resultList;
        static boolean htmlfound;
        static boolean elementInvisible;
    
        @Step("{0}")
        public void logStep(String message) {
            // This will appear as a step in Qase test case execution log
            System.out.println("[QASE STEP] " + message);
        }
    
        @Test
        @Order(1)
        @QaseId(1)
        @QaseTitle("Navigate to Login")
        public void navigateToLoginPage() {
            try {
                page.navigate(loginUrl);
                page.waitForURL(url -> url.contains("login"), new Page.WaitForURLOptions().setTimeout(45000));
                Assertions.assertTrue(page.url().contains("login"), "Not redirected on login page");
                log.info("Navigating to login page: {}", loginUrl);
            } catch (Exception e) {
                log.info("Navigating to login page: {}", page.url());
                Assertions.fail("Login page not found: " + e.getMessage());
            }
        }
    
        @Test
        @Order(2)
        @QaseId(2)
        @QaseTitle("Perform Login")
        public void performLogin() {
            try {
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username")).click();
                page.waitForTimeout(2000);
                log.info("Username field clicked");
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username")).fill(username);
                log.info("Username field filled");
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password")).click();
                page.waitForTimeout(2000);
                log.info("Password field clicked");
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password")).fill(password);
                log.info("Password field filled");
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login").setExact(true)).click();
                page.waitForTimeout(2000);
                log.info("Login button clicked");
                page.waitForSelector(
                    ".loading-screen-wrapper",
                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                );
                page.waitForURL(url -> url.contains("/app/new-home"));
                Assertions.assertTrue(page.url().contains("new-home"), "Login did not navigate to home");
                log.info("Login successful, navigated to home page");
            } catch (Exception e) {
                log.error("Login failed: {}", e.getMessage());
                Assertions.fail("Login failed: " + e.getMessage());
            }
        }
    
        @Test
        @Order(3)
        @QaseId(3)
        @QaseTitle("Handle Pop-Up")
        public void handleInitialPopup() {
            try {
                if (page.locator(".modal-content").isVisible()) {
                    log.info("Modal Pop-Up found");
                    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancel")).click();
                    page.waitForTimeout(2000);
                    page.waitForSelector(
                        ".loading-screen-wrapper",
                        new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                    );
                    log.info("Cancel button clicked");
                }
            } catch (Exception e) {
                log.error("No modal found or not visible");
            }
        }
    
        @Test
        @Order(4)
        @QaseId(4)
        @QaseTitle("Navigate to Intelligent Search")
        public void navigateToIntelligentSearch() {
            page
                .locator("div")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^Intelligent Search$")))
                .click();
            page.waitForTimeout(2000);
            log.info("Intelligent Search Card Clicked!");
            page.waitForURL(url -> url.contains("int-answer"));
            Assertions.assertTrue(page.url().contains("int-answer"));
            log.info("Intelligent Search successfully Opened!");
        }
    
        static boolean cpfFound = false;
    
        private void searchAQuery(String query) {
            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.locator("#searchTxt").fill(query);
            log.info("Search Field Filled with: {}", query);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Search")).click();
            page.waitForTimeout(2000);
            log.info("Search button Clicked!");
            if (query == "Circuit Pack Failed") {
                cpfFound = true;
                log.info("CPF found!");
            }
            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );
        }
    
        public boolean checkResults() {
            try {
                page.waitForSelector(".serch-result-list .btn-link", new Page.WaitForSelectorOptions().setTimeout(15000));
                log.info("Solutions found");
            } catch (Exception e) {
                log.info("Solution Not found!");
                return false;
            }
            Locator resultArea = page.locator(".serch-result-list");
            page.waitForLoadState(LoadState.NETWORKIDLE);
    
            Locator singleResult;
            if (!cpfFound) {
                singleResult = resultArea
                    .locator(".btn-link")
                    .filter(new Locator.FilterOptions().setHasNotText("open_in_new"))
                    .filter(new Locator.FilterOptions().setHasNotText(".html"));
            } else {
                singleResult = resultArea
                    .locator(".btn-link")
                    .filter(new Locator.FilterOptions().setHasNotText("open_in_new"));
            }
    
            resultList = singleResult.all();
    
            log.info("Solution List size: {}", resultList.size());
    
            for (Locator result : resultList) {
                String text = result.textContent().toLowerCase();
                log.info(text);
            }
    
            return !resultList.isEmpty();
        }
    
        public void handleResult() {
            htmlfound = false;
            elementInvisible = false;
    
            Random rnd = new Random();
            int idx = rnd.nextInt(resultList.size());
            Locator element = resultList.get(idx);
            String text = element.textContent().toLowerCase();
    
            log.info("Index: {}", idx);
            log.info("Text: {}", text);
    
            if (text.contains(".html") || text.contains("open_in_new") || !element.isVisible()) {
                htmlfound = text.contains(".html");
                elementInvisible = !element.isVisible() || text.contains("open_in_new");
                return;
            }
    
            element.scrollIntoViewIfNeeded();
            element.click();
            page.waitForTimeout(2000);
            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );
            page.waitForSelector("body");
    
            if (text.contains(".pdf")) {
                page.waitForSelector("pdf-viewer >> div");
                String pageText = page.locator(".navigation-pannel span").innerText();
                int totalPages = Integer.parseInt(pageText.split("of")[1].trim());
                int randomPage = rnd.nextInt(totalPages) + 1;
                page.getByPlaceholder("Enter page no").fill("" + randomPage);
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Go").setExact(true)).click();
                page.waitForTimeout(2000);
                page.waitForSelector(
                    ".loading-screen-wrapper",
                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                );
                page.waitForSelector("pdf-viewer >> div");
                if (randomPage > 1) page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("First")).click();
                page.waitForTimeout(2000);
                page.waitForSelector(
                    ".loading-screen-wrapper",
                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                );
                page.waitForSelector("pdf-viewer >> div");
                if (randomPage < totalPages) page
                    .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Last"))
                    .click();
                page.waitForTimeout(2000);
                page.waitForSelector(
                    ".loading-screen-wrapper",
                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                );
                page.waitForSelector("pdf-viewer >> div");
            }
    
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Close")).click();
            page.waitForTimeout(2000);
            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );
        }
    
        @Test
        @Order(5)
        @QaseId(5)
        @QaseTitle("Search Query")
        public void testSearchQueries() {
            List<String> searchQueries = ExcelReader.readQueriesFromExcel(
                "src/test/resources/SearchQueries.xlsx",
                "Search Query data"
            );
            Assertions.assertFalse(searchQueries.isEmpty(), "No queries found in Excel file.");
    
            for (String query : searchQueries) {
                try {
                    searchAQuery(query);
                    if (!checkResults()) continue;
    
                    int pass = 0;
                    while (pass < 2) {
                        handleResult();
                        if (htmlfound || elementInvisible) {
                            htmlfound = false;
                            elementInvisible = false;
                            pass++;
                            continue;
                        }
    
                        if (pass == 0) {
                            page.getByLabel("Feedback").getByTitle("Negative Feedback").click();
                            page.waitForTimeout(2000);
                            page.waitForSelector(
                                ".loading-screen-wrapper",
                                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                            );
    
                            page
                                .getByRole(
                                    AriaRole.TEXTBOX,
                                    new Page.GetByRoleOptions().setName("Can you please tell us why")
                                )
                                .fill("Issue in this result!");
                            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit")).click();
                            page.waitForTimeout(2000);
                            page.waitForSelector(
                                ".loading-screen-wrapper",
                                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                            );
    
                            page.waitForLoadState(LoadState.NETWORKIDLE);
                        } else {
                            page.getByLabel("Feedback").getByTitle("Positive Feedback").click();
                            page.waitForTimeout(2000);
                            page.waitForSelector(
                                ".loading-screen-wrapper",
                                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                            );
    
                            page.waitForLoadState(LoadState.NETWORKIDLE);
                        }
                        pass++;
                    }
                } catch (Exception e) {
                    Assertions.fail("Test failed for query: " + query + " - " + e.getMessage());
                }
            }
        }
    
        @Test
        @Order(6)
        @QaseId(6)
        @QaseTitle("Test Circuit Pack Failed")
        public void searchCPFQuery() {
            try {
                searchAQuery("Circuit Pack Failed");
                if (!checkResults()) return;
    
                page.getByRole(AriaRole.LISTITEM).locator(".up-action").first().click();
                page.waitForTimeout(2000);
                page.waitForSelector(
                    ".loading-screen-wrapper",
                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                );
                page.getByRole(AriaRole.LISTITEM).locator(".down-action").nth(0).click();
                page.waitForTimeout(2000);
                page.waitForSelector(
                    ".loading-screen-wrapper",
                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                );
                page
                    .getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Can you please tell us why"))
                    .fill("Issue in this result!");
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit")).click();
                page.waitForTimeout(2000);
                page.waitForSelector(
                    ".loading-screen-wrapper",
                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                );
            } catch (Exception e) {
                Assertions.fail("Test failed for query: Circuit Pack Failed - " + e.getMessage());
            }
        }
    
        @Test
        @Order(7)
        @QaseId(7)
        @QaseTitle("Navigate to Log Management")
        public void navigateToLogManangement() {
            // Placeholder for log management nav logic
            try {
                page.locator("body > app > default-layout > div > aside > div.sidebar.collapsed").hover();
                log.info("Sidebar hovered");
    
                page.waitForTimeout(2000);
    
                page.locator("a").filter(new Locator.FilterOptions().setHasText("Intelligent Search")).first().click();
                log.info("Intelligent Search clicked");
    
                page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(" Log Management")).click();
                log.info("Service Request clicked");
    
                page.waitForURL(url -> url.contains("intelligent-mgmt"));
                Assertions.assertTrue(
                    page.url().contains("intelligent-mgmt"),
                    "Log Management page did not load as expected"
                );
    
                log.info("Log Management page loaded successfully");
            } catch (Exception e) {
                log.error("Navigation to Intelligent Diagnostics failed: {}", e.getMessage());
                Assertions.fail("Navigation to Intelligent Diagnostics failed: " + e.getMessage());
            }
        }
    
        @Test
        @Order(8)
        @QaseId(8)
        @QaseTitle("Create New KB")
        public void manageLogs() {
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE);
                page.waitForSelector(
                    ".loading-screen-wrapper",
                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                );
                page.locator(".action-btn").first().click();
    
                page.waitForTimeout(2000);
                log.info("Clicked edit icon.");
    
                // Map<String, String> values = new HashMap<>();
                // String[] labels = { "negativeFeedback", "positiveFeedback", "comment", "username" };
    
                // for (String lbl : labels) {
                //     try {
                //         // Locate the label by text, get its parent row, then find the input
                //         Locator row = page.locator("div.row:has(label:text-is('" + lbl + "'))");
                //         Locator input = row.locator("input");
    
                //         input.scrollIntoViewIfNeeded();
                //         String val = input.getAttribute("value");
    
                //         // Fallback: check sibling div (for rendered value in disabled state)
                //         if (val == null || val.trim().isEmpty()) {
                //             Locator fallback = row.locator("div.input-group-append");
                //             val = fallback.innerText().trim();
                //         }
    
                //         values.put(lbl, val == null ? "" : val.trim());
                //     } catch (Exception e) {
                //         values.put(lbl, "");
                //         log.warn("Stable locator failed for '{}'. Error: {}", lbl, e.getMessage());
                //     }
                // }
    
                // String neg = values.get("negativeFeedback");
                // String pos = values.get("positiveFeedback");
                // String comment = values.get("comment");
                // String username2 = values.get("username");
    
                // log.info("negativeFeedback = {}", neg);
                // log.info("positiveFeedback = {}", pos);
                // log.info("comment = {}", comment);
                // log.info("username = {}", username2);
    
                // Assertions.assertTrue(
                //     "true".equals(neg) &&
                //     "true".equals(pos) &&
                //     !comment.isEmpty() &&
                //     username2.equals(dotenv.get("APP_USERNAME")),
                //     "Log Verification failed: negativeFeedback=" +
                //     neg +
                //     ", positiveFeedback=" +
                //     pos +
                //     ", comment=" +
                //     comment +
                //     ", username=" +
                //     username2
                // );
    
                page.waitForSelector(
                    ".loading-screen-wrapper",
                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                );
    
                page.waitForSelector(
                    ".loading-screen-wrapper",
                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                );
                Locator checkBox = page.locator("#createKB");
                if (!checkBox.isChecked()) {
                    checkBox.check();
                    log.info("Create New KB checked.");
                }
    
                page.waitForSelector(
                    ".loading-screen-wrapper",
                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
                );
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click();
                page.waitForTimeout(2000);
                log.info("Save clicked.");
            } catch (Exception e) {
                Assertions.fail("Log verification failed: " + e.getMessage());
            }
        }
    }
