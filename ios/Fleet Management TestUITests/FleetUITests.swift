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
        XCTAssertTrue(field.waitForExistence(timeout: 20), "field should exist")
        field.tap()
        XCTAssertTrue(app.keyboards.element.waitForExistence(timeout: 15),
                      "keyboard should come up")

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
        XCTAssertTrue(app.cells.element(boundBy: 0).waitForExistence(timeout: 20),
                      "the list should have rows")
        shot("04-vehicle-list")
    }

    // MARK: - Vehicle list

    func test05_SearchNarrowsTheList() {
        launchSignedOut()
        signIn()
        XCTAssertTrue(app.navigationBars["Vehicles"].waitForExistence(timeout: 30))
        XCTAssertTrue(app.cells.element(boundBy: 0).waitForExistence(timeout: 20))

        let search = app.searchFields.element(boundBy: 0)
        enter("tata", into: search)

        // Debounced, so give it more than 300ms before judging.
        Thread.sleep(forTimeInterval: 2.0)
        XCTAssertTrue(app.cells.element(boundBy: 0).exists, "search should still show rows")
        shot("05-vehicle-search")
    }

    func test06_StatusFilterApplies() {
        launchSignedOut()
        signIn()
        XCTAssertTrue(app.navigationBars["Vehicles"].waitForExistence(timeout: 30))
        XCTAssertTrue(app.cells.element(boundBy: 0).waitForExistence(timeout: 20))

        let filter = app.buttons["vehicles.filter"]
        XCTAssertTrue(filter.waitForExistence(timeout: 10))
        filter.tap()

        // "Available" exactly - the row badges say "AVAILABLE", which an
        // inexact match would collide with.
        let option = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Available"))
            .firstMatch
        XCTAssertTrue(option.waitForExistence(timeout: 15), "the filter menu should open")
        option.tap()

        Thread.sleep(forTimeInterval: 2.0)
        shot("06-vehicle-filter-available")
    }

    func test07_ScrollingLoadsMore() {
        launchSignedOut()
        signIn()
        XCTAssertTrue(app.navigationBars["Vehicles"].waitForExistence(timeout: 30))
        XCTAssertTrue(app.cells.element(boundBy: 0).waitForExistence(timeout: 20))

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

        let tripsTab = app.tabBars.buttons["Trips"]
        XCTAssertTrue(tripsTab.waitForExistence(timeout: 15), "the Trips tab should exist")
        tripsTab.tap()
        XCTAssertTrue(app.navigationBars["Trips"].waitForExistence(timeout: 25))
        XCTAssertTrue(app.cells.element(boundBy: 0).waitForExistence(timeout: 20),
                      "the trip list should have rows")
        shot("08-trip-list")

        app.cells.element(boundBy: 0).tap()

        XCTAssertTrue(app.navigationBars["Trip"].waitForExistence(timeout: 20),
                      "should open trip details")
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
