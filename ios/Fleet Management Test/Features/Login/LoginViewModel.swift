//
//  LoginViewModel.swift
//  Fleet Management Test
//

import Combine
import Foundation

@MainActor
final class LoginViewModel: ObservableObject {

    @Published var email = ""
    @Published var password = ""
    @Published var isPasswordVisible = false

    @Published private(set) var isSubmitting = false

    /// Attached to the field they belong to.
    @Published private(set) var emailError: String?
    @Published private(set) var passwordError: String?

    /// Shown as a banner above the form, for failures that belong to no single
    /// field - a wrong password among them.
    @Published private(set) var formError: String?

    private let session: SessionController

    /// Holding the task, rather than only disabling the button, is what actually
    /// prevents a double submit: a fast double tap can land two events before
    /// SwiftUI re-renders the disabled state.
    private var submission: Task<Void, Never>?

    init(session: SessionController) {
        self.session = session
    }

    deinit { submission?.cancel() }

    var canSubmit: Bool { !isSubmitting }

    func submit() {
        guard submission == nil, !isSubmitting else { return }
        formError = nil

        // Client-side first: an obviously invalid form should not cost a round
        // trip, and the user gets the answer immediately.
        guard validate() else { return }

        isSubmitting = true
        submission = Task { [weak self] in
            guard let self else { return }
            defer {
                self.isSubmitting = false
                self.submission = nil
            }
            do {
                try await self.session.signIn(email: self.email, password: self.password)
            } catch let error as APIError {
                self.present(error)
            } catch {
                self.formError = error.localizedDescription
            }
        }
    }

    // MARK: - Validation

    private func validate() -> Bool {
        emailError = nil
        passwordError = nil

        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            emailError = "Enter your email address."
        } else if !Self.looksLikeEmail(trimmed) {
            emailError = "That does not look like an email address."
        }

        if password.isEmpty {
            passwordError = "Enter your password."
        }

        return emailError == nil && passwordError == nil
    }

    /// Deliberately loose. The server is the authority on whether an address
    /// exists; this only catches typing that cannot possibly be an address.
    private static func looksLikeEmail(_ text: String) -> Bool {
        guard !text.contains(" ") else { return false }
        let parts = text.split(separator: "@", omittingEmptySubsequences: false)
        guard parts.count == 2, !parts[0].isEmpty else { return false }
        let domain = parts[1]
        return domain.contains(".") && !domain.hasPrefix(".") && !domain.hasSuffix(".")
    }

    // MARK: - Errors

    private func present(_ error: APIError) {
        emailError = nil
        passwordError = nil

        // A 422 knows which input it is talking about, so put the message there.
        if error.isValidation, !error.fieldErrors.isEmpty {
            emailError = error.fieldErrors["email"]
            passwordError = error.fieldErrors["password"]

            let unattached = error.fieldErrors
                .filter { $0.key != "email" && $0.key != "password" }
                .values
                .sorted()
            if emailError == nil && passwordError == nil {
                formError = error.message
            } else if !unattached.isEmpty {
                formError = unattached.joined(separator: "\n")
            }
            return
        }

        // Everything else - a 401 above all - goes in the banner. Marking the
        // password field as the wrong one would confirm that the email exists.
        formError = error.message
    }
}
