//
//  SignUpViewModel.swift
//  Fleet Management Test
//

import Combine
import Foundation

@MainActor
final class SignUpViewModel: ObservableObject {

    // MARK: - Input

    @Published var email = ""
    @Published var fullName = ""
    @Published var password = ""
    @Published var confirmPassword = ""
    @Published var phoneNumber = ""
    @Published var role: UserRole = .fleetManager
    @Published var licenseNumber = ""
    @Published var licenseExpiry = Date()
    @Published var isPasswordVisible = false

    // MARK: - Output

    @Published private(set) var isSubmitting = false
    @Published private(set) var emailError: String?
    @Published private(set) var fullNameError: String?
    @Published private(set) var passwordError: String?
    @Published private(set) var confirmPasswordError: String?
    @Published private(set) var phoneError: String?
    @Published private(set) var licenseNumberError: String?
    @Published private(set) var licenseExpiryError: String?
    @Published private(set) var formError: String?

    /// The licence block only applies to a driver.
    var requiresLicence: Bool { role == .driver }

    static let minimumPasswordLength = 8

    private let session: SessionController
    private var submission: Task<Void, Never>?

    init(session: SessionController) {
        self.session = session
    }

    deinit { submission?.cancel() }

    var canSubmit: Bool { !isSubmitting }

    // MARK: - Submit

    func submit() {
        guard submission == nil, !isSubmitting else { return }
        formError = nil
        guard validate() else { return }

        let draft = RegisterRequest(
            email: email,
            password: password,
            fullName: fullName,
            phoneNumber: requiresLicence ? phoneNumber : nilIfBlank(phoneNumber),
            role: role,
            licenseNumber: requiresLicence ? licenseNumber : nil,
            licenseExpiry: requiresLicence ? Self.plainDay.string(from: licenseExpiry) : nil
        )

        isSubmitting = true
        submission = Task { [weak self] in
            guard let self else { return }
            defer {
                self.isSubmitting = false
                self.submission = nil
            }
            do {
                try await self.session.signUp(draft)
            } catch let error as APIError {
                self.present(error)
            } catch {
                self.formError = error.localizedDescription
            }
        }
    }

    // MARK: - Validation

    /// Everything the client can know without asking the server. The two
    /// passwords are matched here and here only - the API takes one password.
    private func validate() -> Bool {
        emailError = nil
        fullNameError = nil
        passwordError = nil
        confirmPasswordError = nil
        phoneError = nil
        licenseNumberError = nil
        licenseExpiryError = nil

        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedEmail.isEmpty {
            emailError = "Enter an email address."
        } else if !Self.looksLikeEmail(trimmedEmail) {
            emailError = "That does not look like an email address."
        }

        let trimmedName = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedName.isEmpty {
            fullNameError = "Enter a full name."
        } else if trimmedName.count < 2 {
            fullNameError = "That is too short to be a name."
        }

        if password.isEmpty {
            passwordError = "Choose a password."
        } else if password.count < Self.minimumPasswordLength {
            passwordError = "Use at least \(Self.minimumPasswordLength) characters."
        }

        if confirmPassword.isEmpty {
            confirmPasswordError = "Type the password again."
        } else if confirmPassword != password {
            confirmPasswordError = "The two passwords do not match."
        }

        if requiresLicence {
            // The API rejects a driver without all three of these.
            if phoneNumber.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                phoneError = "A driver needs a phone number."
            }
            if licenseNumber.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                licenseNumberError = "Enter the licence number."
            }
            if licenseExpiry < Calendar.current.startOfDay(for: Date()) {
                licenseExpiryError = "The licence has already expired."
            }
        }

        return emailError == nil && fullNameError == nil && passwordError == nil
            && confirmPasswordError == nil && phoneError == nil
            && licenseNumberError == nil && licenseExpiryError == nil
    }

    // MARK: - Errors

    private func present(_ error: APIError) {
        // A 422 names the offending field in the tail of `loc`.
        if error.isValidation, !error.fieldErrors.isEmpty {
            emailError = error.fieldErrors["email"]
            fullNameError = error.fieldErrors["full_name"] ?? error.fieldErrors["fullName"]
            passwordError = error.fieldErrors["password"]
            phoneError = error.fieldErrors["phone_number"] ?? error.fieldErrors["phoneNumber"]
            licenseNumberError = error.fieldErrors["license_number"]
                ?? error.fieldErrors["licenseNumber"]
            licenseExpiryError = error.fieldErrors["license_expiry"]
                ?? error.fieldErrors["licenseExpiry"]

            let attached = [emailError, fullNameError, passwordError, phoneError,
                            licenseNumberError, licenseExpiryError].contains { $0 != nil }
            if !attached { formError = error.message }
            return
        }

        // 409 - the email or the licence is already taken. The server's wording
        // says which, so it is shown as-is.
        formError = error.message
    }

    private func nilIfBlank(_ text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private static func looksLikeEmail(_ text: String) -> Bool {
        guard !text.contains(" ") else { return false }
        let parts = text.split(separator: "@", omittingEmptySubsequences: false)
        guard parts.count == 2, !parts[0].isEmpty else { return false }
        let domain = parts[1]
        return domain.contains(".") && !domain.hasPrefix(".") && !domain.hasSuffix(".")
    }

    static let plainDay: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}
