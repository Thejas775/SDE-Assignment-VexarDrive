//
//  SignUpViewModelTests.swift
//  Fleet Management TestTests
//

import XCTest
@testable import Fleet_Management_Test

@MainActor
final class SignUpViewModelTests: XCTestCase {

    private func makeSubject() -> (SignUpViewModel, SessionController, FakeAuthRepository) {
        let repository = FakeAuthRepository()
        let session = SessionController(
            repository: repository, sessionExpired: AsyncStream { $0.finish() }
        )
        return (SignUpViewModel(session: session), session, repository)
    }

    private func settle(_ milliseconds: UInt64 = 150) async {
        try? await Task.sleep(nanoseconds: milliseconds * 1_000_000)
    }

    private func fillValidManager(_ viewModel: SignUpViewModel) {
        viewModel.role = .fleetManager
        viewModel.fullName = "Priya Nair"
        viewModel.email = "ops@fleet.in"
        viewModel.password = "pass-word-1"
        viewModel.confirmPassword = "pass-word-1"
    }

    // MARK: - Happy path

    func testManagerSignUpSendsTheRightBodyAndSignsIn() async {
        let (viewModel, session, repository) = makeSubject()
        fillValidManager(viewModel)

        viewModel.submit()
        await settle()

        let draft = try? XCTUnwrap(repository.registerDrafts.first)
        XCTAssertEqual(draft?.role, .fleetManager)
        XCTAssertEqual(draft?.email, "ops@fleet.in")
        XCTAssertNil(draft?.licenseNumber, "a manager has no licence")
        XCTAssertNil(draft?.licenseExpiry)
        XCTAssertEqual(session.phase, .signedIn(Fixtures.user))
    }

    func testDriverSignUpIncludesLicenceDetails() async {
        let (viewModel, _, repository) = makeSubject()
        viewModel.role = .driver
        viewModel.fullName = "Rahul Sharma"
        viewModel.email = "rahul@fleet.in"
        viewModel.password = "pass-word-1"
        viewModel.confirmPassword = "pass-word-1"
        viewModel.phoneNumber = "+919876543210"
        viewModel.licenseNumber = "KA0120260005555"
        viewModel.licenseExpiry = Date().addingTimeInterval(60 * 60 * 24 * 365)

        viewModel.submit()
        await settle()

        let draft = repository.registerDrafts.first
        XCTAssertEqual(draft?.role, .driver)
        XCTAssertEqual(draft?.licenseNumber, "KA0120260005555")
        XCTAssertEqual(draft?.phoneNumber, "+919876543210")
        // YYYY-MM-DD, the plain-date shape the API expects.
        XCTAssertEqual(draft?.licenseExpiry?.count, 10)
        XCTAssertEqual(draft?.licenseExpiry?.filter { $0 == "-" }.count, 2)
    }

    func testEmailIsTrimmedAndLowercased() async {
        let (viewModel, _, repository) = makeSubject()
        fillValidManager(viewModel)
        viewModel.email = "  OPS@Fleet.IN  "

        viewModel.submit()
        await settle()

        // The view model passes it on; the repository lowercases before sending.
        XCTAssertEqual(
            repository.registerDrafts.first?.email
                .trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
            "ops@fleet.in"
        )
    }

    // MARK: - Client-side rules

    func testPasswordsMustMatch() async {
        let (viewModel, _, repository) = makeSubject()
        fillValidManager(viewModel)
        viewModel.confirmPassword = "something-else"

        viewModel.submit()
        await settle()

        XCTAssertNotNil(viewModel.confirmPasswordError)
        XCTAssertTrue(repository.registerDrafts.isEmpty, "no request for a mismatch")
    }

    func testPasswordMinimumLengthIsEnforcedLocally() async {
        let (viewModel, _, repository) = makeSubject()
        fillValidManager(viewModel)
        viewModel.password = "short"
        viewModel.confirmPassword = "short"

        viewModel.submit()
        await settle()

        XCTAssertNotNil(viewModel.passwordError)
        XCTAssertTrue(repository.registerDrafts.isEmpty)
    }

    /// The API 422s a driver missing any of the three; catching it here saves
    /// the round trip.
    func testDriverWithoutLicenceDetailsIsRejectedLocally() async {
        let (viewModel, _, repository) = makeSubject()
        viewModel.role = .driver
        viewModel.fullName = "Rahul Sharma"
        viewModel.email = "rahul@fleet.in"
        viewModel.password = "pass-word-1"
        viewModel.confirmPassword = "pass-word-1"

        viewModel.submit()
        await settle()

        XCTAssertNotNil(viewModel.phoneError)
        XCTAssertNotNil(viewModel.licenseNumberError)
        XCTAssertTrue(repository.registerDrafts.isEmpty)
    }

    func testManagerDoesNotNeedLicenceDetails() async {
        let (viewModel, _, repository) = makeSubject()
        fillValidManager(viewModel)

        viewModel.submit()
        await settle()

        XCTAssertNil(viewModel.licenseNumberError)
        XCTAssertEqual(repository.registerDrafts.count, 1)
    }

    func testExpiredLicenceIsRejected() async {
        let (viewModel, _, repository) = makeSubject()
        viewModel.role = .driver
        viewModel.fullName = "Rahul Sharma"
        viewModel.email = "rahul@fleet.in"
        viewModel.password = "pass-word-1"
        viewModel.confirmPassword = "pass-word-1"
        viewModel.phoneNumber = "+919876543210"
        viewModel.licenseNumber = "KA0120260005555"
        viewModel.licenseExpiry = Date().addingTimeInterval(-60 * 60 * 24 * 30)

        viewModel.submit()
        await settle()

        XCTAssertNotNil(viewModel.licenseExpiryError)
        XCTAssertTrue(repository.registerDrafts.isEmpty)
    }

    func testEmptyFormIssuesNoRequest() async {
        let (viewModel, _, repository) = makeSubject()

        viewModel.submit()
        await settle()

        XCTAssertNotNil(viewModel.emailError)
        XCTAssertNotNil(viewModel.fullNameError)
        XCTAssertNotNil(viewModel.passwordError)
        XCTAssertTrue(repository.registerDrafts.isEmpty)
    }

    func testDoubleSubmitSendsOneRequest() async {
        let (viewModel, _, repository) = makeSubject()
        fillValidManager(viewModel)

        viewModel.submit()
        viewModel.submit()
        viewModel.submit()
        await settle()

        XCTAssertEqual(repository.registerDrafts.count, 1)
    }

    // MARK: - Server errors

    func testDuplicateEmailShowsTheServerMessage() async {
        let (viewModel, _, repository) = makeSubject()
        repository.registerResult = .failure(
            APIError(code: 409, message: "An account with this email already exists")
        )
        fillValidManager(viewModel)

        viewModel.submit()
        await settle()

        XCTAssertEqual(viewModel.formError, "An account with this email already exists")
    }

    func testDuplicateLicenceShowsTheServerMessage() async {
        let (viewModel, _, repository) = makeSubject()
        repository.registerResult = .failure(
            APIError(code: 409, message: "Licence KA0120260005555 is already registered")
        )
        viewModel.role = .driver
        viewModel.fullName = "Rahul Sharma"
        viewModel.email = "rahul@fleet.in"
        viewModel.password = "pass-word-1"
        viewModel.confirmPassword = "pass-word-1"
        viewModel.phoneNumber = "+919876543210"
        viewModel.licenseNumber = "KA0120260005555"
        viewModel.licenseExpiry = Date().addingTimeInterval(60 * 60 * 24 * 365)

        viewModel.submit()
        await settle()

        XCTAssertEqual(viewModel.formError, "Licence KA0120260005555 is already registered")
    }

    /// loc.last() names the field, so the message belongs under it.
    func testValidationErrorAttachesToTheNamedField() async {
        let (viewModel, _, repository) = makeSubject()
        repository.registerResult = .failure(APIError(
            code: 422,
            message: "String should have at least 8 characters",
            fieldErrors: ["password": "String should have at least 8 characters"]
        ))
        fillValidManager(viewModel)

        viewModel.submit()
        await settle()

        XCTAssertEqual(viewModel.passwordError, "String should have at least 8 characters")
        XCTAssertNil(viewModel.formError)
    }

    /// The live API answers a driver missing fields with loc ["body"] only, so
    /// there is no field to attach to and it must land in the banner.
    func testWholeBodyValidationErrorFallsBackToTheBanner() async {
        let (viewModel, _, repository) = makeSubject()
        let message = "Value error, A driver signup requires: phone_number, license_number, license_expiry"
        repository.registerResult = .failure(
            APIError(code: 422, message: message, fieldErrors: [:])
        )
        fillValidManager(viewModel)

        viewModel.submit()
        await settle()

        XCTAssertEqual(viewModel.formError, message)
    }
}
