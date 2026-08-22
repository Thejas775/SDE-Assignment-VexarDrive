//
//  LoginViewModelTests.swift
//  Fleet Management TestTests
//

import XCTest
@testable import Fleet_Management_Test

@MainActor
final class LoginViewModelTests: XCTestCase {

    private func makeSubject(
        _ repository: FakeAuthRepository = FakeAuthRepository()
    ) -> (LoginViewModel, SessionController, FakeAuthRepository) {
        let session = SessionController(
            repository: repository,
            sessionExpired: AsyncStream { $0.finish() }
        )
        return (LoginViewModel(session: session), session, repository)
    }

    private func settle(_ milliseconds: UInt64 = 120) async {
        try? await Task.sleep(nanoseconds: milliseconds * 1_000_000)
    }

    func testValidCredentialsSignIn() async {
        let (viewModel, session, _) = makeSubject()
        viewModel.email = "ops@fleet.in"
        viewModel.password = "pass-word-1"

        viewModel.submit()
        await settle()

        XCTAssertEqual(session.phase, .signedIn(Fixtures.user))
        XCTAssertNil(viewModel.formError)
        XCTAssertFalse(viewModel.isSubmitting)
    }

    /// An obviously invalid form must not cost a round trip.
    func testEmptyFormValidatesLocallyAndIssuesNoRequest() async {
        let (viewModel, _, repository) = makeSubject()

        viewModel.submit()
        await settle()

        XCTAssertNotNil(viewModel.emailError)
        XCTAssertNotNil(viewModel.passwordError)
        XCTAssertTrue(repository.loginCalls.isEmpty, "no request may be sent")
    }

    func testMalformedEmailIsRejectedLocally() async {
        let (viewModel, _, repository) = makeSubject()
        viewModel.email = "not-an-email"
        viewModel.password = "pass-word-1"

        viewModel.submit()
        await settle()

        XCTAssertNotNil(viewModel.emailError)
        XCTAssertTrue(repository.loginCalls.isEmpty)
    }

    /// A wrong password goes in the banner, never on the password field:
    /// marking the field would confirm the email exists.
    func testUnauthorizedShowsTheServerMessageWithoutBlamingAField() async {
        let repository = FakeAuthRepository()
        repository.loginResult = .failure(
            APIError(code: 401, message: "Incorrect email or password")
        )
        let (viewModel, session, _) = makeSubject(repository)
        viewModel.email = "ops@fleet.in"
        viewModel.password = "wrong"

        viewModel.submit()
        await settle()

        XCTAssertEqual(viewModel.formError, "Incorrect email or password")
        XCTAssertNil(viewModel.passwordError)
        XCTAssertNil(viewModel.emailError)
        XCTAssertEqual(session.phase, .launching, "a failed login must not sign anyone in")
    }

    func testValidationErrorAttachesToThePasswordField() async {
        let repository = FakeAuthRepository()
        repository.loginResult = .failure(APIError(
            code: 422,
            message: "String should have at least 8 characters",
            fieldErrors: ["password": "String should have at least 8 characters"]
        ))
        let (viewModel, _, _) = makeSubject(repository)
        viewModel.email = "ops@fleet.in"
        viewModel.password = "short"

        viewModel.submit()
        await settle()

        XCTAssertEqual(viewModel.passwordError, "String should have at least 8 characters")
        XCTAssertNil(viewModel.formError, "a field error should not also fill the banner")
    }

    func testOfflineShowsTheConnectionMessage() async {
        let repository = FakeAuthRepository()
        repository.loginResult = .failure(APIError.offline())
        let (viewModel, _, _) = makeSubject(repository)
        viewModel.email = "ops@fleet.in"
        viewModel.password = "pass-word-1"

        viewModel.submit()
        await settle()

        XCTAssertEqual(viewModel.formError, "No connection to the server.")
        XCTAssertFalse(viewModel.isSubmitting, "the app must not hang")
    }

    /// Disabling the button is not enough - a fast double tap can land two
    /// events before SwiftUI re-renders.
    func testDoubleSubmitSendsOneRequest() async {
        let (viewModel, _, repository) = makeSubject()
        viewModel.email = "ops@fleet.in"
        viewModel.password = "pass-word-1"

        viewModel.submit()
        viewModel.submit()
        viewModel.submit()
        await settle()

        XCTAssertEqual(repository.loginCalls.count, 1)
    }

    func testValidationErrorsClearOnTheNextAttempt() async {
        let (viewModel, _, _) = makeSubject()
        viewModel.submit()
        await settle()
        XCTAssertNotNil(viewModel.emailError)

        viewModel.email = "ops@fleet.in"
        viewModel.password = "pass-word-1"
        viewModel.submit()
        await settle()

        XCTAssertNil(viewModel.emailError)
        XCTAssertNil(viewModel.passwordError)
    }
}

@MainActor
final class SessionControllerTests: XCTestCase {

    func testStoredSessionSkipsLogin() {
        let repository = FakeAuthRepository()
        repository.stored = Fixtures.user
        let session = SessionController(
            repository: repository, sessionExpired: AsyncStream { $0.finish() }
        )

        session.restore()

        XCTAssertEqual(session.phase, .signedIn(Fixtures.user))
    }

    func testNoStoredSessionGoesToLogin() {
        let session = SessionController(
            repository: FakeAuthRepository(), sessionExpired: AsyncStream { $0.finish() }
        )

        session.restore()

        XCTAssertEqual(session.phase, .signedOut)
    }

    /// A refresh rejected during a background request must route back to Login
    /// with an explanation, not leave a dead screen.
    func testExpiredSessionRoutesBackToLoginWithANotice() async {
        var continuation: AsyncStream<Void>.Continuation!
        let stream = AsyncStream<Void> { continuation = $0 }

        let repository = FakeAuthRepository()
        repository.stored = Fixtures.user
        let session = SessionController(repository: repository, sessionExpired: stream)
        session.restore()
        XCTAssertEqual(session.phase, .signedIn(Fixtures.user))

        continuation.yield()
        try? await Task.sleep(nanoseconds: 200_000_000)

        XCTAssertEqual(session.phase, .signedOut)
        XCTAssertNotNil(session.expiryNotice)
    }

    func testSignOutClearsTheSession() async {
        let repository = FakeAuthRepository()
        repository.stored = Fixtures.user
        let session = SessionController(
            repository: repository, sessionExpired: AsyncStream { $0.finish() }
        )
        session.restore()

        await session.signOut()

        XCTAssertEqual(session.phase, .signedOut)
        XCTAssertTrue(repository.didLogout)
    }
}
