package com.qa.nal;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import io.github.cdimascio.dotenv.Dotenv;
import io.qase.commons.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.logging.log4j.*;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("My Application - Core User Flows")
public class DiagnosticTests extends BaseTest {

    private static final Logger log = LogManager.getLogger(DiagnosticTests.class);

    Dotenv dotenv = Dotenv.load();
    private final String username = dotenv.get("APP_USERNAME");
    private final String password = dotenv.get("PASSWORD");
    private final String loginUrl = dotenv.get("DEVDEMO_URL");

    private static boolean shouldFillDescription = false;

    @Test
    @Order(1)
    @QaseId(1)
    @QaseTitle("Navigate to Login Page")
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
            log.info("Username field clicked");

            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username")).fill(username);
            log.info("Username field filled");

            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password")).click();
            log.info("Password field clicked");

            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password")).fill(password);
            log.info("Password field filled");

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login").setExact(true)).click();
            log.info("Login button clicked");

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
    @QaseTitle("Handle Initial Pop-Up")
    public void handleInitialPopup() {
        try {
            page.locator(".modal-content");
            log.info("Modal Pop-Up found");

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancel")).click();
            log.info("Cancel button clicked");
        } catch (Exception e) {
            Assertions.fail("Modal not found or not visible: " + e.getMessage());
            log.error("No modal found");
        }
    }

    @Test
    @Order(4)
    @QaseId(4)
    @QaseTitle("Navigate to Intelligent Diagnostics")
    public void navigateToIntelligentDiagnostics() {
        try {
            page.locator("body > app > default-layout > div > aside > div.sidebar.collapsed").hover();
            log.info("Sidebar hovered");

            page.waitForTimeout(2000);

            page.locator("a").filter(new Locator.FilterOptions().setHasText("Intelligent Diagnostics")).click();
            log.info("Intelligent Diagnostics clicked");
        } catch (Exception e) {
            log.error("Navigation to Intelligent Diagnostics failed: {}", e.getMessage());
            Assertions.fail("Navigation to Intelligent Diagnostics failed: " + e.getMessage());
        }
    }

    @Test
    @Order(5)
    @QaseId(5)
    @QaseTitle("Navigate to Service Request")
    public void navigateToServiceRequest() {
        try {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(" Service Request")).click();
            log.info("Service Request clicked");

            page.waitForURL(url -> url.contains("caseobject"));
            Assertions.assertTrue(page.url().contains("caseobject"), "Service Request page did not load as expected");

            log.info("Service Request page loaded successfully");
        } catch (Exception e) {
            log.error("Navigation to Service Request failed: {}", e.getMessage());
            Assertions.fail("Navigation to Service Request failed: " + e.getMessage());
        }
    }

    @Test
    @Order(6)
    @QaseId(6)
    @QaseTitle("Create Service Request")
    public void createNewServiceRequest() {
        try {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("  Create New")).click();
            log.info("Create New button clicked");

            page.waitForSelector("#modalCenter > div > div", new Page.WaitForSelectorOptions().setTimeout(45000));
            log.info("Create New modal opened");

            page
                .locator("ng-select")
                .filter(new Locator.FilterOptions().setHasText("Manufacturer *"))
                .getByRole(AriaRole.TEXTBOX)
                .click();
            log.info("Picklist clicked");

            page.waitForTimeout(2000);

            if (!shouldFillDescription) {
                page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("ALINITY_I")).click();
                log.info("ALINITY_I option clicked");

                page.locator("textarea[name=\"Description\"]").click();
                //required
                page.locator("textarea[name=\"Description\"]").fill("Main of Data");

                shouldFillDescription = true;
            } else {
                page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("ALINITY-I")).click();
                log.info("ALINITY-I option clicked");

                page.locator("textarea[name=\"Description\"]").click();
                page.locator("textarea[name=\"Description\"]").fill("Main of Data");
            }

            // Click Start Diagnosis button
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Start Diagnosis")).click();
            log.info("Start Diagnosis button clicked");

            // Wait for navigation
            page.waitForURL(url -> url.contains("n7-predictions"), new Page.WaitForURLOptions().setTimeout(10000));
            log.info("Navigated to predictions page");

            Assertions.assertTrue(page.url().contains("n7-predictions"), "Predictions page did not load as expected");
            log.info("Predictions page loaded successfully");

            // Wait until loading screen disappears
            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );

            page.waitForLoadState(LoadState.NETWORKIDLE);
        } catch (Exception e) {
            log.error("Error occurred: {}", e.getMessage());
            Assertions.fail("Test Failed: " + e.getMessage());
        }
    }

    private Boolean checkObs() {
        Boolean obsFound = false;

        try {
            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );

            page.waitForLoadState(LoadState.NETWORKIDLE);

            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );

            page.waitForTimeout(3000);

            page.waitForSelector(".observation-card", new Page.WaitForSelectorOptions().setTimeout(15000));
            log.info("Observation card found");

            page.waitForTimeout(5000);

            page.waitForLoadState(LoadState.NETWORKIDLE);

            List<Locator> problemList = page.locator(".observation-card").all();
            log.info("Problem List size: {}", problemList.size());

            String problemText = problemList.get(0).textContent().trim();

            if (!problemText.contains("Are you seeing something else?")) {
                obsFound = true;
                log.info("Observation card found: {}", problemText);
            } else {
                obsFound = false;
                log.info("Observation card not found");
            }

            return obsFound;
        } catch (Exception e) {
            log.error("Observation card not found: {}", e.getMessage());
            Assertions.fail("Observation card not found: " + e.getMessage());
            return obsFound;
        }
    }

    private void newObs() {
        try {
            page.getByText("Are you seeing something else?").click();
            log.info("Something Else button clicked");

            page.locator("mat-drawer-content").getByRole(AriaRole.TEXTBOX).fill("New Observation");
            log.info("New Observation field filled");

            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create New")).click();
            log.info("Create New button clicked");
        } catch (Exception e) {
            log.error("New Observation not found: {}", e.getMessage());
            Assertions.fail("New Observation not found: " + e.getMessage());
        }
    }

    private Boolean checkInf() {
        Boolean infFound = false;
        try {
            boolean newSolutionCreated = false;

            page.waitForTimeout(5000);

            page.waitForLoadState(LoadState.NETWORKIDLE);

            List<Locator> solutionList = new ArrayList<Locator>();
            try {
                page.waitForSelector(
                    ".resolution",
                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000)
                );
                log.info("Inference found");

                solutionList = page.locator(".resolution").all();
            } catch (Exception e) {
                log.error("No Inference found: {}", e.getMessage());
            }

            if (!solutionList.isEmpty()) {
                log.info("Solutions size: {}", solutionList.size());
                infFound = true;
                log.info("Solutions found: {}", solutionList.size());
            } else if (!newSolutionCreated) {
                newSolutionCreated = true;
                log.info("No solution found!");
                infFound = false;
            }

            return infFound;
        } catch (Exception e) {
            log.error("Existing Observation not found: {}", e.getMessage());
            Assertions.fail("Existing Observation not found: " + e.getMessage());
            return infFound;
        }
    }

    private void existingInf() {
        try {
            page
                .locator(
                    "body > app > default-layout > div > div > div > section > div.container-fluid.main-router-outlet.pl-0.pr-0 > app-new-diagnostic > mat-drawer-container > mat-drawer-content > div > div.card-container > form > div.wrapper.solutions.ng-star-inserted > div.resolution-wrapper > mat-card:nth-child(1) > div > div > mat-card-header > i.far.fa-square.ng-star-inserted"
                )
                .click();
            log.info("Checkbox clicked");
            // page.locator("app-new-diagnostic form div.wrapper.observations
            // div.col-12").locator("button").click();
        } catch (Exception e) {
            log.error("Existing Inference not found: {}", e.getMessage());
            Assertions.fail("Existing Inference not found: " + e.getMessage());
        }
    }

    private void newInf() {
        try {
            log.info("No solution found!");

            page.locator("mat-drawer-content").getByRole(AriaRole.TEXTBOX).fill("New Solution");
            log.info("Solution field filled");

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create New")).click();
            log.info("Solution created");

            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );
        } catch (Exception e) {
            log.error("New Inference not found: {}", e.getMessage());
            Assertions.fail("New Inference not found: " + e.getMessage());
        }
    }

    private void saveAndContinue() {
        try {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save & Continue")).click();
            log.info("Save and Continue button clicked");

            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );
        } catch (Exception e) {
            log.error("Save and Continue button not found: {}", e.getMessage());
            Assertions.fail("Save and Continue button not found: " + e.getMessage());
        }
    }

    private void saveAndClose() {
        try {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save & Close")).click();
            log.info("Save and Close button clicked");

            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Copy")).click();

            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );
        } catch (Exception e) {
            log.error("Save and Close button not found: {}", e.getMessage());
            Assertions.fail("Save and Close button not found: " + e.getMessage());
        }
    }

    @Test
    @Order(7)
    @QaseId(7)
    @QaseTitle("Existing Observation and Existing Inference")
    public void exObsExInf() {
        try {
            if (checkObs()) {
                log.info("Observation card found");
                if (checkInf()) {
                    log.info("Existing Inference found");

                    existingInf();

                    saveAndContinue();
                } else {
                    log.info("Existing Inference not found");

                    page
                        .getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Do you want to resolve with"))
                        .locator("i")
                        .nth(1)
                        .click();
                    log.info("New Solution clicked");

                    newInf();

                    saveAndContinue();
                }
            } else {
                newObs();

                log.info("Observation card not found");
                if (checkInf()) {
                    log.info("Existing Inference found");

                    existingInf();

                    saveAndContinue();
                } else {
                    log.info("Existing Inference not found");

                    newInf();

                    saveAndContinue();
                }
            }
        } catch (Exception e) {
            log.error("Test Failed: {}", e.getMessage());
            Assertions.fail("Test Failed: " + e.getMessage());
        }
    }

    @Test
    @Order(8)
    @QaseId(8)
    @QaseTitle("Delete Investigation")
    public void deleteInvestigation() {
        try {
            page.locator("mat-card-title i").click();
            log.info("Delete button clicked");

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Yes")).click();
            log.info("Yes button clicked");

            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );

            // Wait for the loading screen to disappear
            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );

            // Wait for the page to load completely
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Wait for the loading screen to disappear
            page.waitForSelector(
                ".loading-screen-wrapper",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN)
            );
            log.info("Investigation deleted successfully");

            // Wait for the page to load completely
            page.waitForLoadState(LoadState.NETWORKIDLE);

            page.locator("app-new-diagnostic form div.wrapper.observations div.col-12").locator("button").click();
            log.info("Expand button clicked");
        } catch (Exception e) {
            log.error("Test Failed: {}", e.getMessage());
            Assertions.fail("Test Failed: " + e.getMessage());
        }
    }

    @Test
    @Order(9)
    @QaseId(9)
    @QaseTitle("New Observation and New Inference")
    public void newObsNewInf() {
        try {
            if (checkObs()) {
                log.info("Observation card found");
                newObs();

                if (checkInf()) {
                    log.info("Existing Inference found");

                    newInf();

                    saveAndContinue();
                } else {
                    log.info("Existing Inference not found");

                    newInf();

                    saveAndContinue();
                }
            } else {
                newObs();

                log.info("Observation card not found");
                if (checkInf()) {
                    log.info("Existing Inference found");

                    existingInf();

                    saveAndContinue();
                } else {
                    log.info("Existing Inference not found");

                    newInf();

                    saveAndContinue();
                }
            }

            // deleteInvestigation();
            deleteInvestigation();
        } catch (Exception e) {
            log.error("Test Failed: {}", e.getMessage());
            Assertions.fail("Test Failed: " + e.getMessage());
        }
    }

    @Test
    @Order(10)
    @QaseId(10)
    @QaseTitle("Existing Observation and New Inference")
    public void exObsNewInfExInf() {
        try {
            if (checkObs()) {
                log.info("Observation card found");
                if (checkInf()) {
                    log.info("Existing Inference found");

                    existingInf();

                    page
                        .getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Do you want to resolve with"))
                        .locator("i")
                        .nth(1)
                        .click();
                    log.info("New Solution clicked");

                    newInf();

                    saveAndClose();
                } else {
                    log.info("Existing Inference not found");

                    page
                        .getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Do you want to resolve with"))
                        .locator("i")
                        .nth(1)
                        .click();
                    log.info("New Solution clicked");

                    newInf();

                    saveAndClose();
                }
            } else {
                newObs();

                log.info("Observation card not found");
                if (checkInf()) {
                    log.info("Existing Inference found");

                    existingInf();

                    saveAndClose();
                } else {
                    log.info("Existing Inference not found");

                    newInf();

                    saveAndClose();
                }
            }
        } catch (Exception e) {
            log.error("Test Failed: {}", e.getMessage());
            Assertions.fail("Test Failed: " + e.getMessage());
        }
    }

    @Test
    @Order(11)
    @QaseId(11)
    @QaseTitle("Navigate to Inbox")
    public void navigateToInbox() {
        try {
            handleInitialPopup();

            page.locator("body > app > default-layout > div > aside > div.sidebar.collapsed").hover();
            log.info("Sidebar hovered");

            page.waitForTimeout(2000);

            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Inbox")).click();
            log.info("Inbox clicked");

            page.waitForURL(url -> url.contains("inbox"), new Page.WaitForURLOptions().setTimeout(45000));
            Assertions.assertTrue(page.url().contains("inbox"), "Not redirected on Inbox page");
            log.info("Navigated to Inbox page");
        } catch (Exception e) {
            log.error("Navigation to Inbox failed: {}", e.getMessage());
            Assertions.fail("Navigation to Inbox failed: " + e.getMessage());
        }
    }

    @Test
    @Order(12)
    @QaseId(12)
    @QaseTitle("Manage Inferences and Observations")
    public void manageInfAndObs() {
        try {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create New")).click();
            log.info("Create New button clicked");

            page.waitForSelector("#modalCenter > div > div", new Page.WaitForSelectorOptions().setTimeout(45000));
            log.info("Create New modal opened");

            page
                .locator("div")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^Manufacturer \\*$")))
                .nth(2)
                .click();
            log.info("Manufacturer field clicked");

            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("ALINITY-S")).click();
            log.info("ALINITY-S option clicked");

            page
                .getByLabel("1Please fill out the")
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Next"))
                .click();
            log.info("Next button clicked");

            page.waitForTimeout(2000);

            page.getByRole(AriaRole.TEXTBOX).first().click();
            log.info("Text box clicked");

            page
                .locator("ng-select")
                .filter(new Locator.FilterOptions().setHasText("Search/CreateType to search"))
                .getByRole(AriaRole.TEXTBOX)
                .fill("test observation");
            log.info("Observation field filled");

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create New").setExact(true)).click();
            log.info("Create New button clicked");

            page
                .locator("ng-select")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^Search\\/Create$")))
                .getByRole(AriaRole.TEXTBOX)
                .click();
            log.info("Inference field clicked");

            page
                .locator("ng-select")
                .filter(new Locator.FilterOptions().setHasText("Search/CreateType to"))
                .getByRole(AriaRole.TEXTBOX)
                .fill("test inference");
            log.info("Inference field filled");

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create New").setExact(true)).click();
            log.info("Create New button clicked");

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click();
            log.info("Save button clicked");

            page
                .getByLabel("2Observation Details")
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Next"))
                .click();
            log.info("Next button clicked");

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save Observation")).click();
            log.info("Save Observation button clicked");
        } catch (Exception e) {
            log.error("Test Failed: {}", e.getMessage());
            Assertions.fail("Test Failed: " + e.getMessage());
        }
    }
}
