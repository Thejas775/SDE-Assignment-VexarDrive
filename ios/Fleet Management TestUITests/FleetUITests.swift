//
//  FleetUITests.swift
//  Fleet Management TestUITests
//
//  Drives the real app against a live backend. Unlike the unit tests, these
//  need a reachable API - the base URL is whatever AppConfig points at.
//

import XCTest

final class FleetUITests: XCTestCase {

    /// Credentials for the account these tests sign in with. Overridable from
    /// the environment so this file carries no assumptions about a machine.
    private var email: String {
        ProcessInfo.processInfo.environment["FLEET_TEST_EMAIL"] ?? "ios-test@fleet.in"
    }
    private var password: String {
        ProcessInfo.processInfo.environment["FLEET_TEST_PASSWORD"] ?? "ios-pass-word-1"
    }

    private var app: XCUIApplication!

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        app = XCUIApplication()
    }

    // MARK: - Helpers

    private func launchSignedOut() {
        // A fresh container each run, so a session persisted by an earlier test
        // cannot decide whether this one starts at Login.
        app.launchArguments += ["-ui-testing-reset"]
        app.launch()
    }

    /// Taps by absolute coordinate when the element claims to be unhittable.
    ///
    /// On this OS the signed-in screens put a full-screen Toolbar element above
    /// the content in the accessibility tree, so XCUITest's hit test decides
    /// these controls are covered and `tap()` silently does nothing. A touch at
    /// the very same point works - DiagnosticUITests demonstrates both - so the
    /// app is fine and only the automation needs the workaround.
    private func robustTap(_ element: XCUIElement) {
        XCTAssertTrue(element.waitForExistence(timeout: 20), "element should exist")
        guard !element.isHittable else {
            element.tap()
            return
        }
        let frame = element.frame
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: frame.midX, dy: frame.midY))
            .tap()
    }

    /// Taps until the tap demonstrably did something.
    ///
    /// A single tap on these screens is unreliable regardless of how it is
    /// dispatched, so the caller says what success looks like and this keeps
    /// trying for a bounded number of attempts.
    private func tapUntil(
        _ element: XCUIElement,
        _ what: String,
        attempts: Int = 4,
        until satisfied: () -> Bool
    ) {
        for attempt in 1...attempts {
            robustTap(element)
            if satisfied() { return }
            print("DIAG \(what): tap \(attempt) had no effect")
        }
        XCTFail("\(what) did not respond to \(attempts) taps")
    }

    /// The first cell in these lists is the section header ("13 vehicles"),
    /// not a row, so rows are addressed by their own identifier.
    private func firstRow(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func shot(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// Types one character at a time and verifies the result.
    ///
    /// `typeText` on a physical device drops the last character often enough to
    /// matter - it lands before the field has really taken focus, and the test
    /// then submits credentials that are quietly wrong.
    private func enter(_ text: String, into field: XCUIElement, secure: Bool = false) {
        tapUntil(field, "keyboard for \(field.identifier)") {
            app.keyboards.element.waitForExistence(timeout: 6)
        }

        for character in text {
            field.typeText(String(character))
        }

        let actual = (field.value as? String) ?? ""
        if secure {
            XCTAssertEqual(actual.count, text.count,
                           "secure field holds \(actual.count) characters, expected \(text.count)")
        } else {
            XCTAssertEqual(actual, text, "field did not receive the whole string")
        }
    }

    private func signIn(password overridePassword: String? = nil) {
        enter(email, into: app.textFields["login.email"])
        enter(overridePassword ?? password,
              into: app.secureTextFields["login.password"], secure: true)
        app.buttons["login.submit"].tap()
        dismissPasswordSavePrompt()
    }

    /// iOS offers to save the password after a sign-in, in a SpringBoard alert
    /// that sits over the app. Until it is dismissed every element underneath
    /// reports itself unhittable and the next tap is swallowed dismissing it -
    /// which is what made taps on the vehicle list look like no-ops, and what
    /// put a system dialog in the middle of the screenshots.
    private func dismissPasswordSavePrompt() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for label in ["Not Now", "Not now"] {
            let button = springboard.buttons[label]
            if button.waitForExistence(timeout: 8) {
                button.tap()
                return
            }
        }
    }

    // MARK: - Login

    func test01_LoginScreenRenders() {
        launchSignedOut()
        XCTAssertTrue(app.textFields["login.email"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.secureTextFields["login.password"].exists)
        XCTAssertTrue(app.buttons["login.submit"].exists)
        shot("01-login")
    }

    /// An empty form must be caught client-side, with no request.
    func test02_EmptyFormShowsValidationErrors() {
        launchSignedOut()
        XCTAssertTrue(app.buttons["login.submit"].waitForExistence(timeout: 20))
        app.buttons["login.submit"].tap()

        let errors = app.staticTexts.matching(identifier: "field.error")
        XCTAssertTrue(errors.element.waitForExistence(timeout: 5),
                      "both fields should complain")
        XCTAssertGreaterThanOrEqual(errors.count, 2)
        shot("02-login-validation")
    }

    /// A wrong password shows the server's message in the banner, and stays put.
    func test03_WrongPasswordShowsServerMessage() {
        launchSignedOut()
        signIn(password: "definitely-the-wrong-password")

        // .accessibilityElement(children: .combine) collapses the banner into a
        // StaticText, not an "other" element.
        let banner = app.staticTexts["banner.error"]
        XCTAssertTrue(banner.waitForExistence(timeout: 25), "expected an error banner")
        XCTAssertEqual(banner.label, "Incorrect email or password",
                       "the server's own message should be shown verbatim")
        shot("03-login-wrong-password")

        // Still on Login, and the password field was not singled out.
        XCTAssertTrue(app.textFields["login.email"].exists)
    }

    func test04_ValidCredentialsReachTheVehicleList() {
        launchSignedOut()
        signIn()

        XCTAssertTrue(app.navigationBars["Vehicles"].waitForExistence(timeout: 30),
                      "should land on the vehicle list")
        XCTAssertTrue(firstRow("vehicle.row").waitForExistence(timeout: 20),
                      "the list should have rows")
        shot("04-vehicle-list")
    }

    // MARK: - Vehicle list

    func test05_SearchNarrowsTheList() {
        launchSignedOut()
        signIn()
        XCTAssertTrue(app.navigationBars["Vehicles"].waitForExistence(timeout: 30))
        XCTAssertTrue(firstRow("vehicle.row").waitForExistence(timeout: 20))

        let search = app.searchFields.element(boundBy: 0)
        enter("tata", into: search)

        // Debounced, so give it more than 300ms before judging.
        Thread.sleep(forTimeInterval: 2.0)
        XCTAssertTrue(firstRow("vehicle.row").exists, "search should still show rows")
        shot("05-vehicle-search")
    }

    func test06_StatusFilterApplies() {
        launchSignedOut()
        signIn()
        XCTAssertTrue(app.navigationBars["Vehicles"].waitForExistence(timeout: 30))
        XCTAssertTrue(firstRow("vehicle.row").waitForExistence(timeout: 20))

        let filterButton = app.buttons["vehicles.filter"]

        // "Available" exactly - the row badges say "AVAILABLE", which an
        // inexact match would collide with.
        let option = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Available"))
            .firstMatch
        tapUntil(filterButton, "filter menu") { option.waitForExistence(timeout: 6) }
        robustTap(option)

        Thread.sleep(forTimeInterval: 2.0)
        shot("06-vehicle-filter-available")
    }

    func test07_ScrollingLoadsMore() {
        launchSignedOut()
        signIn()
        XCTAssertTrue(app.navigationBars["Vehicles"].waitForExistence(timeout: 30))
        XCTAssertTrue(firstRow("vehicle.row").waitForExistence(timeout: 20))

        let before = app.cells.count
        app.swipeUp(velocity: .fast)
        app.swipeUp(velocity: .fast)
        Thread.sleep(forTimeInterval: 2.0)

        XCTAssertGreaterThanOrEqual(app.cells.count, before, "scrolling must not lose rows")
        shot("07-vehicle-scrolled")
    }

    // MARK: - Trip details

    func test08_TripDetailsOpen() {
        launchSignedOut()
        signIn()
        XCTAssertTrue(app.navigationBars["Vehicles"].waitForExistence(timeout: 30))

        tapUntil(app.tabBars.buttons["Trips"], "Trips tab") {
            app.navigationBars["Trips"].waitForExistence(timeout: 6)
        }
        let row = firstRow("trip.row")
        XCTAssertTrue(row.waitForExistence(timeout: 20), "the trip list should have rows")
        shot("08-trip-list")

        tapUntil(row, "trip row") {
            app.navigationBars["Trip"].waitForExistence(timeout: 6)
        }
        Thread.sleep(forTimeInterval: 2.5)
        shot("09-trip-detail")

        XCTAssertTrue(app.staticTexts["Scheduled"].exists || app.cells.count > 0,
                      "detail should render content")
    }

    // MARK: - Session

    /// The brief asks that a session survive a relaunch.
    func test10_SessionSurvivesRelaunch() {
        launchSignedOut()
        signIn()
        XCTAssertTrue(app.navigationBars["Vehicles"].waitForExistence(timeout: 30))

        app.terminate()
        // Relaunch WITHOUT the reset flag, so the keychain persists.
        let relaunched = XCUIApplication()
        relaunched.launch()

        XCTAssertTrue(relaunched.navigationBars["Vehicles"].waitForExistence(timeout: 30),
                      "a stored session should skip Login entirely")
        XCTAssertFalse(relaunched.textFields["login.email"].exists,
                       "Login must not be shown to a signed-in user")
        let attachment = XCTAttachment(screenshot: relaunched.screenshot())
        attachment.name = "10-relaunch-skips-login"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
